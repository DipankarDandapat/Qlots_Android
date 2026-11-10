"""Month-by-month projection engine.

One simulation drives the dashboard outlook, 1/2/3/5-year breakdowns, adjustable projections, the
what-if builder and saved projection plans, so every screen agrees with every other screen.

Model (all rates are annual percentages, results are estimates, not predictions):
  * Property / equity / other assets grow with annual compounding: value * (1 + r)^(months / 12).
  * Fixed and recurring deposits use their own compounding formulas and stop growing at maturity.
  * Cash earns 0%. Monthly surplus (income - expenses - loan payments - SIP - RD instalments) is
    added to cash; if cash would go negative the shortfall is shown as a liability (overdraft).
  * Income grows with salary growth and expenses with inflation, stepped once per year.
  * A monthly SIP is invested at the equity return, compounded monthly (end-of-month annuity).
  * Loans amortise monthly. EMIs stop when the loan is repaid or its tenure ends.
  * Scenarios scale market returns: conservative x0.65, base x1.0, optimistic x1.25.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date

from models.models import Assumptions, Entry
from services.dates import add_months, financial_year_end, financial_year_label, today_local
from services.finance import (
    CASH_CATS, DEPOSIT_CATS, PROPERTY_CATS, entry_value, fd_periods, fd_years, health_from_metrics,
    monthly_flow, rd_months,
)

SCENARIO_FACTOR = {"conservative": 0.65, "base": 1.0, "optimistic": 1.25}
MAX_MONTHS = 120


def inr(value: float) -> str:
    """Indian digit grouping: 1234567.8 -> ₹12,34,568."""
    negative = value < 0
    digits = f"{round(abs(value)):d}"
    if len(digits) > 3:
        head, tail = digits[:-3], digits[-3:]
        groups = []
        while len(head) > 2:
            groups.insert(0, head[-2:])
            head = head[:-2]
        if head:
            groups.insert(0, head)
        digits = ",".join(groups + [tail])
    return f"{'-' if negative else ''}₹{digits}"


@dataclass
class SimConfig:
    extra_monthly_saving: float = 0.0
    extra_loan_payment: float = 0.0
    equity_return: float | None = None
    property_growth: float | None = None
    salary_growth: float | None = None
    inflation: float | None = None
    purchases: list[tuple[int, float]] = field(default_factory=list)  # (month number 1.., amount)


def _growth_rate(asset: Entry, equity_rate: float, property_rate: float, factor: float, assumptions: Assumptions, cfg: SimConfig) -> tuple[float, str]:
    equity_pct = cfg.equity_return if cfg.equity_return is not None else assumptions.equity_return
    property_pct = cfg.property_growth if cfg.property_growth is not None else assumptions.property_growth
    if asset.category in PROPERTY_CATS:
        return property_rate, f"Property growth {property_pct:g}% x scenario factor {factor:g}"
    if asset.category in CASH_CATS:
        return 0.0, "Cash earns 0%"
    if asset.growth_rate != 0:
        return asset.growth_rate / 100, f"Your growth rate for this asset {asset.growth_rate:g}%"
    if asset.annual_rate > 0:
        return asset.annual_rate / 100 * factor, f"Asset rate {asset.annual_rate:g}% x scenario factor {factor:g}"
    return equity_rate, f"Equity return {equity_pct:g}% x scenario factor {factor:g}"


def simulate(
    assets: list[Entry], liabilities: list[Entry], income: list[Entry], expenses: list[Entry],
    assumptions: Assumptions, months: int, scenario: str = "base", cfg: SimConfig | None = None,
    start: date | None = None, detail_at: tuple[int, ...] | list[int] = (),
) -> dict:
    cfg = cfg or SimConfig()
    start = start or today_local()
    months = max(1, min(int(months), MAX_MONTHS))
    factor = SCENARIO_FACTOR.get(scenario, 1.0)
    equity_pct = cfg.equity_return if cfg.equity_return is not None else assumptions.equity_return
    salary_pct = cfg.salary_growth if cfg.salary_growth is not None else assumptions.salary_growth
    inflation_pct = cfg.inflation if cfg.inflation is not None else assumptions.inflation
    property_pct = cfg.property_growth if cfg.property_growth is not None else assumptions.property_growth
    equity_rate = equity_pct / 100 * factor
    property_rate = property_pct / 100 * factor
    monthly_income = monthly_flow(income, "income")
    monthly_expenses = monthly_flow(expenses, "expense")
    sip_monthly = assumptions.monthly_investment + cfg.extra_monthly_saving
    detail_set = set(detail_at)
    purchases: dict[int, float] = {}
    for month_no, amount in cfg.purchases:
        purchases[month_no] = purchases.get(month_no, 0.0) + amount

    growth_assets = []
    deposits = []
    liquid_cash0 = 0.0
    for asset in assets:
        if asset.category in DEPOSIT_CATS:
            deposits.append(asset)
        elif asset.category in CASH_CATS:
            liquid_cash0 += entry_value(asset, start)
        else:
            rate, source = _growth_rate(asset, equity_rate, property_rate, factor, assumptions, cfg)
            growth_assets.append((asset, entry_value(asset, start), rate, source))

    loans = [
        {"entry": l, "bal": float(l.amount), "rm": max(l.annual_rate, 0) / 1200, "emi": float(l.emi), "tenure": int(l.tenure_months), "opening": float(l.amount), "interest": 0.0, "paid": 0.0}
        for l in liabilities
    ]

    def totals_at(month: int, cash: float, sip: float) -> tuple[float, float, float]:
        when = add_months(start, month)
        value = sum(cur * (1 + rate) ** (month / 12) for _, cur, rate, _ in growth_assets)
        value += sum(entry_value(d, when) for d in deposits)
        overdraft = max(0.0, -cash)
        return value + sip + max(0.0, cash), sum(l["bal"] for l in loans) + overdraft, overdraft

    cash_delta = 0.0
    sip_balance = 0.0
    flows = {"income": 0.0, "expenses": 0.0, "emi": 0.0, "extra_loan": 0.0, "sip": 0.0, "rd": 0.0, "purchases": 0.0}
    a0, l0, _ = totals_at(0, liquid_cash0, 0.0)
    points = [{"month": 0, "date": start.isoformat(), "assets": a0, "liabilities": l0, "net_worth": a0 - l0, "cash": liquid_cash0, "overdraft": 0.0}]
    details: dict[int, dict] = {}
    last = {"income": 0.0, "expenses": 0.0, "emi": 0.0}

    for m in range(1, months + 1):
        ystep = (m - 1) // 12
        inc = monthly_income * (1 + salary_pct / 100) ** ystep
        exp = monthly_expenses * (1 + inflation_pct / 100) ** ystep
        emi_paid = 0.0
        for loan in loans:
            if loan["bal"] <= 0 or loan["emi"] <= 0:
                continue  # interest-only or settled: no payment modelled
            if loan["tenure"] > 0 and m > loan["tenure"]:
                continue  # tenure over: any residual balance stays as-is
            interest = loan["bal"] * loan["rm"]
            payment = min(loan["emi"], loan["bal"] + interest)
            loan["bal"] = max(0.0, loan["bal"] + interest - payment)
            loan["interest"] += interest
            loan["paid"] += payment
            emi_paid += payment
        extra_applied = 0.0
        extra_left = max(0.0, cfg.extra_loan_payment)
        for loan in sorted(loans, key=lambda x: -x["rm"]):
            if extra_left <= 0:
                break
            if loan["bal"] > 0:
                amount = min(extra_left, loan["bal"])
                loan["bal"] -= amount
                loan["paid"] += amount
                extra_left -= amount
                extra_applied += amount
        rd_out = 0.0
        this_date, prev_date = add_months(start, m), add_months(start, m - 1)
        for dep in deposits:
            if dep.category == "recurring_deposit" and dep.start_date and dep.annual_rate > 0 and (dep.principal or dep.amount) > 0:
                rd_out += (dep.principal or dep.amount) * (rd_months(dep, this_date) - rd_months(dep, prev_date))
        purchase = purchases.get(m, 0.0)
        cash_delta += inc - exp - emi_paid - extra_applied - sip_monthly - rd_out - purchase
        sip_balance = sip_balance * (1 + equity_rate / 12) + sip_monthly
        for key, val in (("income", inc), ("expenses", exp), ("emi", emi_paid), ("extra_loan", extra_applied), ("sip", sip_monthly), ("rd", rd_out), ("purchases", purchase)):
            flows[key] += val
        last = {"income": inc, "expenses": exp, "emi": emi_paid + extra_applied}
        cash_now = liquid_cash0 + cash_delta
        a, l, overdraft = totals_at(m, cash_now, sip_balance)
        points.append({"month": m, "date": this_date.isoformat(), "assets": a, "liabilities": l, "net_worth": a - l, "cash": max(0.0, cash_now), "overdraft": overdraft})
        if m in detail_set:
            details[m] = {
                "month": m, "date": this_date, "years": m / 12, "flows": dict(flows), "liquid_open": liquid_cash0, "cash_end": cash_now,
                "overdraft": overdraft, "sip_balance": sip_balance, "sip_monthly": sip_monthly,
                "growth": [(a_, cur, rate, src, cur * (1 + rate) ** (m / 12)) for a_, cur, rate, src in growth_assets],
                "deposits": [(d, entry_value(d, start), entry_value(d, this_date)) for d in deposits],
                "loans": [dict(l_) for l_ in loans], "last": dict(last),
                "total_assets": a, "total_liabilities": l, "equity_pct": equity_pct, "inflation_pct": inflation_pct,
                "salary_pct": salary_pct, "scenario": scenario, "factor": factor,
            }
    return {"start": start, "months": months, "points": points, "details": details, "loans": loans, "scenario": scenario}


def project_health(entries_assets: list[Entry], detail: dict) -> dict:
    """Financial-health score at a future date, using projected income, spending, EMI and cash."""
    liquid = max(0.0, detail["cash_end"])
    non_cash = len({a.category for a in entries_assets if a.category not in CASH_CATS}) + (1 if detail["sip_monthly"] > 0 and not any(a.category not in CASH_CATS for a in entries_assets) else 0)
    return health_from_metrics(detail["last"]["income"], detail["last"]["expenses"], detail["last"]["emi"], liquid, non_cash)


def build_breakdown(assets, liabilities, income, expenses, assumptions, years: int, scenario: str = "base", cfg: SimConfig | None = None) -> dict:
    """Per-asset projected values with the formula used for each, plus totals and assumptions."""
    months = max(1, min(int(years * 12), MAX_MONTHS))
    sim = simulate(assets, liabilities, income, expenses, assumptions, months, scenario, cfg, detail_at=[months])
    d = sim["details"][months]
    start = sim["start"]
    end = add_months(start, months)
    yrs = months / 12
    rows: list[dict] = []

    for asset, cur, rate, src, value in d["growth"]:
        rows.append({
            "name": asset.name, "category": asset.category, "group": "asset",
            "today": round(cur, 2), "projected": round(value, 2), "rate_pct": round(rate * 100, 2),
            "formula": f"{inr(cur)} × (1 + {rate * 100:.2f}%)^{yrs:g} = {inr(value)}",
            "note": f"Annual compounding. {src}.",
        })
    for dep, today_val, future_val in d["deposits"]:
        if dep.category == "fixed_deposit":
            n = fd_periods(dep)
            principal = dep.principal or dep.amount
            t = fd_years(dep, end)
            if principal > 0 and dep.start_date and dep.annual_rate > 0:
                matured = bool(dep.maturity_date and dep.maturity_date <= end)
                formula = f"{inr(principal)} × (1 + {dep.annual_rate:g}% ÷ {n})^({n} × {t:.2f}) = {inr(future_val)}"
                note = f"Compounded {dep.frequency.replace('_', '-')} ({n}x a year) for {t:.2f} years" + (f"; matures on {dep.maturity_date.isoformat()} and is held at maturity value." if matured else ".")
            else:
                formula, note = f"Held at recorded value {inr(future_val)}", "Add start date and rate to project growth."
        else:
            instalment = dep.principal or dep.amount
            k = rd_months(dep, end)
            if instalment > 0 and dep.start_date and dep.annual_rate > 0:
                i = dep.annual_rate / 12
                formula = f"{inr(instalment)} × [((1 + {i:.3f}%)^{k} − 1) ÷ {i:.3f}%] × (1 + {i:.3f}%) = {inr(future_val)}"
                note = f"{k} monthly instalments compounded monthly; instalments stop at maturity. Each instalment is deducted from projected cash."
            else:
                formula, note = f"Held at recorded value {inr(future_val)}", "Add start date, instalment and rate to project growth."
        rows.append({
            "name": dep.name, "category": dep.category, "group": "asset", "today": round(today_val, 2), "projected": round(future_val, 2),
            "rate_pct": dep.annual_rate, "formula": formula, "note": note,
        })
    if d["sip_monthly"] > 0:
        r = d["equity_pct"] / 100 * d["factor"]
        formula = (f"{inr(d['sip_monthly'])} × [((1 + {r * 100 / 12:.3f}%)^{months} − 1) ÷ {r * 100 / 12:.3f}%] = {inr(d['sip_balance'])}"
                   if r else f"{inr(d['sip_monthly'])} × {months} months = {inr(d['sip_balance'])}")
        rows.append({
            "name": "Monthly investment (SIP)", "category": "sip", "group": "asset", "today": 0.0, "projected": round(d["sip_balance"], 2), "rate_pct": round(r * 100, 2),
            "formula": formula, "note": f"Invested every month at equity return {d['equity_pct']:g}% x scenario factor {d['factor']:g}, compounded monthly.",
        })
    f = d["flows"]
    cash_end = max(0.0, d["cash_end"])
    rows.append({
        "name": "Cash & savings (with cumulative surplus)", "category": "cash", "group": "asset", "today": round(d["liquid_open"], 2), "projected": round(cash_end, 2), "rate_pct": 0.0,
        "formula": f"{inr(d['liquid_open'])} + income {inr(f['income'])} − expenses {inr(f['expenses'])} − loan payments {inr(f['emi'] + f['extra_loan'])} − SIP {inr(f['sip'])} − RD {inr(f['rd'])}"
                   + (f" − purchases {inr(f['purchases'])}" if f["purchases"] else "") + f" = {inr(d['cash_end'])}",
        "note": f"Income grows {d['salary_pct']:g}% a year and expenses {d['inflation_pct']:g}% a year (stepped yearly). Cash earns 0%.",
    })

    liab_rows = []
    for loan in d["loans"]:
        e = loan["entry"]
        remaining = loan["bal"]
        if loan["emi"] > 0:
            formula = f"{inr(loan['opening'])} rolled monthly: balance × (1 + {e.annual_rate / 12:.3f}%) − EMI {inr(loan['emi'])}, for up to {months} months = {inr(remaining)}"
            note = f"Interest paid in this period {inr(loan['interest'])}; total repaid {inr(loan['paid'])}." + (" Loan fully repaid." if remaining <= 0 else "")
        else:
            formula, note = f"No EMI entered; balance held at {inr(remaining)}", "Add an EMI to model repayment."
        liab_rows.append({"name": e.name, "category": e.category, "group": "liability", "today": round(loan["opening"], 2), "projected": round(remaining, 2), "rate_pct": e.annual_rate, "formula": formula, "note": note})
    if d["overdraft"] > 0:
        liab_rows.append({
            "name": "Projected cash shortfall", "category": "overdraft", "group": "liability", "today": 0.0, "projected": round(d["overdraft"], 2), "rate_pct": 0.0,
            "formula": f"Cash would reach {inr(d['cash_end'])}; the shortfall is shown as a liability", "note": "Spending exceeds income in this plan. Interest on the shortfall is not modelled.",
        })

    nw = d["total_assets"] - d["total_liabilities"]
    inflation = d["inflation_pct"] / 100
    real_nw = nw / (1 + inflation) ** yrs
    health = project_health(assets, d)
    assumptions_list = [
        {"label": "Scenario", "value": f"{sim['scenario'].title()} (market returns x{d['factor']:g})"},
        {"label": "Equity return", "value": f"{d['equity_pct']:g}% a year"},
        {"label": "Property growth", "value": f"{(cfg.property_growth if cfg and cfg.property_growth is not None else assumptions.property_growth):g}% a year"},
        {"label": "Salary growth", "value": f"{d['salary_pct']:g}% a year"},
        {"label": "Inflation", "value": f"{d['inflation_pct']:g}% a year"},
        {"label": "Monthly investment (SIP)", "value": inr(d["sip_monthly"])},
        {"label": "Monthly income today", "value": inr(monthly_flow(income, "income"))},
        {"label": "Monthly expenses today", "value": inr(monthly_flow(expenses, "expense"))},
    ]
    if cfg and cfg.extra_loan_payment:
        assumptions_list.append({"label": "Extra loan repayment", "value": f"{inr(cfg.extra_loan_payment)} a month"})
    return {
        "years": yrs, "months": months, "scenario": sim["scenario"], "as_of": start.isoformat(), "end_date": end.isoformat(),
        "financial_year": financial_year_label(end), "financial_year_end": financial_year_end(end).isoformat(),
        "asset_rows": rows, "liability_rows": liab_rows,
        "cash_flow": {k: round(v, 2) for k, v in f.items()},
        "totals": {"assets": round(d["total_assets"], 2), "liabilities": round(d["total_liabilities"], 2), "net_worth": round(nw, 2)},
        "inflation_adjusted": {
            "net_worth": round(real_nw, 2),
            "formula": f"{inr(nw)} ÷ (1 + {d['inflation_pct']:g}%)^{yrs:g} = {inr(real_nw)}",
            "note": "Estimated value in today's purchasing power.",
        },
        "projected_health": {"score": health["score"], "label": health["label"], "metrics": health["metrics"]},
        "assumptions": assumptions_list,
        "disclaimer": "Estimates from the numbers you entered and the assumptions above. They are not predictions or guarantees.",
    }
