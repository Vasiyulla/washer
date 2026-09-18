"""Machine model — a physical washing machine owned by a user."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, Float, Integer, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class Machine(Base):
    __tablename__ = "machines"

    id: Mapped[str] = mapped_column(
        String(36), primary_key=True, default=lambda: str(uuid.uuid4())
    )
    owner_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("users.id"), nullable=False, index=True
    )
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    machine_code: Mapped[str] = mapped_column(
        String(50), unique=True, nullable=False, index=True
    )
    installed_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )

    # ── State ─────────────────────────────────────────────────────────────────
    current_status: Mapped[str] = mapped_column(
        String(20), nullable=False, default="IDLE"
    )
    last_known_status: Mapped[str] = mapped_column(
        String(20), nullable=True
    )

    # ── Per-machine configurable thresholds ───────────────────────────────────
    start_threshold: Mapped[float] = mapped_column(Float, nullable=False, default=2.0)
    stop_threshold: Mapped[float] = mapped_column(Float, nullable=False, default=0.5)
    start_duration_seconds: Mapped[int] = mapped_column(Integer, nullable=False, default=5)
    stop_duration_seconds: Mapped[int] = mapped_column(Integer, nullable=False, default=15)
    offline_timeout_seconds: Mapped[int] = mapped_column(Integer, nullable=False, default=90)

    # ── Relationships ─────────────────────────────────────────────────────────
    owner = relationship("User", back_populates="machines")
    device = relationship("Device", back_populates="machine", uselist=False, lazy="selectin")
    cycles = relationship("WashingCycle", back_populates="machine", lazy="dynamic")
    events = relationship("MachineEvent", back_populates="machine", lazy="dynamic")
    notifications = relationship("Notification", back_populates="machine", lazy="dynamic")
