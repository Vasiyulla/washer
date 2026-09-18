"""MachineEvent model — discrete events (cycle start/stop, device online/offline, etc.)."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, ForeignKey, JSON
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class MachineEvent(Base):
    __tablename__ = "machine_events"

    id: Mapped[str] = mapped_column(
        String(36), primary_key=True, default=lambda: str(uuid.uuid4())
    )
    machine_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("machines.id"), nullable=False, index=True
    )
    event_type: Mapped[str] = mapped_column(
        String(30), nullable=False
    )  # CYCLE_STARTED, CYCLE_COMPLETED, DEVICE_OFFLINE, DEVICE_ONLINE, HIGH_VIBRATION
    timestamp_utc: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
    )
    metadata_json: Mapped[dict | None] = mapped_column(
        "metadata", JSON, nullable=True
    )

    # Relationships
    machine = relationship("Machine", back_populates="events")
