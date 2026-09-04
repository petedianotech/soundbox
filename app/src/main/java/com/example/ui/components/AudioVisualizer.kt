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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.util.SettingsManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 10 Studio-Grade, Professional Audio Spectrum & Visualizer Engines.
 * Designed for high fidelity, non-distracting aesthetics, and smooth physics.
 */
enum class VisualizerStyle(
    val id: String,
    val title: String,
    val subtitle: String
) {
    STUDIO_SPECTRUM("STUDIO_SPECTRUM", "32-Band Studio Spectrum", "Linear frequency bars with peak hold caps"),
    ANALOG_VU_DUAL("ANALOG_VU_DUAL", "Dual Analog VU Meters", "Audiophile left & right needle dials"),
    RADIAL_ORBIT("RADIAL_ORBIT", "Radial Orbit Ring", "Circular radiating acoustic energy rays"),
    OSCILLOSCOPE_CRT("OSCILLOSCOPE_CRT", "Phosphor Oscilloscope", "Continuous analog waveform beam"),
    MIRRORED_STEREO("MIRRORED_STEREO", "Mirrored Stereo Field", "Bilateral symmetrical twin spectrum"),
    FLOATING_PARTICLES("FLOATING_PARTICLES", "Audio Constellation", "Sound-reactive floating particle field"),
    CHROMATIC_WAVES("CHROMATIC_WAVES", "Harmonic Wave Ribbons", "Multi-band flowing acoustic curves"),
    SEGMENTED_LED("SEGMENTED_LED", "Hi-Fi Segmented LEDs", "Discrete calibrated LED headroom stack"),
    ACOUSTIC_CURVE("ACOUSTIC_CURVE", "RTA Spline Envelope", "Continuous bezier frequency envelope"),
    DYNAMIC_PEAK_DOTS("DYNAMIC_PEAK_DOTS", "Minimalist Peak Matrix", "Floating transient harmonic dot matrix");

    companion object {
        fun fromId(id: String): VisualizerStyle {
            return entries.find { it.id.equals(id, ignoreCase = true) }
                ?: when (id) {
                    "WAVEFORM" -> STUDIO_SPECTRUM
                    "SPECTRUM" -> STUDIO_SPECTRUM
                    "OSCILLOSCOPE" -> OSCILLOSCOPE_CRT
                    else -> STUDIO_SPECTRUM
                }
        }
    }
}

@Composable
fun RealtimeAudioVisualizer(
    audioSessionId: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val savedStyleId by settingsManager.visualizerStyle.collectAsState()
    val activeStyle = remember(savedStyleId) { VisualizerStyle.fromId(savedStyleId) }

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

    // Frequency magnitudes (0f to 1f) for each band
    val magnitudes = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.05f) } } }
    val peakHold = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.05f) } } }

    // Fallback animation for preview/no-permission/pause
    val infiniteTransition = rememberInfiniteTransition(label = "VisualizerFallback")
    val fallbackPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Hardware Visualizer setup
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
                                if (activeStyle == VisualizerStyle.OSCILLOSCOPE_CRT || activeStyle == VisualizerStyle.CHROMATIC_WAVES) {
                                    val step = waveform.size / barCount
                                    for (i in 0 until barCount) {
                                        val idx = (i * step).coerceIn(0, waveform.size - 1)
                                        val sample = ((waveform[idx].toInt() and 0xFF) - 128) / 128f
                                        val smoothed = abs(sample)
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
                                        val current = magnitudes[i]
                                        val updated = if (normalized > current) {
                                            (current * 0.22f) + (normalized * 0.78f)
                                        } else {
                                            (current * 0.85f) + (normalized * 0.15f)
                                        }
                                        magnitudes[i] = updated

                                        val currentPeak = peakHold[i]
                                        if (updated >= currentPeak) {
                                            peakHold[i] = updated
                                        } else {
                                            peakHold[i] = (currentPeak - 0.018f).coerceAtLeast(updated)
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

    fun cycleNextStyle() {
        val allStyles = VisualizerStyle.entries
        val nextIndex = (activeStyle.ordinal + 1) % allStyles.size
        settingsManager.setVisualizerStyle(allStyles[nextIndex].id)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Visualizer Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { cycleNextStyle() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
                val width = size.width
                val height = size.height

                // Helper to get normalized magnitude for band i
                fun getMag(i: Int): Float {
                    return if (isPlaying && hasRecordPermission) {
                        magnitudes.getOrElse(i) { 0.05f }
                    } else if (isPlaying) {
                        val freq = (i + 1).toFloat() * 0.65f
                        val wave = (sin(fallbackPhase + freq) * 0.5f + 0.5f)
                        (0.12f + wave * 0.72f).coerceIn(0.06f, 1f)
                    } else {
                        0.04f
                    }
                }

                fun getPeak(i: Int, mag: Float): Float {
                    return if (isPlaying && hasRecordPermission) {
                        peakHold.getOrElse(i) { mag }
                    } else {
                        (mag + 0.06f).coerceAtMost(1f)
                    }
                }

                when (activeStyle) {
                    VisualizerStyle.STUDIO_SPECTRUM -> {
                        // 1. 32-Band Studio Spectrum
                        val totalGap = width * 0.18f
                        val barSpacing = totalGap / (barCount - 1).coerceAtLeast(1)
                        val barWidth = ((width - totalGap) / barCount).coerceAtLeast(2f)

                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val barHeight = (height * 0.85f * mag).coerceAtLeast(4f)
                            val x = i * (barWidth + barSpacing)
                            val y = height - barHeight

                            val barBrush = Brush.verticalGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.65f)),
                                startY = y,
                                endY = height
                            )

                            drawRoundRect(
                                brush = barBrush,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                            )

                            val peak = getPeak(i, mag)
                            val peakY = (height - (height * 0.85f * peak) - 3f).coerceAtLeast(0f)
                            drawCircle(
                                color = accentColor,
                                radius = (barWidth / 2f).coerceAtMost(2.5.dp.toPx()),
                                center = Offset(x + (barWidth / 2f), peakY)
                            )
                        }
                    }

                    VisualizerStyle.ANALOG_VU_DUAL -> {
                        // 2. Dual Audiophile Analog VU Meters (Left & Right channels)
                        val meterWidth = (width - 16.dp.toPx()) / 2f
                        val meterHeight = height

                        for (ch in 0..1) {
                            val meterX = ch * (meterWidth + 16.dp.toPx())
                            val pivotX = meterX + meterWidth / 2f
                            val pivotY = meterHeight * 1.15f
                            val needleLength = meterHeight * 0.95f

                            // Average energy for left (ch=0) vs right (ch=1)
                            val half = barCount / 2
                            val channelMags = if (ch == 0) (0 until half) else (half until barCount)
                            val avgMag = channelMags.map { getMag(it) }.average().toFloat().coerceIn(0.04f, 1f)

                            // Arc sweep: -40 deg to +40 deg
                            val angleDeg = -40f + (avgMag * 80f)
                            val angleRad = (angleDeg - 90f) * (PI / 180f)
                            val needleEndX = pivotX + (cos(angleRad) * needleLength).toFloat()
                            val needleEndY = pivotY + (sin(angleRad) * needleLength).toFloat()

                            // Draw scale arc
                            drawArc(
                                color = secondaryColor.copy(alpha = 0.25f),
                                startAngle = 210f,
                                sweepAngle = 120f,
                                useCenter = false,
                                topLeft = Offset(meterX + 4.dp.toPx(), 6.dp.toPx()),
                                size = Size(meterWidth - 8.dp.toPx(), meterHeight * 1.2f),
                                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Needle
                            val isOverload = avgMag > 0.82f
                            val needleColor = if (isOverload) Color(0xFFEF4444) else accentColor
                            drawLine(
                                color = needleColor,
                                start = Offset(pivotX, pivotY),
                                end = Offset(needleEndX, needleEndY),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Pivot cap
                            drawCircle(
                                color = secondaryColor,
                                radius = 4.dp.toPx(),
                                center = Offset(pivotX, pivotY)
                            )

                            // Peak LED
                            if (isOverload) {
                                drawCircle(
                                    color = Color(0xFFEF4444),
                                    radius = 3.dp.toPx(),
                                    center = Offset(meterX + meterWidth - 10.dp.toPx(), 10.dp.toPx())
                                )
                            }
                        }
                    }

                    VisualizerStyle.RADIAL_ORBIT -> {
                        // 3. Radial Orbit Ring
                        val centerX = width / 2f
                        val centerY = height / 2f
                        val baseRadius = (height / 2f) * 0.45f
                        val maxRayLength = (height / 2f) * 0.5f

                        // Inner core
                        val bassEnergy = (getMag(0) + getMag(1) + getMag(2)) / 3f
                        drawCircle(
                            color = accentColor.copy(alpha = 0.2f),
                            radius = baseRadius + (bassEnergy * 6f),
                            center = Offset(centerX, centerY)
                        )
                        drawCircle(
                            color = accentColor,
                            radius = baseRadius * 0.75f,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        // Radiating rays
                        val angleStep = (2 * PI) / barCount
                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val angle = i * angleStep
                            val startX = centerX + (cos(angle) * baseRadius).toFloat()
                            val startY = centerY + (sin(angle) * baseRadius).toFloat()
                            val rayLen = baseRadius + (mag * maxRayLength)
                            val endX = centerX + (cos(angle) * rayLen).toFloat()
                            val endY = centerY + (sin(angle) * rayLen).toFloat()

                            drawLine(
                                color = if (i % 2 == 0) accentColor else secondaryColor,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    VisualizerStyle.OSCILLOSCOPE_CRT -> {
                        // 4. Phosphor CRT Oscilloscope
                        val path = Path()
                        val stepX = width / (barCount - 1).coerceAtLeast(1)
                        val midY = height / 2f

                        for (i in 0 until barCount) {
                            val mag = if (isPlaying && hasRecordPermission) {
                                (magnitudes.getOrElse(i) { 0.05f } - 0.5f) * 2f
                            } else if (isPlaying) {
                                (sin(fallbackPhase + (i * 0.45f)) * 0.85f)
                            } else {
                                0.0f
                            }

                            val x = i * stepX
                            val y = midY + (mag * (height * 0.38f))

                            if (i == 0) {
                                path.moveTo(x, y)
                            } else {
                                val prevX = (i - 1) * stepX
                                val prevMag = if (isPlaying && hasRecordPermission) {
                                    (magnitudes.getOrElse(i - 1) { 0.05f } - 0.5f) * 2f
                                } else if (isPlaying) {
                                    (sin(fallbackPhase + ((i - 1) * 0.45f)) * 0.85f)
                                } else {
                                    0.0f
                                }
                                val prevY = midY + (prevMag * (height * 0.38f))
                                val cx = (prevX + x) / 2f
                                path.cubicTo(cx, prevY, cx, y, x, y)
                            }
                        }

                        // Outer phosphor glow
                        drawPath(
                            path = path,
                            color = accentColor.copy(alpha = 0.35f),
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // Sharp laser line
                        drawPath(
                            path = path,
                            color = accentColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    VisualizerStyle.MIRRORED_STEREO -> {
                        // 5. Mirrored Stereo Field (Symmetrical from center horizontal axis)
                        val totalGap = width * 0.18f
                        val barSpacing = totalGap / (barCount - 1).coerceAtLeast(1)
                        val barWidth = ((width - totalGap) / barCount).coerceAtLeast(2f)
                        val midY = height / 2f

                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val halfH = (height * 0.42f * mag).coerceAtLeast(2f)
                            val x = i * (barWidth + barSpacing)

                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset(x, midY - halfH),
                                size = Size(barWidth, halfH * 2f),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                            )
                        }

                        // Baseline axis
                        drawLine(
                            color = secondaryColor.copy(alpha = 0.3f),
                            start = Offset(0f, midY),
                            end = Offset(width, midY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    VisualizerStyle.FLOATING_PARTICLES -> {
                        // 6. Audio Constellation Particles
                        val stepX = width / (barCount - 1).coerceAtLeast(1)
                        val particlePoints = mutableListOf<Offset>()

                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val x = i * stepX
                            val y = height - (height * 0.85f * mag).coerceIn(8f, height - 8f)
                            val pt = Offset(x, y)
                            particlePoints.add(pt)

                            val radius = (3.dp.toPx() + (mag * 4.dp.toPx())).coerceAtMost(6.dp.toPx())
                            drawCircle(
                                color = accentColor,
                                radius = radius,
                                center = pt
                            )
                        }

                        // Constellation link lines
                        for (i in 0 until particlePoints.size - 1) {
                            val p1 = particlePoints[i]
                            val p2 = particlePoints[i + 1]
                            drawLine(
                                color = secondaryColor.copy(alpha = 0.35f),
                                start = p1,
                                end = p2,
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    VisualizerStyle.CHROMATIC_WAVES -> {
                        // 7. Harmonic Wave Ribbons (3 harmonic smooth waves)
                        val midY = height / 2f
                        val stepX = width / (barCount - 1).coerceAtLeast(1)

                        val waveConfigs = listOf(
                            Triple(accentColor.copy(alpha = 0.65f), 1.0f, 0.4f),
                            Triple(secondaryColor.copy(alpha = 0.5f), 1.6f, 0.32f),
                            Triple(accentColor.copy(alpha = 0.35f), 2.2f, 0.25f)
                        )

                        for ((color, freqMult, ampMult) in waveConfigs) {
                            val path = Path()
                            for (i in 0 until barCount) {
                                val mag = getMag(i)
                                val x = i * stepX
                                val y = midY + (sin(fallbackPhase * freqMult + (i * 0.4f)) * mag * height * ampMult)

                                if (i == 0) path.moveTo(x, y)
                                else {
                                    val prevX = (i - 1) * stepX
                                    val prevY = midY + (sin(fallbackPhase * freqMult + ((i - 1) * 0.4f)) * getMag(i - 1) * height * ampMult)
                                    val cx = (prevX + x) / 2f
                                    path.cubicTo(cx, prevY, cx, y, x, y)
                                }
                            }
                            drawPath(path = path, color = color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }

                    VisualizerStyle.SEGMENTED_LED -> {
                        // 8. Hi-Fi Segmented LED Stack
                        val columns = 18
                        val segmentsPerCol = 8
                        val colWidth = (width / columns) * 0.72f
                        val colSpacing = (width / columns) * 0.28f
                        val segHeight = (height / segmentsPerCol) * 0.68f
                        val segSpacing = (height / segmentsPerCol) * 0.32f

                        for (c in 0 until columns) {
                            val bandIdx = (c * (barCount / columns)).coerceIn(0, barCount - 1)
                            val mag = getMag(bandIdx)
                            val activeSegments = (mag * segmentsPerCol).toInt().coerceIn(1, segmentsPerCol)
                            val x = c * (colWidth + colSpacing) + (colSpacing / 2f)

                            for (s in 0 until segmentsPerCol) {
                                val segFromBottom = segmentsPerCol - 1 - s
                                val y = s * (segHeight + segSpacing)
                                val isActive = segFromBottom < activeSegments

                                val segColor = when {
                                    !isActive -> secondaryColor.copy(alpha = 0.12f)
                                    segFromBottom >= 7 -> Color(0xFFEF4444) // Red overload
                                    segFromBottom >= 5 -> Color(0xFFF59E0B) // Amber headroom
                                    else -> accentColor // Safe signal level
                                }

                                drawRoundRect(
                                    color = segColor,
                                    topLeft = Offset(x, y),
                                    size = Size(colWidth, segHeight),
                                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                )
                            }
                        }
                    }

                    VisualizerStyle.ACOUSTIC_CURVE -> {
                        // 9. RTA Frequency Spline Envelope
                        val stepX = width / (barCount - 1).coerceAtLeast(1)
                        val curvePath = Path()
                        val fillPath = Path()

                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val x = i * stepX
                            val y = height - (height * 0.88f * mag).coerceAtLeast(4f)

                            if (i == 0) {
                                curvePath.moveTo(x, y)
                                fillPath.moveTo(x, height)
                                fillPath.lineTo(x, y)
                            } else {
                                val prevX = (i - 1) * stepX
                                val prevMag = getMag(i - 1)
                                val prevY = height - (height * 0.88f * prevMag).coerceAtLeast(4f)
                                val cx = (prevX + x) / 2f
                                curvePath.cubicTo(cx, prevY, cx, y, x, y)
                                fillPath.cubicTo(cx, prevY, cx, y, x, y)
                            }
                        }

                        fillPath.lineTo(width, height)
                        fillPath.close()

                        // Gradient fill under curve
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(accentColor.copy(alpha = 0.35f), Color.Transparent),
                                startY = 0f,
                                endY = height
                            ),
                            style = Fill
                        )

                        // Outline curve
                        drawPath(
                            path = curvePath,
                            color = accentColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    VisualizerStyle.DYNAMIC_PEAK_DOTS -> {
                        // 10. Minimalist Peak Dot Matrix
                        val stepX = width / (barCount - 1).coerceAtLeast(1)

                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val peak = getPeak(i, mag)
                            val x = i * stepX
                            val y = height - (height * 0.86f * peak).coerceAtLeast(6f)

                            // Subtle vertical tracer line
                            drawLine(
                                color = secondaryColor.copy(alpha = 0.15f),
                                start = Offset(x, height),
                                end = Offset(x, y),
                                strokeWidth = 1.dp.toPx()
                            )

                            // Glowing peak dot
                            drawCircle(
                                color = accentColor.copy(alpha = 0.35f),
                                radius = 5.dp.toPx(),
                                center = Offset(x, y)
                            )
                            drawCircle(
                                color = accentColor,
                                radius = 2.5.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }
        }

        // Subtitle bar with style name & permission button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { cycleNextStyle() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${activeStyle.ordinal + 1}/10 • ${activeStyle.title}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!hasRecordPermission) {
                    TextButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
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
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Turn off visualizer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
