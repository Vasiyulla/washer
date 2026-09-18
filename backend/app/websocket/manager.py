"""
SmartWash — WebSocket connection manager.

Manages per-machine subscriber sets and broadcasts telemetry / state changes
to all connected clients for a given machine.
"""

import json
import logging
from collections import defaultdict

from fastapi import WebSocket

logger = logging.getLogger(__name__)


class ConnectionManager:
    """Manages WebSocket connections grouped by machine_id."""

    def __init__(self):
        # machine_id → set of active WebSocket connections
        self._connections: dict[str, set[WebSocket]] = defaultdict(set)

    async def connect(self, machine_id: str, websocket: WebSocket) -> None:
        """Accept and register a WebSocket connection for a machine."""
        await websocket.accept()
        self._connections[machine_id].add(websocket)
        logger.info(
            "WebSocket connected for machine %s (total: %d)",
            machine_id, len(self._connections[machine_id]),
        )

    def disconnect(self, machine_id: str, websocket: WebSocket) -> None:
        """Remove a WebSocket connection."""
        self._connections[machine_id].discard(websocket)
        if not self._connections[machine_id]:
            del self._connections[machine_id]
        logger.info("WebSocket disconnected for machine %s", machine_id)

    async def broadcast(self, machine_id: str, data: dict) -> None:
        """Send a JSON message to all subscribers of a machine."""
        dead: list[WebSocket] = []
        for ws in self._connections.get(machine_id, set()):
            try:
                await ws.send_text(json.dumps(data, default=str))
            except Exception:
                dead.append(ws)
        # Clean up broken connections
        for ws in dead:
            self._connections[machine_id].discard(ws)

    def subscriber_count(self, machine_id: str) -> int:
        return len(self._connections.get(machine_id, set()))


# Singleton instance used across the app
manager = ConnectionManager()
