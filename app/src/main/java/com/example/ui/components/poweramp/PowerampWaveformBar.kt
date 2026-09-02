package com.example.ui.components.poweramp

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Poweramp_Cyan
import kotlin.math.abs
import kotlin.math.sin

/**
 * Iconic Poweramp Waveform Seekbar.
 * Renders high-fidelity sound wave peaks with played/unplayed gradients,
 * real-time audio energy modulation, and responsive seek scrub gestures.
 */
@Composable
fun PowerampWaveformBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    height: Dp = 58.dp,
    accentColor: Color = Poweramp_Cyan,
    seedKey: String = "default"
) {
    val progress = remember(currentPosition, duration) {
        if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }

    val effectiveProgress = if (isScrubbing) scrubProgress else progress

    // Subtle audio energy pulse animation while playing
    val infiniteTransition = rememberInfiniteTransition(label = "wavePulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    // Deterministic procedural waveform pattern based on the song's key
    val barCount = 72
    val waveHeights = remember(seedKey, barCount) {
        val hash = abs(seedKey.hashCode())
        val random = java.util.Random(hash.toLong())
        FloatArray(barCount) { index ->
            val baseSin = (sin(index.toDouble() * 0.28) * 0.35 + 0.55).toFloat()
            val noise = (random.nextFloat() * 0.45f)
            val envelope = (sin(index.toDouble() / barCount * Math.PI)).toFloat().coerceIn(0.2f, 1f)
            ((baseSin + noise) * envelope).coerceIn(0.12f, 1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(duration) {
                    detectTapGestures(
                        onTap = { offset ->
                            val tappedProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((tappedProgress * duration).toLong())
                        }
                    )
                }
                .pointerInput(duration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isScrubbing = true
                            scrubProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            scrubProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            onSeek((scrubProgress * duration).toLong())
                            isScrubbing = false
                        },
                        onDragCancel = {
                            isScrubbing = false
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2f
            val totalBars = waveHeights.size
            val barGap = 2.dp.toPx()
            val totalGaps = (totalBars - 1) * barGap
            val barWidth = ((canvasWidth - totalGaps) / totalBars).coerceAtLeast(2.dp.toPx())

            val currentScrubX = effectiveProgress * canvasWidth

            for (i in 0 until totalBars) {
                val x = i * (barWidth + barGap)
                var rawHeight = waveHeights[i]

                // Live dynamic vibration if playing
                if (isPlaying) {
                    val vibration = (sin(pulsePhase + i * 0.4f) * 0.12f).toFloat()
                    rawHeight = (rawHeight + vibration).coerceIn(0.08f, 1f)
                }

                val barH = (rawHeight * (canvasHeight - 6.dp.toPx())).coerceAtLeast(4.dp.toPx())
                val topY = centerY - (barH / 2f)

                val isPlayed = (x + barWidth / 2f) <= currentScrubX

                if (isPlayed) {
                    // Played Waveform: Vibrant glowing gradient
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.85f),
                                accentColor
                            ),
                            startY = topY,
                            endY = topY + barH
                        ),
                        topLeft = Offset(x, topY),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                } else {
                    // Unplayed Waveform: Sleek metallic slate
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2C384C),
                                Color(0xFF192230),
                                Color(0xFF2C384C)
                            ),
                            startY = topY,
                            endY = topY + barH
                        ),
                        topLeft = Offset(x, topY),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }

            // Poweramp Glowing Playhead / Needle
            val needleX = currentScrubX.coerceIn(0f, canvasWidth)
            // Playhead outer glow
            drawLine(
                color = accentColor.copy(alpha = 0.45f),
                start = Offset(needleX, 0f),
                end = Offset(needleX, canvasHeight),
                strokeWidth = 6.dp.toPx()
            )
            // Playhead solid line
            drawLine(
                color = Color.White,
                start = Offset(needleX, 0f),
                end = Offset(needleX, canvasHeight),
                strokeWidth = 2.5.dp.toPx()
            )

            // Playhead circular top/bottom beads
            drawCircle(
                color = accentColor,
                radius = 4.dp.toPx(),
                center = Offset(needleX, 3.dp.toPx())
            )
            drawCircle(
                color = accentColor,
                radius = 4.dp.toPx(),
                center = Offset(needleX, canvasHeight - 3.dp.toPx())
            )
        }

        // Scrubbing Tooltip HUD
        if (isScrubbing) {
            val scrubTimeMs = (scrubProgress * duration).toLong()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0C1017),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-36).dp)
            ) {
                Text(
                    text = "${formatDurationMs(scrubTimeMs)} / ${formatDurationMs(duration)}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    ),
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
