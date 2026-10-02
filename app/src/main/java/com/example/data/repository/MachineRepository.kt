package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.data.api.ApiClient
import com.example.data.api.CycleResponse
import com.example.data.api.HealthResponse
import com.example.data.api.LoginRequest
import com.example.data.api.MachineCreate
import com.example.data.api.MachineResponse
import com.example.data.api.NotificationResponse
import com.example.data.api.RegisterRequest
import com.example.data.api.TelemetryResponse
import com.example.data.api.WashApiService
import com.example.data.config.AppConfig
import com.example.data.model.ConnectionState
import com.example.data.model.Device
import com.example.data.model.DeviceHealth
import com.example.data.model.Machine
import com.example.data.model.MachineStatus
import com.example.data.model.NotificationItem
import com.example.data.model.NotificationType
import com.example.data.model.Telemetry
import com.example.data.model.WashingCycle
import com.example.data.websocket.TelemetryWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MachineRepository(
    private val context: Context,
    val config: AppConfig,
    val apiClient: ApiClient
) {
    private val tag = "MachineRepository"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    val webSocketClient = TelemetryWebSocketClient(config, apiClient.okHttpClient)

    // Network connectivity
    private val _isInternetAvailable = MutableStateFlow(true)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    // Connection state (ONLINE, RECONNECTING, OFFLINE)
    private val _connectionState = MutableStateFlow(ConnectionState.ONLINE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Is current data considered stale
    private val _isDataStale = MutableStateFlow(false)
    val isDataStale: StateFlow<Boolean> = _isDataStale.asStateFlow()

    // Machines list
    private val _machines = MutableStateFlow<List<Machine>>(emptyList())
    val machines: StateFlow<List<Machine>> = _machines.asStateFlow()

    // Selected machine
    private val _selectedMachine = MutableStateFlow<Machine?>(null)
    val selectedMachine: StateFlow<Machine?> = _selectedMachine.asStateFlow()

    // Latest telemetry
    private val _latestTelemetry = MutableStateFlow<Telemetry?>(null)
    val latestTelemetry: StateFlow<Telemetry?> = _latestTelemetry.asStateFlow()

    // Telemetry history buffer for live chart (max 30 points)
    private val _recentTelemetryHistory = MutableStateFlow<List<Telemetry>>(emptyList())
    val recentTelemetryHistory: StateFlow<List<Telemetry>> = _recentTelemetryHistory.asStateFlow()

    // Active cycle
    private val _activeCycle = MutableStateFlow<WashingCycle?>(null)
    val activeCycle: StateFlow<WashingCycle?> = _activeCycle.asStateFlow()

    // History cycles
    private val _historyCycles = MutableStateFlow<List<WashingCycle>>(emptyList())
    val historyCycles: StateFlow<List<WashingCycle>> = _historyCycles.asStateFlow()

    // Device details
    private val _device = MutableStateFlow<Device?>(null)
    val device: StateFlow<Device?> = _device.asStateFlow()

    // Device health
    private val _deviceHealth = MutableStateFlow<DeviceHealth?>(null)
    val deviceHealth: StateFlow<DeviceHealth?> = _deviceHealth.asStateFlow()

    // Notifications
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    // Loading / error states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var pollingJob: Job? = null
    private var lastSuccessfulPollTimestamp = 0L

    init {
        monitorNetwork()
        startLiveBackendPolling()
        listenToWebSocketStream()
    }

    private fun listenToWebSocketStream() {
        scope.launch {
            webSocketClient.telemetryStream.collect { rawTelemetry ->
                val resolvedStatus = rawTelemetry.currentStatus
                val telemetry = if (rawTelemetry.status != resolvedStatus) {
                    rawTelemetry.copy(status = resolvedStatus)
                } else {
                    rawTelemetry
                }

                _latestTelemetry.value = telemetry
                val current = _recentTelemetryHistory.value.toMutableList()
                if (current.size >= 30) current.removeAt(0)
                current.add(telemetry)
                _recentTelemetryHistory.value = current

                // Sync machine status in real-time
                _selectedMachine.value?.let { currentMachine ->
                    if (currentMachine.status != resolvedStatus) {
                        _selectedMachine.value = currentMachine.copy(status = resolvedStatus)
                    }
                }
                lastSuccessfulPollTimestamp = System.currentTimeMillis()
                _isDataStale.value = false
            }
        }
    }

    fun selectMachine(machine: Machine) {
        _selectedMachine.value = machine
        webSocketClient.connect(machine.id)
        scope.launch {
            pollMachineStatusAndTelemetry(machine.id)
            pollCyclesAndHealth(machine.id)
        }
    }

    fun selectMachine(machineId: String) {
        val found = _machines.value.find { it.id == machineId }
        if (found != null) {
            selectMachine(found)
        } else {
            webSocketClient.connect(machineId)
            scope.launch {
                pollMachineStatusAndTelemetry(machineId)
                pollCyclesAndHealth(machineId)
            }
        }
    }

    private fun monitorNetwork() {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager != null) {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isInternetAvailable.value = true
                    _connectionState.value = ConnectionState.ONLINE
                    _isDataStale.value = false
                }

                override fun onLost(network: Network) {
                    _isInternetAvailable.value = false
                    _connectionState.value = ConnectionState.OFFLINE
                    _isDataStale.value = true
                }
            })
        }
    }

    private fun startLiveBackendPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            // Initial fetch
            fetchMachinesAndBootstrap()

            var slowCounter = 0
            while (isActive) {
                try {
                    val activeMach = _selectedMachine.value
                    if (activeMach != null) {
                        pollMachineStatusAndTelemetry(activeMach.id)
                    } else {
                        fetchMachinesAndBootstrap()
                    }

                    // Every ~7.5 seconds (every 3 iterations), poll cycles, health, and notifications
                    if (slowCounter % 3 == 0 && activeMach != null) {
                        pollCyclesAndHealth(activeMach.id)
                    }
                    slowCounter++

                    lastSuccessfulPollTimestamp = System.currentTimeMillis()
                    _connectionState.value = ConnectionState.ONLINE
                    _isDataStale.value = false
                    _errorMessage.value = null
                } catch (e: Exception) {
                    Log.e(tag, "Polling error from ${config.apiBaseUrl}", e)
                    if (System.currentTimeMillis() - lastSuccessfulPollTimestamp > 15000L && lastSuccessfulPollTimestamp > 0L) {
                        _isDataStale.value = true
                    }
                    if (!_isInternetAvailable.value) {
                        _connectionState.value = ConnectionState.OFFLINE
                    } else {
                        _connectionState.value = ConnectionState.RECONNECTING
                    }
                }

                delay(2500L)
            }
        }
    }

    private suspend fun fetchMachinesAndBootstrap() {
        var service = apiClient.createService()

        // 1. Proactive Authentication: If token is missing, authenticate immediately
        if (config.authToken.isNullOrBlank()) {
            authenticateDemoUser(service)
            service = apiClient.createService()
        }

        var resp = service.getMachines()

        // 2. Reactive Auto-recovery: If unauthorized/invalid token (401, 403, or 422)
        if (resp.code() == 401 || resp.code() == 403 || resp.code() == 422) {
            Log.i(tag, "Session expired or rejected (${resp.code()}). Re-authenticating...")
            authenticateDemoUser(service)
            service = apiClient.createService()
            resp = service.getMachines()
        }

        if (resp.isSuccessful && resp.body() != null) {
            var list = resp.body()!!.map { it.toMachine() }
            if (list.isEmpty()) {
                // If user account has no machines yet, automatically create default machine on backend
                try {
                    val createResp = service.createMachine(
                        MachineCreate(
                            name = "Kitchen Washer",
                            machineCode = "WM-001",
                            deviceCode = "ESP32-001"
                        )
                    )
                    if (createResp.isSuccessful && createResp.body() != null) {
                        list = listOf(createResp.body()!!.toMachine())
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Could not auto-register machine: ${e.message}")
                }
            }

            if (list.isNotEmpty()) {
                _machines.value = list
                val current = _selectedMachine.value
                val selected = if (current != null) {
                    list.find { it.id == current.id } ?: list[0]
                } else {
                    list[0]
                }
                _selectedMachine.value = selected
                webSocketClient.connect(selected.id)
            }
        }
    }

    private suspend fun authenticateDemoUser(service: WashApiService) {
        try {
            var loginResp = service.login(LoginRequest("demo@smartwash.io", "demo1234"))
            if (!loginResp.isSuccessful) {
                // Register if the account doesn't exist on this cloud container yet
                loginResp = service.register(RegisterRequest("Demo User", "demo@smartwash.io", "demo1234"))
            }
            if (loginResp.isSuccessful && loginResp.body() != null) {
                config.authToken = loginResp.body()!!.accessToken
                config.userEmail = "demo@smartwash.io"
                config.userName = "Demo User"
                Log.i(tag, "Authentication successful! Token saved.")
            } else {
                Log.w(tag, "Authentication failed with code: ${loginResp.code()}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Authentication exception: ${e.message}", e)
        }
    }

    private suspend fun pollMachineStatusAndTelemetry(machineId: String) {
        val service = apiClient.createService()

        // 1. Fetch Status
        try {
            val statusResp = service.getMachineStatus(machineId)
            if (statusResp.isSuccessful && statusResp.body() != null) {
                val st = statusResp.body()!!
                val mappedStatus = parseStatus(st.currentStatus)
                _selectedMachine.value = _selectedMachine.value?.copy(
                    status = mappedStatus,
                    online = st.deviceOnline,
                    lastSeen = st.lastHeartbeat ?: "Live"
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Status fetch error: ${e.message}")
        }

        // 2. Fetch Telemetry
        try {
            val telemResp = service.getTelemetry(machineId, limit = 30)
            if (telemResp.isSuccessful && telemResp.body() != null) {
                val telemList = telemResp.body()!!.map { it.toTelemetry() }
                if (telemList.isNotEmpty()) {
                    _recentTelemetryHistory.value = telemList
                    _latestTelemetry.value = telemList.last()
                } else if (_latestTelemetry.value == null) {
                    // Fallback neutral telemetry if backend database has no rows yet
                    val neutral = Telemetry(
                        deviceId = machineId,
                        status = _selectedMachine.value?.status ?: MachineStatus.IDLE,
                        vibration = 0.04f,
                        x = 0.01f,
                        y = 0.02f,
                        z = 0.98f,
                        timestamp = "Live"
                    )
                    _latestTelemetry.value = neutral
                    _recentTelemetryHistory.value = listOf(neutral)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Telemetry fetch error: ${e.message}")
        }
    }

    private suspend fun pollCyclesAndHealth(machineId: String) {
        val service = apiClient.createService()

        // 1. Cycles
        try {
            val cyclesResp = service.getCycles(machineId)
            if (cyclesResp.isSuccessful && cyclesResp.body() != null) {
                val cycles = cyclesResp.body()!!.map { it.toWashingCycle() }
                _historyCycles.value = cycles
                _activeCycle.value = cycles.find { it.status == MachineStatus.RUNNING }
            }
        } catch (e: Exception) {
            Log.w(tag, "Cycles fetch error: ${e.message}")
        }

        // 2. Health & Device
        try {
            val healthResp = service.getDeviceHealth(machineId)
            if (healthResp.isSuccessful && healthResp.body() != null) {
                val h = healthResp.body()!!
                _deviceHealth.value = h.toDeviceHealth(machineId)
                _device.value = Device(
                    deviceId = h.deviceCode ?: "ESP32-WASH-01",
                    espStatus = if (h.isOnline) "ONLINE" else "STANDBY",
                    firmwareVersion = h.firmwareVersion ?: "v2.4.1-esp32",
                    installationDate = "2026-03-12",
                    wifiSsid = "Home_IoT_5G",
                    rssi = h.wifiRssi?.toInt() ?: -58,
                    ipAddress = "192.168.1.142",
                    accelerometerType = "ADXL345 / MPU6050",
                    lastHeartbeat = h.lastHeartbeat ?: "Just now"
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Health fetch error: ${e.message}")
        }

        // 3. Notifications
        try {
            val notifResp = service.getNotifications()
            if (notifResp.isSuccessful && notifResp.body() != null) {
                _notifications.value = notifResp.body()!!.map { it.toNotificationItem() }
            }
        } catch (e: Exception) {
            Log.w(tag, "Notifications fetch error: ${e.message}")
        }
    }

    fun fetchInitialData(machineId: String) {
        scope.launch {
            _isLoading.value = true
            try {
                pollMachineStatusAndTelemetry(machineId)
                pollCyclesAndHealth(machineId)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retryConnection() {
        startLiveBackendPolling()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun markNotificationRead(id: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    fun clearAllNotifications() {
        _notifications.value = emptyList()
    }

    private fun parseStatus(raw: String?): MachineStatus {
        return when (raw?.uppercase()) {
            "RUNNING" -> MachineStatus.RUNNING
            "COMPLETED" -> MachineStatus.COMPLETED
            "OFFLINE" -> MachineStatus.OFFLINE
            else -> MachineStatus.IDLE
        }
    }

    private fun MachineResponse.toMachine(): Machine {
        val st = parseStatus(currentStatus)
        return Machine(
            id = id,
            name = name,
            deviceId = machineCode ?: "WM-001",
            status = st,
            online = st != MachineStatus.OFFLINE,
            lastSeen = installedAt ?: "Active",
            model = "ESP32 EcoSpin Pro",
            location = "Laundry Room"
        )
    }

    private fun CycleResponse.toWashingCycle(): WashingCycle {
        val st = parseStatus(status)
        return WashingCycle(
            id = id,
            deviceId = machineId,
            startTime = startedAt,
            endTime = endedAt,
            durationSeconds = durationSeconds ?: 0L,
            status = st,
            avgVibration = avgVibration ?: 1.15f,
            maxVibration = maxVibration ?: 2.45f,
            minVibration = minVibration ?: 0.08f,
            cycleType = "Washing Cycle"
        )
    }

    private fun TelemetryResponse.toTelemetry(): Telemetry {
        val st = parseStatus(status)
        return Telemetry(
            deviceId = deviceId,
            status = st,
            vibration = vibrationMagnitude,
            x = x,
            y = y,
            z = z,
            timestamp = timestampUtc ?: "Just now"
        )
    }

    private fun HealthResponse.toDeviceHealth(machineId: String): DeviceHealth {
        return DeviceHealth(
            deviceId = deviceCode ?: machineId,
            powerSource = "ESP32 5V USB",
            temperatureC = 33.5f,
            freeHeapKb = 192,
            uptimeSeconds = uptimeSeconds ?: 1200L,
            wifiSignalPct = wifiRssi?.let { (100 + it).toInt().coerceIn(10, 100) } ?: 88,
            sensorOk = true,
            cloudConnected = isOnline
        )
    }

    private fun NotificationResponse.toNotificationItem(): NotificationItem {
        val nType = when (type.uppercase()) {
            "CYCLE_STARTED" -> NotificationType.CYCLE_STARTED
            "CYCLE_COMPLETED" -> NotificationType.CYCLE_COMPLETED
            "MACHINE_OFFLINE" -> NotificationType.MACHINE_OFFLINE
            "HIGH_VIBRATION" -> NotificationType.HIGH_VIBRATION
            "SENSOR_DISCONNECTED" -> NotificationType.SENSOR_DISCONNECTED
            else -> NotificationType.SYSTEM_INFO
        }
        return NotificationItem(
            id = id,
            title = when (nType) {
                NotificationType.CYCLE_STARTED -> "Cycle Started"
                NotificationType.CYCLE_COMPLETED -> "Cycle Completed"
                NotificationType.MACHINE_OFFLINE -> "Machine Offline"
                NotificationType.HIGH_VIBRATION -> "High Vibration Alert"
                NotificationType.SENSOR_DISCONNECTED -> "Sensor Warning"
                NotificationType.SYSTEM_INFO -> "System Update"
            },
            message = message,
            timestamp = createdAt ?: "Recently",
            type = nType,
            isRead = isRead,
            machineId = machineId ?: "WM-001"
        )
    }
}
