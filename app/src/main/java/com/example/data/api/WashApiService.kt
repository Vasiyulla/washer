package com.example.data.api

import com.example.data.model.Device
import com.example.data.model.DeviceHealth
import com.example.data.model.Machine
import com.example.data.model.Telemetry
import com.example.data.model.User
import com.example.data.model.WashingCycle
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = "bearer"
)

@JsonClass(generateAdapter = true)
data class MachineCreate(
    @Json(name = "name") val name: String,
    @Json(name = "machine_code") val machineCode: String,
    @Json(name = "device_code") val deviceCode: String
)

@JsonClass(generateAdapter = true)
data class MachineResponse(
    @Json(name = "id") val id: String,
    @Json(name = "owner_id") val ownerId: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "machine_code") val machineCode: String? = null,
    @Json(name = "installed_at") val installedAt: String? = null,
    @Json(name = "current_status") val currentStatus: String = "IDLE",
    @Json(name = "last_known_status") val lastKnownStatus: String? = null,
    @Json(name = "start_threshold") val startThreshold: Double? = 2.0,
    @Json(name = "stop_threshold") val stopThreshold: Double? = 0.5,
    @Json(name = "start_duration_seconds") val startDurationSeconds: Int? = 5,
    @Json(name = "stop_duration_seconds") val stopDurationSeconds: Int? = 15,
    @Json(name = "offline_timeout_seconds") val offlineTimeoutSeconds: Int? = 90
)

@JsonClass(generateAdapter = true)
data class MachineStatusResponse(
    @Json(name = "machine_id") val machineId: String,
    @Json(name = "machine_code") val machineCode: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "current_status") val currentStatus: String = "IDLE",
    @Json(name = "last_known_status") val lastKnownStatus: String? = null,
    @Json(name = "device_online") val deviceOnline: Boolean = false,
    @Json(name = "last_heartbeat") val lastHeartbeat: String? = null
)

@JsonClass(generateAdapter = true)
data class CycleResponse(
    @Json(name = "id") val id: String,
    @Json(name = "machine_id") val machineId: String,
    @Json(name = "started_at") val startedAt: String,
    @Json(name = "ended_at") val endedAt: String? = null,
    @Json(name = "duration_seconds") val durationSeconds: Long? = 0L,
    @Json(name = "status") val status: String = "COMPLETED",
    @Json(name = "avg_vibration") val avgVibration: Float? = null,
    @Json(name = "max_vibration") val maxVibration: Float? = null,
    @Json(name = "min_vibration") val minVibration: Float? = null
)

@JsonClass(generateAdapter = true)
data class TelemetryResponse(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "timestamp_utc") val timestampUtc: String? = null,
    @Json(name = "x") val x: Float = 0f,
    @Json(name = "y") val y: Float = 0f,
    @Json(name = "z") val z: Float = 0f,
    @Json(name = "vibration_magnitude") val vibrationMagnitude: Float = 0f,
    @Json(name = "status") val status: String = "IDLE"
)

@JsonClass(generateAdapter = true)
data class HealthResponse(
    @Json(name = "device_code") val deviceCode: String? = null,
    @Json(name = "firmware_version") val firmwareVersion: String? = null,
    @Json(name = "is_online") val isOnline: Boolean = false,
    @Json(name = "last_heartbeat") val lastHeartbeat: String? = null,
    @Json(name = "wifi_rssi") val wifiRssi: Float? = null,
    @Json(name = "uptime_seconds") val uptimeSeconds: Long? = null
)

@JsonClass(generateAdapter = true)
data class NotificationResponse(
    @Json(name = "id") val id: String,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "machine_id") val machineId: String? = null,
    @Json(name = "type") val type: String = "SYSTEM_INFO",
    @Json(name = "message") val message: String = "",
    @Json(name = "is_read") val isRead: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null
)

interface WashApiService {

    @GET("health")
    suspend fun checkSystemHealth(): Response<Map<String, Any>>

    @GET("api/v1/machines")
    suspend fun getMachines(): Response<List<MachineResponse>>

    @POST("api/v1/machines")
    suspend fun createMachine(
        @Body request: MachineCreate
    ): Response<MachineResponse>

    @GET("api/v1/machines/{machine_id}")
    suspend fun getMachine(
        @Path("machine_id") machineId: String
    ): Response<MachineResponse>

    @GET("api/v1/machines/{machine_id}/status")
    suspend fun getMachineStatus(
        @Path("machine_id") machineId: String
    ): Response<MachineStatusResponse>

    @GET("api/v1/machines/{machine_id}/cycles")
    suspend fun getCycles(
        @Path("machine_id") machineId: String,
        @Query("limit") limit: Int = 50
    ): Response<List<CycleResponse>>

    @GET("api/v1/machines/{machine_id}/telemetry")
    suspend fun getTelemetry(
        @Path("machine_id") machineId: String,
        @Query("limit") limit: Int = 30
    ): Response<List<TelemetryResponse>>

    @GET("api/v1/machines/{machine_id}/health")
    suspend fun getDeviceHealth(
        @Path("machine_id") machineId: String
    ): Response<HealthResponse>

    @GET("api/v1/notifications")
    suspend fun getNotifications(): Response<List<NotificationResponse>>

    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<TokenResponse>

    @POST("api/v1/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<TokenResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>
}
