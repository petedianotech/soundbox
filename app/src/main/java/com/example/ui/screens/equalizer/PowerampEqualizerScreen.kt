package com.example.ui.screens.equalizer

import android.media.audiofx.PresetReverb
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import kotlin.math.abs

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
    val hwBands by viewModel.equalizerHardwareBands.collectAsState()
    val audioSessionId by viewModel.audioSessionId.collectAsState()

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
                            text = "EQUALIZER & DSP",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (equalizerEnabled) colors.accentCyan else colors.textPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accentCyan.copy(alpha = 0.15f))
                                .border(1.dp, colors.accentCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (hwBands > 0) "HW $hwBands-BAND" else "32-BIT FLOAT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
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
                    // Reset to Flat button
                    TextButton(
                        onClick = { viewModel.resetEqualizerToFlat() },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.accentAmber)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "FLAT",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }

                    // Master EQ Switch
                    Switch(
                        checked = equalizerEnabled,
                        onCheckedChange = { viewModel.toggleEqualizer() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.accentCyan,
                            checkedTrackColor = colors.accentCyan.copy(alpha = 0.35f),
                            uncheckedThumbColor = colors.textMuted,
                            uncheckedTrackColor = colors.surfaceVariant
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
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
                .verticalScroll(rememberScrollState())
        ) {
            // Live Hardware DSP Engine Status Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (equalizerEnabled) colors.accentCyan.copy(alpha = 0.1f) else colors.surfaceVariant)
                    .border(1.dp, if (equalizerEnabled) colors.accentCyan.copy(alpha = 0.3f) else colors.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (equalizerEnabled) colors.accentLime else colors.textMuted)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (equalizerEnabled) {
                        if (hwBands > 0) "HARDWARE DSP ENGINE: ACTIVE ($hwBands BANDS)" else "PRECISION DSP ENGINE: ACTIVE"
                    } else "DSP BYPASSED • BIT-PERFECT DIRECT AUDIO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
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
                            fontSize = 8.5.sp
                        ),
                        color = colors.accentAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Studio Presets Carousel
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
                        color = if (isSelected) colors.accentCyan.copy(alpha = 0.22f) else colors.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) colors.accentCyan else colors.border.copy(alpha = 0.5f)
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
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = if (isSelected) colors.accentCyan else colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Frequency Response Spline Visualizer Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(115.dp)
            ) {
                EqCurveCanvas(
                    bandLevels = bandLevels,
                    preamp = preampGain,
                    isEnabled = equalizerEnabled
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section Header: 10-Band Graphic Equalizer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "10-BAND GRAPHIC CONSOLE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "TAP dB TO ZERO • DRAG FADER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp
                    ),
                    color = colors.textMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Precision Tactile Fader Console: Preamp (Master) on left + 10 Frequency bands
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dedicated Master PREAMP Channel
                    Box(
                        modifier = Modifier.padding(start = 6.dp, end = 4.dp)
                    ) {
                        TactileVerticalFader(
                            label = "PRE",
                            subLabel = "GAIN",
                            value = preampGain,
                            onValueChange = { viewModel.setPreampGain(it) },
                            accentColor = colors.accentAmber,
                            isEnabled = equalizerEnabled,
                            faderWidth = 54.dp,
                            trackHeight = 180.dp
                        )
                    }

                    // Vertical Channel Divider
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(1.dp)
                            .height(230.dp)
                            .background(colors.border)
                    )

                    // 10-Band Horizontal Scrollable Console
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bandLevels.forEachIndexed { index, gain ->
                            val label = EQ_BAND_LABELS.getOrElse(index) { "$index" }
                            val subLabel = if (index < 5) "Hz" else "kHz"
                            TactileVerticalFader(
                                label = label,
                                subLabel = subLabel,
                                value = gain,
                                onValueChange = { viewModel.setEqBandLevel(index, it) },
                                accentColor = colors.accentCyan,
                                isEnabled = equalizerEnabled,
                                faderWidth = 54.dp,
                                trackHeight = 180.dp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section Header: Analog Tone & Spatial Controls
            Text(
                text = "ANALOG TONE & SPATIAL STAGE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4-Knob Studio Panel: Bass, Treble, Virtualizer, Balance
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Row 1: Bass Boost & Treble Tone
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
                            knobSize = 96.dp,
                            accentColor = colors.accentLime,
                            subText = "SUB-BASS 55Hz"
                        )

                        PowerampRotaryKnob(
                            value = trebleGain,
                            onValueChange = { viewModel.setTrebleGain(it) },
                            valueRange = -15f..15f,
                            label = "TREBLE AIR",
                            displayValue = "${if (trebleGain > 0) "+" else ""}${String.format(Locale.US, "%.1f", trebleGain)} dB",
                            knobSize = 96.dp,
                            accentColor = colors.accentCyan,
                            subText = "HIGH-AIR 12kHz"
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Row 2: Stereo Expand & Audio Balance
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
                            knobSize = 92.dp,
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
                            knobSize = 92.dp,
                            accentColor = colors.accentAmber,
                            subText = "L <-> R"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section Header: Environmental Reverb Acoustics
            Text(
                text = "ENVIRONMENTAL REVERB ACOUSTICS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            val reverbOptions = listOf(
                "OFF" to PresetReverb.PRESET_NONE.toInt(),
                "STUDIO ROOM" to PresetReverb.PRESET_SMALLROOM.toInt(),
                "CHAMBER" to PresetReverb.PRESET_MEDIUMROOM.toInt(),
                "CONCERT HALL" to PresetReverb.PRESET_LARGEROOM.toInt(),
                "ARENA" to PresetReverb.PRESET_MEDIUMHALL.toInt(),
                "CATHEDRAL" to PresetReverb.PRESET_LARGEHALL.toInt(),
                "PLATE" to PresetReverb.PRESET_PLATE.toInt()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reverbOptions.forEach { (title, id) ->
                    val isSelected = reverbPreset == id
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) colors.accentCyan.copy(alpha = 0.22f) else colors.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) colors.accentCyan else colors.border
                        ),
                        modifier = Modifier.clickable { viewModel.setReverbPreset(id) }
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = if (isSelected) colors.accentCyan else colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

/**
 * Professional Studio Tactile Vertical Fader:
 * Substantial capacitive fader cap (40dp x 26dp), illuminated position bar,
 * center 0dB detent notch, real-time vertical drag, and instant tap-to-zero.
 */
@Composable
fun TactileVerticalFader(
    label: String,
    subLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: Color,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    faderWidth: Dp = 54.dp,
    trackHeight: Dp = 180.dp,
    valueRange: ClosedFloatingPointRange<Float> = -15f..15f
) {
    val colors = SoundboxTheme.colors
    val density = LocalDensity.current
    val trackHeightPx = with(density) { trackHeight.toPx() }
    val capHeightPx = with(density) { 34.dp.toPx() }
    val usableHeightPx = (trackHeightPx - capHeightPx).coerceAtLeast(1f)

    val minVal = valueRange.start
    val maxVal = valueRange.endInclusive
    val normalizedFraction = ((value - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
    val zeroFraction = ((0f - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)

    val currentOnValueChange by rememberUpdatedState(onValueChange)

    Column(
        modifier = modifier.width(faderWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Value indicator pill - Tap to reset to 0.0 dB
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isEnabled && abs(value) > 0.1f) accentColor.copy(alpha = 0.16f) else colors.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(
                0.75.dp,
                if (isEnabled && abs(value) > 0.1f) accentColor.copy(alpha = 0.5f) else colors.border
            ),
            modifier = Modifier.clickable(enabled = isEnabled) { currentOnValueChange(0f) }
        ) {
            Text(
                text = "${if (value > 0.05f) "+" else ""}${String.format(Locale.US, "%.1f", value)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = if (isEnabled) {
                    if (abs(value) > 0.1f) accentColor else colors.textPrimary
                } else colors.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // Quick +0.5 dB step button
        Box(
            modifier = Modifier
                .padding(vertical = 3.dp)
                .size(24.dp, 16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isEnabled) colors.surfaceVariant else Color.Transparent)
                .clickable(enabled = isEnabled) {
                    val newVal = (value + 0.5f).coerceIn(minVal, maxVal)
                    currentOnValueChange(newVal)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Black),
                color = if (isEnabled) accentColor.copy(alpha = 0.85f) else colors.textMuted
            )
        }

        // Fader Channel Interactive Box: Direct 1:1 finger tracking, cannot reverse or stutter
        Box(
            modifier = Modifier
                .width(faderWidth)
                .height(trackHeight)
                .pointerInput(isEnabled, usableHeightPx, minVal, maxVal) {
                    if (!isEnabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val clampedY = (offset.y - capHeightPx / 2f).coerceIn(0f, usableHeightPx)
                            val fraction = 1f - (clampedY / usableHeightPx)
                            val newVal = (minVal + fraction * (maxVal - minVal)).coerceIn(minVal, maxVal)
                            currentOnValueChange(newVal)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val clampedY = (change.position.y - capHeightPx / 2f).coerceIn(0f, usableHeightPx)
                            val fraction = 1f - (clampedY / usableHeightPx)
                            val newVal = (minVal + fraction * (maxVal - minVal)).coerceIn(minVal, maxVal)
                            currentOnValueChange(newVal)
                        }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            // Draw Recessed Track, Center 0dB Detent, Scale Ticks, and Active Level Fill
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val trackWidth = 7.dp.toPx()
                val trackLeft = (w - trackWidth) / 2f

                val recessedColor = if (colors.isDark) Color(0xFF0A0F17) else Color(0xFFE2E8F0)
                val trackBorderColor = if (colors.isDark) Color(0xFF1B2433) else Color(0xFFCBD5E1)

                // Recessed track background groove
                drawRoundRect(
                    color = recessedColor,
                    topLeft = Offset(trackLeft, capHeightPx / 2f),
                    size = Size(trackWidth, usableHeightPx),
                    cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
                )

                // Track subtle inner border
                drawRoundRect(
                    color = trackBorderColor,
                    topLeft = Offset(trackLeft, capHeightPx / 2f),
                    size = Size(trackWidth, usableHeightPx),
                    cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )

                val zeroY = (1f - zeroFraction) * usableHeightPx + capHeightPx / 2f
                val currentCapCenterY = (1f - normalizedFraction) * usableHeightPx + capHeightPx / 2f

                // Active Level Glow Fill from 0dB Center to Current Cap
                if (isEnabled && abs(currentCapCenterY - zeroY) > 1f) {
                    val fillTop = minOf(zeroY, currentCapCenterY)
                    val fillHeight = abs(currentCapCenterY - zeroY)
                    val fillColor = if (value >= 0) accentColor.copy(alpha = 0.85f) else accentColor.copy(alpha = 0.45f)
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(trackLeft + 1.dp.toPx(), fillTop),
                        size = Size(trackWidth - 2.dp.toPx(), fillHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                // 0dB Center Detent horizontal line ticks
                val tickWidth = 6.dp.toPx()
                val detentColor = if (isEnabled) colors.textSecondary else colors.textMuted
                drawLine(
                    color = detentColor,
                    start = Offset(trackLeft - tickWidth - 1.dp.toPx(), zeroY),
                    end = Offset(trackLeft - 1.dp.toPx(), zeroY),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = detentColor,
                    start = Offset(trackLeft + trackWidth + 1.dp.toPx(), zeroY),
                    end = Offset(trackLeft + trackWidth + tickWidth + 1.dp.toPx(), zeroY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // +10dB and -10dB subtle guide ticks
                val guideTickColor = colors.border
                val tenPlusFrac = ((10f - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                val tenPlusY = (1f - tenPlusFrac) * usableHeightPx + capHeightPx / 2f
                drawLine(
                    color = guideTickColor,
                    start = Offset(trackLeft - 4.dp.toPx(), tenPlusY),
                    end = Offset(trackLeft - 1.dp.toPx(), tenPlusY),
                    strokeWidth = 1.dp.toPx()
                )

                val tenMinusFrac = ((-10f - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                val tenMinusY = (1f - tenMinusFrac) * usableHeightPx + capHeightPx / 2f
                drawLine(
                    color = guideTickColor,
                    start = Offset(trackLeft - 4.dp.toPx(), tenMinusY),
                    end = Offset(trackLeft - 1.dp.toPx(), tenMinusY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Tactile Fader Cap (Substantial thumb knob: 46dp x 34dp)
            val capOffsetY = with(density) {
                ((1f - normalizedFraction) * usableHeightPx).toDp()
            }

            val capGradient = if (colors.isDark) {
                if (isEnabled) {
                    listOf(Color(0xFF425164), Color(0xFF25303E), Color(0xFF141C26))
                } else {
                    listOf(Color(0xFF242E3B), Color(0xFF18202A), Color(0xFF10151C))
                }
            } else {
                if (isEnabled) {
                    listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                } else {
                    listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                }
            }
            val capBorderColor = if (colors.isDark) {
                if (isEnabled) Color(0xFF5E7492) else Color(0xFF2A3442)
            } else {
                if (isEnabled) Color(0xFF94A3B8) else Color(0xFFCBD5E1)
            }
            val gripGrooveColor = if (colors.isDark) {
                if (isEnabled) Color(0xFF6B82A1) else Color(0xFF2E3846)
            } else {
                if (isEnabled) Color(0xFFCBD5E1) else Color(0xFFE2E8F0)
            }

            Surface(
                modifier = Modifier
                    .offset(y = capOffsetY)
                    .width(46.dp)
                    .height(34.dp)
                    .shadow(elevation = if (colors.isDark) 8.dp else 4.dp, shape = RoundedCornerShape(6.dp)),
                shape = RoundedCornerShape(6.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(colors = capGradient),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = capBorderColor,
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Tactile horizontal grip grooves
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(1.dp)
                                .background(gripGrooveColor)
                        )

                        // Center illuminated neon indicator line
                        Box(
                            modifier = Modifier
                                .width(30.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(if (isEnabled) accentColor else colors.textMuted)
                        )

                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(1.dp)
                                .background(gripGrooveColor)
                        )
                    }
                }
            }
        }

        // Quick -0.5 dB step button
        Box(
            modifier = Modifier
                .padding(vertical = 3.dp)
                .size(24.dp, 16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isEnabled) colors.surfaceVariant else Color.Transparent)
                .clickable(enabled = isEnabled) {
                    val newVal = (value - 0.5f).coerceIn(minVal, maxVal)
                    currentOnValueChange(newVal)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Black),
                color = if (isEnabled) accentColor.copy(alpha = 0.85f) else colors.textMuted
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Frequency Band & Sub-label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            ),
            color = if (isEnabled) colors.textPrimary else colors.textMuted
        )
        Text(
            text = subLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = if (isEnabled) colors.textSecondary else colors.textMuted
        )
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
    val colors = SoundboxTheme.colors
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val maxGain = 15f

        // Draw 0 dB reference line
        drawLine(
            color = colors.border,
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 1.2.dp.toPx()
        )

        // Draw +10dB and -10dB grid guides
        val y10Plus = centerY - (10f / maxGain) * (h / 2f - 4.dp.toPx())
        drawLine(
            color = colors.borderSubtle,
            start = Offset(0f, y10Plus),
            end = Offset(w, y10Plus),
            strokeWidth = 1.dp.toPx()
        )

        val y10Minus = centerY - (-10f / maxGain) * (h / 2f - 4.dp.toPx())
        drawLine(
            color = colors.borderSubtle,
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

            // Draw translucent cyan gradient underneath
            if (isEnabled) {
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.accentCyan.copy(alpha = 0.25f),
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
                color = if (isEnabled) colors.accentCyan else colors.textMuted,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw frequency node dots
            points.forEach { pt ->
                drawCircle(
                    color = if (isEnabled) colors.accentCyan else colors.textMuted,
                    radius = 3.dp.toPx(),
                    center = pt
                )
                if (isEnabled) {
                    drawCircle(
                        color = if (colors.isDark) Color.White else colors.surface,
                        radius = 1.5.dp.toPx(),
                        center = pt
                    )
                }
            }
        }
    }
}
