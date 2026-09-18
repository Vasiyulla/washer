# SmartWash Backend

Remote washing-machine monitoring API. Receives accelerometer telemetry from
ESP32 devices, detects wash cycles via a server-side state machine, and pushes
real-time updates to mobile clients over WebSocket.

## Architecture

```
ESP32 + MPU6050  →  HTTPS POST  →  FastAPI Backend  →  PostgreSQL
                                        ↕
                                   WebSocket ↔ Mobile App
```

## Quick Start (Local Dev — SQLite)

```bash
cd backend

# Create virtual environment
python -m venv venv
venv\Scripts\activate        # Windows
# source venv/bin/activate   # macOS/Linux

# Install dependencies
pip install -r requirements.txt

# Copy env (already set up for SQLite)
# Edit .env if needed

# Start the server
uvicorn app.main:app --reload --port 8000
```

The server starts at http://localhost:8000.  
Swagger docs: http://localhost:8000/docs

## Quick Start (Docker Compose — Postgres)

```bash
# From the project root (one level above backend/)
docker compose up --build

# In another terminal, run the demo
docker compose exec backend python -m scripts.seed_demo
```

## Run the Demo Script

The seed script simulates a full wash cycle (IDLE → RUNNING → COMPLETED) by
posting fake telemetry to the running backend:

```bash
cd backend
python -m scripts.seed_demo
```

It will register a demo user, create a machine, and post ~50 telemetry readings
that trigger state transitions.

---

## API Reference

All endpoints return JSON. Timestamps are UTC ISO 8601.

### Auth

#### Register
```bash
curl -X POST http://localhost:8000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret123","name":"Alice"}'
```

#### Login
```bash
curl -X POST http://localhost:8000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret123"}'
```
Response: `{"access_token":"...","refresh_token":"...","token_type":"bearer"}`

#### Refresh Token
```bash
curl -X POST http://localhost:8000/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token":"<refresh_token>"}'
```

#### Logout
```bash
curl -X POST http://localhost:8000/api/v1/auth/logout \
  -H "Authorization: Bearer <access_token>"
```

---

### Machines (user-authenticated)

All machine endpoints require `Authorization: Bearer <access_token>`.

#### List Machines
```bash
curl http://localhost:8000/api/v1/machines \
  -H "Authorization: Bearer <token>"
```

#### Create Machine
```bash
curl -X POST http://localhost:8000/api/v1/machines \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Kitchen Washer","machine_code":"WM-001","device_code":"ESP32-001"}'
```

#### Get Machine
```bash
curl http://localhost:8000/api/v1/machines/<machine_id> \
  -H "Authorization: Bearer <token>"
```

#### Get Machine Status
```bash
curl http://localhost:8000/api/v1/machines/<machine_id>/status \
  -H "Authorization: Bearer <token>"
```

#### List Washing Cycles
```bash
# Today's cycles
curl "http://localhost:8000/api/v1/machines/<machine_id>/cycles?range=today" \
  -H "Authorization: Bearer <token>"

# Last 7 days (default)
curl "http://localhost:8000/api/v1/machines/<machine_id>/cycles?range=7d" \
  -H "Authorization: Bearer <token>"

# Custom date range
curl "http://localhost:8000/api/v1/machines/<machine_id>/cycles?range=custom&start=2025-01-01T00:00:00Z&end=2025-12-31T23:59:59Z" \
  -H "Authorization: Bearer <token>"
```

#### Get Cycle Detail (with vibration series)
```bash
curl http://localhost:8000/api/v1/machines/<machine_id>/cycles/<cycle_id> \
  -H "Authorization: Bearer <token>"
```

#### Get Recent Telemetry
```bash
curl "http://localhost:8000/api/v1/machines/<machine_id>/telemetry?since=2025-01-01T00:00:00Z" \
  -H "Authorization: Bearer <token>"
```

#### Get Device Health
```bash
curl http://localhost:8000/api/v1/machines/<machine_id>/health \
  -H "Authorization: Bearer <token>"
```

#### Update Machine Config (thresholds)
```bash
curl -X PATCH http://localhost:8000/api/v1/machines/<machine_id>/config \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"start_threshold":3.0,"stop_threshold":0.8}'
```

---

### Device Ingestion (device-authenticated)

These endpoints use `X-Device-API-Key` header instead of user JWT.

#### Post Telemetry
```bash
curl -X POST http://localhost:8000/api/v1/telemetry \
  -H "X-Device-API-Key: dev-device-key-change-in-production" \
  -H "Content-Type: application/json" \
  -d '{
    "device_id": "ESP32-001",
    "timestamp": "2025-06-15T10:30:00Z",
    "x": 0.12, "y": 0.31, "z": 9.82,
    "vibration": 1.42,
    "status": "RUNNING"
  }'
```

#### Heartbeat
```bash
curl -X POST http://localhost:8000/api/v1/devices/ESP32-001/heartbeat \
  -H "X-Device-API-Key: dev-device-key-change-in-production" \
  -H "Content-Type: application/json" \
  -d '{"wifi_rssi":-45,"uptime_seconds":3600}'
```

---

### Notifications (user-authenticated)

#### List Notifications
```bash
curl http://localhost:8000/api/v1/notifications \
  -H "Authorization: Bearer <token>"
```

#### Mark as Read
```bash
curl -X PATCH http://localhost:8000/api/v1/notifications/<id>/read \
  -H "Authorization: Bearer <token>"
```

---

### WebSocket

Connect using a WebSocket client (e.g. websocat):

```bash
websocat "ws://localhost:8000/ws/machines/<machine_id>?token=<access_token>"
```

Messages are JSON objects pushed on telemetry ingestion and state changes:
```json
{
  "type": "telemetry",
  "machine_id": "...",
  "vibration": 3.42,
  "machine_status": "RUNNING",
  "timestamp": "2025-06-15T10:30:00Z"
}
```

State change messages include `"type": "state_change"` and a `"transition"` field.

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `sqlite+aiosqlite:///./smartwash.db` | Database connection string |
| `SECRET_KEY` | (required) | JWT signing key |
| `ALGORITHM` | `HS256` | JWT algorithm |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | `30` | Access token TTL |
| `REFRESH_TOKEN_EXPIRE_DAYS` | `7` | Refresh token TTL |
| `DEVICE_API_KEY` | (required) | Shared secret for ESP32 auth |
| `DEFAULT_START_THRESHOLD` | `2.0` | Default vibration start threshold |
| `DEFAULT_STOP_THRESHOLD` | `0.5` | Default vibration stop threshold |
| `DEFAULT_START_DURATION_SECONDS` | `5` | Sustained seconds above start threshold |
| `DEFAULT_STOP_DURATION_SECONDS` | `15` | Sustained seconds below stop threshold |
| `DEFAULT_OFFLINE_TIMEOUT_SECONDS` | `90` | Seconds without heartbeat → offline |
| `TELEMETRY_RATE_LIMIT` | `60/minute` | Rate limit for telemetry endpoint |

## State Machine

The backend implements server-side cycle detection:

```
IDLE ──(vibration > start_threshold for 5s)──→ RUNNING
RUNNING ──(vibration < stop_threshold for 15s)──→ COMPLETED
Any ──(no heartbeat for 90s)──→ OFFLINE (preserves last_known_status)
```

Thresholds are configurable per-machine via `PATCH /machines/{id}/config`.
