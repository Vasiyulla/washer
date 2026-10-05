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

    val currentVib = latestTelemetry?.vibration ?: 0.05f
    val xAcc = latestTelemetry?.x ?: 0.01f
    val yAcc = latestTelemetry?.y ?: 0.02f
    val zAcc = latestTelemetry?.z ?: 0.98f
    val vectorMagnitude = sqrt(xAcc * xAcc + yAcc * yAcc + zAcc * zAcc)

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
                    .background(CyanPrimary.copy(alpha = 0.12f))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = CyanPrimary,
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
                                text = if (currentVib > 2.0f) "Peak Spin" else "Normal",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentVib > 2.0f) RosePeak else EmeraldOnline
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Real-time canvas line chart
                        VibrationChart(
                            telemetryHistory = telemetryHistory,
                            heightDp = 140
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(14.dp))

                        // 4 Stats in row: Current, Avg, Peak, Trend
                        val delta = currentVib - avgVibration
                        val trendText = when {
                            delta > 0.03f -> "Rising ↑"
                            delta < -0.03f -> "Falling ↓"
                            else -> "Stable →"
                        }
                        val trendColor = when {
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
                                value = "${String.format(Locale.US, "%.2f", currentVib)}g",
                                color = if (currentVib > 0.50f) RosePeak else CyanPrimary
                            )
                            TelemetryStatColumn(
                                label = "AVERAGE",
                                value = "${String.format(Locale.US, "%.2f", avgVibration)}g",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TelemetryStatColumn(
                                label = "PEAK",
                                value = "${String.format(Locale.US, "%.2f", peakVibration)}g",
                                color = if (peakVibration > 0.50f) RosePeak else AmberWarning
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
                                Icon(Icons.Default.Sensors, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sensor Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text("Operational (Active)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldOnline)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // X-axis bar
                        AxisProgressBar(axis = "X-Axis", value = xAcc, maxRange = 2.0f, color = CyanPrimary)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Y-axis bar
                        AxisProgressBar(axis = "Y-Axis", value = yAcc, maxRange = 2.0f, color = IndigoAccent)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Z-axis bar
                        AxisProgressBar(axis = "Z-Axis", value = zAcc, maxRange = 2.0f, color = EmeraldOnline)
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
                            Text("${String.format(Locale.US, "%.3f", vectorMagnitude)} g", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyanPrimary)
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
                        InfoRow(label = "Machine Name", value = selectedMachine?.name ?: "Washing Machine 01")
                        InfoRow(label = "Device ID", value = selectedMachine?.deviceId ?: "WM-001")
                        InfoRow(label = "ESP32 Status", value = device?.espStatus ?: "ONLINE / ACTIVE", isAccent = true)
                        InfoRow(label = "Firmware Version", value = device?.firmwareVersion ?: "v2.4.1-esp32-freertos")
                        InfoRow(label = "Installation Date", value = device?.installationDate ?: "2026-02-18")
                        InfoRow(label = "Sensor IC", value = device?.accelerometerType ?: "ADXL345 (I2C)")
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
                        InfoRow(label = "Wi-Fi Connection", value = "${device?.wifiSsid ?: "Home_IoT_5G"} (${device?.rssi ?: -54} dBm)")
                        InfoRow(label = "ESP32 Local IP", value = device?.ipAddress ?: "192.168.1.188")
                        InfoRow(label = "Last Heartbeat", value = device?.lastHeartbeat ?: "Just now")
                        InfoRow(label = "Last Telemetry Received", value = latestTelemetry?.timestamp?.takeLast(9)?.take(8) ?: "Live stream")
                        InfoRow(label = "Cloud Connection Status", value = "Connected (FastAPI + PostgreSQL)", isAccent = true)
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
    isAccent: Boolean = false
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
            color = if (isAccent) EmeraldOnline else MaterialTheme.colorScheme.onSurface
        )
    }
}
