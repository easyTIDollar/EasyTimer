package com.jussicodes.easytimer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jussicodes.easytimer.model.TimerState
import com.jussicodes.easytimer.ui.pressScale
import com.jussicodes.easytimer.viewmodel.MainViewModel

@Composable
fun ActiveTimerScreen(viewModel: MainViewModel) {
    val timerState by viewModel.timerState.collectAsState()
    val state = timerState
    if (state !is TimerState.Running) return

    val remainingSeconds = state.remainingSeconds
    val totalSeconds = state.totalSeconds
    val isPaused = state.isPaused
    val progressTarget = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f
    val progress by animateFloatAsState(progressTarget.coerceIn(0f, 1f), label = "timerProgress")

    val progressColor by animateColorAsState(
        targetValue = when {
            isPaused -> MaterialTheme.colorScheme.outline
            remainingSeconds <= 60 -> MaterialTheme.colorScheme.error
            remainingSeconds <= 300 -> Color(0xFFFFC86A)
            else -> MaterialTheme.colorScheme.primary
        },
        label = "progressColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = state.appName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPaused) "计时已暂停" else "将在倒计时结束后强制关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.6f))

        TimerRing(
            remainingSeconds = remainingSeconds,
            progress = progress,
            progressColor = progressColor,
            isPaused = isPaused
        )

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "总时长 ${formatMinutes(totalSeconds)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimerActionButton(
                text = "取消",
                filled = false,
                modifier = Modifier.weight(1f),
                onClick = viewModel::cancelTimer
            )
            TimerActionButton(
                text = if (isPaused) "继续" else "暂停",
                filled = true,
                modifier = Modifier.weight(1f),
                icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                onClick = { if (isPaused) viewModel.resumeTimer() else viewModel.pauseTimer() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TimerActionButton(
            text = "立即关闭",
            filled = false,
            danger = true,
            icon = Icons.Default.Stop,
            modifier = Modifier.fillMaxWidth(),
            onClick = viewModel::forceStopNow
        )
    }
}

@Composable
private fun TimerRing(
    remainingSeconds: Int,
    progress: Float,
    progressColor: Color,
    isPaused: Boolean
) {
    Box(
        modifier = Modifier.size(248.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(232.dp)) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatTime(remainingSeconds),
                fontSize = 46.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isPaused) "已暂停" else "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimerActionButton(
    text: String,
    filled: Boolean,
    modifier: Modifier,
    danger: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scaled = modifier
        .height(54.dp)
        .pressScale(interactionSource)

    if (filled) {
        Button(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(18.dp),
            modifier = scaled
        ) {
            icon?.let {
                Icon(it, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = scaled
        ) {
            icon?.let {
                Icon(it, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatMinutes(totalSeconds: Int): String {
    val m = totalSeconds / 60
    return if (m >= 60) "${m / 60} 小时 ${m % 60} 分钟" else "$m 分钟"
}
