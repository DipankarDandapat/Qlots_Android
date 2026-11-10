"""Date helpers shared by the finance engine, snapshots and the nightly job."""
from __future__ import annotations

import calendar
import os
from datetime import date, datetime
from zoneinfo import ZoneInfo

APP_TIMEZONE = os.getenv("APP_TIMEZONE", "Asia/Kolkata")


def app_zone() -> ZoneInfo:
    try:
        return ZoneInfo(APP_TIMEZONE)
    except Exception:  # pragma: no cover - unknown tz name falls back to IST
        return ZoneInfo("Asia/Kolkata")


def today_local() -> date:
    """Today's date in the app time zone (IST by default), not the server's zone."""
    return datetime.now(app_zone()).date()


def add_months(d: date, months: int) -> date:
    """Calendar-month addition that clamps to the last valid day (31 Jan + 1m = 28/29 Feb)."""
    year, month0 = divmod(d.year * 12 + d.month - 1 + months, 12)
    month = month0 + 1
    return date(year, month, min(d.day, calendar.monthrange(year, month)[1]))


def month_start(d: date) -> date:
    return d.replace(day=1)


def financial_year_end(d: date) -> date:
    """Indian financial year ends on 31 March."""
    return date(d.year + 1 if d.month > 3 else d.year, 3, 31)


def financial_year_label(d: date) -> str:
    end = financial_year_end(d)
    return f"FY {end.year - 1}-{str(end.year)[2:]}"
