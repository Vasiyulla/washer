package com.example.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionState
import com.example.data.model.MachineStatus
import com.example.ui.components.ConnectionBar
import com.example.ui.components.MachineStatusBadge
import com.example.ui.components.StatMetricCard
import com.example.ui.components.VibrationChart
import com.example.ui.components.VibrationLevelBadge
import com.example.ui.components.WashingDrumGraphic
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.RosePeak
import com.example.ui.theme.SlateOffline
import com.example.ui.viewmodel.WashViewModel
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: WashViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isDataStale by viewModel.isDataStale.collectAsState()
    val selectedMachine by viewModel.selectedMachine.collectAsState()
    val machines by viewModel.machines.collectAsState()
    val latestTelemetry by viewModel.latestTelemetry.collectAsState()
    val telemetryHistory by viewModel.telemetryHistory.collectAsState()
    val activeCycle by viewModel.activeCycle.collectAsState()
    val liveDuration by viewModel.liveDurationFormatted.collectAsState()
    val vibrationLevel by viewModel.currentVibrationLevel.collectAsState()
    val avgVibration by viewModel.avgVibration.collectAsState()
    val peakVibration by viewModel.peakVibration.collectAsState()
    val deviceHealth by viewModel.deviceHealth.collectAsState()

    var showMachineMenu by remember { mutableStateOf(false) }

    val currentStatus = selectedMachine?.status ?: MachineStatus.IDLE
    val vibrationMag = latestTelemetry?.vibration ?: 0.05f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Real-time Connection Bar (shows if offline, reconnecting, demo mode, or stale data)
        ConnectionBar(
            connectionState = connectionState,
            isDataStale = isDataStale,
            onRetry = { viewModel.refreshData() }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Header Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Machine Selector Dropdown
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showMachineMenu = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "MONITORED APPLIANCE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = selectedMachine?.name ?: "Washing Machine 01",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Select Machine",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showMachineMenu,
                            onDismissRequest = { showMachineMenu = false }
                        ) {
                            machines.forEach { machine ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(machine.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${machine.location} • ${machine.deviceId}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectMachine(machine.id)
                                        showMachineMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Online/Offline Pill & Refresh
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selectedMachine?.online == true) EmeraldOnline.copy(alpha = 0.15f)
                                    else SlateOffline.copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedMachine?.online == true) EmeraldOnline else SlateOffline)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (selectedMachine?.online == true) "Online" else "Offline",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedMachine?.online == true) EmeraldOnline else SlateOffline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.refreshData() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Central Machine Status Hero Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Status Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MachineStatusBadge(status = currentStatus)
                            VibrationLevelBadge(level = vibrationLevel)
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Animated Washing Drum Graphic
                        WashingDrumGraphic(
                            status = currentStatus,
                            vibrationMagnitude = vibrationMag,
                            size = 180.dp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Running Duration Display
                        if (currentStatus == MachineStatus.RUNNING) {
                            Text(
                                text = "RUNNING FOR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = liveDuration,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CyanPrimary
                            )
                        } else if (currentStatus == MachineStatus.COMPLETED) {
                            Text(
                                text = "CYCLE FINISHED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Laundry Ready",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = EmeraldOnline
                            )
                        } else {
                            Text(
                                text = "READY FOR NEXT LOAD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Machine Idle",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Start / End Time & Last Completed Cycle Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "STARTED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeCycle?.startTime ?: "10:42 AM",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "COMPLETED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeCycle?.endTime ?: if (currentStatus == MachineStatus.RUNNING) "Estim. 11:15 AM" else "09:12 AM",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "DEVICE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (selectedMachine?.online == true) "Online" else "Offline",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedMachine?.online == true) EmeraldOnline else SlateOffline
                                )
                            }
                        }
                    }
                }
            }

            // Real-Time Vibration Telemetry Sparkline Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Vibration,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Live Vibration Telemetry",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "${String.format(Locale.US, "%.2f", vibrationMag)} g",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = if (vibrationMag > 2.0f) RosePeak else CyanPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Real-time canvas line chart
                        VibrationChart(
                            telemetryHistory = telemetryHistory,
                            heightDp = 130
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Avg: ${String.format(Locale.US, "%.2f", avgVibration)}g • Peak: ${String.format(Locale.US, "%.2f", peakVibration)}g",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Updated: ${latestTelemetry?.timestamp?.takeLast(9)?.take(8) ?: "Just now"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Telemetry Metric Tiles Grid
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricCard(
                        title = "Current Vibration",
                        value = String.format(Locale.US, "%.2f", vibrationMag),
                        unit = "g",
                        icon = Icons.Default.Speed,
                        accentColor = if (vibrationMag > 2.0f) RosePeak else CyanPrimary,
                        modifier = Modifier.weight(1f),
                        subtext = vibrationLevel.name.lowercase().replaceFirstChar { it.uppercase() }
                    )

                    StatMetricCard(
                        title = "Device Health",
                        value = if (deviceHealth?.sensorOk == true) "Normal" else "Warning",
                        unit = null,
                        icon = Icons.Default.Bolt,
                        accentColor = EmeraldOnline,
                        modifier = Modifier.weight(1f),
                        subtext = "${deviceHealth?.temperatureC?.toInt() ?: 36}°C • 92% WiFi"
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricCard(
                        title = "Last Start Time",
                        value = activeCycle?.startTime ?: "10:42 AM",
                        unit = null,
                        icon = Icons.Default.AccessTime,
                        accentColor = IndigoAccent,
                        modifier = Modifier.weight(1f),
                        subtext = "Cycle: Cotton Eco"
                    )

                    StatMetricCard(
                        title = "Last Completed",
                        value = "57 min",
                        unit = null,
                        icon = Icons.Default.CheckCircle,
                        accentColor = EmeraldOnline,
                        modifier = Modifier.weight(1f),
                        subtext = "Today at 09:12 AM"
                    )
                }
            }
        }
    }
}
