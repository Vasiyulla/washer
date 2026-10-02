"""
SmartWash — Offline checker background task.

Periodically scans all devices and transitions machines to OFFLINE if no
heartbeat or telemetry has been received within the configured timeout.
Preserves last_known_status so the app can show "OFFLINE (last known: RUNNING)".
"""

import asyncio
import logging
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import async_session
from app.models.device import Device
from app.models.machine import Machine
from app.models.machine_event import MachineEvent
from app.models.notification import Notification

logger = logging.getLogger(__name__)

_checker_task: asyncio.Task | None = None


async def _check_offline_devices() -> None:
    """Single pass: check all devices for heartbeat timeout."""
    async with async_session() as db:
        result = await db.execute(select(Device).where(Device.is_online == True))  # noqa: E712
        devices = result.scalars().all()

        now = datetime.now(timezone.utc)

        for device in devices:
            # Load the machine
            machine_result = await db.execute(
                select(Machine).where(Machine.id == device.machine_id)
            )
            machine = machine_result.scalar_one_or_none()
            if not machine:
                continue

            timeout = machine.offline_timeout_seconds
            last = device.last_heartbeat
            if not last:
                continue

            if last.tzinfo is None:
                last = last.replace(tzinfo=timezone.utc)

            elapsed = (now - last).total_seconds()
            if elapsed > timeout:
                logger.warning(
                    "Device %s offline (%.0fs since last heartbeat, timeout=%ds)",
                    device.device_code, elapsed, timeout,
                )

                device.is_online = False

                # Preserve last known status before transitioning
                if machine.current_status != "OFFLINE":
                    machine.last_known_status = machine.current_status
                    machine.current_status = "OFFLINE"

                    event = MachineEvent(
                        machine_id=machine.id,
                        event_type="DEVICE_OFFLINE",
                        timestamp_utc=now,
                        metadata_json={
                            "last_heartbeat": last.isoformat(),
                            "elapsed_seconds": elapsed,
                        },
                    )
                    db.add(event)

                    notification = Notification(
                        user_id=machine.owner_id,
                        machine_id=machine.id,
                        type="DEVICE_OFFLINE",
                        message=f"Device {device.device_code} went offline (last known: {machine.last_known_status})",
                    )
                    db.add(notification)

        await db.commit()


async def _run_checker_loop(interval: int = 30) -> None:
    """Background loop that runs the offline check every `interval` seconds."""
    while True:
        try:
            await _check_offline_devices()
        except Exception:
            logger.exception("Error in offline checker")
        await asyncio.sleep(interval)


def start_offline_checker() -> None:
    """Start the background offline checker (call once at app startup)."""
    global _checker_task
    if _checker_task is None or _checker_task.done():
        _checker_task = asyncio.create_task(_run_checker_loop())
        logger.info("Offline checker background task started")


def stop_offline_checker() -> None:
    """Stop the background offline checker (call at app shutdown)."""
    global _checker_task
    if _checker_task and not _checker_task.done():
        _checker_task.cancel()
        logger.info("Offline checker background task stopped")
