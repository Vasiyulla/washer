"""Washing cycle response schemas."""

from datetime import datetime
from typing import Optional, List

from pydantic import BaseModel

from app.schemas.telemetry import TelemetryResponse


class CycleResponse(BaseModel):
    id: str
    machine_id: str
    started_at: datetime
    ended_at: Optional[datetime] = None
    duration_seconds: Optional[int] = None
    status: str
    avg_vibration: Optional[float] = None
    max_vibration: Optional[float] = None
    min_vibration: Optional[float] = None

    model_config = {"from_attributes": True}


class CycleDetailResponse(CycleResponse):
    """Full cycle detail including the vibration time-series."""
    vibration_series: List[TelemetryResponse] = []
