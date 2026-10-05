"""
Telemetry router — device-authenticated ingestion endpoints.

Rate-limited to prevent telemetry flooding.
"""

from fastapi import APIRouter, Depends, Request
from slowapi import Limiter
from slowapi.util import get_remote_address

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.core.security import verify_device_api_key
from app.schemas.telemetry import TelemetryCreate
from app.schemas.device import HeartbeatRequest
from app.services import telemetry_service
from app.websocket.manager import manager

import logging

logger = logging.getLogger(__name__)

limiter = Limiter(key_func=get_remote_address)

router = APIRouter(prefix="/api/v1", tags=["device"])


@router.post("/telemetry")
@limiter.limit(settings.TELEMETRY_RATE_LIMIT)
async def ingest_telemetry(
    request: Request,
    payload: TelemetryCreate,
    _key: str = Depends(verify_device_api_key),
    db: AsyncSession = Depends(get_db),
):
    """Accept telemetry from ESP32 devices, run state machine, broadcast via WebSocket."""
    try:
        broadcast_data = await telemetry_service.ingest_telemetry(db, payload)

        # Broadcast to WebSocket subscribers
        machine_id = broadcast_data.get("machine_id")
        if machine_id:
            await manager.broadcast(machine_id, broadcast_data)

        return {"status": "ok"}
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("Failed to ingest telemetry for device %s: %s", payload.device_id, e)
        raise HTTPException(
            status_code=500,
            detail=f"Telemetry ingestion internal error: {str(e)}"
        )


@router.post("/devices/{device_code}/heartbeat")
async def heartbeat(
    device_code: str,
    payload: HeartbeatRequest = HeartbeatRequest(),
    _key: str = Depends(verify_device_api_key),
    db: AsyncSession = Depends(get_db),
):
    data = await telemetry_service.process_heartbeat(
        db,
        device_code,
        wifi_rssi=payload.wifi_rssi,
        uptime_seconds=payload.uptime_seconds,
        firmware_version=payload.firmware_version,
    )

    # Immediately notify WebSocket subscribers that device is online
    machine_id = data.get("machine_id")
    if machine_id:
        await manager.broadcast(
            machine_id,
            {
                "type": "device_heartbeat",
                "machine_id": machine_id,
                "device_id": device_code,
                "status": "IDLE",
                "machine_status": "IDLE",
                "online": True,
                "vibration": 0.0,
                "x": 0.0,
                "y": 0.0,
                "z": 1.0,
                "timestamp": data.get("timestamp"),
            },
        )

    return data
