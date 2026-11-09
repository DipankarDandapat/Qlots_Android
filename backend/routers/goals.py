from __future__ import annotations

from datetime import date
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from database.db import db_session
from models.models import Goal, GoalLog, User
from services.auth import current_user
from services.dates import today_local
from services.finance import CASH_CATS, collect_finances, entry_value
from services.planning import goal_view

router = APIRouter(prefix="/api/goals", tags=["goals"])


class GoalIn(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    kind: Literal["emergency_fund", "home_deposit", "education", "custom"] = "custom"
    target_amount: float = Field(gt=0, le=1e13)
    current_amount: float = Field(default=0, ge=0, le=1e13)
    monthly_contribution: float = Field(default=0, ge=0, le=1e10)
    expected_return: float = Field(default=0, ge=-20, le=40)
    target_date: date | None = None
    auto_link_cash: bool = False


class ProgressIn(BaseModel):
    current_amount: float = Field(ge=0, le=1e13)


def _liquid(db: Session, user_id: int) -> float:
    assets, _, _, _ = collect_finances(db, user_id)
    return sum(entry_value(a, today_local()) for a in assets if a.category in CASH_CATS)


def _log(db: Session, goal: Goal, amount: float) -> None:
    today = today_local()
    row = db.scalar(select(GoalLog).where(GoalLog.goal_id == goal.id, GoalLog.log_date == today))
    if row is None:
        db.add(GoalLog(goal_id=goal.id, log_date=today, amount=amount))
    else:
        row.amount = amount


def _view(db: Session, goal: Goal, liquid: float) -> dict:
    logs = list(db.scalars(select(GoalLog).where(GoalLog.goal_id == goal.id)))
    return goal_view(goal, logs, liquid)


def _own(db: Session, user: User, goal_id: int) -> Goal:
    goal = db.scalar(select(Goal).where(Goal.id == goal_id, Goal.user_id == user.id))
    if not goal:
        raise HTTPException(status_code=404, detail="Goal not found")
    return goal


@router.get("")
def list_goals(user: User = Depends(current_user), db: Session = Depends(db_session)):
    liquid = _liquid(db, user.id)
    goals = list(db.scalars(select(Goal).where(Goal.user_id == user.id).order_by(Goal.created_at.asc())))
    for g in goals:  # keep a daily progress point for cash-linked goals
        if g.auto_link_cash:
            _log(db, g, liquid)
    db.commit()
    return {"goals": [_view(db, g, liquid) for g in goals]}


@router.post("", status_code=201)
def create_goal(body: GoalIn, user: User = Depends(current_user), db: Session = Depends(db_session)):
    liquid = _liquid(db, user.id)
    start = liquid if body.auto_link_cash else body.current_amount
    goal = Goal(user_id=user.id, start_amount=start, **{**body.model_dump(), "current_amount": start})
    db.add(goal)
    db.commit()
    db.refresh(goal)
    _log(db, goal, start)
    db.commit()
    return _view(db, goal, liquid)


@router.put("/{goal_id}")
def update_goal(goal_id: int, body: GoalIn, user: User = Depends(current_user), db: Session = Depends(db_session)):
    goal = _own(db, user, goal_id)
    liquid = _liquid(db, user.id)
    for key, value in body.model_dump().items():
        setattr(goal, key, value)
    _log(db, goal, liquid if goal.auto_link_cash else goal.current_amount)
    db.commit()
    db.refresh(goal)
    return _view(db, goal, liquid)


@router.post("/{goal_id}/progress")
def record_progress(goal_id: int, body: ProgressIn, user: User = Depends(current_user), db: Session = Depends(db_session)):
    goal = _own(db, user, goal_id)
    goal.current_amount = body.current_amount
    goal.auto_link_cash = False
    _log(db, goal, body.current_amount)
    db.commit()
    db.refresh(goal)
    return _view(db, goal, _liquid(db, user.id))


@router.delete("/{goal_id}", status_code=204)
def delete_goal(goal_id: int, user: User = Depends(current_user), db: Session = Depends(db_session)):
    goal = _own(db, user, goal_id)
    for log in db.scalars(select(GoalLog).where(GoalLog.goal_id == goal.id)):
        db.delete(log)
    db.delete(goal)
    db.commit()
    return Response(status_code=204)
