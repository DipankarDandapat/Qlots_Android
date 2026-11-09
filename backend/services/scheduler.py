"""Runs the net-worth refresh every night at 12:00 AM (app time zone, IST by default)."""
from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timedelta

from services.dates import app_zone
from services.snapshots import run_nightly_refresh

logger = logging.getLogger(__name__)


def seconds_until_midnight(now: datetime | None = None) -> float:
    now = now or datetime.now(app_zone())
    next_midnight = (now + timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
    return max(1.0, (next_midnight - now).total_seconds())


async def nightly_loop() -> None:
    # Catch-up on start so a server that was down at midnight still records today's value.
    try:
        await asyncio.to_thread(run_nightly_refresh)
    except Exception:
        logger.exception("Start-up refresh failed")
    while True:
        await asyncio.sleep(seconds_until_midnight() + 2)
        try:
            await asyncio.to_thread(run_nightly_refresh)
        except Exception:
            logger.exception("Nightly refresh failed")
