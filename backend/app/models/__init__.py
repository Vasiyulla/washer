"""
SmartWash — SQLAlchemy ORM models.

Re-exports every model so Alembic and the app can do:
    from app.models import User, Machine, ...
"""

from app.models.user import User
from app.models.machine import Machine
from app.models.device import Device
from app.models.telemetry import Telemetry
from app.models.washing_cycle import WashingCycle
from app.models.machine_event import MachineEvent
from app.models.notification import Notification
from app.models.device_health import DeviceHealth

__all__ = [
    "User",
    "Machine",
    "Device",
    "Telemetry",
    "WashingCycle",
    "MachineEvent",
    "Notification",
    "DeviceHealth",
]
