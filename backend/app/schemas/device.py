"""Device request/response schemas."""

from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class DeviceResponse(BaseModel):
    id: str
    machine_id: str
    device_code: str
    firmware_version: Optional[str] = None
    last_heartbeat: Optional[datetime] = None
    is_online: bool

    model_config = {"from_attributes": True}


class HeartbeatRequest(BaseModel):
    wifi_rssi: Optional[float] = None
    uptime_seconds: Optional[int] = None
    firmware_version: Optional[str] = None
