"""Device model — an ESP32 board attached to a machine."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, Boolean, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class Device(Base):
    __tablename__ = "devices"

    id: Mapped[str] = mapped_column(
        String(36), primary_key=True, default=lambda: str(uuid.uuid4())
    )
    machine_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("machines.id"), unique=True, nullable=False, index=True
    )
    device_code: Mapped[str] = mapped_column(
        String(50), unique=True, nullable=False, index=True
    )
    firmware_version: Mapped[str] = mapped_column(String(20), nullable=True, default="1.0.0")
    last_heartbeat: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    is_online: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)

    # Relationships
    machine = relationship("Machine", back_populates="device")
    telemetry_rows = relationship("Telemetry", back_populates="device", lazy="dynamic")
    health = relationship("DeviceHealth", back_populates="device", uselist=False, lazy="selectin")
