from __future__ import annotations

from datetime import date
from sqlalchemy import select
from sqlalchemy.orm import Session

from models.models import Assumptions, Entry
from services.dates import add_months, financial_year_end, financial_year_label, today_local

CASH_CATS = ("cash", "bank_balance", "savings")
PROPERTY_CATS = ("property", "real_estate")
DEPOSIT_CATS = ("fixed_deposit", "recurring_deposit")
FREQ_PER_YEAR = {"daily": 365, "weekly": 52, "monthly": 12, "quarterly": 4, "half_yearly": 2, "yearly": 1}
HORIZON_YEARS = (1, 2, 3, 5)


def annualized(amount: float, frequency: str) -> float:
    return amount * FREQ_PER_YEAR.get(frequency, 12)


def rd_months(entry: Entry, on_date: date | None = None) -> int:
    """Whole instalments counted for a recurring deposit up to `on_date` (capped at maturity)."""
    if not entry.start_date:
        return 0
    valuation_date = on_date or today_local()
    end_date = min(valuation_date, entry.maturity_date) if entry.maturity_date else valuation_date
    return max(0, (end_date.year - entry.start_date.year) * 12 + end_date.month - entry.start_date.month)


def rd_value(entry: Entry, on_date: date | None = None) -> float:
    """Recurring deposit as an annuity-due: instalment * ((1+i)^n - 1)/i * (1+i), i = rate/12.

    n instalments are paid at the start of n consecutive months and compound until the end of month n,
    which matches how a bank's maturity amount is built.
    """
    instalment = entry.principal or entry.amount
    if instalment <= 0 or not entry.start_date or entry.annual_rate <= 0:
        return entry.amount
    months = rd_months(entry, on_date)
    r = entry.annual_rate / 1200
    return instalment * ((1 + r) ** months - 1) / r * (1 + r)


def fd_years(entry: Entry, on_date: date | None = None) -> float:
    valuation_date = on_date or today_local()
    end_date = min(valuation_date, entry.maturity_date) if entry.maturity_date else valuation_date
    if not entry.start_date:
        return 0.0
    return max(0, (end_date - entry.start_date).days) / 365.2425


def fd_periods(entry: Entry) -> int:
    return {"daily": 365, "monthly": 12, "quarterly": 4, "half_yearly": 2, "yearly": 1}.get(entry.frequency, 4)


def fd_value(entry: Entry, on_date: date | None = None) -> float:
    """Fixed deposit: P * (1 + r/n)^(n*t). Growth stops at the maturity date."""
    principal = entry.principal or entry.amount
    if principal <= 0 or not entry.start_date or entry.annual_rate <= 0:
        return entry.amount
    periods = fd_periods(entry)
    return principal * (1 + entry.annual_rate / 100 / periods) ** (periods * fd_years(entry, on_date))


def entry_value(entry: Entry, on_date: date | None = None) -> float:
    if entry.kind == "asset" and entry.category == "fixed_deposit":
        return fd_value(entry, on_date)
    if entry.kind == "asset" and entry.category == "recurring_deposit":
        return rd_value(entry, on_date)
    return float(entry.amount or 0)


def entry_dict(entry: Entry) -> dict:
    return {
        "id": entry.id, "kind": entry.kind, "category": entry.category, "name": entry.name,
        "institution": entry.institution,
        "amount": round(entry_value(entry), 2) if entry.kind == "asset" else round(entry.amount, 2),
        "principal": entry.principal, "annual_rate": entry.annual_rate, "frequency": entry.frequency,
        "emi": entry.emi, "tenure_months": entry.tenure_months,
        "start_date": entry.start_date.isoformat() if entry.start_date else None,
        "maturity_date": entry.maturity_date.isoformat() if entry.maturity_date else None,
        "growth_rate": entry.growth_rate, "notes": entry.notes,
    }


def monthly_flow(entries: list[Entry], kind: str) -> float:
    return sum(annualized(e.amount, e.frequency) / 12 for e in entries if e.kind == kind)


def loan_balance_after(balance: float, annual_rate: float, emi: float, months: int) -> float:
    if balance <= 0:
        return 0.0
    monthly_rate = max(annual_rate, 0) / 1200
    remaining = balance
    for _ in range(max(0, months)):
        interest = remaining * monthly_rate
        payment = emi if emi > 0 else interest
        remaining = max(0.0, remaining + interest - payment)
    return remaining


def collect_finances(db: Session, user_id: int) -> tuple[list[Entry], list[Entry], list[Entry], list[Entry]]:
    rows = list(db.scalars(select(Entry).where(Entry.user_id == user_id)))
    return tuple([e for e in rows if e.kind == k] for k in ("asset", "liability", "income", "expense"))  # type: ignore[return-value]


def get_assumptions(db: Session, user_id: int) -> Assumptions:
    row = db.scalar(select(Assumptions).where(Assumptions.user_id == user_id))
    if row is None:
        row = Assumptions(user_id=user_id)
        db.add(row)
        db.commit()
        db.refresh(row)
    return row


def assumptions_dict(assumptions: Assumptions) -> dict:
    return {
        "salary_growth": assumptions.salary_growth, "equity_return": assumptions.equity_return,
        "property_growth": assumptions.property_growth, "inflation": assumptions.inflation,
        "monthly_investment": assumptions.monthly_investment,
    }


def project(assets: list[Entry], liabilities: list[Entry], income: list[Entry], expenses: list[Entry], assumptions: Assumptions, years: int, scenario: str = "base") -> dict:
    """Year-end net-worth projection. Delegates to the month-by-month engine in services/engine.py."""
    from services.engine import SCENARIO_FACTOR, simulate

    scenario = scenario.lower()
    years = max(1, min(years, 10))
    sim = simulate(assets, liabilities, income, expenses, assumptions, years * 12, scenario if scenario in SCENARIO_FACTOR else "base")
    inflation = assumptions.inflation / 100
    start = sim["start"]
    rows = []
    for year in range(1, years + 1):
        point = sim["points"][year * 12]
        rows.append({
            "year": year,
            "date": add_months(start, year * 12).isoformat(),
            "financial_year": financial_year_label(add_months(start, year * 12)),
            "financial_year_end": financial_year_end(add_months(start, year * 12)).isoformat(),
            "assets": round(point["assets"], 2),
            "liabilities": round(point["liabilities"], 2),
            "net_worth": round(point["net_worth"], 2),
            "real_net_worth": round(point["net_worth"] / (1 + inflation) ** year, 2),
        })
    monthly_income = monthly_flow(income, "income")
    monthly_expenses = monthly_flow(expenses, "expense")
    return {
        "scenario": scenario if scenario in SCENARIO_FACTOR else "base",
        "as_of": start.isoformat(),
        "assumptions": assumptions_dict(assumptions),
        "monthly_income": round(monthly_income, 2), "monthly_expenses": round(monthly_expenses, 2),
        "monthly_emi": round(sum(x.emi for x in liabilities if x.emi > 0), 2), "projections": rows,
    }


def health_from_metrics(monthly_income: float, monthly_expenses: float, monthly_emi: float, liquid: float, non_cash_categories: int, has_data: bool = True) -> dict:
    monthly_surplus = monthly_income - monthly_expenses - monthly_emi
    if not has_data:
        return {"score": 0, "label": "Building", "metrics": {"savings_rate_pct": 0, "debt_to_income_pct": 0, "emergency_fund_months": 0, "monthly_surplus": 0}, "disclaimer": "Add your assets, income and expenses to get a financial health score."}
    savings_rate = (monthly_surplus / monthly_income * 100) if monthly_income else 0
    dti = (monthly_emi / monthly_income * 100) if monthly_income else 0
    emergency_months = liquid / monthly_expenses if monthly_expenses else 0
    score = 0
    score += min(25, max(0, savings_rate) * 0.5)
    score += max(0, 25 - min(25, dti * 0.6)) if monthly_income > 0 else 0
    score += min(20, emergency_months * 5)
    score += 15 if non_cash_categories >= 3 else min(15, non_cash_categories * 5)
    score += 15 if monthly_surplus > 0 else (7 if monthly_surplus == 0 and monthly_income > 0 else 0)
    score = round(min(100, max(0, score)))
    return {"score": score, "label": "Building" if score < 50 else "Steady" if score < 75 else "Strong", "metrics": {"savings_rate_pct": round(savings_rate, 1), "debt_to_income_pct": round(dti, 1), "emergency_fund_months": round(emergency_months, 1), "monthly_surplus": round(monthly_surplus, 2)}, "disclaimer": "An educational indicator based on the information entered—not professional financial advice."}


def health_score(assets: list[Entry], liabilities: list[Entry], income: list[Entry], expenses: list[Entry]) -> dict:
    liquid = sum(entry_value(a) for a in assets if a.category in CASH_CATS)
    non_cash = len({a.category for a in assets if a.category not in CASH_CATS})
    return health_from_metrics(
        monthly_flow(income, "income"), monthly_flow(expenses, "expense"),
        sum(x.emi for x in liabilities), liquid, non_cash,
        has_data=bool(assets or liabilities or income or expenses),
    )


def totals_for(assets: list[Entry], liabilities: list[Entry], on_date: date | None = None) -> tuple[float, float]:
    return sum(entry_value(a, on_date) for a in assets), sum(l.amount for l in liabilities)


def dashboard_data(db: Session, user_id: int, user_name: str) -> dict:
    from services.snapshots import record_auto_snapshot

    assets, liabilities, income, expenses = collect_finances(db, user_id)
    assumptions = get_assumptions(db, user_id)
    today = today_local()
    total_assets, total_liabilities = totals_for(assets, liabilities, today)
    monthly_income = monthly_flow(income, "income")
    monthly_expenses = monthly_flow(expenses, "expense")
    emi_total = sum(l.emi for l in liabilities)
    forecast = project(assets, liabilities, income, expenses, assumptions, 5)
    by_year = {row["year"]: row for row in forecast["projections"]}
    record_auto_snapshot(db, user_id, today, "auto", assets, liabilities, income, expenses, assumptions)
    allocation: dict[str, float] = {}
    for a in assets:
        allocation[a.category] = allocation.get(a.category, 0) + entry_value(a, today)
    return {
        "currency": "INR", "as_of": today.isoformat(), "refreshes": "Net worth is re-calculated automatically every night at 12:00 AM IST.",
        "totals": {"assets": round(total_assets, 2), "liabilities": round(total_liabilities, 2), "net_worth": round(total_assets - total_liabilities, 2)},
        "monthly": {"income": round(monthly_income, 2), "expenses": round(monthly_expenses, 2), "emi": round(emi_total, 2), "surplus": round(monthly_income - monthly_expenses - emi_total, 2)},
        "asset_breakdown": [{"category": k, "value": round(v, 2)} for k, v in sorted(allocation.items(), key=lambda kv: -kv[1])],
        "assets": [entry_dict(a) for a in assets],
        "liabilities": [entry_dict(l) for l in liabilities],
        "projections": {
            "one_year": by_year[1], "two_year": by_year[2], "three_year": by_year[3], "five_year": by_year[5],
            "horizons": [by_year[y] for y in HORIZON_YEARS],
            "assumptions": forecast["assumptions"],
            "disclaimer": "Model-based projections; actual outcomes may differ and are not guaranteed.",
        },
        "health": health_score(assets, liabilities, income, expenses),
    }
