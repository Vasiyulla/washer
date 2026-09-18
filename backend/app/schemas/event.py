"""MachineEvent response schemas."""

from datetime import datetime
from typing import Optional, Any

from pydantic import BaseModel


class EventResponse(BaseModel):
    id: str
    machine_id: str
    event_type: str
    timestamp_utc: datetime
    metadata_json: Optional[Any] = None

    model_config = {"from_attributes": True}
