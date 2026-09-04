package com.example.ui.components.poweramp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Poweramp_Amber
import com.example.ui.theme.Poweramp_Cyan
import com.example.ui.theme.Poweramp_Knob_Cap
import com.example.ui.theme.Poweramp_Knob_Rim
import com.example.ui.theme.SoundboxTheme
import kotlin.math.*

/**
 * Authentic rotary knob control with radial ticks,
 * metallic bevels, glowing value arc, and tactile drag interaction.
 */
@Composable
fun PowerampRotaryKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    label: String = "",
    displayValue: String = "",
    knobSize: Dp = 80.dp,
    accentColor: Color = SoundboxTheme.colors.accentCyan,
    subText: String? = null
) {
    val colors = SoundboxTheme.colors
    val isDark = colors.isDark

    val tickInactiveColor = if (isDark) Color(0xFF2C384C) else Color(0xFFCBD5E1)
    val inactiveTrackColor = if (isDark) Color(0xFF161E2B) else Color(0xFFE2E8F0)
    val capRimColor = if (isDark) Poweramp_Knob_Rim else Color(0xFFE2E8F0)
    val capBodyColor = if (isDark) Poweramp_Knob_Cap else Color(0xFFF1F5F9)
    val capDarkEdge = if (isDark) Color(0xFF0C1017) else Color(0xFFCBD5E1)
    val capBorderColor = if (isDark) Color(0xFF3B4A61) else Color(0xFF94A3B8)
    val capGrooveColor = if (isDark) Color(0xFF0F151E) else Color(0xFFE2E8F0)

    val normalizedValue = remember(value, valueRange) {
        ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    }

    // Sweep angle: from -140 deg (min) to +140 deg (max) -> 280 deg total sweep
    val startAngle = 130f
    val sweepAngleTotal = 280f
    val currentAngle = startAngle + (normalizedValue * sweepAngleTotal)

    val animatedAngle by animateFloatAsState(
        targetValue = currentAngle,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "knobAngle"
    )

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentValueRange by rememberUpdatedState(valueRange)
    var activeDragNorm by remember { mutableFloatStateOf(normalizedValue) }

    LaunchedEffect(normalizedValue) {
        activeDragNorm = normalizedValue
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(knobSize)
                .pointerInput(currentValueRange) {
                    detectDragGestures(
                        onDragStart = {
                            activeDragNorm = normalizedValue
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val sensitivity = 0.005f
                            activeDragNorm = (activeDragNorm - dragAmount.y * sensitivity).coerceIn(0f, 1f)
                            val computed = currentValueRange.start + activeDragNorm * (currentValueRange.endInclusive - currentValueRange.start)
                            currentOnValueChange(computed)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                val strokeWidth = radius * 0.12f
                val knobRadius = radius * 0.72f

                // 1. Draw outer tick marks
                val tickCount = 21
                for (i in 0 until tickCount) {
                    val tickNormalized = i.toFloat() / (tickCount - 1)
                    val tickAngleDeg = startAngle + (tickNormalized * sweepAngleTotal)
                    val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())

                    val isHighlighted = tickNormalized <= normalizedValue
                    val tickColor = if (isHighlighted) {
                        accentColor.copy(alpha = 0.9f)
                    } else {
                        tickInactiveColor
                    }

                    val tickLength = if (i == 0 || i == tickCount - 1 || i == tickCount / 2) radius * 0.18f else radius * 0.11f
                    val outerX = center.x + (radius - 2.dp.toPx()) * cos(tickAngleRad).toFloat()
                    val outerY = center.y + (radius - 2.dp.toPx()) * sin(tickAngleRad).toFloat()
                    val innerX = center.x + (radius - 2.dp.toPx() - tickLength) * cos(tickAngleRad).toFloat()
                    val innerY = center.y + (radius - 2.dp.toPx() - tickLength) * sin(tickAngleRad).toFloat()

                    drawLine(
                        color = tickColor,
                        start = Offset(innerX, innerY),
                        end = Offset(outerX, outerY),
                        strokeWidth = if (isHighlighted) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 2. Draw outer active track arc
                val arcRadius = radius * 0.85f
                val arcRect = Size(arcRadius * 2, arcRadius * 2)
                val arcOffset = Offset(center.x - arcRadius, center.y - arcRadius)

                // Inactive base track
                drawArc(
                    color = inactiveTrackColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngleTotal,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = arcRect,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Active glow track
                if (normalizedValue > 0.01f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.4f),
                                accentColor
                            ),
                            center = center
                        ),
                        startAngle = startAngle,
                        sweepAngle = (animatedAngle - startAngle).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = arcOffset,
                        size = arcRect,
                        style = Stroke(width = strokeWidth * 1.15f, cap = StrokeCap.Round)
                    )
                }

                // 3. Metallic Rotary Cap (Brushed finish with 3D bevel)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            capRimColor,
                            capBodyColor,
                            capDarkEdge
                        ),
                        center = center,
                        radius = knobRadius
                    ),
                    radius = knobRadius,
                    center = center
                )

                // Cap outer metallic border
                drawCircle(
                    color = capBorderColor,
                    radius = knobRadius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Inner inset groove
                drawCircle(
                    color = capGrooveColor,
                    radius = knobRadius * 0.82f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // 4. Indicator Notch / LED Needle
                val indicatorRad = Math.toRadians(animatedAngle.toDouble())
                val dotCenterDistance = knobRadius * 0.65f
                val dotX = center.x + dotCenterDistance * cos(indicatorRad).toFloat()
                val dotY = center.y + dotCenterDistance * sin(indicatorRad).toFloat()

                // Glow aura around LED needle
                drawCircle(
                    color = accentColor.copy(alpha = 0.35f),
                    radius = radius * 0.14f,
                    center = Offset(dotX, dotY)
                )

                // Solid bright LED core
                drawCircle(
                    color = accentColor,
                    radius = radius * 0.07f,
                    center = Offset(dotX, dotY)
                )
                drawCircle(
                    color = Color.White,
                    radius = radius * 0.035f,
                    center = Offset(dotX, dotY)
                )
            }
        }

        // Numerical Value Readout Badge
        if (displayValue.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colors.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    ),
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (subText != null) {
            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = colors.textMuted
                ),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
