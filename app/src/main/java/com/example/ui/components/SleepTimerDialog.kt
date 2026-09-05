package com.example.ui.components

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * A delightful, interactive Sleep Timer dialog with a rotary touch dial,
 * custom minute stepper, quick presets, end-of-track option, and live countdown.
 */
@Composable
fun SleepTimerDialog(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = SoundboxTheme.colors
    val sleepTimerLeft by viewModel.sleepTimerMillis.collectAsState()
    val isTimerActive = sleepTimerLeft > 0

    // Selected duration in minutes (default 30)
    var selectedMinutes by remember { mutableIntStateOf(30) }
    var fadeOutAtEnd by remember { mutableStateOf(true) }
    var isEditingCustomTime by remember { mutableStateOf(false) }
    var customInputText by remember { mutableStateOf("") }
    var isAdjustingActiveTimer by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    // Format target bedtime (current time + selectedMinutes)
    val targetTimeText = remember(selectedMinutes) {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, selectedMinutes)
        }
        val is24Hour = DateFormat.is24HourFormat(context)
        val format = if (is24Hour) SimpleDateFormat("HH:mm", Locale.getDefault()) else SimpleDateFormat("h:mm a", Locale.getDefault())
        format.format(calendar.time)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 440.dp)
                    .clickable(enabled = false) {}
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = colors.dialogBackground),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTimerActive && !isAdjustingActiveTimer) Icons.Default.Bedtime else Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = colors.accentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isTimerActive && !isAdjustingActiveTimer) "Sleep Timer Active" else "Sleep Timer",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                )
                                Text(
                                    text = if (isTimerActive && !isAdjustingActiveTimer) "Music is soothingly scheduled" else "Set audio turn-off time",
                                    style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isTimerActive && !isAdjustingActiveTimer) {
                        // -------------------------------------------------------------
                        // ACTIVE TIMER VIEW: Live Countdown & Extension Actions
                        // -------------------------------------------------------------
                        val minutesLeft = (sleepTimerLeft / 1000) / 60
                        val secondsLeft = (sleepTimerLeft / 1000) % 60
                        val formattedCountdown = String.format(Locale.getDefault(), "%02d:%02d", minutesLeft, secondsLeft)

                        Box(
                            modifier = Modifier
                                .size(210.dp)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Pulsing / decorative progress circle
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 10.dp.toPx()
                                val radius = (size.minDimension - strokeWidth) / 2
                                val center = Offset(size.width / 2, size.height / 2)

                                // Background track
                                drawCircle(
                                    color = colors.surfaceVariant,
                                    radius = radius,
                                    center = center,
                                    style = Stroke(width = strokeWidth)
                                )

                                // Active glowing arc
                                val sweep = (sleepTimerLeft % 3600000L).toFloat() / 3600000L * 360f
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(colors.accentCyan, Color(0xFF60A5FA), colors.accentCyan),
                                        center = center
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = sweep.coerceAtLeast(15f),
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = formattedCountdown,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 42.sp,
                                        color = colors.textPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                Text(
                                    text = "REMAINING",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = colors.accentCyan,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick extension / snooze buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.extendSleepTimer(5) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = colors.surfaceVariant.copy(alpha = 0.5f),
                                    contentColor = colors.textPrimary
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(colors.border, colors.border)))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+5 Min", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.extendSleepTimer(15) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = colors.surfaceVariant.copy(alpha = 0.5f),
                                    contentColor = colors.textPrimary
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(colors.border, colors.border)))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+15 Min", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.stopSleepTimer()
                                    isAdjustingActiveTimer = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444).copy(alpha = 0.18f),
                                    contentColor = Color(0xFFEF4444)
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Timer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = { isAdjustingActiveTimer = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentCyan,
                                    contentColor = Color(0xFF0F172A)
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    } else {
                        // -------------------------------------------------------------
                        // INTERACTIVE TIMER SETTER: Rotary Dial, Steppers, & Presets
                        // -------------------------------------------------------------

                        // Interactive Circular Rotary Dial
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            RotaryTimeDial(
                                minutes = selectedMinutes,
                                maxMinutes = 120,
                                accentColor = colors.accentCyan,
                                trackColor = colors.surfaceVariant,
                                onMinutesChanged = { newMins ->
                                    selectedMinutes = newMins.coerceIn(1, 180)
                                }
                            )

                            // Central digital readout inside the dial
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.clickable {
                                    customInputText = selectedMinutes.toString()
                                    isEditingCustomTime = true
                                }
                            ) {
                                Text(
                                    text = "$selectedMinutes",
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 46.sp,
                                        color = colors.textPrimary,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                )
                                Text(
                                    text = "MINUTES",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = colors.accentCyan,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Stops at $targetTimeText",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Tactile Stepper Adjusters (-10, -1, +1, +10)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { selectedMinutes = (selectedMinutes - 10).coerceAtLeast(1) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("-10", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.textPrimary)
                            }
                            IconButton(
                                onClick = { selectedMinutes = (selectedMinutes - 1).coerceAtLeast(1) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("-1", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.textPrimary)
                            }

                            // Center chip: Tap to manually type exact number
                            Surface(
                                onClick = {
                                    customInputText = selectedMinutes.toString()
                                    isEditingCustomTime = !isEditingCustomTime
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = colors.surfaceElevated,
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(colors.border, colors.border)))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = colors.accentCyan
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Custom",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textPrimary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedMinutes = (selectedMinutes + 1).coerceAtMost(300) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("+1", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.textPrimary)
                            }
                            IconButton(
                                onClick = { selectedMinutes = (selectedMinutes + 10).coerceAtMost(300) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("+10", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.textPrimary)
                            }
                        }

                        // Inline Custom Number Input Field
                        AnimatedVisibility(visible = isEditingCustomTime) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customInputText,
                                        onValueChange = { input ->
                                            if (input.all { it.isDigit() } && input.length <= 4) {
                                                customInputText = input
                                            }
                                        },
                                        placeholder = { Text("e.g. 25, 42, 90") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(onDone = {
                                            val parsed = customInputText.toIntOrNull()
                                            if (parsed != null && parsed > 0) {
                                                selectedMinutes = parsed.coerceIn(1, 480)
                                            }
                                            isEditingCustomTime = false
                                            focusManager.clearFocus()
                                        }),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.accentCyan,
                                            unfocusedBorderColor = colors.border
                                        )
                                    )

                                    Button(
                                        onClick = {
                                            val parsed = customInputText.toIntOrNull()
                                            if (parsed != null && parsed > 0) {
                                                selectedMinutes = parsed.coerceIn(1, 480)
                                            }
                                            isEditingCustomTime = false
                                            focusManager.clearFocus()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.accentCyan,
                                            contentColor = Color(0xFF0F172A)
                                        )
                                    ) {
                                        Text("Set", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick Presets Carousel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15, 30, 45, 60, 90).forEach { preset ->
                                val isSelected = selectedMinutes == preset
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedMinutes = preset },
                                    label = { Text("${preset}m", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accentCyan,
                                        selectedLabelColor = Color(0xFF0F172A),
                                        containerColor = colors.surfaceVariant.copy(alpha = 0.5f),
                                        labelColor = colors.textSecondary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    border = null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Special Preset: End of Current Song
                        Surface(
                            onClick = {
                                viewModel.startSleepTimerEndOfTrack(fadeOutAtEnd = fadeOutAtEnd)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceVariant.copy(alpha = 0.45f),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(colors.border, colors.border))),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = colors.accentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Stop after current track finishes",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Gentle Volume Fade-Out Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.35f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeDown,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Gentle Fade Out (Last 15s)",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Text(
                                        text = "Softens volume so you aren't startled awake",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Switch(
                                checked = fadeOutAtEnd,
                                onCheckedChange = { fadeOutAtEnd = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF0F172A),
                                    checkedTrackColor = colors.accentCyan,
                                    uncheckedThumbColor = colors.textSecondary,
                                    uncheckedTrackColor = colors.surfaceVariant
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Bottom Actions (Cancel / Start)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(0.8f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = colors.textSecondary
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(colors.border, colors.border)))
                            ) {
                                Text("Cancel", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    viewModel.startSleepTimer(selectedMinutes, fadeOutAtEnd)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentCyan,
                                    contentColor = Color(0xFF0F172A)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Start (${selectedMinutes}m)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive touch rotary dial that translates circular drag gestures into minutes (1..maxMinutes).
 */
@Composable
private fun RotaryTimeDial(
    minutes: Int,
    maxMinutes: Int,
    accentColor: Color,
    trackColor: Color,
    onMinutesChanged: (Int) -> Unit
) {
    val sweepAngle by animateFloatAsState(
        targetValue = (minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0.01f, 1.0f) * 360f,
        animationSpec = tween(durationMillis = 120),
        label = "dialSweep"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(maxMinutes) {
                detectDragGestures { change, _ ->
                    val touchX = change.position.x - size.width / 2f
                    val touchY = change.position.y - size.height / 2f

                    // Calculate touch angle in degrees (-180 to 180), offset so 12 o'clock is 0
                    var angleDeg = Math.toDegrees(atan2(touchY.toDouble(), touchX.toDouble())).toFloat() + 90f
                    if (angleDeg < 0f) angleDeg += 360f

                    val fraction = (angleDeg / 360f).coerceIn(0.01f, 1.0f)
                    val newMinutes = (fraction * maxMinutes).roundToInt().coerceIn(1, maxMinutes)
                    onMinutesChanged(newMinutes)
                    change.consume()
                }
            }
    ) {
        val strokeWidth = 14.dp.toPx()
        val radius = (size.minDimension - strokeWidth * 2f) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // 1. Background full circle track
        drawCircle(
            color = trackColor,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // 2. Active gradient progress arc
        drawArc(
            brush = Brush.sweepGradient(
                listOf(accentColor, Color(0xFF60A5FA), accentColor),
                center = center
            ),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 3. Thumb knob at current angle
        val thumbAngleRad = Math.toRadians((sweepAngle - 90f).toDouble())
        val thumbX = center.x + radius * cos(thumbAngleRad).toFloat()
        val thumbY = center.y + radius * sin(thumbAngleRad).toFloat()

        // Thumb outer glow
        drawCircle(
            color = accentColor.copy(alpha = 0.35f),
            radius = strokeWidth * 1.1f,
            center = Offset(thumbX, thumbY)
        )

        // Thumb inner dot
        drawCircle(
            color = Color.White,
            radius = strokeWidth * 0.55f,
            center = Offset(thumbX, thumbY)
        )

        drawCircle(
            color = accentColor,
            radius = strokeWidth * 0.35f,
            center = Offset(thumbX, thumbY)
        )
    }
}
