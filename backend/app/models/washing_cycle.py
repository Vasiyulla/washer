"""WashingCycle model — records one complete wash cycle with computed stats."""

import uuid
from datetime import datetime

from sqlalchemy import String, Integer, Float, DateTime, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class WashingCycle(Base):
    __tablename__ = "washing_cycles"

    id: Mapped[str] = mapped_column(
        String(36), primary_key=True, default=lambda: str(uuid.uuid4())
    )
    machine_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("machines.id"), nullable=False, index=True
    )
    started_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False
    )
    ended_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    duration_seconds: Mapped[int | None] = mapped_column(Integer, nullable=True)
    status: Mapped[str] = mapped_column(
        String(20), nullable=False, default="RUNNING"
    )  # RUNNING or COMPLETED

    # Computed on cycle completion from telemetry rows
    avg_vibration: Mapped[float | None] = mapped_column(Float, nullable=True)
    max_vibration: Mapped[float | None] = mapped_column(Float, nullable=True)
    min_vibration: Mapped[float | None] = mapped_column(Float, nullable=True)

    # Relationships
    machine = relationship("Machine", back_populates="cycles")
