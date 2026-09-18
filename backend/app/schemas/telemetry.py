"""Telemetry request/response schemas — matches ESP32 payload."""

from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class TelemetryCreate(BaseModel):
    """Payload sent by the ESP32 on each telemetry POST."""
    device_id: str          # e.g. "ESP32-001" (device_code, not UUID)
    timestamp: datetime     # UTC ISO 8601 from NTP
    x: float
    y: float
    z: float
    vibration: float        # vibration deviation from gravity baseline
    status: str             # device-local state opinion (IDLE/RUNNING)


class TelemetryResponse(BaseModel):
    id: int
    device_id: str
    timestamp_utc: datetime
    x: float
    y: float
    z: float
    vibration_magnitude: float
    status: str

    model_config = {"from_attributes": True}
