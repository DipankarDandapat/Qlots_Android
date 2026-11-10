from __future__ import annotations

import logging

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from database.db import db_session
from models.models import Snapshot, User
from services.auth import current_user
from services.finance import dashboard_data
from services.snapshots import snapshot_dict

logger = logging.getLogger(__name__)
router = APIRouter(tags=["dashboard"])


@router.get("/api/dashboard")
def dashboard(user: User = Depends(current_user), db: Session = Depends(db_session)):
    try:
        return dashboard_data(db, user.id, user.name)
    except Exception as e:
        logger.exception(f"Dashboard error for user {user.id}: {e}")
        raise


@router.get("/api/history")
def history(user: User = Depends(current_user), db: Session = Depends(db_session)):
    # Newest 365 days, returned oldest-first so charts read left to right.
    rows = list(db.scalars(select(Snapshot).where(Snapshot.user_id == user.id).order_by(Snapshot.snapshot_date.desc()).limit(365)))
    return [snapshot_dict(s) for s in reversed(rows)]
