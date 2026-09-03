package com.example.ui.screens.equalizer

import android.media.audiofx.PresetReverb
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.poweramp.PowerampRotaryKnob
import com.example.ui.theme.Poweramp_Amber
import com.example.ui.theme.Poweramp_Cyan
import com.example.ui.theme.Poweramp_Lime
import com.example.ui.theme.Poweramp_Purple
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel
import java.util.Locale

data class PowerampPresetItem(
    val name: String,
    val gains: List<Float>,
    val bass: Int = 300,
    val treble: Float = 0f,
    val virtualizer: Int = 0,
    val reverb: Int = PresetReverb.PRESET_NONE.toInt()
)

val POWERAMP_PRESETS = listOf(
    PowerampPresetItem("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), 0, 0f, 0, PresetReverb.PRESET_NONE.toInt()),
    PowerampPresetItem("Bass Extreme", listOf(11f, 9f, 6f, 3f, 0f, 0f, 1f, 2f, 4f, 5f), 850, 2f, 200, PresetReverb.PRESET_NONE.toInt()),
    PowerampPresetItem("Rock & Metal", listOf(7f, 5f, 2f, -1f, -2f, 1f, 4f, 7f, 8f, 7f), 450, 4f, 150, PresetReverb.PRESET_SMALLROOM.toInt()),
    PowerampPresetItem("Electronic", listOf(8f, 7f, 3f, 0f, -2f, 2f, 5f, 7f, 8f, 9f), 650, 3f, 300, PresetReverb.PRESET_MEDIUMROOM.toInt()),
    PowerampPresetItem("Techno Pulse", listOf(9f, 8f, 4f, 0f, -1f, 1f, 4f, 6f, 8f, 9f), 700, 2.5f, 250, PresetReverb.PRESET_MEDIUMHALL.toInt()),
    PowerampPresetItem("Acoustic Live", listOf(4f, 3f, 1f, 2f, 3f, 3f, 4f, 5f, 5f, 4f), 200, 1.5f, 100, PresetReverb.PRESET_LARGEROOM.toInt()),
    PowerampPresetItem("Vocal Clarity", listOf(-3f, -2f, 0f, 4f, 7f, 7f, 5f, 3f, 1f, 0f), 100, 1f, 0, PresetReverb.PRESET_NONE.toInt()),
    PowerampPresetItem("Treble Sparkle", listOf(-2f, -1f, 0f, 0f, 2f, 4f, 7f, 10f, 11f, 11f), 150, 6f, 200, PresetReverb.PRESET_PLATE.toInt()),
    PowerampPresetItem("Hip-Hop Punch", listOf(10f, 9f, 6f, 2f, -1f, -1f, 2f, 5f, 7f, 8f), 800, 1.5f, 150, PresetReverb.PRESET_NONE.toInt()),
    PowerampPresetItem("Jazz Lounge", listOf(5f, 4f, 2f, 2f, 0f, 0f, 2f, 3f, 4f, 5f), 300, 2f, 100, PresetReverb.PRESET_SMALLROOM.toInt()),
    PowerampPresetItem("Club Atmosphere", listOf(6f, 5f, 3f, 1f, 0f, 2f, 4f, 5f, 6f, 6f), 500, 3f, 400, PresetReverb.PRESET_LARGEHALL.toInt())
)

val EQ_BAND_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerampEqualizerScreen(
    viewModel: MusicViewModel,
    onNavigateBack: () -> Unit
) {
    val equalizerEnabled by viewModel.equalizerEnabled.collectAsState()
    val bandLevels by viewModel.eqBandLevels.collectAsState()
    val preampGain by viewModel.preampGain.collectAsState()
    val bassBoostStrength by viewModel.bassBoostStrength.collectAsState()
    val trebleGain by viewModel.trebleGain.collectAsState()
    val virtualizerStrength by viewModel.virtualizerStrength.collectAsState()
    val audioBalance by viewModel.audioBalance.collectAsState()
    val reverbPreset by viewModel.reverbPreset.collectAsState()
    val currentPresetName by viewModel.currentPresetName.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val playbackPitch by viewModel.playbackPitch.collectAsState()
    val hwBands by viewModel.equalizerHardwareBands.collectAsState()
    val eqStatus by viewModel.equalizerStatus.collectAsState()
    val audioSessionId by viewModel.audioSessionId.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Equalizer, 1: Tone & Space, 2: Reverb & Tempo

    val colors = SoundboxTheme.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "EQUALIZER / DSP",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.accentCyan
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accentCyan.copy(alpha = 0.15f))
                                .border(1.dp, colors.accentCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (hwBands > 0) "HW $hwBands-BAND" else "32-BIT FLOAT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 7.5.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.accentCyan
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    // Poweramp Master EQ Switch
                    Switch(
                        checked = equalizerEnabled,
                        onCheckedChange = { viewModel.toggleEqualizer() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.accentCyan,
                            checkedTrackColor = colors.accentCyan.copy(alpha = 0.35f),
                            uncheckedThumbColor = colors.textMuted,
                            uncheckedTrackColor = colors.surfaceVariant
                        )
                    )
                    IconButton(onClick = { viewModel.resetEqualizerToFlat() }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset EQ", tint = colors.accentAmber)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.topBarBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(colors.background)
        ) {
            // Live Hardware DSP Engine Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (equalizerEnabled) colors.accentCyan.copy(alpha = 0.12f) else colors.surfaceVariant)
                    .border(1.dp, if (equalizerEnabled) colors.accentCyan.copy(alpha = 0.35f) else colors.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (equalizerEnabled) colors.accentLime else colors.textMuted)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (equalizerEnabled) {
                        if (hwBands > 0) "HARDWARE DSP: ACTIVE ($hwBands BANDS)" else "HARDWARE DSP: READY"
                    } else "DSP BYPASSED (DIRECT AUDIO PATH)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (equalizerEnabled) colors.accentCyan else colors.textMuted,
                    modifier = Modifier.weight(1f)
                )
                if (equalizerEnabled && audioSessionId > 0) {
                    Text(
                        text = "SESSION #$audioSessionId",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = colors.accentAmber
                    )
                }
            }
            // Poweramp Sub-tabs selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("GRAPHIC EQ", "TONE & SPACE", "REVERB & TEMPO").forEachIndexed { index, label ->
                    val isSelected = activeTab == index
                    val tabColor by animateColorAsState(
                        if (isSelected) colors.accentCyan else colors.textMuted,
                        label = "tabColor"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.surfaceElevated else Color.Transparent)
                            .clickable { activeTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = tabColor
                        )
                    }
                }
            }

            // Poweramp Presets Scrollable Chip Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                POWERAMP_PRESETS.forEach { preset ->
                    val isSelected = currentPresetName == preset.name
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) colors.accentCyan.copy(alpha = 0.2f) else colors.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) colors.accentCyan else colors.border
                        ),
                        modifier = Modifier.clickable {
                            viewModel.applyPowerampPreset(
                                presetName = preset.name,
                                bandGains = preset.gains,
                                bassBoost = preset.bass,
                                treble = preset.treble,
                                virtualizer = preset.virtualizer,
                                reverb = preset.reverb
                            )
                        }
                    ) {
                        Text(
                            text = preset.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (isSelected) colors.accentCyan else colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            when (activeTab) {
                0 -> {
                    // TAB 0: 10-Band Graphic Equalizer with Spline Curve
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Poweramp EQ Spline Curve Visualizer
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F1521),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1C283A)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            EqCurveCanvas(
                                bandLevels = bandLevels,
                                preamp = preampGain,
                                isEnabled = equalizerEnabled
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Preamp + 10 Faders Container
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Master Preamp Vertical Slider
                            VerticalEqFader(
                                label = "PRE",
                                value = preampGain,
                                onValueChange = { viewModel.setPreampGain(it) },
                                accentColor = Poweramp_Amber,
                                isEnabled = equalizerEnabled,
                                modifier = Modifier.weight(1f)
                            )

                            VerticalDivider(
                                color = Color(0xFF1F293A),
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .fillMaxHeight()
                            )

                            // 10 Frequency Faders
                            bandLevels.forEachIndexed { index, gain ->
                                val label = EQ_BAND_LABELS.getOrElse(index) { "$index" }
                                VerticalEqFader(
                                    label = label,
                                    value = gain,
                                    onValueChange = { viewModel.setEqBandLevel(index, it) },
                                    accentColor = Poweramp_Cyan,
                                    isEnabled = equalizerEnabled,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
                1 -> {
                    // TAB 1: Tone & Space (Rotary Knobs for Bass, Treble, Stereo Virtualizer, Balance)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TONE & DYNAMICS ENGINE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Poweramp_Amber,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Row 1: Bass & Treble Rotary Knobs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PowerampRotaryKnob(
                                value = bassBoostStrength.toFloat(),
                                onValueChange = { viewModel.setBassBoost(it.toInt()) },
                                valueRange = 0f..1000f,
                                label = "BASS BOOST",
                                displayValue = if (bassBoostStrength > 0) "+${bassBoostStrength / 10}%" else "0%",
                                knobSize = 110.dp,
                                accentColor = Poweramp_Lime,
                                subText = "SUB-BASS 55Hz"
                            )

                            PowerampRotaryKnob(
                                value = trebleGain,
                                onValueChange = { viewModel.setTrebleGain(it) },
                                valueRange = -15f..15f,
                                label = "TREBLE TONE",
                                displayValue = "${if (trebleGain > 0) "+" else ""}${String.format(Locale.US, "%.1f", trebleGain)} dB",
                                knobSize = 110.dp,
                                accentColor = Poweramp_Cyan,
                                subText = "HIGH-AIR 12kHz"
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Row 2: Stereo Virtualizer & Audio Balance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PowerampRotaryKnob(
                                value = virtualizerStrength.toFloat(),
                                onValueChange = { viewModel.setVirtualizerStrength(it.toInt()) },
                                valueRange = 0f..1000f,
                                label = "STEREO EXPAND",
                                displayValue = "${virtualizerStrength / 10}%",
                                knobSize = 100.dp,
                                accentColor = Poweramp_Purple,
                                subText = "3D SPATIAL"
                            )

                            PowerampRotaryKnob(
                                value = audioBalance,
                                onValueChange = { viewModel.setAudioBalance(it) },
                                valueRange = -1f..1f,
                                label = "BALANCE",
                                displayValue = when {
                                    audioBalance < -0.05f -> "L ${(-audioBalance * 100).toInt()}%"
                                    audioBalance > 0.05f -> "R ${(audioBalance * 100).toInt()}%"
                                    else -> "CENTER"
                                },
                                knobSize = 100.dp,
                                accentColor = Poweramp_Amber,
                                subText = "L <-> R"
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                2 -> {
                    // TAB 2: Reverb & Tempo (Environmental Reverb, Speed, Pitch)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "REVERBERATION ENVIRONMENT",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Poweramp_Cyan,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Reverb preset buttons
                        val reverbOptions = listOf(
                            "OFF" to PresetReverb.PRESET_NONE.toInt(),
                            "STUDIO ROOM" to PresetReverb.PRESET_SMALLROOM.toInt(),
                            "CHAMBER" to PresetReverb.PRESET_MEDIUMROOM.toInt(),
                            "CONCERT HALL" to PresetReverb.PRESET_LARGEROOM.toInt(),
                            "ARENA HALL" to PresetReverb.PRESET_MEDIUMHALL.toInt(),
                            "CATHEDRAL" to PresetReverb.PRESET_LARGEHALL.toInt(),
                            "PLATE REVERB" to PresetReverb.PRESET_PLATE.toInt()
                        )

                        reverbOptions.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { (title, id) ->
                                    val isSelected = reverbPreset == id
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Poweramp_Cyan.copy(alpha = 0.2f) else Color(0xFF131A26),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) Poweramp_Cyan else Color(0xFF243042)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.setReverbPreset(id) }
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 0.5.sp
                                            ),
                                            color = if (isSelected) Poweramp_Cyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "TEMPO & PITCH DYNAMICS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Poweramp_Amber,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PowerampRotaryKnob(
                                value = playbackSpeed,
                                onValueChange = { viewModel.setPlaybackRate(it, playbackPitch) },
                                valueRange = 0.5f..2.0f,
                                label = "PLAY SPEED",
                                displayValue = "${String.format(Locale.US, "%.2f", playbackSpeed)}x",
                                knobSize = 100.dp,
                                accentColor = Poweramp_Cyan,
                                subText = "0.5x - 2.0x"
                            )

                            PowerampRotaryKnob(
                                value = playbackPitch,
                                onValueChange = { viewModel.setPlaybackRate(playbackSpeed, it) },
                                valueRange = 0.5f..2.0f,
                                label = "AUDIO PITCH",
                                displayValue = "${String.format(Locale.US, "%.2f", playbackPitch)}x",
                                knobSize = 100.dp,
                                accentColor = Poweramp_Amber,
                                subText = "KEY ADJUST"
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/**
 * Spline Frequency Response Curve rendered on top of the EQ
 */
@Composable
private fun EqCurveCanvas(
    bandLevels: List<Float>,
    preamp: Float,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val maxGain = 15f

        // Draw 0 dB reference line
        drawLine(
            color = Color(0xFF2C384C),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 1.dp.toPx()
        )

        // Draw +10dB and -10dB grid guides
        val y10Plus = centerY - (10f / maxGain) * (h / 2f - 4.dp.toPx())
        val y10Minus = centerY - (-10f / maxGain) * (h / 2f - 4.dp.toPx())
        drawLine(
            color = Color(0xFF1A2230),
            start = Offset(0f, y10Plus),
            end = Offset(w, y10Plus),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color(0xFF1A2230),
            start = Offset(0f, y10Minus),
            end = Offset(w, y10Minus),
            strokeWidth = 1.dp.toPx()
        )

        if (bandLevels.isNotEmpty()) {
            val totalPoints = bandLevels.size
            val stepX = w / (totalPoints - 1).coerceAtLeast(1)

            val points = mutableListOf<Offset>()
            for (i in 0 until totalPoints) {
                val gain = (bandLevels[i] + preamp).coerceIn(-maxGain, maxGain)
                val x = i * stepX
                val y = if (isEnabled) centerY - (gain / maxGain) * (h / 2f - 6.dp.toPx()) else centerY
                points.add(Offset(x, y))
            }

            // Build smooth Bezier Curve
            val path = Path()
            val fillPath = Path()
            path.moveTo(points[0].x, points[0].y)
            fillPath.moveTo(points[0].x, centerY)
            fillPath.lineTo(points[0].x, points[0].y)

            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }

            fillPath.lineTo(points.last().x, centerY)
            fillPath.close()

            // Draw translucent neon gradient underneath
            if (isEnabled) {
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Poweramp_Cyan.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h
                    )
                )
            }

            // Draw glowing spline line
            drawPath(
                path = path,
                color = if (isEnabled) Poweramp_Cyan else Color(0xFF4C5D75),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw nodes
            points.forEach { pt ->
                drawCircle(
                    color = if (isEnabled) Poweramp_Cyan else Color(0xFF4C5D75),
                    radius = 3.dp.toPx(),
                    center = pt
                )
                if (isEnabled) {
                    drawCircle(
                        color = Color.White,
                        radius = 1.5.dp.toPx(),
                        center = pt
                    )
                }
            }
        }
    }
}

/**
 * Vertical Equalizer Fader with dB indicator and tactile drag
 */
@Composable
private fun VerticalEqFader(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: Color,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Value text
        Text(
            text = "${if (value > 0) "+" else ""}${value.toInt()}dB",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = if (isEnabled) accentColor else Color(0xFF5A687D)
        )

        // Custom vertical slider track
        Box(
            modifier = Modifier
                .weight(1f)
                .width(28.dp)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = -15f..15f,
                enabled = isEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = if (isEnabled) accentColor else Color(0xFF4A5568),
                    activeTrackColor = if (isEnabled) accentColor else Color(0xFF2D3748),
                    inactiveTrackColor = Color(0xFF161E2A)
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(180.dp)
                    .rotate(-90f)
            )
        }

        // Frequency band label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            ),
            color = if (isEnabled) Color(0xFFC5D1E0) else Color(0xFF5A687D)
        )
    }
}
