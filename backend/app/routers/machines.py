"""
Machines router — all endpoints scoped to the authenticated user's machines.
"""

from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user
from app.models.user import User
from app.models.telemetry import Telemetry
from app.models.device import Device
from app.models.device_health import DeviceHealth
from app.schemas.machine import MachineCreate, MachineResponse, MachineStatus, MachineConfigUpdate
from app.schemas.telemetry import TelemetryResponse
from app.schemas.cycle import CycleResponse, CycleDetailResponse
from app.schemas.device import DeviceResponse
from app.services import machine_service, cycle_service

router = APIRouter(prefix="/api/v1/machines", tags=["machines"])


# ── Machine CRUD ──────────────────────────────────────────────────────────────

@router.get("", response_model=list[MachineResponse])
async def list_machines(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    machines = await machine_service.get_user_machines(db, user)
    return machines


@router.post("", response_model=MachineResponse, status_code=201)
async def create_machine(
    req: MachineCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    return await machine_service.create_machine(db, user, req)


@router.get("/{machine_id}", response_model=MachineResponse)
async def get_machine(
    machine_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    return await machine_service.get_machine_for_user(db, user, machine_id)


# ── Status ────────────────────────────────────────────────────────────────────

@router.get("/{machine_id}/status", response_model=MachineStatus)
async def get_machine_status(
    machine_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    machine = await machine_service.get_machine_for_user(db, user, machine_id)
    device = machine.device

    is_online = False
    if device and device.is_online and device.last_heartbeat:
        last = device.last_heartbeat
        if last.tzinfo is None:
            last = last.replace(tzinfo=timezone.utc)
        now = datetime.now(timezone.utc)
        timeout = machine.offline_timeout_seconds or 90
        is_online = (now - last).total_seconds() <= timeout

    current_status = machine.current_status
    if not is_online and current_status != "RUNNING":
        current_status = "OFFLINE"

    return MachineStatus(
        machine_id=machine.id,
        machine_code=machine.machine_code,
        name=machine.name,
        current_status=current_status,
        last_known_status=machine.last_known_status,
        device_online=is_online,
        last_heartbeat=device.last_heartbeat if device else None,
    )


# ── Cycles ────────────────────────────────────────────────────────────────────

@router.get("/{machine_id}/cycles", response_model=list[CycleResponse])
async def list_cycles(
    machine_id: str,
    range: str = Query("7d", alias="range"),
    start: Optional[datetime] = None,
    end: Optional[datetime] = None,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    # Enforce ownership
    await machine_service.get_machine_for_user(db, user, machine_id)
    return await cycle_service.get_cycles(db, machine_id, range, start, end)


@router.get("/{machine_id}/cycles/{cycle_id}", response_model=CycleDetailResponse)
async def get_cycle_detail(
    machine_id: str,
    cycle_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    await machine_service.get_machine_for_user(db, user, machine_id)
    return await cycle_service.get_cycle_detail(db, machine_id, cycle_id)


# ── Telemetry ─────────────────────────────────────────────────────────────────

@router.get("/{machine_id}/telemetry", response_model=list[TelemetryResponse])
async def get_telemetry(
    machine_id: str,
    since: Optional[datetime] = None,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    machine = await machine_service.get_machine_for_user(db, user, machine_id)
    device = machine.device
    if not device:
        return []

    query = select(Telemetry).where(Telemetry.device_id == device.id)
    if since:
        query = query.where(Telemetry.timestamp_utc >= since)
    query = query.order_by(Telemetry.timestamp_utc.desc()).limit(200)

    result = await db.execute(query)
    return list(result.scalars().all())


# ── Health ────────────────────────────────────────────────────────────────────

@router.get("/{machine_id}/health")
async def get_health(
    machine_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    machine = await machine_service.get_machine_for_user(db, user, machine_id)
    device = machine.device
    if not device:
        return {"error": "No device linked"}

    health_result = await db.execute(
        select(DeviceHealth).where(DeviceHealth.device_id == device.id)
    )
    health = health_result.scalar_one_or_none()

    return {
        "device_code": device.device_code,
        "firmware_version": device.firmware_version,
        "is_online": device.is_online,
        "last_heartbeat": device.last_heartbeat.isoformat() if device.last_heartbeat else None,
        "wifi_rssi": health.wifi_rssi if health else None,
        "uptime_seconds": health.uptime_seconds if health else None,
        "last_seen": health.last_seen.isoformat() if health and health.last_seen else None,
    }


# ── Config ────────────────────────────────────────────────────────────────────

@router.patch("/{machine_id}/config", response_model=MachineResponse)
async def update_config(
    machine_id: str,
    req: MachineConfigUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    machine = await machine_service.get_machine_for_user(db, user, machine_id)
    return await machine_service.update_machine_config(db, machine, req)
