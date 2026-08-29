package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun EqualizerPanel(
    enabled: Boolean,
    preset: String,
    bands: List<Int>,
    bassBoost: Int,
    virtualizer: Int,
    onToggleEnabled: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onBandLevelChanged: (Int, Int) -> Unit,
    onBassBoostChanged: (Int) -> Unit,
    onVirtualizerChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val bandLabels = remember { listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz") }
    val presetList = remember {
        listOf("Flat", "Bass Booster", "Rock", "Pop", "Jazz", "Classical", "Vocal Booster", "Treble Booster", "Bass Reducer", "Heavy Metal", "Acoustic")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header: Title & Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Audio Equalizer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (enabled) "5-Band Master Processing Active" else "Equalizer Bypassed",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            AnimatedVisibility(
                visible = enabled,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    // Preset Selector Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PRESET PROFILE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(presetList) { item ->
                                val isSelected = preset == item
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onPresetSelected(item) },
                                    label = { Text(item, style = MaterialTheme.typography.bodyMedium) },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                )
                            }
                        }
                    }

                    // Professional Frequency Band Controls Rack
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Frequency Curve Canvas
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val surfaceColor = MaterialTheme.colorScheme.surface
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val width = size.width
                                    val height = size.height
                                    val step = width / (bandLabels.size - 1).coerceAtLeast(1)

                                    val points = (0 until bandLabels.size).map { idx ->
                                        val levelMb = bands.getOrElse(idx) { 0 }
                                        // levelMb ranges from -1500 to +1500
                                        val norm = ((1500 - levelMb) / 3000f).coerceIn(0f, 1f)
                                        Offset(idx * step, norm * height)
                                    }

                                    // Draw reference center grid line (0dB)
                                    drawLine(
                                        color = primaryColor.copy(alpha = 0.15f),
                                        start = Offset(0f, height / 2f),
                                        end = Offset(width, height / 2f),
                                        strokeWidth = 1.dp.toPx()
                                    )

                                    // Draw smooth frequency curve path
                                    if (points.size > 1) {
                                        val path = Path().apply {
                                            moveTo(points[0].x, points[0].y)
                                            for (i in 0 until points.size - 1) {
                                                val p1 = points[i]
                                                val p2 = points[i + 1]
                                                val controlX = (p1.x + p2.x) / 2f
                                                cubicTo(controlX, p1.y, controlX, p2.y, p2.x, p2.y)
                                            }
                                        }

                                        val filledPath = Path().apply {
                                            addPath(path)
                                            lineTo(width, height)
                                            lineTo(0f, height)
                                            close()
                                        }

                                        drawPath(
                                            path = filledPath,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    primaryColor.copy(alpha = 0.35f),
                                                    Color.Transparent
                                                )
                                            )
                                        )

                                        drawPath(
                                            path = path,
                                            color = primaryColor,
                                            style = Stroke(width = 2.5.dp.toPx())
                                        )

                                        points.forEach { pt ->
                                            drawCircle(
                                                color = primaryColor,
                                                radius = 4.dp.toPx(),
                                                center = pt
                                            )
                                        }
                                    }
                                }
                            }

                            // Vertical Sliders Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                bandLabels.forEachIndexed { index, label ->
                                    val levelMb = bands.getOrElse(index) { 0 }
                                    VerticalBandControl(
                                        freqLabel = label,
                                        levelMb = levelMb,
                                        onLevelChange = { newLevel ->
                                            onBandLevelChanged(index, newLevel)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Sound Effects: Bass Boost & 3D Virtualizer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Bass Boost Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Bass Boost",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        "${bassBoost / 10}%",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                SleekCompactSlider(
                                    value = bassBoost.toFloat(),
                                    valueRange = 0f..1000f,
                                    onValueChange = { onBassBoostChanged(it.toInt()) }
                                )
                            }
                        }

                        // Virtualizer Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "3D Spatial",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        "${virtualizer / 10}%",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                SleekCompactSlider(
                                    value = virtualizer.toFloat(),
                                    valueRange = 0f..1000f,
                                    onValueChange = { onVirtualizerChanged(it.toInt()) }
                                )
                            }
                        }
                    }

                    // Reset Profile Button
                    OutlinedButton(
                        onClick = { onPresetSelected("Flat") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Equalizer to Flat")
                    }
                }
            }
        }
    }
}

/**
 * Custom Vertical Band Slider for Studio Equalizer.
 * Designed with a slim, professional track and compact horizontal thumb.
 */
@Composable
fun VerticalBandControl(
    freqLabel: String,
    levelMb: Int, // -1500 to +1500
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dbValue = levelMb / 100f
    val dbText = if (dbValue > 0) String.format(Locale.US, "+%.1f", dbValue) else String.format(Locale.US, "%.1f", dbValue)

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeTrackColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Gain text badge
        Text(
            text = dbText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (levelMb != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Custom Vertical Track Box
        var containerHeightPx by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .width(36.dp)
                .height(130.dp)
                .onGloballyPositioned { containerHeightPx = it.size.height.toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (containerHeightPx > 0) {
                            val norm = 1f - (offset.y / containerHeightPx).coerceIn(0f, 1f)
                            val newMb = (norm * 3000 - 1500).toInt()
                            onLevelChange(newMb)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (containerHeightPx > 0) {
                            val norm = 1f - (change.position.y / containerHeightPx).coerceIn(0f, 1f)
                            val newMb = (norm * 3000 - 1500).toInt()
                            onLevelChange(newMb)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val trackWidth = 5.dp.toPx()
                val centerX = width / 2f
                val centerY = height / 2f

                // Normalized position: 0 (bottom, -1500) to 1 (top, +1500)
                val normLevel = ((levelMb + 1500) / 3000f).coerceIn(0f, 1f)
                val thumbY = height * (1f - normLevel)

                // Background track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(centerX - trackWidth / 2f, 0f),
                    size = Size(trackWidth, height),
                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                )

                // Reference 0dB line
                drawLine(
                    color = activeTrackColor.copy(alpha = 0.3f),
                    start = Offset(centerX - 8.dp.toPx(), centerY),
                    end = Offset(centerX + 8.dp.toPx(), centerY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Active level line from 0dB center to current thumb position
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(centerX - trackWidth / 2f, minOf(centerY, thumbY)),
                    size = Size(trackWidth, kotlin.math.abs(centerY - thumbY).coerceAtLeast(2.dp.toPx())),
                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                )

                // Sleek, slim horizontal thumb bar (24dp wide, 6dp high)
                val thumbWidth = 22.dp.toPx()
                val thumbHeight = 6.dp.toPx()
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(centerX - thumbWidth / 2f, thumbY - thumbHeight / 2f),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }

        // Frequency Label
        Text(
            text = freqLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Custom Sleek Horizontal Slider replacing the bulky standard Compose Slider.
 * Features a thin 4dp track and a compact, professional 10dp thumb knob.
 */
@Composable
fun SleekCompactSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onGloballyPositioned { widthPx = it.size.width.toFloat() }
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    if (widthPx > 0) {
                        val fraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (widthPx > 0) {
                        val fraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val w = size.width
            val centerY = h / 2f
            val trackHeight = 4.dp.toPx()

            val norm = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val thumbX = w * norm

            // Background Track
            drawRoundRect(
                color = trackBgColor,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(w, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )

            // Active Track
            if (thumbX > 0) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(thumbX, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )
            }

            // Compact Professional Thumb (10dp diameter with subtle center dot)
            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
        }
    }
}
