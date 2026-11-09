from __future__ import annotations

from datetime import date
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from database.db import db_session
from models.models import ProjectionPlan, Snapshot, User
from services.auth import current_user
from services.dates import today_local
from services.finance import collect_finances, entry_value, get_assumptions
from services.insights import (
    allocation_groups, debt_progress, monthly_cashflow, plan_summary, plan_vs_actual, save_plan, savings_rate_trend,
)
from services.planning import deposit_planner
from services.snapshots import record_manual_snapshot, snapshot_dict

router = APIRouter(tags=["insights"])


class SnapshotIn(BaseModel):
    snapshot_date: date | None = None
    assets: float | None = Field(default=None, ge=0, le=1e15)
    liabilities: float | None = Field(default=None, ge=0, le=1e15)
    note: str = Field(default="", max_length=200)


class PlanIn(BaseModel):
    label: str = Field(default="", max_length=120)


@router.get("/api/snapshots")
def list_snapshots(user: User = Depends(current_user), db: Session = Depends(db_session)):
    rows = list(db.scalars(select(Snapshot).where(Snapshot.user_id == user.id).order_by(Snapshot.snapshot_date.desc()).limit(730)))
    return {"snapshots": [snapshot_dict(s) for s in reversed(rows)]}


@router.post("/api/snapshots", status_code=201)
def record_snapshot(body: SnapshotIn, user: User = Depends(current_user), db: Session = Depends(db_session)):
    if body.snapshot_date and body.snapshot_date > today_local():
        raise HTTPException(status_code=422, detail="Snapshots cannot be dated in the future")
    return snapshot_dict(record_manual_snapshot(db, user.id, body.snapshot_date, body.assets, body.liabilities, body.note))


@router.delete("/api/snapshots/{snapshot_id}", status_code=204)
def delete_snapshot(snapshot_id: int, user: User = Depends(current_user), db: Session = Depends(db_session)):
    row = db.scalar(select(Snapshot).where(Snapshot.id == snapshot_id, Snapshot.user_id == user.id))
    if not row:
        raise HTTPException(status_code=404, detail="Snapshot not found")
    db.delete(row)
    db.commit()
    return Response(status_code=204)


@router.get("/api/insights")
def insights(user: User = Depends(current_user), db: Session = Depends(db_session)):
    assets, liabilities, income, expenses = collect_finances(db, user.id)
    assumptions = get_assumptions(db, user.id)
    return {
        "as_of": today_local().isoformat(),
        "allocation": allocation_groups(assets),
        "cashflow": monthly_cashflow(income, expenses, liabilities),
        "savings_rate": savings_rate_trend(db, user.id, income, expenses, liabilities, assumptions.monthly_investment),
        "debt": debt_progress(db, user.id, liabilities),
        "deposits": deposit_planner(assets),
    }


@router.get("/api/projection/plans")
def list_plans(user: User = Depends(current_user), db: Session = Depends(db_session)):
    plans = list(db.scalars(select(ProjectionPlan).where(ProjectionPlan.user_id == user.id).order_by(ProjectionPlan.created_on.desc(), ProjectionPlan.id.desc())))
    if not plans:
        assets, liabilities, _, _ = collect_finances(db, user.id)
        if assets or liabilities:  # first visit: keep a baseline so later snapshots have something to be compared with
            plans = [save_plan(db, user.id, "Starting plan", "auto")]
    return {"plans": [plan_summary(p) for p in plans]}


@router.post("/api/projection/plans", status_code=201)
def create_plan(body: PlanIn, user: User = Depends(current_user), db: Session = Depends(db_session)):
    return plan_summary(save_plan(db, user.id, body.label.strip(), "manual"))


@router.get("/api/projection/plans/{plan_id}/vs-actual")
def compare_plan(plan_id: int, user: User = Depends(current_user), db: Session = Depends(db_session)):
    plan = db.scalar(select(ProjectionPlan).where(ProjectionPlan.id == plan_id, ProjectionPlan.user_id == user.id))
    if not plan:
        raise HTTPException(status_code=404, detail="Plan not found")
    return plan_vs_actual(db, user.id, plan)


@router.delete("/api/projection/plans/{plan_id}", status_code=204)
def delete_plan(plan_id: int, user: User = Depends(current_user), db: Session = Depends(db_session)):
    plan = db.scalar(select(ProjectionPlan).where(ProjectionPlan.id == plan_id, ProjectionPlan.user_id == user.id))
    if not plan:
        raise HTTPException(status_code=404, detail="Plan not found")
    db.delete(plan)
    db.commit()
    return Response(status_code=204)
