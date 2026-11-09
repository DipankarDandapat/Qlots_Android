"""Net-worth snapshots: automatic (dashboard / nightly) and user-recorded."""
from __future__ import annotations

import logging
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from database.db import SessionLocal
from models.models import Snapshot, User
from services.dates import today_local
from services.finance import collect_finances, entry_value, get_assumptions, monthly_flow

logger = logging.getLogger(__name__)


def _compute(assets, liabilities, income, expenses, assumptions, on_date: date) -> dict:
    total_assets = sum(entry_value(a, on_date) for a in assets)
    total_liabilities = sum(l.amount for l in liabilities)
    return {
        "assets": round(total_assets, 2), "liabilities": round(total_liabilities, 2),
        "net_worth": round(total_assets - total_liabilities, 2),
        "monthly_income": round(monthly_flow(income, "income"), 2),
        "monthly_expenses": round(monthly_flow(expenses, "expense"), 2),
        "monthly_emi": round(sum(l.emi for l in liabilities), 2),
        "monthly_investment": round(assumptions.monthly_investment, 2),
    }


def record_auto_snapshot(db: Session, user_id: int, on_date: date, source: str, assets, liabilities, income, expenses, assumptions) -> Snapshot | None:
    """Upsert the automatic snapshot for a day. A snapshot the user recorded by hand is never overwritten."""
    values = _compute(assets, liabilities, income, expenses, assumptions, on_date)
    row = db.scalar(select(Snapshot).where(Snapshot.user_id == user_id, Snapshot.snapshot_date == on_date))
    if row is None:
        row = Snapshot(user_id=user_id, snapshot_date=on_date, source=source)
        db.add(row)
    elif row.source == "manual":
        return row
    else:
        row.source = source
    for key, val in values.items():
        setattr(row, key, val)
    db.commit()
    return row


def record_manual_snapshot(db: Session, user_id: int, on_date: date | None, assets_total: float | None, liabilities_total: float | None, note: str = "") -> Snapshot:
    on_date = on_date or today_local()
    assets, liabilities, income, expenses = collect_finances(db, user_id)
    values = _compute(assets, liabilities, income, expenses, get_assumptions(db, user_id), on_date)
    if assets_total is not None:
        values["assets"] = round(assets_total, 2)
    if liabilities_total is not None:
        values["liabilities"] = round(liabilities_total, 2)
    values["net_worth"] = round(values["assets"] - values["liabilities"], 2)
    row = db.scalar(select(Snapshot).where(Snapshot.user_id == user_id, Snapshot.snapshot_date == on_date))
    if row is None:
        row = Snapshot(user_id=user_id, snapshot_date=on_date)
        db.add(row)
    row.source = "manual"
    row.note = note[:200]
    for key, val in values.items():
        setattr(row, key, val)
    db.commit()
    db.refresh(row)
    return row


def snapshot_dict(s: Snapshot) -> dict:
    savings_rate = None
    if s.monthly_income:
        savings_rate = round((s.monthly_income - (s.monthly_expenses or 0) - (s.monthly_emi or 0)) / s.monthly_income * 100, 1)
    return {
        "id": s.id, "date": s.snapshot_date.isoformat(), "net_worth": round(s.net_worth, 2),
        "assets": None if s.assets is None else round(s.assets, 2),
        "liabilities": None if s.liabilities is None else round(s.liabilities, 2),
        "monthly_income": s.monthly_income, "monthly_expenses": s.monthly_expenses, "monthly_emi": s.monthly_emi,
        "savings_rate": savings_rate, "source": s.source or "auto", "note": s.note or "",
    }


def run_nightly_refresh(for_date: date | None = None) -> int:
    """Re-value every user's records as of `for_date` (default: today in IST). Safe to run repeatedly."""
    on_date = for_date or today_local()
    count = 0
    db = SessionLocal()
    try:
        for user_id in list(db.scalars(select(User.id))):
            try:
                assets, liabilities, income, expenses = collect_finances(db, user_id)
                if not (assets or liabilities):
                    continue
                record_auto_snapshot(db, user_id, on_date, "nightly", assets, liabilities, income, expenses, get_assumptions(db, user_id))
                count += 1
            except Exception:
                db.rollback()
                logger.exception("Nightly refresh failed for user %s", user_id)
    finally:
        db.close()
    logger.info("Nightly net-worth refresh for %s updated %d user(s)", on_date, count)
    return count
