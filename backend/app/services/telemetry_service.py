"""
SmartWash — Telemetry ingestion service.

Accepts telemetry from ESP32 devices, persists it, runs the state machine,
and broadcasts to WebSocket subscribers.
"""

import logging
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException, status

from app.models.device import Device
from app.models.telemetry import Telemetry
from app.models.device_health import DeviceHealth
from app.schemas.telemetry import TelemetryCreate
from app.services import state_machine

logger = logging.getLogger(__name__)


async def ingest_telemetry(db: AsyncSession, payload: TelemetryCreate) -> dict:
    """
    Process a single telemetry reading from an ESP32 device.

    1. Resolve device by device_code
    2. Persist the telemetry row
    3. Update device heartbeat / online status
    4. Run the state machine
    5. Return the result for WebSocket broadcast
    """
    # 1. Resolve device
    result = await db.execute(
        select(Device).where(Device.device_code == payload.device_id)
    )
    device = result.scalar_one_or_none()
    if not device:
        # Auto-provision device for registered machine if available
        mach_res = await db.execute(select(Machine))
        first_mach = mach_res.scalars().first()
        if first_mach:
            import uuid
            device = Device(
                id=str(uuid.uuid4()),
                machine_id=first_mach.id,
                device_code=payload.device_id,
                firmware_version="1.0.0",
                is_online=True,
            )
            db.add(device)
            await db.flush()
        else:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Device '{payload.device_id}' not found",
            )

    now = datetime.now(timezone.utc)

    # 2. Persist telemetry
    telemetry = Telemetry(
        device_id=device.id,
        timestamp_utc=payload.timestamp,
        x=payload.x,
        y=payload.y,
        z=payload.z,
        vibration_magnitude=payload.vibration,
        status=payload.status,
    )
    db.add(telemetry)

    # 3. Update device heartbeat
    device.last_heartbeat = now
    device.is_online = True

    # Update device health
    health_result = await db.execute(
        select(DeviceHealth).where(DeviceHealth.device_id == device.id)
    )
    health = health_result.scalar_one_or_none()
    if health:
        health.last_seen = now

    # 4. Run state machine
    # Eagerly load the machine relationship
    machine_result = await db.execute(
        select(Device).where(Device.id == device.id)
    )
    device = machine_result.scalar_one()
    await db.refresh(device, ["machine"])
    machine = device.machine

    transition = await state_machine.evaluate_state(
        db, machine, payload.vibration, payload.timestamp
    )

    await db.flush()

    # 5. Build broadcast payload
    broadcast_data = {
        "type": "telemetry",
        "machine_id": machine.id,
        "device_id": payload.device_id,
        "timestamp": payload.timestamp.isoformat(),
        "x": payload.x,
        "y": payload.y,
        "z": payload.z,
        "vibration": payload.vibration,
        "status": machine.current_status,
        "machine_status": machine.current_status,
    }

    if transition:
        broadcast_data["transition"] = transition
        broadcast_data["type"] = "state_change"

    return broadcast_data


async def process_heartbeat(
    db: AsyncSession, device_code: str, wifi_rssi: float | None = None,
    uptime_seconds: int | None = None, firmware_version: str | None = None
) -> dict:
    """Process a heartbeat from a device."""
    result = await db.execute(
        select(Device).where(Device.device_code == device_code)
    )
    device = result.scalar_one_or_none()
    if not device:
        mach_res = await db.execute(select(Machine))
        first_mach = mach_res.scalars().first()
        if first_mach:
            import uuid
            device = Device(
                id=str(uuid.uuid4()),
                machine_id=first_mach.id,
                device_code=device_code,
                firmware_version=firmware_version or "1.0.0",
                is_online=True,
            )
            db.add(device)
            await db.flush()
        else:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Device '{device_code}' not found",
            )

    now = datetime.now(timezone.utc)
    device.last_heartbeat = now
    device.is_online = True
    if firmware_version:
        device.firmware_version = firmware_version

    # Update DeviceHealth
    health_result = await db.execute(
        select(DeviceHealth).where(DeviceHealth.device_id == device.id)
    )
    health = health_result.scalar_one_or_none()
    if health:
        health.last_seen = now
        if wifi_rssi is not None:
            health.wifi_rssi = wifi_rssi
        if uptime_seconds is not None:
            health.uptime_seconds = uptime_seconds

    await db.flush()
    return {
        "status": "ok",
        "device_code": device_code,
        "machine_id": device.machine_id,
        "timestamp": now.isoformat(),
    }
