"""Machine request/response schemas."""

from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class MachineCreate(BaseModel):
    name: str
    machine_code: str
    device_code: str  # Also creates the linked Device


class MachineResponse(BaseModel):
    id: str
    owner_id: str
    name: str
    machine_code: str
    installed_at: datetime
    current_status: str
    last_known_status: Optional[str] = None
    start_threshold: float
    stop_threshold: float
    start_duration_seconds: int
    stop_duration_seconds: int
    offline_timeout_seconds: int

    model_config = {"from_attributes": True}


class MachineStatus(BaseModel):
    machine_id: str
    machine_code: str
    name: str
    current_status: str
    last_known_status: Optional[str] = None
    device_online: bool
    last_heartbeat: Optional[datetime] = None


class MachineConfigUpdate(BaseModel):
    start_threshold: Optional[float] = None
    stop_threshold: Optional[float] = None
    start_duration_seconds: Optional[int] = None
    stop_duration_seconds: Optional[int] = None
    offline_timeout_seconds: Optional[int] = None
