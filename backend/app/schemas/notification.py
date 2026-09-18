"""Notification response schemas."""

from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class NotificationResponse(BaseModel):
    id: str
    user_id: str
    machine_id: str
    type: str
    message: str
    is_read: bool
    created_at: datetime

    model_config = {"from_attributes": True}
