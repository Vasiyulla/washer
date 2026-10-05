"""
SmartWash — Server-side state machine for washing-machine cycle detection.

Uses a rolling window of recent vibration readings to decide transitions:
  IDLE → RUNNING  : sustained vibration above start_threshold for start_duration seconds
  RUNNING → COMPLETED : sustained vibration below stop_threshold for stop_duration seconds
  Any → OFFLINE   : handled separately by offline_checker.py

The backend is the authority — it does NOT trust the ESP32's self-reported status.
"""

import logging
from collections import defaultdict, deque
from datetime import datetime, timezone
from typing import Optional

from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.machine import Machine
from app.models.device import Device
from app.models.telemetry import Telemetry
from app.models.washing_cycle import WashingCycle
from app.models.machine_event import MachineEvent
from app.models.notification import Notification

logger = logging.getLogger(__name__)

# ── In-memory rolling window per machine ──────────────────────────────────────
# Stores deque of (timestamp_utc, vibration_magnitude) tuples.
# This avoids a DB round-trip on every telemetry point for window checks.
_vibration_windows: dict[str, deque] = defaultdict(lambda: deque(maxlen=500))


def _window_all_above(window: deque, threshold: float, duration_seconds: int, now: datetime) -> bool:
    """Check if the average reading in the last `duration_seconds` exceeds `threshold`."""
    cutoff = now.timestamp() - duration_seconds
    recent = [v for t, v in window if t.timestamp() >= cutoff]
    if len(recent) < 2:
        return False
    return (sum(recent) / len(recent)) > threshold


def _window_all_below(window: deque, threshold: float, duration_seconds: int, now: datetime) -> bool:
    """Check if the average reading in the last `duration_seconds` is below `threshold`."""
    cutoff = now.timestamp() - duration_seconds
    recent = [v for t, v in window if t.timestamp() >= cutoff]
    if len(recent) < 2:
        return False
    return (sum(recent) / len(recent)) < threshold


async def evaluate_state(
    db: AsyncSession,
    machine: Machine,
    vibration: float,
    timestamp_utc: datetime,
) -> Optional[str]:
    """
    Evaluate the machine state after a new telemetry reading.

    Returns the new status string if a transition occurred, or None if no change.
    Side effects: creates WashingCycle / closes it, creates MachineEvents and Notifications.
    """
    machine_id = machine.id
    window = _vibration_windows[machine_id]
    window.append((timestamp_utc, vibration))

    old_status = machine.current_status
    new_status: Optional[str] = None

    # ── IDLE → RUNNING ────────────────────────────────────────────────────────
    if old_status in ("IDLE", "COMPLETED", "OFFLINE"):
        if _window_all_above(window, machine.start_threshold, machine.start_duration_seconds, timestamp_utc):
            new_status = "RUNNING"
            machine.current_status = "RUNNING"
            machine.last_known_status = "RUNNING"

            # Create a new WashingCycle
            cycle = WashingCycle(
                machine_id=machine_id,
                started_at=timestamp_utc,
                status="RUNNING",
            )
            db.add(cycle)

            # Create CYCLE_STARTED event
            event = MachineEvent(
                machine_id=machine_id,
                event_type="CYCLE_STARTED",
                timestamp_utc=timestamp_utc,
                metadata_json={"vibration": vibration},
            )
            db.add(event)

            # Notify machine owner
            notification = Notification(
                user_id=machine.owner_id,
                machine_id=machine_id,
                type="CYCLE_STARTED",
                message=f"Washing cycle started on {machine.name}",
            )
            db.add(notification)

            logger.info("Machine %s transitioned IDLE → RUNNING", machine.machine_code)

    # ── RUNNING → COMPLETED ───────────────────────────────────────────────────
    elif old_status == "RUNNING":
        if _window_all_below(window, machine.stop_threshold, machine.stop_duration_seconds, timestamp_utc):
            new_status = "COMPLETED"
            machine.current_status = "COMPLETED"
            machine.last_known_status = "COMPLETED"

            # Find the open (RUNNING) cycle and close it
            result = await db.execute(
                select(WashingCycle)
                .where(WashingCycle.machine_id == machine_id)
                .where(WashingCycle.status == "RUNNING")
                .order_by(WashingCycle.started_at.desc())
                .limit(1)
            )
            cycle = result.scalar_one_or_none()

            if cycle:
                cycle.ended_at = timestamp_utc
                cycle.status = "COMPLETED"
                cycle.duration_seconds = int(
                    (timestamp_utc - cycle.started_at).total_seconds()
                )

                # Compute vibration stats from telemetry within the cycle window
                device = machine.device
                if device:
                    stats_result = await db.execute(
                        select(
                            func.avg(Telemetry.vibration_magnitude),
                            func.max(Telemetry.vibration_magnitude),
                            func.min(Telemetry.vibration_magnitude),
                        )
                        .where(Telemetry.device_id == device.id)
                        .where(Telemetry.timestamp_utc >= cycle.started_at)
                        .where(Telemetry.timestamp_utc <= timestamp_utc)
                    )
                    row = stats_result.one()
                    cycle.avg_vibration = round(row[0], 4) if row[0] else None
                    cycle.max_vibration = round(row[1], 4) if row[1] else None
                    cycle.min_vibration = round(row[2], 4) if row[2] else None

            # Create CYCLE_COMPLETED event
            event = MachineEvent(
                machine_id=machine_id,
                event_type="CYCLE_COMPLETED",
                timestamp_utc=timestamp_utc,
                metadata_json={
                    "duration_seconds": cycle.duration_seconds if cycle else None,
                    "avg_vibration": cycle.avg_vibration if cycle else None,
                },
            )
            db.add(event)

            # Notify machine owner
            duration_min = (cycle.duration_seconds // 60) if cycle and cycle.duration_seconds else 0
            notification = Notification(
                user_id=machine.owner_id,
                machine_id=machine_id,
                type="CYCLE_COMPLETED",
                message=f"Washing cycle completed on {machine.name} ({duration_min} min)",
            )
            db.add(notification)

            logger.info("Machine %s transitioned RUNNING → COMPLETED", machine.machine_code)

    await db.flush()
    return new_status


def clear_window(machine_id: str) -> None:
    """Clear the in-memory rolling window for a machine (e.g. on device reconnect)."""
    _vibration_windows.pop(machine_id, None)
