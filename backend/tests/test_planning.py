import os
import sys
from datetime import date, timedelta
from pathlib import Path
from uuid import uuid4

os.environ["DATABASE_URL"] = "sqlite:///./test_qlots.db"
os.environ["APP_ENV"] = "test"
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest
from fastapi.testclient import TestClient

from main import app
from models.models import Assumptions, Entry as _Entry
from services.dates import add_months, financial_year_end, today_local
from services.engine import SimConfig, build_breakdown, inr, simulate
from services.finance import fd_value, project, rd_value
from services.planning import goal_contribution, loan_payoff, months_to_target, required_monthly
from services.scheduler import seconds_until_midnight

client = TestClient(app)


def Entry(**kw):
    """Entry with the column defaults the database would apply (SQLAlchemy applies them on insert only)."""
    base = dict(institution="", amount=0.0, principal=0.0, annual_rate=0.0, frequency="monthly", emi=0.0, tenure_months=0, growth_rate=0.0, notes="")
    return _Entry(**{**base, **kw})


FLAT = dict(salary_growth=0, equity_return=0, property_growth=0, inflation=0, monthly_investment=0)


def register():
    email = f"{uuid4().hex}@example.com"
    r = client.post("/api/auth/register", json={"name": "Tester", "email": email, "password": "safe-test-password-123"})
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


def put(headers, **entry):
    r = client.post("/api/entries", json=entry, headers=headers)
    assert r.status_code == 201, r.text
    return r.json()


# ---------------------------------------------------------------- pure calculations
def test_inr_indian_grouping():
    assert inr(1234567.8) == "₹12,34,568"
    assert inr(999) == "₹999"
    assert inr(-100000) == "-₹1,00,000"


def test_add_months_clamps_and_financial_year():
    assert add_months(date(2026, 1, 31), 1) == date(2026, 2, 28)
    assert add_months(date(2024, 2, 29), 12) == date(2025, 2, 28)
    assert financial_year_end(date(2026, 11, 10)) == date(2027, 3, 31)
    assert financial_year_end(date(2026, 2, 1)) == date(2026, 3, 31)


def test_growth_asset_compounds_annually():
    stock = Entry(kind="asset", category="stocks", name="S", amount=100000)
    a = Assumptions(**{**FLAT, "equity_return": 10})
    result = project([stock], [], [], [], a, 3)
    assert [r["net_worth"] for r in result["projections"]] == [110000.0, 121000.0, 133100.0]


def test_scenarios_scale_returns():
    stock = Entry(kind="asset", category="stocks", name="S", amount=100000)
    a = Assumptions(**{**FLAT, "equity_return": 10})
    cons = project([stock], [], [], [], a, 1, "conservative")["projections"][0]["net_worth"]
    opt = project([stock], [], [], [], a, 1, "optimistic")["projections"][0]["net_worth"]
    assert cons == pytest.approx(106500) and opt == pytest.approx(112500)


def test_sip_future_value_matches_annuity_formula():
    a = Assumptions(**{**FLAT, "equity_return": 12, "monthly_investment": 10000})
    income = [Entry(kind="income", category="salary", name="s", amount=10000, frequency="monthly")]
    result = project([], [], income, [], a, 1)
    expected = 10000 * (((1.01) ** 12 - 1) / 0.01)
    assert result["projections"][0]["assets"] == pytest.approx(expected, abs=0.01)
    # SIP is paid out of income, so cash stays at zero and nothing is double counted
    assert result["projections"][0]["liabilities"] == 0


def test_emi_stops_after_loan_is_repaid():
    # 12 interest-free EMIs of 1,000 clear a 12,000 loan; income covers it exactly then keeps accruing.
    loan = Entry(id=1, kind="liability", category="personal_loan", name="L", amount=12000, annual_rate=0, emi=1000, tenure_months=12)
    income = [Entry(kind="income", category="salary", name="s", amount=1000, frequency="monthly")]
    a = Assumptions(**FLAT)
    result = project([], [loan], income, [], a, 2)
    y1, y2 = result["projections"]
    assert y1["liabilities"] == 0 and y1["assets"] == 0
    assert y2["assets"] == 12000  # EMI no longer deducted in year two
    assert y2["net_worth"] == 12000


def test_fd_rd_formulas():
    fd = Entry(kind="asset", category="fixed_deposit", name="FD", amount=100000, principal=100000, annual_rate=8, frequency="quarterly", start_date=date(2025, 1, 1), maturity_date=date(2027, 1, 1))
    assert fd_value(fd, date(2027, 1, 1)) == pytest.approx(100000 * 1.02 ** (4 * 2.0), rel=1e-3)
    assert fd_value(fd, date(2030, 1, 1)) == pytest.approx(fd_value(fd, date(2027, 1, 1)))  # growth stops at maturity
    rd = Entry(kind="asset", category="recurring_deposit", name="RD", amount=5000, principal=5000, annual_rate=6, start_date=date(2025, 1, 1), maturity_date=date(2026, 1, 1))
    i = 0.005
    assert rd_value(rd, date(2026, 1, 1)) == pytest.approx(5000 * ((1 + i) ** 12 - 1) / i * (1 + i))


def test_rd_instalments_leave_cash_so_net_worth_is_not_double_counted():
    start = today_local()
    rd = Entry(kind="asset", category="recurring_deposit", name="RD", amount=1000, principal=1000, annual_rate=0.0001, start_date=start, maturity_date=add_months(start, 24))
    income = [Entry(kind="income", category="salary", name="s", amount=1000, frequency="monthly")]
    a = Assumptions(**FLAT)
    y1 = project([rd], [], income, [], a, 1)["projections"][0]
    assert y1["net_worth"] == pytest.approx(12000, abs=5)  # 12 months of income, not 24k


def test_cash_deficit_becomes_liability_and_horizon_dates():
    income = [Entry(kind="income", category="salary", name="s", amount=50, frequency="monthly")]
    expenses = [Entry(kind="expense", category="living", name="l", amount=100, frequency="monthly")]
    r = project([], [], income, expenses, Assumptions(**FLAT), 1)["projections"][0]
    assert (r["assets"], r["liabilities"], r["net_worth"]) == (0, 600, -600)
    assert r["date"] == add_months(today_local(), 12).isoformat()


def test_real_net_worth_discounts_by_inflation():
    cash = Entry(kind="asset", category="cash", name="c", amount=100000)
    r = project([cash], [], [], [], Assumptions(**{**FLAT, "inflation": 5}), 2)["projections"][1]
    assert r["real_net_worth"] == pytest.approx(100000 / 1.05 ** 2, abs=0.01)


def test_breakdown_lists_formulas_and_totals_match_projection():
    stock = Entry(kind="asset", category="stocks", name="Equity", amount=200000)
    fd = Entry(kind="asset", category="fixed_deposit", name="FD", amount=100000, principal=100000, annual_rate=7, frequency="quarterly", start_date=today_local(), maturity_date=add_months(today_local(), 60))
    cash = Entry(kind="asset", category="cash", name="Cash", amount=50000)
    a = Assumptions(**{**FLAT, "equity_return": 10, "monthly_investment": 5000, "inflation": 5})
    income = [Entry(kind="income", category="salary", name="s", amount=100000, frequency="monthly")]
    expenses = [Entry(kind="expense", category="living", name="l", amount=60000, frequency="monthly")]
    b = build_breakdown([stock, fd, cash], [], income, expenses, a, 2)
    proj = project([stock, fd, cash], [], income, expenses, a, 2)["projections"][1]
    assert b["totals"]["net_worth"] == pytest.approx(proj["net_worth"], abs=0.02)
    assert sum(r["projected"] for r in b["asset_rows"]) == pytest.approx(b["totals"]["assets"], abs=0.05)
    names = {r["name"]: r for r in b["asset_rows"]}
    assert "× (1 + 10.00%)^2" in names["Equity"]["formula"]
    assert "(1 + 7% ÷ 4)^(4 ×" in names["FD"]["formula"]
    assert b["end_date"] == add_months(today_local(), 24).isoformat()
    assert b["financial_year_end"].endswith("-03-31")
    assert b["inflation_adjusted"]["net_worth"] < b["totals"]["net_worth"]


def test_extra_saving_and_extra_loan_payment_change_outcome():
    loan = Entry(id=1, kind="liability", category="home_loan", name="H", amount=500000, annual_rate=10, emi=10000)
    a = Assumptions(**{**FLAT, "equity_return": 10})
    income = [Entry(kind="income", category="salary", name="s", amount=40000, frequency="monthly")]
    base = simulate([], [loan], income, [], a, 36)["points"][-1]["net_worth"]
    extra = simulate([], [loan], income, [], a, 36, cfg=SimConfig(extra_loan_payment=5000))["points"][-1]["net_worth"]
    assert extra > base  # paying 10% debt early beats leaving cash at 0%


def test_goal_math():
    assert required_monthly(120000, 0, 0, 12) == 10000
    assert required_monthly(120000, 0, 12, 12) == pytest.approx(120000 * 0.01 / (1.01 ** 12 - 1), rel=1e-9)
    assert required_monthly(100, 500, 0, 12) == 0
    n = months_to_target(120000, 0, 10000, 0)
    assert n == 12
    n2 = months_to_target(120000, 0, required_monthly(120000, 0, 12, 12), 12)
    assert n2 == 12
    out = goal_contribution(120000, 20000, 6, 24)
    assert out["path"][-1]["amount"] == pytest.approx(120000, abs=1)
    assert out["total_contributions"] + out["growth_on_money"] + 20000 == pytest.approx(120000, abs=1)


def test_loan_payoff_strategies_save_interest():
    loans = [
        Entry(id=1, kind="liability", category="credit_card", name="Card", amount=100000, annual_rate=36, emi=5000),
        Entry(id=2, kind="liability", category="home_loan", name="Home", amount=500000, annual_rate=8, emi=8000),
    ]
    res = loan_payoff(loans, 5000, None)
    by = {s["key"]: s for s in res["strategies"]}
    assert by["avalanche"]["total_interest"] < by["minimum"]["total_interest"]
    assert by["avalanche"]["total_interest"] <= by["snowball"]["total_interest"]
    assert by["avalanche"]["months"] < by["minimum"]["months"]
    assert by["avalanche"]["interest_saved"] > 0 and by["avalanche"]["months_saved"] > 0
    chosen = loan_payoff(loans, 5000, 2)
    assert any(s["key"] == "chosen" for s in chosen["strategies"])


def test_seconds_until_midnight_is_within_a_day():
    assert 0 < seconds_until_midnight() <= 86400


# ---------------------------------------------------------------- API
def seed(headers):
    put(headers, kind="asset", category="cash", name="Savings", amount=200000)
    put(headers, kind="asset", category="stocks", name="Portfolio", amount=800000)
    put(headers, kind="asset", category="property", name="Flat", amount=3000000)
    put(headers, kind="asset", category="fixed_deposit", name="Bank FD", principal=500000, amount=500000, annual_rate=7, frequency="quarterly", start_date=(today_local() - timedelta(days=200)).isoformat(), maturity_date=(today_local() + timedelta(days=60)).isoformat())
    put(headers, kind="liability", category="home_loan", name="Home loan", amount=2000000, annual_rate=8.5, emi=25000)
    put(headers, kind="liability", category="credit_card", name="Card", amount=60000, annual_rate=36, emi=6000)
    put(headers, kind="income", category="salary", name="Salary", amount=150000, frequency="monthly")
    put(headers, kind="expense", category="living", name="Household", amount=60000, frequency="monthly")
    put(headers, kind="expense", category="education", name="School fees", amount=90000, frequency="quarterly", start_date="2026-01-10")


def test_dashboard_has_all_horizons_with_dates():
    h = register()
    seed(h)
    d = client.get("/api/dashboard", headers=h).json()
    horizons = d["projections"]["horizons"]
    assert [x["year"] for x in horizons] == [1, 2, 3, 5]
    assert horizons[0]["date"] == add_months(today_local(), 12).isoformat()
    assert horizons[3]["financial_year_end"].endswith("-03-31")
    assert d["projections"]["two_year"]["net_worth"] != d["projections"]["one_year"]["net_worth"]
    assert horizons[0]["real_net_worth"] < horizons[0]["net_worth"]


def test_snapshots_manual_nightly_and_history_shape():
    from services.snapshots import run_nightly_refresh
    h = register()
    seed(h)
    client.get("/api/dashboard", headers=h)
    run_nightly_refresh()
    snap = client.post("/api/snapshots", json={"snapshot_date": (today_local() - timedelta(days=40)).isoformat(), "assets": 4000000, "liabilities": 2300000, "note": "Year-end"}, headers=h)
    assert snap.status_code == 201 and snap.json()["net_worth"] == 1700000 and snap.json()["source"] == "manual"
    assert client.post("/api/snapshots", json={"snapshot_date": (today_local() + timedelta(days=2)).isoformat()}, headers=h).status_code == 422
    # a dashboard load must not overwrite a manual snapshot for the same day
    client.post("/api/snapshots", json={"assets": 1, "liabilities": 0}, headers=h)
    client.get("/api/dashboard", headers=h)
    today_rows = [s for s in client.get("/api/history", headers=h).json() if s["date"] == today_local().isoformat()]
    assert today_rows[0]["assets"] == 1 and today_rows[0]["source"] == "manual"
    rows = client.get("/api/snapshots", headers=h).json()["snapshots"]
    assert [r["date"] for r in rows] == sorted(r["date"] for r in rows)
    assert client.delete(f"/api/snapshots/{rows[0]['id']}", headers=h).status_code == 204


def test_history_returns_latest_365_oldest_first():
    from database.db import SessionLocal
    from models.models import Snapshot
    h = register()
    uid = client.get("/api/me", headers=h).json()["id"]
    db = SessionLocal()
    for i in range(400):
        db.add(Snapshot(user_id=uid, snapshot_date=today_local() - timedelta(days=i), net_worth=1000 + i))
    db.commit(); db.close()
    rows = client.get("/api/history", headers=h).json()
    assert len(rows) == 365 and rows[-1]["date"] == today_local().isoformat() and rows[0]["date"] < rows[-1]["date"]


def test_insights_payload():
    h = register()
    seed(h)
    data = client.get("/api/insights", headers=h).json()
    groups = {g["group"]: g for g in data["allocation"]["groups"]}
    assert set(groups) >= {"Cash", "Deposits", "Investments", "Property"}
    assert sum(g["pct"] for g in data["allocation"]["groups"]) == pytest.approx(100, abs=0.2)
    months = data["cashflow"]["months"]
    assert len(months) == 12 and any(m["is_current"] for m in months)
    fee_months = [m for m in months if m["expenses"] > 60000]
    assert fee_months and len(fee_months) <= 5 and fee_months[0]["spike"]   # quarterly fees show up as seasonal spikes
    assert data["savings_rate"]["current_rate"] == pytest.approx((150000 - 90000 - 31000) / 150000 * 100, abs=0.1)
    assert data["debt"]["total_balance"] == 2060000
    card = next(l for l in data["debt"]["loans"] if l["name"] == "Card")
    assert card["payoff_date"] and card["months_left"] < 20
    assert data["deposits"]["deposits"][0]["maturity_value"] > 500000
    assert data["deposits"]["deposits"][0]["status"] == "maturing_soon"


def test_goals_crud_and_contribution():
    h = register()
    seed(h)
    target = (today_local() + timedelta(days=366)).isoformat()
    g = client.post("/api/goals", json={"name": "Home deposit", "kind": "home_deposit", "target_amount": 600000, "current_amount": 120000, "expected_return": 6, "target_date": target, "monthly_contribution": 30000}, headers=h)
    assert g.status_code == 201
    gv = g.json()
    assert gv["progress_pct"] == 20.0 and gv["monthly_required"] > 0 and gv["projected_completion"]
    r = client.post(f"/api/goals/{gv['id']}/progress", json={"current_amount": 300000}, headers=h).json()
    assert r["progress_pct"] == 50.0 and r["history"][-1]["amount"] == 300000
    em = client.post("/api/goals", json={"name": "Emergency", "kind": "emergency_fund", "target_amount": 540000, "auto_link_cash": True}, headers=h).json()
    assert em["current_amount"] == 200000  # linked to cash
    assert len(client.get("/api/goals", headers=h).json()["goals"]) == 2
    other = register()
    assert client.get("/api/goals", headers=other).json()["goals"] == []
    assert client.delete(f"/api/goals/{gv['id']}", headers=other).status_code == 404
    assert client.delete(f"/api/goals/{gv['id']}", headers=h).status_code == 204


def test_planning_endpoints():
    h = register()
    seed(h)
    out = client.post("/api/planning/outlook", json={"years": 3, "extra_monthly_saving": 5000}, headers=h).json()
    assert len(out["scenarios"]["base"]) == 37
    assert out["scenarios"]["optimistic"][-1]["net_worth"] > out["scenarios"]["base"][-1]["net_worth"] > out["scenarios"]["conservative"][-1]["net_worth"]
    assert out["scenarios"]["base"][-1]["real_net_worth"] < out["scenarios"]["base"][-1]["net_worth"]
    bd = client.post("/api/planning/breakdown?scenario=base", json={"years": 2}, headers=h).json()
    assert bd["asset_rows"] and bd["liability_rows"] and bd["assumptions"] and bd["projected_health"]["score"] >= 0
    wi = client.post("/api/planning/what-if", json={"years": 5, "extra_monthly_saving": 5000, "extra_loan_payment": 3000, "purchase_amount": 400000, "purchase_in_months": 6, "delay_months": 12}, headers=h).json()
    keys = [r["key"] for r in wi["runs"]]
    assert keys[0] == "baseline" and "combined" in keys and "delay" in keys
    save = next(r for r in wi["runs"] if r["key"] == "save_more")
    assert save["delta_vs_baseline"] > 0
    ef = client.get("/api/planning/emergency-fund?months=6", headers=h).json()
    assert ef["monthly_essential"] == 121000 and ef["target"] == 121000 * 6
    assert ef["current_liquid"] == 200000 and ef["gap"] == pytest.approx(max(0, ef["target"] - 200000))
    assert client.get("/api/planning/emergency-fund?months=99", headers=h).status_code == 422
    gc = client.post("/api/planning/goal-contribution", json={"target_amount": 1000000, "current_amount": 100000, "annual_return": 8, "months": 36}, headers=h).json()
    assert gc["monthly_required"] > 0 and gc["path"][-1]["amount"] == pytest.approx(1000000, abs=2)
    lp = client.post("/api/planning/loan-payoff", json={"extra_monthly": 10000}, headers=h).json()
    assert {s["key"] for s in lp["strategies"]} >= {"minimum", "avalanche", "snowball"}
    assert client.get("/api/planning/deposits", headers=h).json()["summary"]["count"] == 1


def test_projection_plan_vs_actual():
    from database.db import SessionLocal
    from models.models import ProjectionPlan, Snapshot
    h = register()
    seed(h)
    plans = client.get("/api/projection/plans", headers=h).json()["plans"]
    assert len(plans) == 1 and plans[0]["source"] == "auto"
    plan_id = plans[0]["id"]
    # back-date the plan by 6 months and add later snapshots, one above and one below the projection
    db = SessionLocal()
    plan = db.get(ProjectionPlan, plan_id)
    plan.created_on = add_months(today_local(), -6)
    uid = plan.user_id
    db.add(Snapshot(user_id=uid, snapshot_date=plan.created_on, net_worth=plan.starting_net_worth))
    db.add(Snapshot(user_id=uid, snapshot_date=add_months(today_local(), -3), net_worth=plan.starting_net_worth * 1.01))
    db.commit(); db.close()
    client.get("/api/dashboard", headers=h)
    cmp = client.get(f"/api/projection/plans/{plan_id}/vs-actual", headers=h).json()
    assert len(cmp["projected"]) == 61 and len(cmp["actual"]) >= 2
    s = cmp["summary"]
    assert s["status"] in ("ahead", "on_track", "behind") and s["projected_low"] <= s["projected_high"]
    new = client.post("/api/projection/plans", json={"label": "After raise"}, headers=h)
    assert new.status_code == 201 and new.json()["label"] == "After raise"
    assert client.get("/api/projection/plans/9999999/vs-actual", headers=h).status_code == 404


def test_deleting_account_cascades_to_all_user_data():
    from database.db import SessionLocal
    from models.models import Entry as RealEntry, Goal, Snapshot
    h = register()
    seed(h)
    client.post("/api/goals", json={"name": "G", "target_amount": 1000}, headers=h)
    client.get("/api/dashboard", headers=h)
    uid = client.get("/api/me", headers=h).json()["id"]
    assert client.delete("/api/account", headers=h).status_code == 204
    db = SessionLocal()
    try:
        for model in (RealEntry, Goal, Snapshot):
            assert db.query(model).filter(model.user_id == uid).count() == 0
    finally:
        db.close()
