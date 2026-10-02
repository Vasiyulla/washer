package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.MachineStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanLight
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.RosePeak
import com.example.ui.theme.SlateOffline
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WashingDrumGraphic(
    status: MachineStatus,
    vibrationMagnitude: Float,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp
) {
    val isRunning = status == MachineStatus.RUNNING

    // Dynamic rotation speed: faster when vibration is high
    val rotationDuration = when {
        !isRunning -> 12000
        vibrationMagnitude > 2.0f -> 1200 // High speed spin
        vibrationMagnitude > 1.2f -> 2500 // Medium agitation
        else -> 4000 // Gentle wash
    }

    val infiniteTransition = rememberInfiniteTransition(label = "DrumAnimation")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isRunning) 800 else 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    val primaryColor = when (status) {
        MachineStatus.RUNNING -> if (vibrationMagnitude > 2.2f) RosePeak else CyanPrimary
        MachineStatus.COMPLETED -> EmeraldOnline
        MachineStatus.IDLE -> MaterialTheme.colorScheme.outline
        MachineStatus.OFFLINE -> SlateOffline
    }

    val glowColor = when (status) {
        MachineStatus.RUNNING -> if (vibrationMagnitude > 2.2f) RosePeak.copy(alpha = 0.35f) else CyanLight.copy(alpha = 0.35f)
        MachineStatus.COMPLETED -> EmeraldOnline.copy(alpha = 0.3f)
        MachineStatus.IDLE -> Color.Transparent
        MachineStatus.OFFLINE -> Color.Transparent
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outerRadius = (this.size.minDimension / 2f) - 12.dp.toPx()

            // 1. Outer Vibration Wave / Pulse Ring (when running)
            if (isRunning) {
                drawCircle(
                    color = glowColor,
                    radius = outerRadius * pulseScale + 8.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 2. Machine Bezel outer rim
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF334155),
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                    ),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = center
            )

            // Bezel Chrome Ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.75f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // 3. Drum Door Glass Cavity
            val drumRadius = outerRadius * 0.78f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (isRunning) Color(0xFF0284C7).copy(alpha = 0.45f) else Color(0xFF1E293B),
                        Color(0xFF0B132B)
                    ),
                    center = center,
                    radius = drumRadius
                ),
                radius = drumRadius,
                center = center
            )

            // 4. Rotating Drum Fins & Perforations
            val effectiveAngle = if (isRunning) rotationAngle else 0f
            rotate(degrees = effectiveAngle, pivot = center) {
                // 3 Drum Paddles / Baffles
                for (i in 0 until 3) {
                    val angleRad = Math.toRadians((i * 120.0)).toFloat()
                    val finStart = Offset(
                        center.x + cos(angleRad) * (drumRadius * 0.25f),
                        center.y + sin(angleRad) * (drumRadius * 0.25f)
                    )
                    val finEnd = Offset(
                        center.x + cos(angleRad) * (drumRadius * 0.88f),
                        center.y + sin(angleRad) * (drumRadius * 0.88f)
                    )
                    drawLine(
                        color = Color.White.copy(alpha = if (isRunning) 0.85f else 0.45f),
                        start = finStart,
                        end = finEnd,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Drum Perforation dots
                val dotRadius = drumRadius * 0.55f
                for (j in 0 until 8) {
                    val dotAngle = Math.toRadians((j * 45.0)).toFloat()
                    val dotCenter = Offset(
                        center.x + cos(dotAngle) * dotRadius,
                        center.y + sin(dotAngle) * dotRadius
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.22f),
                        radius = 2.dp.toPx(),
                        center = dotCenter
                    )
                }
            }

            // 5. Water vortex wave (when running)
            if (isRunning) {
                val waterPath = Path().apply {
                    val waterY = center.y + (drumRadius * 0.15f)
                    moveTo(center.x - drumRadius * 0.65f, waterY)
                    var x = center.x - drumRadius * 0.65f
                    while (x <= center.x + drumRadius * 0.65f) {
                        val relX = x - center.x
                        val yOffset = sin((relX * 0.08f) + wavePhase) * 6.dp.toPx()
                        lineTo(x, waterY + yOffset)
                        x += 4f
                    }
                }
                drawPath(
                    path = waterPath,
                    color = CyanLight.copy(alpha = 0.55f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 6. Center Drum Hub Cap
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, primaryColor),
                    center = center,
                    radius = drumRadius * 0.18f
                ),
                radius = drumRadius * 0.18f,
                center = center
            )

            // Drum glass reflection arc
            drawArc(
                color = Color.White.copy(alpha = 0.28f),
                startAngle = 200f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(center.x - drumRadius * 0.82f, center.y - drumRadius * 0.82f),
                size = androidx.compose.ui.geometry.Size(drumRadius * 1.64f, drumRadius * 1.64f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
