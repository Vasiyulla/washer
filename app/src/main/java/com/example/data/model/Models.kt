package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class MachineStatus {
    @Json(name = "IDLE") IDLE,
    @Json(name = "RUNNING") RUNNING,
    @Json(name = "COMPLETED") COMPLETED,
    @Json(name = "OFFLINE") OFFLINE;

    val displayName: String
        get() = when (this) {
            IDLE -> "IDLE"
            RUNNING -> "RUNNING"
            COMPLETED -> "COMPLETED"
            OFFLINE -> "OFFLINE"
        }
}

enum class VibrationLevel {
    QUIET,
    NORMAL,
    ELEVATED,
    HIGH;

    companion object {
        fun fromMagnitude(v: Float): VibrationLevel = when {
            v < 0.15f -> QUIET
            v < 1.20f -> NORMAL
            v < 2.20f -> ELEVATED
            else -> HIGH
        }
    }
}

@JsonClass(generateAdapter = true)
data class Machine(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "status") val status: MachineStatus = MachineStatus.IDLE,
    @Json(name = "online") val online: Boolean = true,
    @Json(name = "last_seen") val lastSeen: String? = null,
    @Json(name = "model") val model: String = "EcoSpin Smart Pro",
    @Json(name = "location") val location: String = "Laundry Room"
)

@JsonClass(generateAdapter = true)
data class Device(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "esp_status") val espStatus: String = "ACTIVE",
    @Json(name = "firmware_version") val firmwareVersion: String = "v2.4.1-esp32",
    @Json(name = "installation_date") val installationDate: String = "2026-03-12",
    @Json(name = "wifi_ssid") val wifiSsid: String = "Home_IoT_5G",
    @Json(name = "rssi") val rssi: Int = -58,
    @Json(name = "ip_address") val ipAddress: String = "192.168.1.142",
    @Json(name = "accelerometer_type") val accelerometerType: String = "ADXL345 / MPU6050",
    @Json(name = "last_heartbeat") val lastHeartbeat: String = "Just now"
)

@JsonClass(generateAdapter = true)
data class Telemetry(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "status") val status: MachineStatus = MachineStatus.IDLE,
    @Json(name = "vibration") val vibration: Float,
    @Json(name = "x") val x: Float,
    @Json(name = "y") val y: Float,
    @Json(name = "z") val z: Float,
    @Json(name = "timestamp") val timestamp: String
)

@JsonClass(generateAdapter = true)
data class WashingCycle(
    @Json(name = "id") val id: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "start_time") val startTime: String,
    @Json(name = "end_time") val endTime: String? = null,
    @Json(name = "duration_seconds") val durationSeconds: Long = 0L,
    @Json(name = "status") val status: MachineStatus = MachineStatus.RUNNING,
    @Json(name = "avg_vibration") val avgVibration: Float = 1.15f,
    @Json(name = "max_vibration") val maxVibration: Float = 2.45f,
    @Json(name = "min_vibration") val minVibration: Float = 0.08f,
    @Json(name = "cycle_type") val cycleType: String = "Cotton Quick Wash"
)

@JsonClass(generateAdapter = true)
data class DeviceHealth(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "power_source") val powerSource: String = "Mains 5V 2A",
    @Json(name = "temperature_c") val temperatureC: Float = 34.2f,
    @Json(name = "free_heap_kb") val freeHeapKb: Int = 184,
    @Json(name = "uptime_seconds") val uptimeSeconds: Long = 864200L,
    @Json(name = "wifi_signal_pct") val wifiSignalPct: Int = 92,
    @Json(name = "sensor_ok") val sensorOk: Boolean = true,
    @Json(name = "cloud_connected") val cloudConnected: Boolean = true
)

enum class NotificationType {
    CYCLE_STARTED,
    CYCLE_COMPLETED,
    MACHINE_OFFLINE,
    SENSOR_DISCONNECTED,
    HIGH_VIBRATION,
    SYSTEM_INFO
}

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val type: NotificationType,
    val isRead: Boolean = false,
    val machineId: String = "WM-001",
    val cycleId: String? = null
)

@JsonClass(generateAdapter = true)
data class User(
    @Json(name = "id") val id: String,
    @Json(name = "email") val email: String,
    @Json(name = "name") val name: String,
    @Json(name = "token") val token: String? = null,
    @Json(name = "created_at") val createdAt: String = "2026-01-10"
)

enum class ConnectionState {
    ONLINE,
    RECONNECTING,
    OFFLINE
}

enum class WebSocketState {
    CONNECTED,
    CONNECTING,
    RECONNECTING,
    DISCONNECTED
}
