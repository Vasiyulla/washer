"""
SmartWash — WebSocket router.

Endpoint: /ws/machines/{machine_id}?token=<JWT>
Authenticates via JWT in query param, then streams telemetry and state changes.
"""

import logging

from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query

from app.core.security import decode_token
from app.websocket.manager import manager

logger = logging.getLogger(__name__)

router = APIRouter()


@router.websocket("/ws/machines/{machine_id}")
async def machine_websocket(
    websocket: WebSocket,
    machine_id: str,
    token: str = Query(...),
):
    """
    Authenticated WebSocket endpoint for real-time machine updates.

    Pass JWT as query parameter: /ws/machines/{id}?token=<access_token>
    """
    # Authenticate
    try:
        payload = decode_token(token)
        if payload.get("type") != "access":
            await websocket.close(code=4001, reason="Expected access token")
            return
    except Exception:
        await websocket.close(code=4001, reason="Invalid token")
        return

    user_id = payload["sub"]
    logger.info("WebSocket auth OK for user %s on machine %s", user_id, machine_id)

    await manager.connect(machine_id, websocket)
    try:
        # Keep the connection alive — wait for client messages (pings) or disconnect
        while True:
            # We don't expect meaningful client→server messages, but we need to
            # consume them to detect disconnects.
            data = await websocket.receive_text()
            # Optionally handle ping/pong or client commands here
            if data == "ping":
                await websocket.send_text("pong")
    except WebSocketDisconnect:
        manager.disconnect(machine_id, websocket)
    except Exception:
        manager.disconnect(machine_id, websocket)
