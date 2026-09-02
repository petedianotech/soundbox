package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.hypot
import kotlin.math.sin

enum class VisualizerStyle {
    SPECTRUM_BARS,
    WAVE_LINE,
    COMPACT_PULSE
}

@Composable
fun RealtimeAudioVisualizer(
    audioSessionId: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary
) {
    val context = LocalContext.current
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
    }

    var visualizerStyle by remember { mutableStateOf(VisualizerStyle.SPECTRUM_BARS) }

    // Frequency magnitudes (0f to 1f) for each bar
    val magnitudes = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.05f) } } }
    val peakHold = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.05f) } } }

    // Fallback animation for preview/no-permission/pause
    val infiniteTransition = rememberInfiniteTransition(label = "VisualizerFallback")
    val fallbackPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Real hardware visualizer setup
    DisposableEffect(audioSessionId, isPlaying, hasRecordPermission) {
        var visualizer: Visualizer? = null
        if (hasRecordPermission && isPlaying && audioSessionId > 0) {
            try {
                val captureSize = 256
                visualizer = Visualizer(audioSessionId).apply {
                    this.captureSize = captureSize
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                v: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) {
                                if (waveform == null || !isPlaying) return
                                if (visualizerStyle == VisualizerStyle.WAVE_LINE) {
                                    val step = waveform.size / barCount
                                    for (i in 0 until barCount) {
                                        val idx = (i * step).coerceIn(0, waveform.size - 1)
                                        val sample = ((waveform[idx].toInt() and 0xFF) - 128) / 128f
                                        val smoothed = kotlin.math.abs(sample)
                                        if (i < magnitudes.size) {
                                            magnitudes[i] = (magnitudes[i] * 0.4f) + (smoothed * 0.6f)
                                        }
                                    }
                                }
                            }

                            override fun onFftDataCapture(
                                v: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) {
                                if (fft == null || !isPlaying) return
                                val n = fft.size / 2
                                val bandsPerBar = (n / barCount).coerceAtLeast(1)

                                for (i in 0 until barCount) {
                                    var sumMag = 0f
                                    val start = i * bandsPerBar
                                    val end = ((i + 1) * bandsPerBar).coerceAtMost(n)

                                    for (k in start until end) {
                                        val reIdx = 2 * k
                                        val imIdx = 2 * k + 1
                                        if (imIdx < fft.size) {
                                            val re = fft[reIdx].toFloat()
                                            val im = fft[imIdx].toFloat()
                                            val mag = hypot(re, im)
                                            sumMag += mag
                                        }
                                    }
                                    val count = (end - start).coerceAtLeast(1)
                                    val avgMag = (sumMag / count) / 64f
                                    val normalized = avgMag.coerceIn(0.04f, 1.0f)

                                    if (i < magnitudes.size) {
                                        // Physics lerp decay
                                        val current = magnitudes[i]
                                        val updated = if (normalized > current) {
                                            (current * 0.25f) + (normalized * 0.75f)
                                        } else {
                                            (current * 0.82f) + (normalized * 0.18f)
                                        }
                                        magnitudes[i] = updated

                                        // Peak hold decay
                                        val currentPeak = peakHold[i]
                                        if (updated >= currentPeak) {
                                            peakHold[i] = updated
                                        } else {
                                            peakHold[i] = (currentPeak - 0.02f).coerceAtLeast(updated)
                                        }
                                    }
                                }
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        true,
                        true
                    )
                    enabled = true
                }
            } catch (e: Exception) {
                Log.w("AudioVisualizer", "Could not initialize hardware visualizer: ${e.message}")
            }
        }

        onDispose {
            try {
                visualizer?.enabled = false
                visualizer?.release()
            } catch (e: Exception) {
                Log.w("AudioVisualizer", "Error releasing visualizer: ${e.message}")
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Visualizer Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clickable {
                    // Cycle visualizer styles on tap
                    visualizerStyle = when (visualizerStyle) {
                        VisualizerStyle.SPECTRUM_BARS -> VisualizerStyle.WAVE_LINE
                        VisualizerStyle.WAVE_LINE -> VisualizerStyle.COMPACT_PULSE
                        VisualizerStyle.COMPACT_PULSE -> VisualizerStyle.SPECTRUM_BARS
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                when (visualizerStyle) {
                    VisualizerStyle.SPECTRUM_BARS -> {
                        val totalGap = width * 0.15f
                        val barSpacing = totalGap / (barCount - 1).coerceAtLeast(1)
                        val barWidth = ((width - totalGap) / barCount).coerceAtLeast(2f)

                        for (i in 0 until barCount) {
                            val mag = if (isPlaying && hasRecordPermission) {
                                magnitudes.getOrElse(i) { 0.05f }
                            } else if (isPlaying) {
                                // Smooth mathematical fallback spectrum
                                val freq = (i + 1).toFloat() * 0.8f
                                val wave = (sin(fallbackPhase + freq) * 0.5f + 0.5f)
                                (0.15f + wave * 0.7f).coerceIn(0.08f, 1f)
                            } else {
                                0.05f
                            }

                            val barHeight = (height * 0.88f * mag).coerceAtLeast(4f)
                            val x = i * (barWidth + barSpacing)
                            val y = height - barHeight

                            // Main solid bar
                            drawRoundRect(
                                color = if (i % 2 == 0) accentColor else secondaryColor,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                            )

                            // Floating peak point
                            val peak = if (isPlaying && hasRecordPermission) {
                                peakHold.getOrElse(i) { mag }
                            } else {
                                mag + 0.05f
                            }
                            val peakY = (height - (height * 0.88f * peak) - 4f).coerceAtLeast(0f)
                            drawCircle(
                                color = accentColor,
                                radius = (barWidth / 2).coerceAtMost(3.dp.toPx()),
                                center = Offset(x + (barWidth / 2), peakY)
                            )
                        }
                    }

                    VisualizerStyle.WAVE_LINE -> {
                        val path = Path()
                        val stepX = width / (barCount - 1).coerceAtLeast(1)
                        val midY = height / 2f

                        for (i in 0 until barCount) {
                            val mag = if (isPlaying && hasRecordPermission) {
                                magnitudes.getOrElse(i) { 0.05f }
                            } else if (isPlaying) {
                                (sin(fallbackPhase + (i * 0.5f)) * 0.45f)
                            } else {
                                0.0f
                            }

                            val x = i * stepX
                            val y = midY + (mag * (height * 0.4f))

                            if (i == 0) {
                                path.moveTo(x, y)
                            } else {
                                val prevX = (i - 1) * stepX
                                val prevMag = if (isPlaying && hasRecordPermission) {
                                    magnitudes.getOrElse(i - 1) { 0.05f }
                                } else if (isPlaying) {
                                    (sin(fallbackPhase + ((i - 1) * 0.5f)) * 0.45f)
                                } else {
                                    0.0f
                                }
                                val prevY = midY + (prevMag * (height * 0.4f))
                                val cx = (prevX + x) / 2f
                                path.cubicTo(cx, prevY, cx, y, x, y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = accentColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    VisualizerStyle.COMPACT_PULSE -> {
                        val avgMag = if (isPlaying && hasRecordPermission) {
                            magnitudes.average().toFloat().coerceIn(0.1f, 1f)
                        } else if (isPlaying) {
                            (sin(fallbackPhase) * 0.35f + 0.55f).coerceIn(0.1f, 1f)
                        } else {
                            0.1f
                        }

                        val maxRadius = (height / 2f) * 0.9f
                        drawCircle(
                            color = secondaryColor.copy(alpha = 0.3f),
                            radius = maxRadius * avgMag,
                            center = Offset(width / 2f, height / 2f)
                        )
                        drawCircle(
                            color = accentColor,
                            radius = (maxRadius * 0.6f) * avgMag,
                            center = Offset(width / 2f, height / 2f)
                        )
                    }
                }
            }
        }

        // Subtitle bar with style switcher & permission button if needed
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    visualizerStyle = when (visualizerStyle) {
                        VisualizerStyle.SPECTRUM_BARS -> VisualizerStyle.WAVE_LINE
                        VisualizerStyle.WAVE_LINE -> VisualizerStyle.COMPACT_PULSE
                        VisualizerStyle.COMPACT_PULSE -> VisualizerStyle.SPECTRUM_BARS
                    }
                }
            ) {
                Icon(
                    imageVector = when (visualizerStyle) {
                        VisualizerStyle.SPECTRUM_BARS -> Icons.Default.GraphicEq
                        VisualizerStyle.WAVE_LINE -> Icons.Default.Waves
                        VisualizerStyle.COMPACT_PULSE -> Icons.Default.GraphicEq
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when (visualizerStyle) {
                        VisualizerStyle.SPECTRUM_BARS -> "Frequency Spectrum"
                        VisualizerStyle.WAVE_LINE -> "Audio Wave"
                        VisualizerStyle.COMPACT_PULSE -> "Audio Pulse"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!hasRecordPermission) {
                TextButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Enable Live Audio Spectrum",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Enable Live FFT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
