package com.example.data.websocket

import android.util.Log
import com.example.data.config.AppConfig
import com.example.data.model.MachineStatus
import com.example.data.model.Telemetry
import com.example.data.model.WebSocketState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TelemetryWebSocketClient(
    private val config: AppConfig,
    private val okHttpClient: OkHttpClient
) {
    private val tag = "TelemetryWebSocket"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val telemetryAdapter = moshi.adapter(Telemetry::class.java)

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var shouldReconnect = true
    private var currentMachineId: String = "WM-001"
    private var reconnectAttempts = 0

    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    private val _telemetryStream = MutableSharedFlow<Telemetry>(extraBufferCapacity = 64)
    val telemetryStream: SharedFlow<Telemetry> = _telemetryStream.asSharedFlow()

    private val _lastMessageReceivedAt = MutableStateFlow<Long>(0L)
    val lastMessageReceivedAt: StateFlow<Long> = _lastMessageReceivedAt.asStateFlow()

    fun connect(machineId: String) {
        currentMachineId = machineId
        shouldReconnect = true
        reconnectJob?.cancel()
        reconnectAttempts = 0
        startConnection()
    }

    private fun startConnection() {
        webSocket?.cancel()
        _connectionState.value = if (reconnectAttempts == 0) WebSocketState.CONNECTING else WebSocketState.RECONNECTING

        val baseUrl = config.webSocketUrl.removeSuffix("/")
        val token = config.authToken
        val authQuery = if (!token.isNullOrBlank()) "?token=$token" else ""
        val fullUrl = "$baseUrl/machines/$currentMachineId$authQuery"

        Log.d(tag, "Connecting to WebSocket: $fullUrl")

        val request = try {
            Request.Builder()
                .url(fullUrl)
                .addHeader("User-Agent", "WashPulse-Android/1.0")
                .build()
        } catch (e: Exception) {
            Log.e(tag, "Invalid WebSocket URL format: $fullUrl", e)
            _connectionState.value = WebSocketState.DISCONNECTED
            scheduleReconnect()
            return
        }

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(tag, "WebSocket connected successfully to $currentMachineId")
                _connectionState.value = WebSocketState.CONNECTED
                reconnectAttempts = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _lastMessageReceivedAt.value = System.currentTimeMillis()
                try {
                    val telemetry = telemetryAdapter.fromJson(text)
                    if (telemetry != null) {
                        scope.launch {
                            _telemetryStream.emit(telemetry)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to parse telemetry message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing ($code): $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed ($code): $reason")
                _connectionState.value = WebSocketState.DISCONNECTED
                if (shouldReconnect) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(tag, "WebSocket failure: ${t.message}")
                _connectionState.value = WebSocketState.DISCONNECTED
                if (shouldReconnect) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempts++
            val backoffMs = (reconnectAttempts * 2000L).coerceAtMost(15000L)
            Log.d(tag, "Scheduling WebSocket reconnect in ${backoffMs}ms (attempt $reconnectAttempts)")
            _connectionState.value = WebSocketState.RECONNECTING
            delay(backoffMs)
            startConnection()
        }
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "App paused or switched mode")
        webSocket = null
        _connectionState.value = WebSocketState.DISCONNECTED
    }

    fun sendDemoTelemetry(telemetry: Telemetry) {
        _lastMessageReceivedAt.value = System.currentTimeMillis()
        scope.launch {
            _telemetryStream.emit(telemetry)
        }
    }
}
