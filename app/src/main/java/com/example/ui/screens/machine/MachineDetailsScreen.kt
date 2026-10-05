package com.example.ui.screens.machine

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.StatMetricCard
import com.example.ui.components.VibrationChart
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanLight
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.RosePeak
import com.example.ui.theme.SlateOffline
import com.example.ui.viewmodel.WashViewModel
import java.util.Locale
import kotlin.math.sqrt

@Composable
fun MachineDetailsScreen(
    viewModel: WashViewModel,
    modifier: Modifier = Modifier
) {
    val selectedMachine by viewModel.selectedMachine.collectAsState()
    val device by viewModel.device.collectAsState()
    val deviceHealth by viewModel.deviceHealth.collectAsState()
    val latestTelemetry by viewModel.latestTelemetry.collectAsState()
    val telemetryHistory by viewModel.telemetryHistory.collectAsState()
    val avgVibration by viewModel.avgVibration.collectAsState()
    val peakVibration by viewModel.peakVibration.collectAsState()

    val isMachineOnline = selectedMachine?.online == true && selectedMachine?.status != MachineStatus.OFFLINE
    val currentVib = if (isMachineOnline) (latestTelemetry?.vibration ?: 0f) else 0f
    val xAcc = if (isMachineOnline) (latestTelemetry?.x ?: 0f) else 0f
    val yAcc = if (isMachineOnline) (latestTelemetry?.y ?: 0f) else 0f
    val zAcc = if (isMachineOnline) (latestTelemetry?.z ?: 0f) else 0f
    val vectorMagnitude = if (isMachineOnline) sqrt(xAcc * xAcc + yAcc * yAcc + zAcc * zAcc) else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Screen Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "HARDWARE & TELEMETRY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Machine & Sensor Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isMachineOnline) CyanPrimary.copy(alpha = 0.12f) else SlateOffline.copy(alpha = 0.12f))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = if (isMachineOnline) CyanPrimary else SlateOffline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 6: Vibration Monitoring
            item {
                Text(
                    text = "VIBRATION MONITORING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            Text(
                                text = "Recent Vibration Waveform",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (!isMachineOnline) "Offline" else if (currentVib > 0.50f) "Peak Spin" else "Normal",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isMachineOnline) SlateOffline else if (currentVib > 0.50f) RosePeak else EmeraldOnline
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Real-time canvas line chart
                        VibrationChart(
                            telemetryHistory = if (isMachineOnline) telemetryHistory else emptyList(),
                            heightDp = 140
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(14.dp))

                        // 4 Stats in row: Current, Avg, Peak, Trend
                        val delta = currentVib - avgVibration
                        val trendText = when {
                            !isMachineOnline -> "No Signal —"
                            delta > 0.03f -> "Rising ↑"
                            delta < -0.03f -> "Falling ↓"
                            else -> "Stable →"
                        }
                        val trendColor = when {
                            !isMachineOnline -> SlateOffline
                            delta > 0.03f -> RosePeak
                            delta < -0.03f -> CyanPrimary
                            else -> EmeraldOnline
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            TelemetryStatColumn(
                                label = "CURRENT",
                                value = if (isMachineOnline) "${String.format(Locale.US, "%.2f", currentVib)}g" else "0.00g",
                                color = if (!isMachineOnline) SlateOffline else if (currentVib > 0.50f) RosePeak else CyanPrimary
                            )
                            TelemetryStatColumn(
                                label = "AVERAGE",
                                value = if (isMachineOnline) "${String.format(Locale.US, "%.2f", avgVibration)}g" else "0.00g",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TelemetryStatColumn(
                                label = "PEAK",
                                value = if (isMachineOnline) "${String.format(Locale.US, "%.2f", peakVibration)}g" else "0.00g",
                                color = if (!isMachineOnline) SlateOffline else if (peakVibration > 0.50f) RosePeak else AmberWarning
                            )
                            TelemetryStatColumn(
                                label = "TREND",
                                value = trendText,
                                color = trendColor
                            )
                        }
                    }
                }
            }

            // Section 7A: Sensor Information
            item {
                Text(
                    text = "ACCELEROMETER 3-AXIS TELEMETRY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                    Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = if (isMachineOnline) CyanPrimary else SlateOffline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sensor Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = if (isMachineOnline) "Operational (Active)" else "Offline (Powered Down)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isMachineOnline) EmeraldOnline else SlateOffline
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // X-axis bar
                        AxisProgressBar(axis = "X-Axis", value = xAcc, maxRange = 2.0f, color = if (isMachineOnline) CyanPrimary else SlateOffline)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Y-axis bar
                        AxisProgressBar(axis = "Y-Axis", value = yAcc, maxRange = 2.0f, color = if (isMachineOnline) IndigoAccent else SlateOffline)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Z-axis bar
                        AxisProgressBar(axis = "Z-Axis", value = zAcc, maxRange = 2.0f, color = if (isMachineOnline) EmeraldOnline else SlateOffline)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Vector Magnitude √[x²+y²+z²]:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (isMachineOnline) "${String.format(Locale.US, "%.3f", vectorMagnitude)} g" else "0.000 g (Offline)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMachineOnline) CyanPrimary else SlateOffline
                            )
                        }
                    }
                }
            }

            // Section 7B: Machine & ESP32 Information
            item {
                Text(
                    text = "MACHINE & ESP32 HARDWARE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InfoRow(label = "Machine Name", value = selectedMachine?.name ?: "Kitchen Washer")
                        InfoRow(label = "Device ID", value = selectedMachine?.deviceId ?: "ESP32-001")
                        InfoRow(
                            label = "ESP32 Status",
                            value = if (isMachineOnline) (device?.espStatus ?: "ONLINE / ACTIVE") else "OFFLINE / DISCONNECTED",
                            isAccent = true,
                            accentColor = if (isMachineOnline) EmeraldOnline else SlateOffline
                        )
                        InfoRow(label = "Firmware Version", value = device?.firmwareVersion ?: "v1.0.0")
                        InfoRow(label = "Installation Date", value = device?.installationDate ?: "2026-03-12")
                        InfoRow(label = "Sensor IC", value = device?.accelerometerType ?: "MPU-6050 (6-DOF I2C)")
                    }
                }
            }

            // Section 7C: Connectivity
            item {
                Text(
                    text = "CONNECTIVITY & CLOUD LINK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InfoRow(
                            label = "Wi-Fi Connection",
                            value = if (isMachineOnline) "${device?.wifiSsid ?: "TP-Link"} (${device?.rssi ?: -53} dBm)" else "Disconnected (No Power)"
                        )
                        InfoRow(
                            label = "ESP32 Local IP",
                            value = if (isMachineOnline) (device?.ipAddress ?: "192.168.1.36") else "Unavailable (Offline)"
                        )
                        InfoRow(
                            label = "Last Heartbeat",
                            value = if (isMachineOnline) (device?.lastHeartbeat ?: "Just now") else (selectedMachine?.lastSeen ?: "Offline")
                        )
                        InfoRow(
                            label = "Last Telemetry Received",
                            value = if (isMachineOnline) (latestTelemetry?.timestamp?.takeLast(9)?.take(8) ?: "Live stream") else "No incoming stream"
                        )
                        InfoRow(
                            label = "Cloud Connection Status",
                            value = if (isMachineOnline) "Connected (Real-time Stream)" else "Standby (Awaiting Device)",
                            isAccent = true,
                            accentColor = if (isMachineOnline) EmeraldOnline else SlateOffline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryStatColumn(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
private fun AxisProgressBar(
    axis: String,
    value: Float,
    maxRange: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = axis, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "${String.format(Locale.US, "%+.2f", value)} g", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        val progress = (Math.abs(value) / maxRange).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isAccent: Boolean = false,
    accentColor: Color = EmeraldOnline
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = if (isAccent) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isAccent) accentColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
