package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ApiClient
import com.example.data.config.AppConfig
import com.example.data.model.ConnectionState
import com.example.data.model.Device
import com.example.data.model.DeviceHealth
import com.example.data.model.Machine
import com.example.data.model.MachineStatus
import com.example.data.model.NotificationItem
import com.example.data.model.Telemetry
import com.example.data.model.VibrationLevel
import com.example.data.model.WashingCycle
import com.example.data.repository.AuthRepository
import com.example.data.repository.MachineRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class HistoryFilter {
    ALL,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS
}

class WashViewModel(application: Application) : AndroidViewModel(application) {

    val config = AppConfig(application)
    val apiClient = ApiClient(config)
    val repository = MachineRepository(application, config, apiClient)
    val authRepository = AuthRepository(config, apiClient)

    // Data streams from repository
    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val isDataStale: StateFlow<Boolean> = repository.isDataStale
    val machines: StateFlow<List<Machine>> = repository.machines
    val selectedMachine: StateFlow<Machine?> = repository.selectedMachine
    val latestTelemetry: StateFlow<Telemetry?> = repository.latestTelemetry
    val telemetryHistory: StateFlow<List<Telemetry>> = repository.recentTelemetryHistory
    val activeCycle: StateFlow<WashingCycle?> = repository.activeCycle
    val device: StateFlow<Device?> = repository.device
    val deviceHealth: StateFlow<DeviceHealth?> = repository.deviceHealth
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val errorMessage: StateFlow<String?> = repository.errorMessage

    // Notifications
    val notifications: StateFlow<List<NotificationItem>> = repository.notifications

    // Selected cycle for detailed modal
    private val _selectedCycleForDetail = MutableStateFlow<WashingCycle?>(null)
    val selectedCycleForDetail: StateFlow<WashingCycle?> = _selectedCycleForDetail.asStateFlow()

    // History filter
    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    // Filtered cycles
    val filteredCycles: StateFlow<List<WashingCycle>> = combine(
        repository.historyCycles,
        _historyFilter
    ) { cycles, filter ->
        when (filter) {
            HistoryFilter.ALL -> cycles
            HistoryFilter.TODAY -> cycles.filter { it.startTime.contains("Today", ignoreCase = true) }
            HistoryFilter.LAST_7_DAYS -> cycles.filter {
                it.startTime.contains("Today", ignoreCase = true) ||
                it.startTime.contains("Yesterday", ignoreCase = true) ||
                it.startTime.contains("days ago", ignoreCase = true) && (it.startTime.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0) <= 7
            }
            HistoryFilter.LAST_30_DAYS -> cycles
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Running duration string (auto-incrementing every second while RUNNING)
    private val _liveDurationSec = MutableStateFlow(0L)
    val liveDurationFormatted: StateFlow<String> = _liveDurationSec.combine(activeCycle) { liveSec, cycle ->
        val effectiveSec = if (cycle != null && cycle.status == MachineStatus.RUNNING) {
            maxOf(cycle.durationSeconds, liveSec)
        } else {
            cycle?.durationSeconds ?: 0L
        }
        formatDuration(effectiveSec)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0 min 00 sec")

    // Current vibration level enum
    val currentVibrationLevel: StateFlow<VibrationLevel> = latestTelemetry.combine(selectedMachine) { telem, machine ->
        if (machine?.status == MachineStatus.IDLE || machine?.status == MachineStatus.OFFLINE) {
            VibrationLevel.QUIET
        } else {
            VibrationLevel.fromMagnitude(telem?.vibration ?: 0f)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VibrationLevel.QUIET)

    // Current vibration stats
    val avgVibration: StateFlow<Float> = telemetryHistory.combine(activeCycle) { history, cycle ->
        if (cycle != null && cycle.avgVibration > 0f) {
            cycle.avgVibration
        } else if (history.isNotEmpty()) {
            history.map { it.vibration }.average().toFloat()
        } else {
            0.08f
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.15f)

    val peakVibration: StateFlow<Float> = telemetryHistory.combine(activeCycle) { history, cycle ->
        val fromCycle = cycle?.maxVibration ?: 0f
        val fromHistory = history.maxOfOrNull { it.vibration } ?: 0f
        maxOf(fromCycle, fromHistory, 0.05f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2.42f)

    init {
        // Start duration increment ticker
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val status = selectedMachine.value?.status
                if (status == MachineStatus.RUNNING) {
                    _liveDurationSec.value += 1L
                }
            }
        }
    }

    fun selectMachine(machineId: String) {
        repository.selectMachine(machineId)
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun openCycleDetail(cycle: WashingCycle) {
        _selectedCycleForDetail.value = cycle
    }

    fun closeCycleDetail() {
        _selectedCycleForDetail.value = null
    }

    fun updateApiConfig(apiUrl: String) {
        config.apiBaseUrl = apiUrl
        repository.retryConnection()
    }

    fun markNotificationRead(id: String) {
        repository.markNotificationRead(id)
    }

    fun clearNotifications() {
        repository.clearAllNotifications()
    }

    fun refreshData() {
        repository.retryConnection()
    }

    fun dismissError() {
        repository.clearError()
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> String.format(Locale.US, "%d hr %02d min", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%d min %02d sec", minutes, seconds)
            else -> String.format(Locale.US, "%d sec", seconds)
        }
    }
}
