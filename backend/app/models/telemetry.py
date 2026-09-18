"""Telemetry model — individual accelerometer readings from devices."""

from datetime import datetime, timezone

from sqlalchemy import Integer, String, Float, DateTime, ForeignKey, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class Telemetry(Base):
    __tablename__ = "telemetry"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    device_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("devices.id"), nullable=False
    )
    timestamp_utc: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
    )
    x: Mapped[float] = mapped_column(Float, nullable=False)
    y: Mapped[float] = mapped_column(Float, nullable=False)
    z: Mapped[float] = mapped_column(Float, nullable=False)
    vibration_magnitude: Mapped[float] = mapped_column(Float, nullable=False)
    status: Mapped[str] = mapped_column(String(20), nullable=False)

    # Composite index for efficient time-range queries per device
    __table_args__ = (
        Index("ix_telemetry_device_time", "device_id", "timestamp_utc"),
    )

    # Relationships
    device = relationship("Device", back_populates="telemetry_rows")
