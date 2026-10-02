package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Telemetry
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanLight
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.RosePeak
import java.util.Locale

@Composable
fun VibrationChart(
    telemetryHistory: List<Telemetry>,
    modifier: Modifier = Modifier,
    heightDp: Int = 160,
    warningThreshold: Float = 2.0f
) {
    val primaryColor = CyanLight
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Extract vibration values
    val values = remember(telemetryHistory) {
        if (telemetryHistory.isEmpty()) {
            listOf(0.05f, 0.05f)
        } else {
            telemetryHistory.map { it.vibration }
        }
    }

    val maxVal = remember(values) {
        (values.maxOrNull() ?: 2.5f).coerceAtLeast(2.5f)
    }
    val minVal = 0.0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val paddingBottom = 20.dp.toPx()
            val paddingTop = 12.dp.toPx()
            val chartHeight = height - paddingBottom - paddingTop

            if (values.size < 2) return@Canvas

            // 1. Grid horizontal lines (0.0g, 1.0g, 2.0g, max)
            val gridSteps = listOf(0.0f, 1.0f, 2.0f)
            gridSteps.forEach { stepVal ->
                val normY = (stepVal - minVal) / (maxVal - minVal)
                val y = height - paddingBottom - (normY * chartHeight)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 2. Threshold Warning Line (2.0g)
            val threshNormY = (warningThreshold - minVal) / (maxVal - minVal)
            val threshY = height - paddingBottom - (threshNormY * chartHeight)
            val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            drawLine(
                color = AmberWarning.copy(alpha = 0.7f),
                start = Offset(0f, threshY),
                end = Offset(width, threshY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dashedEffect
            )

            // 3. Compute Points for Line
            val stepX = width / (values.size - 1)
            val points = values.mapIndexed { index, v ->
                val normY = (v - minVal) / (maxVal - minVal)
                val clampedNormY = normY.coerceIn(0f, 1f)
                val x = index * stepX
                val y = height - paddingBottom - (clampedNormY * chartHeight)
                Offset(x, y)
            }

            // 4. Smooth Bezier Path
            val linePath = Path()
            val fillPath = Path()

            linePath.moveTo(points.first().x, points.first().y)
            fillPath.moveTo(points.first().x, height - paddingBottom)
            fillPath.lineTo(points.first().x, points.first().y)

            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val controlX = (p0.x + p1.x) / 2f
                linePath.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                fillPath.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
            }

            fillPath.lineTo(points.last().x, height - paddingBottom)
            fillPath.close()

            // Draw Area Fill Gradient
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f),
                        primaryColor.copy(alpha = 0.03f)
                    ),
                    startY = paddingTop,
                    endY = height - paddingBottom
                )
            )

            // Draw Line Curve
            drawPath(
                path = linePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(CyanPrimary, CyanLight, if (values.last() > 2.0f) RosePeak else CyanLight)
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw Current Telemetry Glowing Dot at last point
            val lastPoint = points.last()
            drawCircle(
                color = if (values.last() > 2.0f) RosePeak.copy(alpha = 0.35f) else CyanLight.copy(alpha = 0.35f),
                radius = 8.dp.toPx(),
                center = lastPoint
            )
            drawCircle(
                color = if (values.last() > 2.0f) RosePeak else Color.White,
                radius = 4.dp.toPx(),
                center = lastPoint
            )
        }

        // Top labels
        Text(
            text = "2.0g Limit",
            style = TextStyle(fontSize = 10.sp, color = AmberWarning, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp, top = 2.dp)
        )

        // Bottom labels
        Text(
            text = "T-45s",
            style = TextStyle(fontSize = 10.sp, color = textColor),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 2.dp)
        )
        Text(
            text = "Live (Now)",
            style = TextStyle(fontSize = 10.sp, color = primaryColor, fontWeight = FontWeight.Medium),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 2.dp)
        )
    }
}
