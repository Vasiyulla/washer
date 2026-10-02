package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MachineStatus
import com.example.data.model.VibrationLevel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.RosePeak
import com.example.ui.theme.SlateOffline

@Composable
fun MachineStatusBadge(
    status: MachineStatus,
    modifier: Modifier = Modifier
) {
    val (dotColor, bgColor, textColor, label) = when (status) {
        MachineStatus.RUNNING -> StatusVisualConfig(
            CyanPrimary,
            CyanPrimary.copy(alpha = 0.15f),
            CyanPrimary,
            "RUNNING"
        )
        MachineStatus.COMPLETED -> StatusVisualConfig(
            EmeraldOnline,
            EmeraldOnline.copy(alpha = 0.15f),
            EmeraldOnline,
            "COMPLETED"
        )
        MachineStatus.IDLE -> StatusVisualConfig(
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "IDLE"
        )
        MachineStatus.OFFLINE -> StatusVisualConfig(
            SlateOffline,
            SlateOffline.copy(alpha = 0.18f),
            SlateOffline,
            "OFFLINE"
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "StatusPulsing")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = if (status == MachineStatus.RUNNING) 0.35f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun VibrationLevelBadge(
    level: VibrationLevel,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (level) {
        VibrationLevel.QUIET -> Pair(SlateOffline, "Quiet")
        VibrationLevel.NORMAL -> Pair(EmeraldOnline, "Normal")
        VibrationLevel.ELEVATED -> Pair(AmberWarning, "Elevated")
        VibrationLevel.HIGH -> Pair(RosePeak, "High")
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private data class StatusVisualConfig(val dotColor: Color, val bgColor: Color, val textColor: Color, val label: String)
