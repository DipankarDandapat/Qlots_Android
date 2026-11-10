"""Planning calculators: what-if, loan payoff, emergency fund, goal contribution, deposits, goals."""
from __future__ import annotations

import math
from datetime import date

from models.models import Entry, Goal, GoalLog
from services.dates import add_months, today_local
from services.engine import SimConfig, simulate
from services.finance import (
    CASH_CATS, DEPOSIT_CATS, entry_value, fd_value, monthly_flow, rd_months, rd_value,
)


# --------------------------------------------------------------------------- what-if
def _yearly_rows(sim: dict, inflation_pct: float, years: int) -> list[dict]:
    rows = []
    for y in range(1, years + 1):
        p = sim["points"][y * 12]
        rows.append({
            "year": y, "date": p["date"], "assets": round(p["assets"], 2), "liabilities": round(p["liabilities"], 2),
            "net_worth": round(p["net_worth"], 2), "real_net_worth": round(p["net_worth"] / (1 + inflation_pct / 100) ** y, 2),
        })
    return rows


def what_if(assets, liabilities, income, expenses, assumptions, *, years: int, scenario: str, extra_monthly_saving: float,
            extra_loan_payment: float, purchase_amount: float, purchase_in_months: int, delay_months: int) -> dict:
    years = max(1, min(int(years), 10))
    horizon = years * 12
    infl = assumptions.inflation

    def purchase_list(delay: int) -> list[tuple[int, float]]:
        if purchase_amount <= 0:
            return []
        when = max(1, purchase_in_months) + max(0, delay)
        if when > horizon:  # purchase falls beyond the horizon: not part of this window
            return []
        # Prices are assumed to rise with inflation until the purchase date.
        return [(when, purchase_amount * (1 + infl / 100) ** (when / 12))]

    def run(key: str, label: str, description: str, cfg: SimConfig) -> dict:
        sim = simulate(assets, liabilities, income, expenses, assumptions, horizon, scenario, cfg)
        rows = _yearly_rows(sim, infl, years)
        return {"key": key, "label": label, "description": description, "yearly": rows,
                "final_net_worth": rows[-1]["net_worth"], "final_liabilities": rows[-1]["liabilities"], "final_real_net_worth": rows[-1]["real_net_worth"]}

    base_purchase = purchase_list(0)
    runs = [run("baseline", "Current plan", "Your records and assumptions as they stand" + (f", with the purchase in month {max(1, purchase_in_months)}" if base_purchase else ""), SimConfig(purchases=base_purchase))]
    combined = SimConfig(purchases=purchase_list(delay_months))
    if extra_monthly_saving > 0:
        runs.append(run("save_more", f"Save {extra_monthly_saving:,.0f} more a month", "Extra amount comes out of your monthly surplus (otherwise idle cash at 0%) and is invested at your equity-return assumption, so the gain is the growth on that money", SimConfig(extra_monthly_saving=extra_monthly_saving, purchases=base_purchase)))
        combined.extra_monthly_saving = extra_monthly_saving
    if extra_loan_payment > 0:
        runs.append(run("pay_loan", f"Pay {extra_loan_payment:,.0f} extra on loans", "Extra repayment goes to the highest-interest loan first", SimConfig(extra_loan_payment=extra_loan_payment, purchases=base_purchase)))
        combined.extra_loan_payment = extra_loan_payment
    if purchase_amount > 0 and delay_months > 0:
        runs.append(run("delay", f"Delay purchase by {delay_months} months", "Cash earns 0% in this model, so delaying mainly shows price inflation and the timing of your cash", SimConfig(purchases=purchase_list(delay_months))))
    active = [r for r in runs if r["key"] != "baseline"]
    if len(active) > 1:
        runs.append(run("combined", "All changes together", "Every change you selected, applied at once", combined))
    base_final = runs[0]["final_net_worth"]
    for r in runs:
        r["delta_vs_baseline"] = round(r["final_net_worth"] - base_final, 2)
    return {"years": years, "scenario": scenario, "end_date": add_months(today_local(), horizon).isoformat(), "runs": runs,
            "disclaimer": "What-if results are estimates from your records and assumptions, not predictions."}


# --------------------------------------------------------------------------- loan payoff
def _payoff(loans: list[dict], strategy: str, extra: float, target_id: int | None, rollover: bool = True, max_months: int = 600) -> dict:
    state = [{**l, "paid_off_month": None, "interest": 0.0} for l in loans]
    start = today_local()
    series = [{"month": 0, "balance": round(sum(s["bal"] for s in state), 2)}]
    freed = 0.0
    total_paid = 0.0

    def priority(s: dict):
        if strategy == "avalanche":
            return (-s["rate"], s["bal"])
        if strategy == "snowball":
            return (s["bal"], -s["rate"])
        if strategy == "chosen":
            return (0 if s["id"] == target_id else 1, -s["rate"])
        return (0,)

    months = 0
    for m in range(1, max_months + 1):
        active = [s for s in state if s["bal"] > 0.005]
        if not active:
            break
        months = m
        for s in active:
            interest = s["bal"] * s["rm"]
            s["bal"] += interest
            s["interest"] += interest
            minimum = s["emi"] if s["emi"] > 0 else interest  # no EMI entered: interest-only
            pay = min(minimum, s["bal"])
            s["bal"] -= pay
            total_paid += pay
        pool = 0.0 if strategy == "minimum" else extra + (freed if rollover else 0.0)
        for s in sorted([s for s in state if s["bal"] > 0.005], key=priority):
            if pool <= 0:
                break
            amount = min(pool, s["bal"])
            s["bal"] -= amount
            pool -= amount
            total_paid += amount
        for s in state:
            if s["paid_off_month"] is None and s["bal"] <= 0.005:
                s["bal"] = 0.0
                s["paid_off_month"] = m
                if s["emi"] > 0:
                    freed += s["emi"]
        if m <= 120 or m % 6 == 0:
            series.append({"month": m, "balance": round(sum(s["bal"] for s in state), 2)})
    cleared = all(s["paid_off_month"] is not None for s in state)
    if series[-1]["month"] != months:
        series.append({"month": months, "balance": round(sum(s["bal"] for s in state), 2)})
    return {
        "debt_free": cleared, "months": months if cleared else None,
        "debt_free_date": add_months(start, months).isoformat() if cleared else None,
        "total_interest": round(sum(s["interest"] for s in state), 2), "total_paid": round(total_paid, 2),
        "remaining_balance": round(sum(s["bal"] for s in state), 2),
        "payoff_order": sorted(
            [{"name": s["name"], "month": s["paid_off_month"], "date": add_months(start, s["paid_off_month"]).isoformat()} for s in state if s["paid_off_month"] is not None],
            key=lambda r: r["month"]),
        "series": series,
    }


def loan_payoff(liabilities: list[Entry], extra_monthly: float, target_loan_id: int | None) -> dict:
    loans = [
        {"id": l.id, "name": l.name, "bal": float(l.amount), "rate": max(l.annual_rate, 0), "rm": max(l.annual_rate, 0) / 1200, "emi": float(l.emi)}
        for l in liabilities if l.amount > 0
    ]
    if not loans:
        return {"loans": [], "strategies": [], "extra_monthly": extra_monthly, "note": "Add a loan to compare repayment approaches."}
    plans = [("minimum", "Minimum payments only", "Pay each loan's EMI and nothing more")]
    plans.append(("avalanche", "Highest interest first", "Extra money and freed-up EMIs go to the loan with the highest rate"))
    plans.append(("snowball", "Smallest balance first", "Extra money and freed-up EMIs clear the smallest loan first"))
    if target_loan_id is not None and any(l["id"] == target_loan_id for l in loans):
        name = next(l["name"] for l in loans if l["id"] == target_loan_id)
        plans.append(("chosen", f"Extra on {name}", f"Extra money goes to {name} first, then the highest-rate loan"))
    results = []
    for key, label, description in plans:
        res = _payoff(loans, key, extra_monthly, target_loan_id)
        res.update({"key": key, "label": label, "description": description})
        results.append(res)
    baseline = results[0]
    for res in results:
        if res["key"] == "minimum":
            res["interest_saved"], res["months_saved"] = 0.0, 0
        elif baseline["debt_free"] and res["debt_free"]:
            res["interest_saved"] = round(baseline["total_interest"] - res["total_interest"], 2)
            res["months_saved"] = baseline["months"] - res["months"]
        elif res["debt_free"]:
            res["interest_saved"], res["months_saved"] = None, None  # baseline never clears within 50 years
        else:
            res["interest_saved"], res["months_saved"] = None, None
    return {
        "loans": [{"id": l["id"], "name": l["name"], "balance": round(l["bal"], 2), "rate": l["rate"], "emi": l["emi"]} for l in loans],
        "extra_monthly": extra_monthly, "strategies": results,
        "note": "Minimum payment is the EMI you entered; loans without an EMI are treated as interest-only. Tenure limits are ignored so every plan runs to a zero balance (up to 50 years).",
    }


# --------------------------------------------------------------------------- emergency fund
def emergency_fund(assets, liabilities, expenses, *, months: int, include_emi: bool, build_in_months: int) -> dict:
    today = today_local()
    monthly_expenses = monthly_flow(expenses, "expense")
    monthly_emi = sum(l.emi for l in liabilities) if include_emi else 0.0
    essential = monthly_expenses + monthly_emi
    liquid = sum(entry_value(a, today) for a in assets if a.category in CASH_CATS)
    target = essential * months
    gap = max(0.0, target - liquid)
    build_in_months = max(1, build_in_months)

    def row(n: int) -> dict:
        t = essential * n
        return {"months": n, "target": round(t, 2), "gap": round(max(0.0, t - liquid), 2)}

    return {
        "months": months, "include_emi": include_emi, "monthly_expenses": round(monthly_expenses, 2), "monthly_emi": round(monthly_emi, 2),
        "monthly_essential": round(essential, 2), "target": round(target, 2), "current_liquid": round(liquid, 2),
        "gap": round(gap, 2), "covered_months": round(liquid / essential, 1) if essential else 0.0,
        "progress_pct": round(min(100.0, liquid / target * 100), 1) if target else 0.0,
        "build_in_months": build_in_months, "monthly_needed": round(gap / build_in_months, 2),
        "options": [row(n) for n in (3, 6, 9, 12)],
        "note": "Based on the monthly expenses (and EMIs, if included) you have recorded. Liquid money is the cash and bank balances you entered.",
    }


# --------------------------------------------------------------------------- goal maths
def required_monthly(target: float, current: float, annual_return: float, months: int) -> float:
    """Monthly contribution (paid at month end) so that current grows to target in `months`."""
    r = annual_return / 1200
    if months <= 0:
        return max(0.0, target - current)
    if r == 0:
        return max(0.0, (target - current) / months)
    growth = (1 + r) ** months
    return max(0.0, (target - current * growth) * r / (growth - 1))


def months_to_target(target: float, current: float, monthly: float, annual_return: float, cap: int = 1200) -> int | None:
    if current >= target:
        return 0
    r = annual_return / 1200
    if monthly <= 0 and (r <= 0 or current <= 0):
        return None
    if r == 0:
        return math.ceil((target - current) / monthly)
    numerator, denominator = target * r + monthly, current * r + monthly
    if denominator <= 0 or numerator <= 0:
        return None
    n = math.log(numerator / denominator) / math.log(1 + r)
    return min(cap, math.ceil(n - 1e-9)) if n > 0 else 0


def goal_path(current: float, monthly: float, annual_return: float, months: int) -> list[dict]:
    r = annual_return / 1200
    balance, start, out = current, today_local(), [{"month": 0, "date": today_local().isoformat(), "amount": round(current, 2)}]
    for m in range(1, min(months, 240) + 1):
        balance = balance * (1 + r) + monthly
        out.append({"month": m, "date": add_months(start, m).isoformat(), "amount": round(balance, 2)})
    return out


def goal_contribution(target: float, current: float, annual_return: float, months: int) -> dict:
    need = required_monthly(target, current, annual_return, months)
    contributions = need * months
    final = current * (1 + annual_return / 1200) ** months + (need * (((1 + annual_return / 1200) ** months - 1) / (annual_return / 1200)) if annual_return else need * months)
    path = goal_path(current, need, annual_return, months)
    return {
        "target": round(target, 2), "current": round(current, 2), "annual_return": annual_return, "months": months,
        "target_date": add_months(today_local(), months).isoformat(), "monthly_required": round(need, 2),
        "total_contributions": round(contributions, 2), "growth_on_money": round(max(0.0, final - current - contributions), 2),
        "already_covered": current >= target or need <= 0.005, "path": path,
        "formula": f"(Target − Current × (1 + r)^n) × r ÷ ((1 + r)^n − 1), r = {annual_return:g}% ÷ 12 = {annual_return / 12:.4f}% a month, n = {months} months" if annual_return else f"(Target − Current) ÷ {months} months",
        "note": "Assumes equal contributions at the end of each month and a steady return. Actual returns will vary.",
    }


def goal_view(goal: Goal, logs: list[GoalLog], liquid_cash: float) -> dict:
    today = today_local()
    current = liquid_cash if goal.auto_link_cash else goal.current_amount
    target = goal.target_amount
    progress = min(100.0, current / target * 100) if target > 0 else 0.0
    months_left = None
    need = None
    if goal.target_date:
        months_left = max(0, (goal.target_date.year - today.year) * 12 + goal.target_date.month - today.month)
        need = required_monthly(target, current, goal.expected_return, months_left) if months_left > 0 else max(0.0, target - current)
    eta_months = months_to_target(target, current, goal.monthly_contribution, goal.expected_return)
    eta_date = add_months(today, eta_months) if eta_months is not None else None
    on_track = None
    if current >= target:
        on_track = True
    elif goal.target_date and eta_date:
        on_track = eta_date <= goal.target_date
    elif goal.target_date and eta_date is None:
        on_track = False
    history = [{"date": goal.created_at.date().isoformat(), "amount": round(goal.start_amount, 2)}]
    history += [{"date": l.log_date.isoformat(), "amount": round(l.amount, 2)} for l in sorted(logs, key=lambda x: x.log_date)]
    if not any(h["date"] == today.isoformat() for h in history):
        history.append({"date": today.isoformat(), "amount": round(current, 2)})
    horizon = eta_months if eta_months is not None else (months_left or 24)
    return {
        "id": goal.id, "name": goal.name, "kind": goal.kind, "target_amount": round(target, 2), "current_amount": round(current, 2),
        "start_amount": round(goal.start_amount, 2), "monthly_contribution": goal.monthly_contribution, "expected_return": goal.expected_return,
        "target_date": goal.target_date.isoformat() if goal.target_date else None, "auto_link_cash": bool(goal.auto_link_cash),
        "progress_pct": round(progress, 1), "remaining": round(max(0.0, target - current), 2), "months_left": months_left,
        "monthly_required": None if need is None else round(need, 2),
        "projected_completion": eta_date.isoformat() if eta_date else None, "on_track": on_track,
        "history": history, "projection": goal_path(current, goal.monthly_contribution, goal.expected_return, min(max(horizon, 1), 120)),
    }


# --------------------------------------------------------------------------- deposits
def deposit_planner(assets: list[Entry]) -> dict:
    today = today_local()
    rows = []
    for dep in assets:
        if dep.category not in DEPOSIT_CATS:
            continue
        maturity = dep.maturity_date
        if not maturity and dep.start_date and dep.tenure_months > 0:
            maturity = add_months(dep.start_date, dep.tenure_months)
        is_fd = dep.category == "fixed_deposit"
        current = entry_value(dep, today)
        if is_fd:
            invested = dep.principal or dep.amount
            at_maturity = fd_value(dep, maturity) if maturity else None
        else:
            instalment = dep.principal or dep.amount
            invested = instalment * rd_months(dep, today)
            at_maturity = rd_value(dep, maturity) if maturity else None
        total_invested_at_maturity = invested if is_fd else ((dep.principal or dep.amount) * rd_months(dep, maturity) if maturity else None)
        days_left = (maturity - today).days if maturity else None
        status = "no_date" if not maturity else ("matured" if days_left is not None and days_left <= 0 else ("maturing_soon" if days_left is not None and days_left <= 90 else "active"))
        rows.append({
            "id": dep.id, "name": dep.name, "institution": dep.institution, "type": "FD" if is_fd else "RD",
            "principal": round(dep.principal or dep.amount, 2), "annual_rate": dep.annual_rate,
            "compounding": dep.frequency if is_fd else "monthly",
            "start_date": dep.start_date.isoformat() if dep.start_date else None, "maturity_date": maturity.isoformat() if maturity else None,
            "days_to_maturity": days_left, "status": status, "invested_so_far": round(invested, 2), "current_value": round(current, 2),
            "interest_so_far": round(max(0.0, current - invested), 2),
            "maturity_value": None if at_maturity is None else round(at_maturity, 2),
            "interest_at_maturity": None if at_maturity is None or total_invested_at_maturity is None else round(at_maturity - total_invested_at_maturity, 2),
            "formula": ("P × (1 + r/n)^(n×t)" if is_fd else "Instalment × [((1+i)^n − 1)/i] × (1+i), i = rate ÷ 12"),
        })
    rows.sort(key=lambda r: (r["maturity_date"] is None, r["maturity_date"] or ""))
    return {
        "deposits": rows,
        "summary": {
            "count": len(rows), "current_value": round(sum(r["current_value"] for r in rows), 2),
            "maturity_value": round(sum(r["maturity_value"] or r["current_value"] for r in rows), 2),
            "next_maturity": next((r["maturity_date"] for r in rows if r["maturity_date"] and (r["days_to_maturity"] or 0) > 0), None),
        },
        "note": "Maturity values assume the rate and compounding you entered stay unchanged. Bank rules on tax (TDS) and premature closure are not included.",
    }
