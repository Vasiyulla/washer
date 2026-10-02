package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.RosePeak

@Composable
fun ConnectionBar(
    connectionState: ConnectionState,
    isDataStale: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shouldShow = connectionState != ConnectionState.ONLINE || isDataStale

    AnimatedVisibility(
        visible = shouldShow,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        val (bgColor, icon, message, actionText) = when {
            connectionState == ConnectionState.OFFLINE -> ConnectionBarConfig(
                RosePeak.copy(alpha = 0.92f),
                Icons.Default.WifiOff,
                "Internet connection unavailable",
                "Retry"
            )
            connectionState == ConnectionState.RECONNECTING -> ConnectionBarConfig(
                AmberWarning.copy(alpha = 0.95f),
                Icons.Default.CloudOff,
                "Connecting to Cloud Backend...",
                null
            )
            isDataStale -> ConnectionBarConfig(
                AmberWarning.copy(alpha = 0.92f),
                Icons.Default.Info,
                "Data may be outdated • Refreshing",
                "Refresh"
            )
            else -> ConnectionBarConfig(
                EmeraldOnline,
                Icons.Default.Info,
                "Connected to Cloud Backend",
                null
            )
        }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (actionText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = actionText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private data class ConnectionBarConfig<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
