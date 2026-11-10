from __future__ import annotations

from datetime import date, datetime, timezone

from sqlalchemy import Boolean, Date, DateTime, Float, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from database.db import Base


def utcnow_naive() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    email: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    name: Mapped[str] = mapped_column(String(100), default="")
    password_hash: Mapped[str] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow_naive)


class Entry(Base):
    __tablename__ = "entries"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(16), index=True)
    category: Mapped[str] = mapped_column(String(40), index=True)
    name: Mapped[str] = mapped_column(String(120))
    institution: Mapped[str] = mapped_column(String(120), default="")
    amount: Mapped[float] = mapped_column(Float, default=0)
    principal: Mapped[float] = mapped_column(Float, default=0)
    annual_rate: Mapped[float] = mapped_column(Float, default=0)
    frequency: Mapped[str] = mapped_column(String(20), default="monthly")
    emi: Mapped[float] = mapped_column(Float, default=0)
    tenure_months: Mapped[int] = mapped_column(Integer, default=0)
    start_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    maturity_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    growth_rate: Mapped[float] = mapped_column(Float, default=0)
    notes: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow_naive)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow_naive, onupdate=utcnow_naive)


class Assumptions(Base):
    __tablename__ = "assumptions"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), unique=True)
    salary_growth: Mapped[float] = mapped_column(Float, default=8)
    equity_return: Mapped[float] = mapped_column(Float, default=10)
    property_growth: Mapped[float] = mapped_column(Float, default=6)
    inflation: Mapped[float] = mapped_column(Float, default=5)
    monthly_investment: Mapped[float] = mapped_column(Float, default=0)


class Snapshot(Base):
    __tablename__ = "snapshots"
    __table_args__ = (UniqueConstraint("user_id", "snapshot_date", name="uq_snapshot_user_date"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    snapshot_date: Mapped[date] = mapped_column(Date, default=date.today)
    net_worth: Mapped[float] = mapped_column(Float, default=0)
    # Added in v0.3 - nullable so snapshots saved by older versions stay valid.
    assets: Mapped[float | None] = mapped_column(Float, nullable=True)
    liabilities: Mapped[float | None] = mapped_column(Float, nullable=True)
    monthly_income: Mapped[float | None] = mapped_column(Float, nullable=True)
    monthly_expenses: Mapped[float | None] = mapped_column(Float, nullable=True)
    monthly_emi: Mapped[float | None] = mapped_column(Float, nullable=True)
    monthly_investment: Mapped[float | None] = mapped_column(Float, nullable=True)
    source: Mapped[str] = mapped_column(String(12), default="auto")  # auto | nightly | manual
    note: Mapped[str] = mapped_column(String(200), default="")


class Goal(Base):
    __tablename__ = "goals"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    name: Mapped[str] = mapped_column(String(120))
    kind: Mapped[str] = mapped_column(String(24), default="custom")  # emergency_fund | home_deposit | education | custom
    target_amount: Mapped[float] = mapped_column(Float, default=0)
    start_amount: Mapped[float] = mapped_column(Float, default=0)
    current_amount: Mapped[float] = mapped_column(Float, default=0)
    monthly_contribution: Mapped[float] = mapped_column(Float, default=0)
    expected_return: Mapped[float] = mapped_column(Float, default=0)
    target_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    auto_link_cash: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow_naive)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow_naive, onupdate=utcnow_naive)


class GoalLog(Base):
    __tablename__ = "goal_logs"
    __table_args__ = (UniqueConstraint("goal_id", "log_date", name="uq_goal_log_date"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    goal_id: Mapped[int] = mapped_column(ForeignKey("goals.id", ondelete="CASCADE"), index=True)
    log_date: Mapped[date] = mapped_column(Date)
    amount: Mapped[float] = mapped_column(Float, default=0)


class ProjectionPlan(Base):
    """A saved projection, kept so it can later be compared with actual snapshots."""
    __tablename__ = "projection_plans"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    created_on: Mapped[date] = mapped_column(Date)
    label: Mapped[str] = mapped_column(String(120), default="")
    source: Mapped[str] = mapped_column(String(12), default="manual")  # manual | auto
    starting_net_worth: Mapped[float] = mapped_column(Float, default=0)
    horizon_months: Mapped[int] = mapped_column(Integer, default=60)
    payload: Mapped[str] = mapped_column(Text, default="{}")  # JSON {scenario: [net worth per month 0..N]}
    assumptions: Mapped[str] = mapped_column(Text, default="{}")
