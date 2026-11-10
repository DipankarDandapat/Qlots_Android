from __future__ import annotations

from typing import Literal

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from database.db import db_session
from models.models import User
from services.auth import current_user
from services.engine import SCENARIO_FACTOR, SimConfig, build_breakdown, simulate
from services.finance import collect_finances, get_assumptions
from services.planning import deposit_planner, emergency_fund, goal_contribution, loan_payoff, what_if
from services.dates import add_months, today_local

router = APIRouter(prefix="/api/planning", tags=["planning"])
Scenario = Literal["conservative", "base", "optimistic"]


class OutlookIn(BaseModel):
    """Adjustable 2- and 3-year (up to 10-year) projection. Any field left empty uses the saved assumptions."""
    years: int = Field(default=3, ge=1, le=10)
    extra_monthly_saving: float = Field(default=0, ge=0, le=1e9)
    extra_loan_payment: float = Field(default=0, ge=0, le=1e9)
    equity_return: float | None = Field(default=None, ge=-50, le=100)
    property_growth: float | None = Field(default=None, ge=-50, le=100)
    salary_growth: float | None = Field(default=None, ge=-50, le=100)
    inflation: float | None = Field(default=None, ge=-50, le=100)


class WhatIfIn(BaseModel):
    years: int = Field(default=5, ge=1, le=10)
    scenario: Scenario = "base"
    extra_monthly_saving: float = Field(default=0, ge=0, le=1e9)
    extra_loan_payment: float = Field(default=0, ge=0, le=1e9)
    purchase_amount: float = Field(default=0, ge=0, le=1e12)
    purchase_in_months: int = Field(default=1, ge=1, le=120)
    delay_months: int = Field(default=0, ge=0, le=120)


class LoanPayoffIn(BaseModel):
    extra_monthly: float = Field(default=0, ge=0, le=1e9)
    target_loan_id: int | None = None


class GoalCalcIn(BaseModel):
    target_amount: float = Field(gt=0, le=1e13)
    current_amount: float = Field(default=0, ge=0, le=1e13)
    annual_return: float = Field(default=0, ge=-20, le=40)
    months: int = Field(ge=1, le=600)


def _cfg(body: OutlookIn) -> SimConfig:
    return SimConfig(
        extra_monthly_saving=body.extra_monthly_saving, extra_loan_payment=body.extra_loan_payment, equity_return=body.equity_return,
        property_growth=body.property_growth, salary_growth=body.salary_growth, inflation=body.inflation,
    )


@router.post("/outlook")
def outlook(body: OutlookIn, user: User = Depends(current_user), db: Session = Depends(db_session)):
    """Base, conservative and optimistic net-worth paths, with inflation-adjusted values."""
    assets, liabilities, income, expenses = collect_finances(db, user.id)
    a = get_assumptions(db, user.id)
    cfg = _cfg(body)
    months = body.years * 12
    inflation = cfg.inflation if cfg.inflation is not None else a.inflation
    scenarios = {}
    for name in ("conservative", "base", "optimistic"):
        sim = simulate(assets, liabilities, income, expenses, a, months, name, cfg)
        scenarios[name] = [
            {"month": p["month"], "date": p["date"], "assets": round(p["assets"], 2), "liabilities": round(p["liabilities"], 2),
             "net_worth": round(p["net_worth"], 2), "real_net_worth": round(p["net_worth"] / (1 + inflation / 100) ** (p["month"] / 12), 2)}
            for p in sim["points"]
        ]
    return {
        "years": body.years, "end_date": add_months(today_local(), months).isoformat(), "inflation": inflation, "scenarios": scenarios,
        "assumptions": {
            "equity_return": cfg.equity_return if cfg.equity_return is not None else a.equity_return,
            "property_growth": cfg.property_growth if cfg.property_growth is not None else a.property_growth,
            "salary_growth": cfg.salary_growth if cfg.salary_growth is not None else a.salary_growth,
            "inflation": inflation, "monthly_investment": a.monthly_investment, "extra_monthly_saving": body.extra_monthly_saving,
            "extra_loan_payment": body.extra_loan_payment,
        },
        "factors": SCENARIO_FACTOR,
        "disclaimer": "Estimates from your records and the assumptions shown. Not predictions or guarantees.",
    }


@router.post("/breakdown")
def breakdown(body: OutlookIn, scenario: Scenario = "base", user: User = Depends(current_user), db: Session = Depends(db_session)):
    assets, liabilities, income, expenses = collect_finances(db, user.id)
    return build_breakdown(assets, liabilities, income, expenses, get_assumptions(db, user.id), body.years, scenario, _cfg(body))


@router.post("/what-if")
def what_if_route(body: WhatIfIn, user: User = Depends(current_user), db: Session = Depends(db_session)):
    assets, liabilities, income, expenses = collect_finances(db, user.id)
    return what_if(assets, liabilities, income, expenses, get_assumptions(db, user.id), years=body.years, scenario=body.scenario,
                   extra_monthly_saving=body.extra_monthly_saving, extra_loan_payment=body.extra_loan_payment,
                   purchase_amount=body.purchase_amount, purchase_in_months=body.purchase_in_months, delay_months=body.delay_months)


@router.get("/emergency-fund")
def emergency(months: int = 6, include_emi: bool = True, build_in_months: int = 12, user: User = Depends(current_user), db: Session = Depends(db_session)):
    if not 1 <= months <= 36:
        raise HTTPException(status_code=422, detail="Choose between 1 and 36 months")
    assets, liabilities, _, expenses = collect_finances(db, user.id)
    return emergency_fund(assets, liabilities, expenses, months=months, include_emi=include_emi, build_in_months=build_in_months)


@router.post("/goal-contribution")
def goal_calc(body: GoalCalcIn, user: User = Depends(current_user)):
    return goal_contribution(body.target_amount, body.current_amount, body.annual_return, body.months)


@router.post("/loan-payoff")
def payoff(body: LoanPayoffIn, user: User = Depends(current_user), db: Session = Depends(db_session)):
    _, liabilities, _, _ = collect_finances(db, user.id)
    return loan_payoff(liabilities, body.extra_monthly, body.target_loan_id)


@router.get("/deposits")
def deposits(user: User = Depends(current_user), db: Session = Depends(db_session)):
    assets, _, _, _ = collect_finances(db, user.id)
    return deposit_planner(assets)
