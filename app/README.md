# SmartWash Android App

Modern, native Android client built with Jetpack Compose (Material3) and MVVM architecture for real-time washing machine monitoring and vibration telemetry visualization.

## Architecture

* **UI Layer**: Jetpack Compose, Material3 Design System, Canvas-based real-time vibration graph.
* **State Management**: AndroidX `ViewModel`, Kotlin `StateFlow` and unidirectional data flow (UDF).
* **Network & Data**: Retrofit2 (REST), OkHttp3 (WebSocket Client with auto-reconnect), SharedPreferences configuration.

## Folder Structure
```
app/src/main/java/com/example/
├── MainActivity.kt                  # Single-activity container with edge-to-edge support
│
├── data/                            # DATA LAYER
│   ├── api/                         # Retrofit API definitions & OkHttp client
│   ├── config/                      # AppConfig (Base URLs, auth token, preferences)
│   ├── model/                       # Data Transfer Objects (DTOs) & WebSocket event models
│   ├── repository/                  # AuthRepository & MachineRepository
│   └── websocket/                   # WebSocketManager (Real-time telemetry stream)
│
└── ui/                              # PRESENTATION LAYER
    ├── components/                  # Reusable Compose widgets
    │   ├── ConnectionBar.kt         # Real-time WebSocket connection state banner
    │   ├── CycleItemCard.kt         # Completed wash cycle card
    │   ├── StatMetricCard.kt        # Metric indicator widget
    │   ├── StatusBadge.kt           # Animated status badge (IDLE, RUNNING, COMPLETED)
    │   ├── VibrationChart.kt        # Real-time vibration deviation canvas graph
    │   └── WashingDrumGraphic.kt    # Animated washing machine drum graphic
    ├── navigation/                  # NavGraph and destinations
    ├── screens/                     # Full-screen Composable screens
    │   ├── dashboard/               # Machine overview screen
    │   ├── history/                 # Past wash cycles history
    │   ├── machine/                 # Live telemetry, drum animation & timer
    │   ├── notifications/           # Alerts & push notifications view
    │   └── profile/                 # User settings & cloud configuration
    ├── theme/                       # Color, Theme, and Typography
    └── viewmodel/                   # WashViewModel (Reactive state holder)
```

## Backend Endpoints Configuration
App settings are configured in [`AppConfig.kt`](file:///c:/Users/vasiy/Documents/washer/app/src/main/java/com/example/data/config/AppConfig.kt):
* **REST API**: `https://washer-u2y9.onrender.com/api/v1`
* **WebSocket**: `wss://washer-u2y9.onrender.com/ws/machines/{machine_id}`
