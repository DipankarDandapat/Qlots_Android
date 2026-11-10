"""Create a demo account with realistic Indian household data, 14 months of snapshots, goals and a saved plan.

DEVELOPMENT ONLY - used for local testing and Play Store screenshots. Never run against production.

    cd backend
    DATABASE_URL=sqlite:///./qlots.db .venv/bin/python scripts/seed_demo.py
    # sign in with demo@qlots.app / DemoPass#2026
"""
from __future__ import annotations

import json
import os
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
if os.getenv("APP_ENV") == "production":
    raise SystemExit("Refusing to seed demo data in production")

from sqlalchemy import select  # noqa: E402

from database.db import Base, SessionLocal, engine, run_migrations, run_v3_migrations  # noqa: E402
from models.models import Assumptions, Entry, Goal, GoalLog, ProjectionPlan, Snapshot, User  # noqa: E402
from services.auth import hash_password  # noqa: E402
from services.dates import add_months, today_local  # noqa: E402
from services.engine import SCENARIO_FACTOR, simulate  # noqa: E402
from services.finance import collect_finances, entry_value, monthly_flow  # noqa: E402
from services.insights import _plan_series  # noqa: E402

EMAIL, PASSWORD = "demo@qlots.app", "DemoPass#2026"
Base.metadata.create_all(bind=engine)
run_migrations()
run_v3_migrations()
db = SessionLocal()
today = today_local()

from sqlalchemy import text  # noqa: E402

for table in ("entries", "assumptions", "snapshots", "goals", "projection_plans"):  # clear rows orphaned by older runs
    db.execute(text(f"DELETE FROM {table} WHERE user_id NOT IN (SELECT id FROM users)"))
db.execute(text("DELETE FROM goal_logs WHERE goal_id NOT IN (SELECT id FROM goals)"))
db.commit()
existing = db.scalar(select(User).where(User.email == EMAIL))
if existing:
    db.delete(existing)
    db.commit()
user = User(email=EMAIL, name="Aarav Mehta", password_hash=hash_password(PASSWORD))
db.add(user)
db.commit()
db.refresh(user)
uid = user.id


def entry(kind, category, name, amount, **kw):
    db.add(Entry(user_id=uid, kind=kind, category=category, name=name, amount=amount, **kw))


entry("asset", "bank_balance", "HDFC Savings", 420000, institution="HDFC Bank")
entry("asset", "fixed_deposit", "SBI Fixed Deposit", 500000, principal=500000, annual_rate=7.1, frequency="quarterly", institution="SBI", start_date=add_months(today, -14), maturity_date=add_months(today, 10))
entry("asset", "fixed_deposit", "ICICI 1-year FD", 300000, principal=300000, annual_rate=6.9, frequency="quarterly", institution="ICICI Bank", start_date=add_months(today, -9), maturity_date=add_months(today, 2))
entry("asset", "recurring_deposit", "Post Office RD", 10000, principal=10000, annual_rate=6.7, frequency="monthly", institution="India Post", start_date=add_months(today, -8), maturity_date=add_months(today, 16))
entry("asset", "mutual_funds", "Nifty 50 Index Fund", 680000, institution="Zerodha")
entry("asset", "stocks", "Direct equity portfolio", 320000, institution="Groww")
entry("asset", "property", "2BHK apartment, Pune", 3800000)
entry("asset", "gold", "Gold jewellery & coins", 110000, growth_rate=7)
entry("liability", "home_loan", "Home loan", 2400000, principal=3200000, annual_rate=8.4, emi=31500, tenure_months=180, institution="HDFC Ltd")
entry("liability", "car_loan", "Car loan", 310000, principal=700000, annual_rate=9.5, emi=14500, tenure_months=24, institution="Axis Bank")
entry("liability", "credit_card", "Credit card dues", 45000, principal=45000, annual_rate=36, emi=7500, institution="ICICI Bank")
entry("income", "salary", "Salary (take-home)", 185000, frequency="monthly")
entry("income", "business", "Freelance design", 15000, frequency="monthly")
entry("income", "salary", "Annual bonus", 120000, frequency="yearly", start_date=date(today.year - 1, 3, 31))
entry("expense", "housing", "Household & maintenance", 38000, frequency="monthly")
entry("expense", "food", "Groceries & dining", 16000, frequency="monthly")
entry("expense", "transport", "Fuel & commute", 6500, frequency="monthly")
entry("expense", "utilities", "Electricity, internet, phone", 5500, frequency="monthly")
entry("expense", "entertainment", "Subscriptions & outings", 7000, frequency="monthly")
entry("expense", "education", "School fees (quarterly)", 45000, frequency="quarterly", start_date=date(today.year - 1, 4, 10))
entry("expense", "health", "Health insurance premium", 36000, frequency="yearly", start_date=date(today.year - 1, 12, 5))
entry("expense", "entertainment", "Annual family holiday", 60000, frequency="yearly", start_date=date(today.year - 1, 5, 20))
db.add(Assumptions(user_id=uid, salary_growth=8, equity_return=10, property_growth=6, inflation=5, monthly_investment=25000))
db.commit()

assets, liabilities, income, expenses = collect_finances(db, uid)
assets_now = sum(entry_value(a, today) for a in assets)
liab_now = sum(l.amount for l in liabilities)
inc, exp, emi = monthly_flow(income, "income"), monthly_flow(expenses, "expense"), sum(l.emi for l in liabilities)

# 14 months of month-start snapshots plus weekly ones for the last 8 weeks (values drift towards today's).
points = []
for k in range(14, 0, -1):
    d = add_months(today, -k)
    points.append((d, k))
for w in range(8, 0, -1):
    points.append((today.fromordinal(today.toordinal() - 7 * w), w * 7 / 30))
nw_at: dict[date, float] = {}
for d, k in sorted(points):
    wobble = 1 + (0.004 if int(k * 10) % 2 else -0.003)
    a = assets_now * (1 - 0.0115 * k) * wobble
    l = liab_now + 32000 * k + (6000 * k if k > 6 else 0)
    inc_k = inc * (1 - 0.004 * k)
    exp_k = exp * (1 + 0.006 * k)
    db.add(Snapshot(user_id=uid, snapshot_date=d, net_worth=round(a - l, 2), assets=round(a, 2), liabilities=round(l, 2),
                    monthly_income=round(inc_k, 2), monthly_expenses=round(exp_k, 2), monthly_emi=round(emi + 2500 * min(k, 8) / 8, 2),
                    monthly_investment=25000, source="manual" if k in (12, 6) else "auto", note="Year check-in" if k == 12 else ("Half-year review" if k == 6 else "")))
    nw_at[d] = a - l
db.add(Snapshot(user_id=uid, snapshot_date=today, net_worth=round(assets_now - liab_now, 2), assets=round(assets_now, 2), liabilities=round(liab_now, 2),
                monthly_income=round(inc, 2), monthly_expenses=round(exp, 2), monthly_emi=round(emi, 2), monthly_investment=25000, source="nightly"))
db.commit()

# Saved plan from 5 months ago, scaled so month 0 equals what the net worth was then.
plan_date = add_months(today, -5)
series = _plan_series(assets, liabilities, income, expenses, db.scalar(select(Assumptions).where(Assumptions.user_id == uid)))
start_nw = min(nw_at.items(), key=lambda kv: abs((kv[0] - plan_date).days))[1]
scale = start_nw / series["base"][0]
series = {k: [round(v * scale, 2) for v in vals] for k, vals in series.items()}
# Make the saved plan a little more cautious than what actually happened, so the demo shows 'ahead of plan'.
series = {k: [round(vals[0] + (v - vals[0]) * 0.55, 2) for v in vals] for k, vals in series.items()}
db.add(ProjectionPlan(user_id=uid, created_on=plan_date, label="My 5-year plan", source="manual", starting_net_worth=round(start_nw, 2), horizon_months=60,
                      payload=json.dumps(series), assumptions=json.dumps({"salary_growth": 8, "equity_return": 10, "property_growth": 6, "inflation": 5, "monthly_investment": 25000})))

liquid = sum(entry_value(a, today) for a in assets if a.category in ("cash", "bank_balance", "savings"))
essential = exp + emi


def goal(name, kind, target, start, current, monthly, ret, target_date, auto=False, created_months_ago=10):
    g = Goal(user_id=uid, name=name, kind=kind, target_amount=target, start_amount=start, current_amount=current, monthly_contribution=monthly,
             expected_return=ret, target_date=target_date, auto_link_cash=auto)
    db.add(g)
    db.commit()
    db.refresh(g)
    g.created_at = g.created_at.replace(year=add_months(today, -created_months_ago).year, month=add_months(today, -created_months_ago).month, day=1)
    steps = created_months_ago
    for i in range(steps + 1):
        frac = i / steps
        db.add(GoalLog(goal_id=g.id, log_date=add_months(today, -(steps - i)), amount=round(start + (current - start) * frac, 2)))
    db.commit()


goal("Emergency fund", "emergency_fund", round(essential * 6, -3), 180000, liquid, 15000, 4, add_months(today, 14), auto=True)
goal("Child's education fund", "education", 1500000, 350000, 610000, 20000, 9, add_months(today, 84), created_months_ago=12)
goal("Home renovation", "home_deposit", 800000, 60000, 265000, 25000, 6.5, add_months(today, 18), created_months_ago=8)
db.commit()
print(f"Seeded {EMAIL} / {PASSWORD}  net worth ₹{assets_now - liab_now:,.0f}")
