"""
SmartWash — Cycle query service with date-range filtering.
"""

from datetime import datetime, timedelta, timezone
from typing import Optional

from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException, status

from app.models.washing_cycle import WashingCycle
from app.models.telemetry import Telemetry
from app.models.device import Device
from app.schemas.cycle import CycleDetailResponse
from app.schemas.telemetry import TelemetryResponse


async def get_cycles(
    db: AsyncSession,
    machine_id: str,
    range_param: str = "7d",
    start: Optional[datetime] = None,
    end: Optional[datetime] = None,
) -> list[WashingCycle]:
    """Get washing cycles for a machine filtered by date range."""
    now = datetime.now(timezone.utc)

    if range_param == "custom" and start and end:
        filter_start, filter_end = start, end
    elif range_param == "today":
        filter_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
        filter_end = now
    elif range_param == "30d":
        filter_start = now - timedelta(days=30)
        filter_end = now
    else:  # default "7d"
        filter_start = now - timedelta(days=7)
        filter_end = now

    result = await db.execute(
        select(WashingCycle)
        .where(
            and_(
                WashingCycle.machine_id == machine_id,
                WashingCycle.started_at >= filter_start,
                WashingCycle.started_at <= filter_end,
            )
        )
        .order_by(WashingCycle.started_at.desc())
    )
    return list(result.scalars().all())


async def get_cycle_detail(
    db: AsyncSession,
    machine_id: str,
    cycle_id: str,
) -> CycleDetailResponse:
    """Get full cycle detail including the vibration time-series."""
    result = await db.execute(
        select(WashingCycle).where(
            WashingCycle.id == cycle_id,
            WashingCycle.machine_id == machine_id,
        )
    )
    cycle = result.scalar_one_or_none()
    if not cycle:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Cycle not found",
        )

    # Get the device for this machine
    device_result = await db.execute(
        select(Device).where(Device.machine_id == machine_id)
    )
    device = device_result.scalar_one_or_none()

    vibration_series: list[TelemetryResponse] = []
    if device and cycle.started_at:
        end_time = cycle.ended_at or datetime.now(timezone.utc)
        telem_result = await db.execute(
            select(Telemetry)
            .where(
                Telemetry.device_id == device.id,
                Telemetry.timestamp_utc >= cycle.started_at,
                Telemetry.timestamp_utc <= end_time,
            )
            .order_by(Telemetry.timestamp_utc)
        )
        vibration_series = [
            TelemetryResponse.model_validate(t) for t in telem_result.scalars().all()
        ]

    return CycleDetailResponse(
        id=cycle.id,
        machine_id=cycle.machine_id,
        started_at=cycle.started_at,
        ended_at=cycle.ended_at,
        duration_seconds=cycle.duration_seconds,
        status=cycle.status,
        avg_vibration=cycle.avg_vibration,
        max_vibration=cycle.max_vibration,
        min_vibration=cycle.min_vibration,
        vibration_series=vibration_series,
    )
