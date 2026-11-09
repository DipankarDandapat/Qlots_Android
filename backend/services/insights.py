"""Read-only analytics for the Insights screens (charts) and saved-projection tracking."""
from __future__ import annotations

import calendar
import json
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from models.models import Entry, ProjectionPlan, Snapshot
from services.dates import add_months, month_start, today_local
from services.engine import SCENARIO_FACTOR, inr, simulate
from services.finance import (
    CASH_CATS, collect_finances, entry_value, get_assumptions, monthly_flow,
)
from services.snapshots import snapshot_dict

ALLOCATION_GROUPS = [
    ("Cash", ("cash", "bank_balance", "savings")),
    ("Deposits", ("fixed_deposit", "recurring_deposit")),
    ("Investments", ("stocks", "mutual_funds", "etfs")),
    ("Property", ("property", "real_estate")),
]


def allocation_groups(assets: list[Entry], on_date: date | None = None) -> dict:
    on_date = on_date or today_local()
    buckets: dict[str, dict] = {name: {"value": 0.0, "items": {}} for name, _ in ALLOCATION_GROUPS}
    buckets["Other"] = {"value": 0.0, "items": {}}
    for a in assets:
        group = next((name for name, cats in ALLOCATION_GROUPS if a.category in cats), "Other")
        value = entry_value(a, on_date)
        buckets[group]["value"] += value
        buckets[group]["items"][a.category] = buckets[group]["items"].get(a.category, 0.0) + value
    total = sum(b["value"] for b in buckets.values())
    groups = [
        {"group": name, "value": round(b["value"], 2), "pct": round(b["value"] / total * 100, 1) if total else 0.0,
         "categories": [{"category": c, "value": round(v, 2)} for c, v in sorted(b["items"].items(), key=lambda kv: -kv[1])]}
        for name, b in buckets.items() if b["value"] > 0
    ]
    groups.sort(key=lambda g: -g["value"])
    return {"total": round(total, 2), "groups": groups}


# --------------------------------------------------------------------------- monthly cash flow
_STEP = {"quarterly": 3, "half_yearly": 6, "yearly": 12}


def _month_amount(e: Entry, year: int, month: int) -> float:
    """Amount an income/expense record contributes in a given calendar month, from its schedule."""
    first, last = date(year, month, 1), date(year, month, calendar.monthrange(year, month)[1])
    if e.start_date and e.start_date > last:
        return 0.0
    if e.maturity_date and e.maturity_date < first:
        return 0.0
    days = last.day
    if e.frequency == "daily":
        return e.amount * days
    if e.frequency == "weekly":
        return e.amount * days / 7
    if e.frequency in _STEP:
        anchor = e.start_date.month if e.start_date else (e.created_at.month if e.created_at else 1)
        return e.amount if (month - anchor) % _STEP[e.frequency] == 0 else 0.0
    return e.amount


def monthly_cashflow(income: list[Entry], expenses: list[Entry], liabilities: list[Entry], months_back: int = 5, months_ahead: int = 6) -> dict:
    today = today_local()
    first = add_months(month_start(today), -months_back)
    avg_income, avg_expense = monthly_flow(income, "income"), monthly_flow(expenses, "expense")
    emi = sum(l.emi for l in liabilities)
    rows = []
    for i in range(months_back + months_ahead + 1):
        d = add_months(first, i)
        inc_items = [(e.name, _month_amount(e, d.year, d.month)) for e in income]
        exp_items = [(e.name, _month_amount(e, d.year, d.month)) for e in expenses]
        inc, exp = sum(v for _, v in inc_items), sum(v for _, v in exp_items)
        seasonal = [name for e, (name, v) in zip(expenses, exp_items) if e.frequency in _STEP and v > 0]
        seasonal_income = [name for e, (name, v) in zip(income, inc_items) if e.frequency in _STEP and v > 0]
        rows.append({
            "month": d.strftime("%Y-%m"), "label": d.strftime("%b"), "year": d.year, "is_current": d.year == today.year and d.month == today.month,
            "is_future": d > month_start(today), "income": round(inc, 2), "expenses": round(exp, 2), "emi": round(emi, 2),
            "surplus": round(inc - exp - emi, 2),
            "spike": bool(avg_expense and exp > avg_expense * 1.10), "seasonal_items": seasonal, "seasonal_income_items": seasonal_income,
        })
    highlights = []
    for r in sorted(rows, key=lambda x: -x["expenses"]):
        if r["spike"]:
            highlights.append(f"{r['label']} {r['year']}: higher spending ({inr(r['expenses'])}) from " + ", ".join(r["seasonal_items"] or ["recurring items"]))
    recurring_expense = sum(e.amount for e in expenses if e.frequency in ("monthly",))
    return {
        "months": rows, "average_income": round(avg_income, 2), "average_expenses": round(avg_expense, 2), "emi": round(emi, 2),
        "highlights": highlights[:4],
        "note": "Built from the recurring schedules you entered (monthly, quarterly, yearly…). Expense bars include loan EMIs. Qlots does not read bank transactions, so earlier months mirror today's records.",
        "recurring_monthly_expenses": round(recurring_expense, 2),
    }


# --------------------------------------------------------------------------- savings rate
def savings_rate_trend(db: Session, user_id: int, income: list[Entry], expenses: list[Entry], liabilities: list[Entry], monthly_investment: float) -> dict:
    rows = list(db.scalars(select(Snapshot).where(Snapshot.user_id == user_id, Snapshot.monthly_income.is_not(None)).order_by(Snapshot.snapshot_date.asc())))
    by_month: dict[str, Snapshot] = {}
    for s in rows:
        by_month[s.snapshot_date.strftime("%Y-%m")] = s  # last snapshot of the month wins
    points = []
    for key, s in sorted(by_month.items()):
        if s.monthly_income and s.monthly_income > 0:
            surplus = s.monthly_income - (s.monthly_expenses or 0) - (s.monthly_emi or 0)
            points.append({"month": key, "label": s.snapshot_date.strftime("%b %y"), "rate": round(surplus / s.monthly_income * 100, 1), "saved": round(surplus, 2), "income": round(s.monthly_income, 2)})
    inc, exp, emi = monthly_flow(income, "income"), monthly_flow(expenses, "expense"), sum(l.emi for l in liabilities)
    surplus = inc - exp - emi
    current = round(surplus / inc * 100, 1) if inc > 0 else None
    now_key = today_local().strftime("%Y-%m")
    if current is not None and not any(p["month"] == now_key for p in points):
        points.append({"month": now_key, "label": today_local().strftime("%b %y"), "rate": current, "saved": round(surplus, 2), "income": round(inc, 2)})
    trend = None
    if len(points) >= 2:
        trend = round(points[-1]["rate"] - points[0]["rate"], 1)
    return {
        "current_rate": current, "monthly_saved": round(surplus, 2), "monthly_income": round(inc, 2), "monthly_investment": round(monthly_investment, 2),
        "invested_share_of_income": round(monthly_investment / inc * 100, 1) if inc > 0 else None,
        "trend_change": trend, "points": points[-12:], "benchmark": 20.0,
        "note": "Savings rate = (income − expenses − EMIs) ÷ income: the share of income you did not spend, i.e. saved or invested. 20% is a common rule-of-thumb, not a target for everyone.",
    }


# --------------------------------------------------------------------------- debt
def debt_progress(db: Session, user_id: int, liabilities: list[Entry]) -> dict:
    today = today_local()
    loans = []
    totals_series: dict[int, float] = {}
    flat_balance = 0.0
    for l in liabilities:
        bal = float(l.amount)
        rm = max(l.annual_rate, 0) / 1200
        months, interest, payoff = 0, 0.0, None
        sched = [bal]
        if l.emi > 0 and bal > 0:
            b = bal
            while months < 600 and b > 0.005:
                i = b * rm
                if l.emi <= i and months == 0 and rm > 0:
                    break  # EMI does not cover interest: never clears
                pay = min(l.emi, b + i)
                b = b + i - pay
                interest += i
                months += 1
                sched.append(b)
            payoff = add_months(today, months) if b <= 0.005 else None
        original = max(l.principal, bal)
        if payoff is None:
            flat_balance += bal  # never clears with the EMI entered: held flat on the chart
        else:
            for m, v in enumerate(sched):
                totals_series[m] = totals_series.get(m, 0.0) + v
        loans.append({
            "id": l.id, "name": l.name, "category": l.category, "balance": round(bal, 2), "original": round(original, 2), "rate": l.annual_rate, "emi": l.emi,
            "paid_pct": round((1 - bal / original) * 100, 1) if original > 0 else 0.0,
            "months_left": months if payoff else None, "payoff_date": payoff.isoformat() if payoff else None,
            "interest_remaining": round(interest, 2) if payoff else None,
        })
    longest = max((m for m in totals_series), default=0)
    if flat_balance > 0:
        longest = max(longest, 12)
        for m in range(0, longest + 1):
            totals_series[m] = totals_series.get(m, 0.0) + flat_balance
    projection = []
    stride = max(1, longest // 60)
    for m in range(0, longest + 1, stride):
        projection.append({"month": m, "date": add_months(today, m).isoformat(), "balance": round(totals_series.get(m, 0.0), 2)})
    if projection and projection[-1]["month"] != longest and longest > 0:
        projection.append({"month": longest, "date": add_months(today, longest).isoformat(), "balance": round(totals_series.get(longest, 0.0), 2)})
    hist = list(db.scalars(select(Snapshot).where(Snapshot.user_id == user_id, Snapshot.liabilities.is_not(None)).order_by(Snapshot.snapshot_date.asc())))
    history = [{"date": s.snapshot_date.isoformat(), "balance": round(s.liabilities or 0.0, 2)} for s in hist][-120:]
    current = sum(l["balance"] for l in loans)
    peak = max([sum(l["original"] for l in loans)] + [h["balance"] for h in history]) if loans or history else 0.0
    cleared = all(l["payoff_date"] for l in loans) and bool(loans)
    debt_free = max((l["payoff_date"] for l in loans if l["payoff_date"]), default=None) if cleared else None
    return {
        "total_balance": round(current, 2), "peak_balance": round(peak, 2),
        "paid_pct": round((1 - current / peak) * 100, 1) if peak > 0 else 0.0,
        "debt_free_date": debt_free, "monthly_emi": round(sum(l.emi for l in liabilities), 2),
        "loans": sorted(loans, key=lambda r: -r["balance"]), "history": history, "projection": projection,
        "note": "Projection follows each loan's EMI, rate and balance as entered; extra payments are not included (use the Loan payoff planner).",
    }


# --------------------------------------------------------------------------- projection plans
def _plan_series(assets, liabilities, income, expenses, assumptions, months: int = 60) -> dict[str, list[float]]:
    out = {}
    for scenario in SCENARIO_FACTOR:
        sim = simulate(assets, liabilities, income, expenses, assumptions, months, scenario)
        out[scenario] = [round(p["net_worth"], 2) for p in sim["points"]]
    return out


def save_plan(db: Session, user_id: int, label: str, source: str = "manual") -> ProjectionPlan:
    assets, liabilities, income, expenses = collect_finances(db, user_id)
    assumptions = get_assumptions(db, user_id)
    series = _plan_series(assets, liabilities, income, expenses, assumptions)
    today = today_local()
    plan = ProjectionPlan(
        user_id=user_id, created_on=today, label=label or f"Plan of {today.strftime('%d %b %Y')}", source=source,
        starting_net_worth=series["base"][0], horizon_months=60, payload=json.dumps(series),
        assumptions=json.dumps({"salary_growth": assumptions.salary_growth, "equity_return": assumptions.equity_return, "property_growth": assumptions.property_growth, "inflation": assumptions.inflation, "monthly_investment": assumptions.monthly_investment}),
    )
    db.add(plan)
    db.commit()
    db.refresh(plan)
    return plan


def plan_summary(p: ProjectionPlan) -> dict:
    return {"id": p.id, "label": p.label, "created_on": p.created_on.isoformat(), "source": p.source, "starting_net_worth": round(p.starting_net_worth, 2), "assumptions": json.loads(p.assumptions or "{}")}


def _month_index(start: date, when: date) -> float:
    return (when.year - start.year) * 12 + (when.month - start.month) + (when.day - start.day) / 30.0


def _interp(series: list[float], x: float) -> float:
    x = max(0.0, min(float(len(series) - 1), x))
    lo = int(x)
    hi = min(lo + 1, len(series) - 1)
    return series[lo] + (series[hi] - series[lo]) * (x - lo)


def plan_vs_actual(db: Session, user_id: int, plan: ProjectionPlan) -> dict:
    series = json.loads(plan.payload)
    start = plan.created_on
    projected = [
        {"month": m, "date": add_months(start, m).isoformat(), "base": series["base"][m], "conservative": series["conservative"][m], "optimistic": series["optimistic"][m]}
        for m in range(0, len(series["base"]))
    ]
    snaps = list(db.scalars(select(Snapshot).where(Snapshot.user_id == user_id, Snapshot.snapshot_date >= start).order_by(Snapshot.snapshot_date.asc())))
    actual = []
    for s in snaps:
        x = _month_index(start, s.snapshot_date)
        if x > len(series["base"]) - 1:
            continue
        base = _interp(series["base"], x)
        actual.append({"date": s.snapshot_date.isoformat(), "net_worth": round(s.net_worth, 2), "projected_base": round(base, 2), "variance": round(s.net_worth - base, 2), "source": s.source or "auto"})
    summary = None
    if actual and (len(actual) > 1 or actual[0]["date"] != start.isoformat()):
        last = actual[-1]
        x = _month_index(start, date.fromisoformat(last["date"]))
        lo, hi = _interp(series["conservative"], x), _interp(series["optimistic"], x)
        base = last["projected_base"]
        pct = (last["net_worth"] - base) / abs(base) * 100 if base else 0.0
        status = "ahead" if pct > 5 else "behind" if pct < -5 else "on_track"
        hint = {
            "ahead": "Your recorded net worth is above the base projection. Your savings or returns may be stronger than assumed; consider re-saving a fresh plan with updated assumptions.",
            "behind": "Your recorded net worth is below the base projection. Check whether income, spending, returns or debt repayment differ from your assumptions, then update them and save a new plan.",
            "on_track": "Your recorded net worth is close to the base projection.",
        }[status]
        summary = {
            "as_of": last["date"], "actual": last["net_worth"], "projected_base": round(base, 2), "projected_low": round(lo, 2), "projected_high": round(hi, 2),
            "variance": round(last["net_worth"] - base, 2), "variance_pct": round(pct, 1), "status": status,
            "within_range": min(lo, hi) <= last["net_worth"] <= max(lo, hi), "hint": hint,
        }
    return {"plan": plan_summary(p=plan), "projected": projected, "actual": actual, "summary": summary,
            "note": "Compares the saved projection with net-worth snapshots recorded after the plan date. Projections are estimates, not guarantees."}
