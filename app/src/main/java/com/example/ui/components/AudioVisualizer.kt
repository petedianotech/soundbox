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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * 16 Studio-Grade, Professional Audio Spectrum & Visualizer Engines.
 * Designed for high fidelity, non-distracting aesthetics, smooth physics,
 * zero gradient overload, and lightweight CPU/GPU efficiency on Android 5.0+.
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
    ACOUSTIC_CURVE("ACOUSTIC_CURVE", "RTA Spline Envelope", "Continuous frequency envelope"),
    DYNAMIC_PEAK_DOTS("DYNAMIC_PEAK_DOTS", "Minimalist Peak Matrix", "Floating transient harmonic dot matrix"),
    CIRCULAR_SPECTRUM("CIRCULAR_SPECTRUM", "360° Circular Equalizer", "Radial frequency ring with audio reactive burst"),
    HEXAGON_PULSE("HEXAGON_PULSE", "Harmonic Hexagon Matrix", "Resonant concentric geometric wireframe polygons"),
    WATERFALL_BARCODE("WATERFALL_BARCODE", "Audiophile Barcode Waterfall", "High-density vertical spectrum stripes"),
    VINTAGE_VU_BARS("VINTAGE_VU_BARS", "Vintage Hi-Fi RTA Stacker", "Classic retro digital equalizer stack with peak decay"),
    TUNNEL_VORTEX("TUNNEL_VORTEX", "Acoustic Resonance Tunnel", "Pulsing concentric perspective audio rings"),
    FLUID_RIPPLE("FLUID_RIPPLE", "Harmonic Fluid Ripples", "Multi-frequency sine interference field");

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
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val savedStyleId by settingsManager.visualizerStyle.collectAsState()
    val visualizerMode by settingsManager.visualizerMode.collectAsState()

    // Dynamic resolved style based on mode (MANUAL or AUTO_TIME)
    var resolvedStyleId by remember { mutableStateOf(settingsManager.getEffectiveVisualizerStyle()) }

    // Lightweight periodic check for Auto-Time mode (updates every 30 seconds without recreating audio engine)
    LaunchedEffect(visualizerMode, savedStyleId, isPlaying) {
        while (true) {
            resolvedStyleId = settingsManager.getEffectiveVisualizerStyle()
            delay(30_000L)
        }
    }

    val activeStyle = remember(resolvedStyleId) { VisualizerStyle.fromId(resolvedStyleId) }

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

    // Hardware Visualizer setup - persists across style transitions without recreation
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
        val nextId = allStyles[nextIndex].id
        settingsManager.setVisualizerMode("MANUAL")
        settingsManager.setVisualizerStyle(nextId)
        resolvedStyleId = nextId
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

                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                            )

                            val peak = getPeak(i, mag)
                            val peakY = (height - (height * 0.85f * peak) - 3f).coerceAtLeast(0f)
                            drawCircle(
                                color = accentColor.copy(alpha = 0.9f),
                                radius = (barWidth / 2f).coerceAtMost(2.5.dp.toPx()),
                                center = Offset(x + (barWidth / 2f), peakY)
                            )
                        }
                    }

                    VisualizerStyle.ANALOG_VU_DUAL -> {
                        // 2. Dual Audiophile Analog VU Meters
                        val meterWidth = (width - 16.dp.toPx()) / 2f
                        val meterHeight = height

                        for (ch in 0..1) {
                            val meterX = ch * (meterWidth + 16.dp.toPx())
                            val pivotX = meterX + meterWidth / 2f
                            val pivotY = meterHeight * 1.15f
                            val needleLength = meterHeight * 0.95f

                            val half = barCount / 2
                            val channelMags = if (ch == 0) (0 until half) else (half until barCount)
                            val avgMag = channelMags.map { getMag(it) }.average().toFloat().coerceIn(0.04f, 1f)

                            val angleDeg = -40f + (avgMag * 80f)
                            val angleRad = (angleDeg - 90f) * (PI / 180f)
                            val needleEndX = pivotX + (cos(angleRad) * needleLength).toFloat()
                            val needleEndY = pivotY + (sin(angleRad) * needleLength).toFloat()

                            drawArc(
                                color = secondaryColor.copy(alpha = 0.25f),
                                startAngle = 210f,
                                sweepAngle = 120f,
                                useCenter = false,
                                topLeft = Offset(meterX + 4.dp.toPx(), 6.dp.toPx()),
                                size = Size(meterWidth - 8.dp.toPx(), meterHeight * 1.2f),
                                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            )

                            val isOverload = avgMag > 0.82f
                            val needleColor = if (isOverload) Color(0xFFEF4444) else accentColor
                            drawLine(
                                color = needleColor,
                                start = Offset(pivotX, pivotY),
                                end = Offset(needleEndX, needleEndY),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            drawCircle(
                                color = secondaryColor,
                                radius = 4.dp.toPx(),
                                center = Offset(pivotX, pivotY)
                            )
                        }
                    }

                    VisualizerStyle.RADIAL_ORBIT -> {
                        // 3. Radial Orbit Ring
                        val centerX = width / 2f
                        val centerY = height / 2f
                        val baseRadius = min(width, height) * 0.24f

                        drawCircle(
                            color = secondaryColor.copy(alpha = 0.2f),
                            radius = baseRadius,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val angle = (i.toFloat() / barCount) * (PI * 2).toFloat()
                            val rayLen = (min(width, height) * 0.22f * mag).coerceAtLeast(2f)

                            val startX = centerX + (cos(angle) * baseRadius)
                            val startY = centerY + (sin(angle) * baseRadius)
                            val endX = centerX + (cos(angle) * (baseRadius + rayLen))
                            val endY = centerY + (sin(angle) * (baseRadius + rayLen))

                            drawLine(
                                color = accentColor,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    VisualizerStyle.OSCILLOSCOPE_CRT -> {
                        // 4. Phosphor Oscilloscope
                        val midY = height / 2f
                        val path = Path()
                        val stepX = width / (barCount - 1).coerceAtLeast(1)

                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val waveSample = (sin(fallbackPhase * 2f + (i * 0.5f)) * mag * (height * 0.42f))
                            val x = i * stepX
                            val y = (midY + waveSample).coerceIn(2f, height - 2f)

                            if (i == 0) path.moveTo(x, y)
                            else path.lineTo(x, y)
                        }

                        drawPath(
                            path = path,
                            color = accentColor.copy(alpha = 0.35f),
                            style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = path,
                            color = accentColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    VisualizerStyle.MIRRORED_STEREO -> {
                        // 5. Mirrored Stereo Field
                        val midY = height / 2f
                        val totalGap = width * 0.15f
                        val barSpacing = totalGap / (barCount - 1).coerceAtLeast(1)
                        val barWidth = ((width - totalGap) / barCount).coerceAtLeast(2f)

                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val halfHeight = (height * 0.42f * mag).coerceAtLeast(2f)
                            val x = i * (barWidth + barSpacing)

                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset(x, midY - halfHeight),
                                size = Size(barWidth, halfHeight * 2),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                            )
                        }
                    }

                    VisualizerStyle.FLOATING_PARTICLES -> {
                        // 6. Audio Constellation
                        val particlePoints = mutableListOf<Offset>()
                        val stepX = width / (barCount - 1).coerceAtLeast(1)

                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val x = i * stepX
                            val waveY = (sin(fallbackPhase + (i * 0.7f)) * (height * 0.25f))
                            val y = ((height / 2f) + waveY - (mag * height * 0.35f)).coerceIn(4f, height - 4f)
                            val pt = Offset(x, y)
                            particlePoints.add(pt)

                            val radius = (1.5.dp.toPx() + (mag * 3.5.dp.toPx())).coerceAtMost(5.dp.toPx())
                            drawCircle(
                                color = accentColor,
                                radius = radius,
                                center = pt
                            )
                        }

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
                        // 7. Harmonic Wave Ribbons
                        val midY = height / 2f
                        val stepX = width / (barCount - 1).coerceAtLeast(1)

                        val waveConfigs = listOf(
                            Triple(accentColor, 1.0f, 0.4f),
                            Triple(secondaryColor.copy(alpha = 0.7f), 1.6f, 0.32f),
                            Triple(accentColor.copy(alpha = 0.45f), 2.2f, 0.25f)
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
                                    segFromBottom >= 7 -> Color(0xFFEF4444)
                                    segFromBottom >= 5 -> Color(0xFFF59E0B)
                                    else -> accentColor
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

                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val x = i * stepX
                            val y = height - (height * 0.88f * mag).coerceAtLeast(4f)

                            if (i == 0) {
                                curvePath.moveTo(x, y)
                            } else {
                                val prevX = (i - 1) * stepX
                                val prevMag = getMag(i - 1)
                                val prevY = height - (height * 0.88f * prevMag).coerceAtLeast(4f)
                                val cx = (prevX + x) / 2f
                                curvePath.cubicTo(cx, prevY, cx, y, x, y)
                            }
                        }

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

                            drawLine(
                                color = secondaryColor.copy(alpha = 0.2f),
                                start = Offset(x, height),
                                end = Offset(x, y),
                                strokeWidth = 1.dp.toPx()
                            )

                            drawCircle(
                                color = accentColor,
                                radius = 2.5.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }

                    VisualizerStyle.CIRCULAR_SPECTRUM -> {
                        // 11. 360° Circular Equalizer
                        val centerX = width / 2f
                        val centerY = height / 2f
                        val baseR = min(width, height) * 0.25f

                        drawCircle(
                            color = secondaryColor.copy(alpha = 0.2f),
                            radius = baseR,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        for (i in 0 until barCount) {
                            val mag = getMag(i)
                            val angle = (i.toFloat() / barCount) * (PI * 2).toFloat() - (PI / 2).toFloat()
                            val barLen = (min(width, height) * 0.22f * mag).coerceAtLeast(3f)

                            val startX = centerX + cos(angle) * baseR
                            val startY = centerY + sin(angle) * baseR
                            val endX = centerX + cos(angle) * (baseR + barLen)
                            val endY = centerY + sin(angle) * (baseR + barLen)

                            drawLine(
                                color = accentColor,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 2.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            val peak = getPeak(i, mag)
                            val peakX = centerX + cos(angle) * (baseR + (min(width, height) * 0.22f * peak))
                            val peakY = centerY + sin(angle) * (baseR + (min(width, height) * 0.22f * peak))
                            drawCircle(
                                color = secondaryColor,
                                radius = 1.8.dp.toPx(),
                                center = Offset(peakX, peakY)
                            )
                        }
                    }

                    VisualizerStyle.HEXAGON_PULSE -> {
                        // 12. Harmonic Hexagon Matrix
                        val centerX = width / 2f
                        val centerY = height / 2f
                        val bassEnergy = (getMag(0) + getMag(1) + getMag(2)) / 3f

                        val layerScales = listOf(0.18f, 0.32f, 0.44f)
                        for ((idx, scale) in layerScales.withIndex()) {
                            val radius = (min(width, height) * scale * (1f + (bassEnergy * 0.25f * (idx + 1)))).coerceAtLeast(4f)
                            val hexPath = Path()
                            for (corner in 0..5) {
                                val angle = (corner * 60f) * (PI / 180f).toFloat() + (if (idx % 2 == 1) fallbackPhase * 0.2f else -fallbackPhase * 0.2f)
                                val hx = centerX + cos(angle) * radius
                                val hy = centerY + sin(angle) * radius
                                if (corner == 0) hexPath.moveTo(hx, hy) else hexPath.lineTo(hx, hy)
                            }
                            hexPath.close()

                            val color = if (idx == 0) accentColor else secondaryColor.copy(alpha = if (idx == 1) 0.6f else 0.3f)
                            drawPath(
                                path = hexPath,
                                color = color,
                                style = Stroke(width = (2.5f - (idx * 0.5f)).dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    VisualizerStyle.WATERFALL_BARCODE -> {
                        // 13. Audiophile Barcode Waterfall
                        val stripeCount = (barCount * 1.5f).toInt()
                        val stripeWidth = (width / stripeCount) * 0.65f
                        val stripeGap = (width / stripeCount) * 0.35f
                        val midY = height / 2f

                        for (i in 0 until stripeCount) {
                            val mappedBand = (i * barCount / stripeCount).coerceIn(0, barCount - 1)
                            val mag = getMag(mappedBand)
                            val stripeHeight = (height * 0.85f * mag).coerceAtLeast(3f)
                            val x = i * (stripeWidth + stripeGap) + (stripeGap / 2f)

                            val color = if (i % 2 == 0) accentColor else secondaryColor.copy(alpha = 0.7f)
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(x, midY - (stripeHeight / 2f)),
                                size = Size(stripeWidth, stripeHeight),
                                cornerRadius = CornerRadius(stripeWidth / 2f, stripeWidth / 2f)
                            )
                        }
                    }

                    VisualizerStyle.VINTAGE_VU_BARS -> {
                        // 14. Vintage Hi-Fi RTA Stacker
                        val columns = 16
                        val rows = 7
                        val colW = (width / columns) * 0.75f
                        val colSpacing = (width / columns) * 0.25f
                        val rowH = (height / rows) * 0.7f
                        val rowSpacing = (height / rows) * 0.3f

                        for (c in 0 until columns) {
                            val bandIdx = (c * barCount / columns).coerceIn(0, barCount - 1)
                            val mag = getMag(bandIdx)
                            val activeRows = (mag * rows).toInt().coerceIn(1, rows)
                            val x = c * (colW + colSpacing) + (colSpacing / 2f)

                            for (r in 0 until rows) {
                                val rFromBottom = rows - 1 - r
                                val y = r * (rowH + rowSpacing)
                                val isActive = rFromBottom < activeRows

                                val blockColor = when {
                                    !isActive -> secondaryColor.copy(alpha = 0.1f)
                                    rFromBottom >= 6 -> Color(0xFFEF4444)
                                    rFromBottom >= 4 -> Color(0xFFF59E0B)
                                    else -> accentColor
                                }

                                drawRect(
                                    color = blockColor,
                                    topLeft = Offset(x, y),
                                    size = Size(colW, rowH)
                                )
                            }
                        }
                    }

                    VisualizerStyle.TUNNEL_VORTEX -> {
                        // 15. Acoustic Resonance Tunnel
                        val centerX = width / 2f
                        val centerY = height / 2f
                        val ringCount = 5
                        val bass = (getMag(0) + getMag(1)) / 2f

                        for (r in 1..ringCount) {
                            val scale = r.toFloat() / ringCount
                            val baseRadius = (min(width, height) * 0.44f * scale)
                            val reactiveR = (baseRadius * (1f + (bass * 0.2f * (1f - (scale * 0.5f))))).coerceAtLeast(2f)

                            val alpha = 0.2f + (0.8f * (1f - (scale * 0.6f)))
                            drawCircle(
                                color = accentColor.copy(alpha = alpha.coerceIn(0.15f, 1f)),
                                radius = reactiveR,
                                center = Offset(centerX, centerY),
                                style = Stroke(width = 1.8.dp.toPx())
                            )
                        }

                        // Perspective cross-beams
                        val angles = listOf(30f, 150f, 210f, 330f)
                        val maxR = min(width, height) * 0.44f
                        for (deg in angles) {
                            val rad = deg * (PI / 180f).toFloat()
                            drawLine(
                                color = secondaryColor.copy(alpha = 0.25f),
                                start = Offset(centerX, centerY),
                                end = Offset(centerX + cos(rad) * maxR, centerY + sin(rad) * maxR),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    VisualizerStyle.FLUID_RIPPLE -> {
                        // 16. Harmonic Fluid Ripples
                        val midY = height / 2f
                        val stepX = width / (barCount - 1).coerceAtLeast(1)

                        val waveConfigs = listOf(
                            Triple(accentColor, 1.0f, 0.42f),
                            Triple(secondaryColor.copy(alpha = 0.75f), 1.7f, 0.3f),
                            Triple(accentColor.copy(alpha = 0.4f), 2.5f, 0.22f),
                            Triple(secondaryColor.copy(alpha = 0.3f), 3.2f, 0.15f)
                        )

                        for ((color, speed, amp) in waveConfigs) {
                            val path = Path()
                            for (i in 0 until barCount) {
                                val mag = getMag(i)
                                val x = i * stepX
                                val y = midY + (sin(fallbackPhase * speed + (i * 0.45f)) * mag * height * amp)
                                if (i == 0) path.moveTo(x, y)
                                else {
                                    val prevX = (i - 1) * stepX
                                    val prevY = midY + (sin(fallbackPhase * speed + ((i - 1) * 0.45f)) * getMag(i - 1) * height * amp)
                                    val cx = (prevX + x) / 2f
                                    path.cubicTo(cx, prevY, cx, y, x, y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
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
            val isAutoTime = visualizerMode == "AUTO_TIME"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { cycleNextStyle() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = if (isAutoTime) Icons.Default.Schedule else Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${activeStyle.ordinal + 1}/${VisualizerStyle.entries.size} • ${activeStyle.title}${if (isAutoTime) " (Auto Time)" else ""}",
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
