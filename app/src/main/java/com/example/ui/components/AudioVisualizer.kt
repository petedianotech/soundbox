package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.*

enum class VisualizerStyle(val displayName: String) {
    OFF("Spectrum Off (Disabled)"),
    SPECTRUM_BARS("Neon Spectrum"),
    AI_NEURAL_GLOW("AI Neural Flow"),
    MINIMALIST_DOT_MATRIX("Minimalist Matrix"),
    CIRCULAR_RADIAL("Cosmic Halo"),
    WAVE_LINE("Fluid Neon Wave"),
    COMPACT_PULSE("Sub-Bass Aura")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealtimeAudioVisualizer(
    audioSessionId: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary
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
    var showStylePickerSheet by remember { mutableStateOf(false) }

    // Frequency magnitudes (0f to 1f) for each bar
    val magnitudes = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.05f) } } }
    val peakHold = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.05f) } } }

    // Fallback organic animation for preview / no-permission / idle states
    val infiniteTransition = rememberInfiniteTransition(label = "VisualizerAnimation")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val secondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sec_phase"
    )

    // Hardware visualizer integration
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
                                        val smoothed = abs(sample)
                                        if (i < magnitudes.size) {
                                            magnitudes[i] = (magnitudes[i] * 0.35f) + (smoothed * 0.65f)
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
                                            (current * 0.2f) + (normalized * 0.8f)
                                        } else {
                                            (current * 0.8f) + (normalized * 0.2f)
                                        }
                                        magnitudes[i] = updated

                                        // Peak hold decay
                                        val currentPeak = peakHold[i]
                                        if (updated >= currentPeak) {
                                            peakHold[i] = updated
                                        } else {
                                            peakHold[i] = (currentPeak - 0.025f).coerceAtLeast(updated)
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
        // Visualizer Canvas Box with smooth interactive cycling
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (visualizerStyle == VisualizerStyle.OFF) 44.dp else 72.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    // Cycle to next style on tap
                    val styles = VisualizerStyle.values()
                    val nextIndex = (visualizerStyle.ordinal + 1) % styles.size
                    visualizerStyle = styles[nextIndex]
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
        ) {
            if (visualizerStyle == VisualizerStyle.OFF) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Spectrum Disabled (Tap to enable)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    when (visualizerStyle) {
                        VisualizerStyle.OFF -> {}
                        VisualizerStyle.SPECTRUM_BARS -> {
                        val totalGap = width * 0.14f
                        val barSpacing = totalGap / (barCount - 1).coerceAtLeast(1)
                        val barWidth = ((width - totalGap) / barCount).coerceAtLeast(2.5f)

                        for (i in 0 until barCount) {
                            val mag = if (isPlaying && hasRecordPermission) {
                                magnitudes.getOrElse(i) { 0.05f }
                            } else if (isPlaying) {
                                val freq = (i + 1).toFloat() * 0.75f
                                val wave = (sin(phase + freq) * 0.45f + 0.55f)
                                (0.12f + wave * 0.78f).coerceIn(0.08f, 1f)
                            } else {
                                0.06f
                            }

                            val barHeight = (height * 0.85f * mag).coerceAtLeast(4f)
                            val x = i * (barWidth + barSpacing)
                            val y = height - barHeight - 4f

                            val barBrush = Brush.verticalGradient(
                                colors = listOf(accentColor, secondaryColor),
                                startY = y,
                                endY = height
                            )

                            // Main equalizer bar
                            drawRoundRect(
                                brush = barBrush,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                            )

                            // Peak indicator
                            val peak = if (isPlaying && hasRecordPermission) {
                                peakHold.getOrElse(i) { mag }
                            } else {
                                mag + 0.06f
                            }
                            val peakY = (height - (height * 0.85f * peak) - 8f).coerceAtLeast(0f)
                            drawCircle(
                                color = tertiaryColor,
                                radius = (barWidth / 2.2f).coerceIn(1.5f, 3.5.dp.toPx()),
                                center = Offset(x + (barWidth / 2), peakY)
                            )
                        }
                    }

                    VisualizerStyle.AI_NEURAL_GLOW -> {
                        val nodeCount = 14
                        val stepX = width / (nodeCount - 1).coerceAtLeast(1)
                        val midY = height / 2f
                        val path = Path()

                        val nodePositions = mutableListOf<Offset>()

                        for (i in 0 until nodeCount) {
                            val mag = if (isPlaying && hasRecordPermission) {
                                magnitudes.getOrElse(i * 2) { 0.1f }
                            } else if (isPlaying) {
                                (sin(phase * 1.5f + (i * 0.7f)) * 0.4f + 0.5f)
                            } else {
                                0.15f
                            }

                            val x = i * stepX
                            val y = midY + ((mag - 0.5f) * height * 0.75f)
                            val pt = Offset(x, y)
                            nodePositions.add(pt)

                            if (i == 0) {
                                path.moveTo(x, y)
                            } else {
                                val prev = nodePositions[i - 1]
                                val cx = (prev.x + x) / 2f
                                path.cubicTo(cx, prev.y, cx, y, x, y)
                            }
                        }

                        // Draw connecting energy neon wave
                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(listOf(accentColor, secondaryColor, tertiaryColor)),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Draw neural node glow circles & pulses
                        nodePositions.forEachIndexed { idx, pt ->
                            val energy = if (isPlaying && hasRecordPermission) {
                                magnitudes.getOrElse(idx * 2) { 0.1f }
                            } else if (isPlaying) {
                                (sin(phase + idx) * 0.35f + 0.5f)
                            } else {
                                0.2f
                            }
                            // Outer aura
                            drawCircle(
                                color = accentColor.copy(alpha = 0.25f),
                                radius = 7.dp.toPx() * energy,
                                center = pt
                            )
                            // Core point
                            drawCircle(
                                color = if (idx % 2 == 0) tertiaryColor else secondaryColor,
                                radius = 3.dp.toPx(),
                                center = pt
                            )
                        }
                    }

                    VisualizerStyle.MINIMALIST_DOT_MATRIX -> {
                        val cols = 18
                        val rows = 6
                        val colWidth = (width / cols)
                        val dotRadius = (colWidth * 0.28f).coerceIn(2f, 4.dp.toPx())
                        val rowHeight = (height - 8f) / rows

                        for (c in 0 until cols) {
                            val mag = if (isPlaying && hasRecordPermission) {
                                magnitudes.getOrElse(c) { 0.05f }
                            } else if (isPlaying) {
                                (sin(phase + (c * 0.6f)) * 0.45f + 0.55f).coerceIn(0.1f, 1f)
                            } else {
                                0.05f
                            }

                            val activeDots = (mag * rows).roundToInt().coerceIn(1, rows)
                            val centerX = (c * colWidth) + (colWidth / 2)

                            for (r in 0 until rows) {
                                val centerY = height - ((r + 0.5f) * rowHeight) - 2f
                                val isActive = (r < activeDots)
                                val dotColor = if (isActive) {
                                    if (r >= rows - 2) tertiaryColor else accentColor
                                } else {
                                    secondaryColor.copy(alpha = 0.15f)
                                }

                                drawCircle(
                                    color = dotColor,
                                    radius = dotRadius,
                                    center = Offset(centerX, centerY)
                                )
                            }
                        }
                    }

                    VisualizerStyle.CIRCULAR_RADIAL -> {
                        val centerX = width / 2f
                        val centerY = height / 2f
                        val baseRadius = (height / 2f) * 0.45f
                        val rayCount = 24
                        val angleStep = (2 * Math.PI) / rayCount

                        val avgEnergy = if (isPlaying && hasRecordPermission) {
                            magnitudes.average().toFloat().coerceIn(0.1f, 1f)
                        } else if (isPlaying) {
                            (sin(phase) * 0.35f + 0.65f).coerceIn(0.2f, 1f)
                        } else {
                            0.15f
                        }

                        // Center core pulse
                        drawCircle(
                            color = accentColor.copy(alpha = 0.2f),
                            radius = baseRadius * 1.3f * avgEnergy,
                            center = Offset(centerX, centerY)
                        )
                        drawCircle(
                            color = accentColor,
                            radius = baseRadius * 0.85f,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // 360 Radial Rays
                        for (i in 0 until rayCount) {
                            val angle = i * angleStep + phase * 0.2f
                            val mag = if (isPlaying && hasRecordPermission) {
                                magnitudes.getOrElse(i) { 0.1f }
                            } else if (isPlaying) {
                                (sin(phase + (i * 0.5f)) * 0.4f + 0.6f)
                            } else {
                                0.1f
                            }

                            val rayLength = (height / 2f * 0.5f * mag).coerceAtLeast(4f)
                            val startX = (centerX + cos(angle) * (baseRadius + 2f)).toFloat()
                            val startY = (centerY + sin(angle) * (baseRadius + 2f)).toFloat()
                            val endX = (centerX + cos(angle) * (baseRadius + 2f + rayLength)).toFloat()
                            val endY = (centerY + sin(angle) * (baseRadius + 2f + rayLength)).toFloat()

                            drawLine(
                                color = if (i % 2 == 0) secondaryColor else tertiaryColor,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    VisualizerStyle.WAVE_LINE -> {
                        val path1 = Path()
                        val path2 = Path()
                        val stepX = width / (barCount - 1).coerceAtLeast(1)
                        val midY = height / 2f

                        for (i in 0 until barCount) {
                            val mag1 = if (isPlaying && hasRecordPermission) {
                                magnitudes.getOrElse(i) { 0.05f }
                            } else if (isPlaying) {
                                (sin(phase + (i * 0.45f)) * 0.45f)
                            } else {
                                0.0f
                            }
                            val mag2 = if (isPlaying && hasRecordPermission) {
                                magnitudes.getOrElse((i + 4) % barCount) { 0.05f }
                            } else if (isPlaying) {
                                (cos(secondaryPhase + (i * 0.45f)) * 0.35f)
                            } else {
                                0.0f
                            }

                            val x = i * stepX
                            val y1 = midY + (mag1 * (height * 0.42f))
                            val y2 = midY + (mag2 * (height * 0.42f))

                            if (i == 0) {
                                path1.moveTo(x, y1)
                                path2.moveTo(x, y2)
                            } else {
                                val prevX = (i - 1) * stepX
                                val prevY1 = midY + ((if (isPlaying && hasRecordPermission) magnitudes.getOrElse(i - 1) { 0.05f } else if (isPlaying) sin(phase + ((i - 1) * 0.45f)) * 0.45f else 0.0f) * (height * 0.42f))
                                val prevY2 = midY + ((if (isPlaying && hasRecordPermission) magnitudes.getOrElse((i + 3) % barCount) { 0.05f } else if (isPlaying) cos(secondaryPhase + ((i - 1) * 0.45f)) * 0.35f else 0.0f) * (height * 0.42f))

                                val cx = (prevX + x) / 2f
                                path1.cubicTo(cx, prevY1, cx, y1, x, y1)
                                path2.cubicTo(cx, prevY2, cx, y2, x, y2)
                            }
                        }

                        // Background harmonic wave
                        drawPath(
                            path = path2,
                            color = secondaryColor.copy(alpha = 0.5f),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // Foreground glowing wave
                        drawPath(
                            path = path1,
                            brush = Brush.horizontalGradient(listOf(accentColor, tertiaryColor)),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    VisualizerStyle.COMPACT_PULSE -> {
                        val avgMag = if (isPlaying && hasRecordPermission) {
                            magnitudes.average().toFloat().coerceIn(0.1f, 1f)
                        } else if (isPlaying) {
                            (sin(phase) * 0.35f + 0.6f).coerceIn(0.15f, 1f)
                        } else {
                            0.12f
                        }

                        val maxRadius = (height / 2f) * 0.95f
                        val centerPt = Offset(width / 2f, height / 2f)

                        // 3 ambient aura rings
                        drawCircle(
                            color = secondaryColor.copy(alpha = 0.15f * avgMag),
                            radius = maxRadius * avgMag,
                            center = centerPt
                        )
                        drawCircle(
                            color = tertiaryColor.copy(alpha = 0.25f * avgMag),
                            radius = (maxRadius * 0.7f) * avgMag,
                            center = centerPt
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accentColor, secondaryColor),
                                center = centerPt,
                                radius = (maxRadius * 0.45f) * avgMag
                            ),
                            radius = (maxRadius * 0.45f) * avgMag,
                            center = centerPt
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

        // Interactive Footer Bar with Style Selector & Live FFT Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { showStylePickerSheet = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = when (visualizerStyle) {
                            VisualizerStyle.OFF -> Icons.Default.VisibilityOff
                            VisualizerStyle.SPECTRUM_BARS -> Icons.Default.GraphicEq
                            VisualizerStyle.AI_NEURAL_GLOW -> Icons.Default.AutoAwesome
                            VisualizerStyle.MINIMALIST_DOT_MATRIX -> Icons.Default.GridView
                            VisualizerStyle.CIRCULAR_RADIAL -> Icons.Default.Adjust
                            VisualizerStyle.WAVE_LINE -> Icons.Default.Waves
                            VisualizerStyle.COMPACT_PULSE -> Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = visualizerStyle.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select visualizer style",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (!hasRecordPermission) {
                TextButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Enable Live Audio Spectrum",
                        modifier = Modifier.size(13.dp),
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

        // Modal Sheet for choosing visualizer style
        if (showStylePickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStylePickerSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Spectrum Visualizer Style",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                VisualizerStyle.values().forEach { style ->
                    val isSelected = style == visualizerStyle
                    Surface(
                        onClick = {
                            visualizerStyle = style
                            showStylePickerSheet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (style) {
                                        VisualizerStyle.OFF -> Icons.Default.VisibilityOff
                                        VisualizerStyle.SPECTRUM_BARS -> Icons.Default.GraphicEq
                                        VisualizerStyle.AI_NEURAL_GLOW -> Icons.Default.AutoAwesome
                                        VisualizerStyle.MINIMALIST_DOT_MATRIX -> Icons.Default.GridView
                                        VisualizerStyle.CIRCULAR_RADIAL -> Icons.Default.Adjust
                                        VisualizerStyle.WAVE_LINE -> Icons.Default.Waves
                                        VisualizerStyle.COMPACT_PULSE -> Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = style.displayName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
