# SmartWash IoT Ecosystem

End-to-end intelligent washing machine monitoring system featuring edge vibration analysis, cloud state-machine orchestration, and a real-time Android mobile application.

```
  ┌────────────────────────────────────────────────────────┐
  │                 ESP32 + MPU-6050 FIRMWARE              │
  │     • 50 Hz accelerometer sampling                     │
  │     • Deviation filter: |√(x²+y²+z²) - 9.81|           │
  │     • Offline ring buffer (16 payloads)                │
  └───────────────────────────┬────────────────────────────┘
                              │ HTTPS POST (TLS 1.2/1.3)
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │                   FASTAPI CLOUD BACKEND                │
  │     • Device authentication (X-Device-API-Key)         │
  │     • Cycle state machine (IDLE ↔ RUNNING ↔ COMPLETED) │
  │     • PostgreSQL / SQLite time-series storage          │
  │     • Real-time WebSocket broadcasting                 │
  └───────────────────────────┬────────────────────────────┘
                              │ WebSocket & REST
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │                 ANDROID JETPACK COMPOSE APP            │
  │     • MVVM + Unidirectional Data Flow (StateFlow)      │
  │     • Real-time Canvas vibration waveform chart        │
  │     • Animated washing drum & cycle alerts             │
  └────────────────────────────────────────────────────────┘
```

---

## Repository Structure

```
.
├── app/                     # Android Native App (Kotlin, Jetpack Compose, MVVM)
│   ├── src/main/java/com/example/
│   │   ├── data/            # Repositories, Retrofit API, WebSocket manager, Models
│   │   └── ui/              # Compose screens, ViewModels, Reusable components, Theme
│   └── README.md            # Mobile app architecture & setup guide
│
├── backend/                 # Cloud REST & WebSocket API (FastAPI, SQLAlchemy, Alembic)
│   ├── app/
│   │   ├── core/            # Config, Security (JWT & Device API Key), Database session
│   │   ├── models/          # SQLAlchemy ORM (Machine, Device, Telemetry, Cycle, User)
│   │   ├── routers/         # Endpoints (auth, machines, telemetry, devices, notifications)
│   │   ├── schemas/         # Pydantic validation schemas
│   │   ├── services/        # State machine, Telemetry ingestion, Machine service
│   │   └── websocket/       # Real-time WebSocket manager and router
│   ├── scripts/             # Seed scripts & database utilities
│   ├── Dockerfile           # Backend container definition
│   └── README.md            # Backend API documentation & run guide
│
├── firmware/                # Embedded Firmware (ESP32, C++17, PlatformIO)
│   ├── src/
│   │   ├── config.h         # Wi-Fi, backend URLs, device identity & vibration thresholds
│   │   ├── main.cpp         # FreeRTOS setup, timing & main telemetry loop
│   │   ├── network.h/.cpp   # Wi-Fi station mode, NTP time sync, HTTPS client & ring buffer
│   │   └── vibration.h/.cpp # MPU-6050 / ADXL345 I2C driver & rolling-window analysis
│   ├── platformio.ini       # PlatformIO build configuration
│   └── README.md            # Hardware wiring, circuit specs & flashing guide
│
├── docker-compose.yml       # Local orchestration (FastAPI + PostgreSQL)
└── render.yaml              # Production deployment config for Render.com
```

---

## Component Quick Start

### 1. Firmware (`/firmware`)
1. Connect MPU-6050 to ESP32 (`VCC` $\rightarrow$ `VIN`/`3V3`, `GND` $\rightarrow$ `GND`, `SCL` $\rightarrow$ `D22`, `SDA` $\rightarrow$ `D21`, `AD0` $\rightarrow$ `GND`).
2. Verify credentials in [`firmware/src/config.h`](file:///c:/Users/vasiy/Documents/washer/firmware/src/config.h).
3. Build and upload:
   ```bash
   pio run -d firmware -t upload
   pio device monitor -d firmware
   ```

### 2. Backend (`/backend`)
```bash
cd backend
python -m venv venv
venv\Scripts\activate        # Windows (or: source venv/bin/activate on Linux/Mac)
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```
Swagger API Documentation available at: `http://localhost:8000/docs`

### 3. Mobile App (`/app`)
1. Open the project root in **Android Studio**.
2. Sync Gradle files.
3. Configure target backend URL in [`AppConfig.kt`](file:///c:/Users/vasiy/Documents/washer/app/src/main/java/com/example/data/config/AppConfig.kt).
4. Run on an Android emulator or connected device (API 26+).
