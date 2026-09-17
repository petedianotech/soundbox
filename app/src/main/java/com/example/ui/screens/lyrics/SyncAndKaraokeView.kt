package com.example.ui.screens.lyrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SoundboxTheme
import java.util.Locale

/**
 * UNIFIED 1-TAP SYNC & LIVE KARAOKE VIEW
 *
 * Direct user flow:
 * 1. Listen to the audio.
 * 2. Hit Pause right when a lyric line is spoken/sung.
 * 3. Tap "⚓ Align Song Here" on that lyric line.
 * 4. The entire song's timing shifts automatically by the calculated offset!
 * 5. Tap Play to sing along and verify in real-time Karaoke mode.
 * 6. Save & Finish!
 */
@Composable
fun SyncAndKaraokeView(
    linesList: List<SyncLine>,
    currentPosition: Long,
    isPlaying: Boolean,
    songDuration: Long,
    lazyListState: LazyListState,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onAnchorAndShiftAll: (Int, Long) -> Unit,
    onTagSingleLine: (Int, Long) -> Unit,
    onShiftSingle: (Int, Long) -> Unit,
    onShiftAll: (Long) -> Unit,
    onAlignVideoIntro: () -> Unit,
    onAuditionLine: (Long) -> Unit,
    onClearLineTag: (Int) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    onEditLine: (SyncLine) -> Unit,
    onDeleteLine: (Int) -> Unit,
    onAddLine: () -> Unit,
    onShowGlobalShiftDialog: () -> Unit,
    onSaveAndExit: () -> Unit
) {
    val colors = SoundboxTheme.colors
    var autoScrollEnabled by remember { mutableStateOf(true) }

    // Active lyric index based on current playback position
    val activeLyricIndex = remember(linesList, currentPosition) {
        linesList.indexOfLast { it.timeMs != null && currentPosition >= it.timeMs }
    }

    // Auto-scroll when playing
    LaunchedEffect(activeLyricIndex, isPlaying, autoScrollEnabled) {
        if (autoScrollEnabled && isPlaying && activeLyricIndex >= 0) {
            val target = (activeLyricIndex - 1).coerceAtLeast(0)
            lazyListState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // TOP CONTROL DECK & GUIDANCE BANNER
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Audio Status & Guidance Banner (No gradients, clean high-contrast M3 theme)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPlaying) colors.accentLime.copy(alpha = 0.15f) else colors.accentCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isPlaying) colors.accentLime else colors.accentCyan)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = if (isPlaying) colors.accentLime else colors.accentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPlaying) "PLAYING • KARAOKE ACTIVE" else "PAUSED @ ${formatPositionTime(currentPosition)}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = if (isPlaying) colors.accentLime else colors.accentCyan
                            )
                            Text(
                                text = if (isPlaying) {
                                    "Listen for the singer's voice. Hit Pause right when the line starts!"
                                } else {
                                    "Tap 'Align Song Here' on that lyric line below to auto-shift the whole song!"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                // Quick Playback & Alignment Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Replay 5s
                        IconButton(
                            onClick = { onSeekTo((currentPosition - 5000L).coerceAtLeast(0L)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Replay5, contentDescription = "Back 5s", tint = colors.textPrimary)
                        }

                        // Big Play / Pause Button
                        FilledIconButton(
                            onClick = onPlayPause,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colors.accentCyan,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Forward 5s
                        IconButton(
                            onClick = { onSeekTo((currentPosition + 5000L).coerceAtMost(songDuration)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Forward5, contentDescription = "Forward 5s", tint = colors.textPrimary)
                        }
                    }

                    // Interactive Scrub Bar / Time Display
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Cut Video Intro Quick Button
                        OutlinedButton(
                            onClick = onAlignVideoIntro,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.accentCyan)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cut Intro", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.accentCyan)
                        }

                        // Auto-scroll toggle chip
                        FilterChip(
                            selected = autoScrollEnabled,
                            onClick = { autoScrollEnabled = !autoScrollEnabled },
                            label = { Text("Auto-scroll", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.surfaceVariant,
                                selectedLabelColor = colors.accentCyan
                            )
                        )
                    }
                }

                // Interactive Audio Timeline Progress Slider
                if (songDuration > 0L) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    ) {
                        Slider(
                            value = (currentPosition.toFloat() / songDuration.toFloat()).coerceIn(0f, 1f),
                            onValueChange = { frac ->
                                val target = (frac * songDuration).toLong()
                                onSeekTo(target)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = colors.accentCyan,
                                activeTrackColor = colors.accentCyan,
                                inactiveTrackColor = colors.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatPositionTime(currentPosition),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.accentCyan
                            )
                            Text(
                                text = formatPositionTime(songDuration),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // LYRICS STREAM (CARDS WITH DIRECT TAP-TO-ALIGN BUTTONS)
        if (linesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No lyrics loaded yet", color = colors.textSecondary)
                    Button(
                        onClick = onAddLine,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add First Lyric Line", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
            ) {
                itemsIndexed(linesList, key = { _, it -> it.index }) { index, line ->
                    val isCurrent = index == activeLyricIndex

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) colors.accentCyan.copy(alpha = 0.2f) else colors.surface,
                        border = BorderStroke(
                            width = if (isCurrent) 2.dp else 1.dp,
                            color = if (isCurrent) colors.accentCyan else colors.border
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Header Row: Line #, Timestamp Badge, and Tools
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Line Number
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isCurrent) colors.accentCyan else colors.surfaceVariant
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = if (isCurrent) Color.Black else colors.textSecondary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Timestamp Label
                                    if (line.timeMs != null) {
                                        Text(
                                            text = "@ ${formatLrcTimeLabel(line.timeMs)}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            ),
                                            color = if (isCurrent) colors.accentCyan else colors.accentLime
                                        )

                                        // When paused, show potential shift preview
                                        if (!isPlaying) {
                                            val deltaMs = currentPosition - line.timeMs
                                            if (deltaMs != 0L) {
                                                val deltaSec = deltaMs / 1000.0
                                                val deltaSign = if (deltaMs >= 0) "+${String.format(Locale.US, "%.2f", deltaSec)}s" else "${String.format(Locale.US, "%.2f", deltaSec)}s"
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = colors.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = "Shift all $deltaSign",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 9.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        ),
                                                        color = colors.textSecondary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "[Untimed]",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = colors.textSecondary
                                        )
                                    }
                                }

                                // Secondary line actions (Test 3s, Edit text, Delete)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (line.timeMs != null) {
                                        IconButton(
                                            onClick = { onAuditionLine(line.timeMs) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Test 3s", tint = colors.accentCyan, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = { onEditLine(line) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteLine(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Lyric Text
                            Text(
                                text = line.text.ifBlank { "—" },
                                style = if (isCurrent) {
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                } else {
                                    MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
                                },
                                color = if (isCurrent) colors.accentCyan else colors.textPrimary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            // Direct Action Buttons Row (The Core Feature)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1. THE MAIN BUTTON: ALIGN SONG HERE & SHIFT ALL!
                                Button(
                                    onClick = { onAnchorAndShiftAll(index, currentPosition) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentCyan,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Align Song Here",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                // 2. Tag single line without shifting
                                OutlinedButton(
                                    onClick = { onTagSingleLine(index, currentPosition) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, colors.border)
                                ) {
                                    Text(
                                        text = "Only Line",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = colors.textPrimary
                                    )
                                }

                                // 3. Micro nudges
                                OutlinedButton(
                                    onClick = { onShiftSingle(index, -200L) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, colors.border)
                                ) {
                                    Text("-0.2s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textSecondary)
                                }
                                OutlinedButton(
                                    onClick = { onShiftSingle(index, 200L) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, colors.border)
                                ) {
                                    Text("+0.2s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textSecondary)
                                }

                                // 4. Clear tag
                                if (line.timeMs != null) {
                                    IconButton(
                                        onClick = { onClearLineTag(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM DOCK: GLOBAL OFFSET CALIBRATION & SAVE
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Global Song Offset Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Song Offset:",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = colors.textSecondary
                    )

                    listOf(
                        Pair("-1s", -1000L),
                        Pair("-0.5s", -500L),
                        Pair("-0.1s", -100L),
                        Pair("+0.1s", 100L),
                        Pair("+0.5s", 500L),
                        Pair("+1s", 1000L)
                    ).forEach { (label, delta) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onShiftAll(delta) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                ),
                                color = colors.accentCyan,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onShowGlobalShiftDialog,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Custom shift", tint = colors.accentCyan, modifier = Modifier.size(16.dp))
                    }
                }

                // Action buttons (Add Line, Undo, Save & Exit)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddLine,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Line", style = MaterialTheme.typography.labelMedium)
                    }

                    if (canUndo) {
                        OutlinedButton(
                            onClick = onUndo,
                            modifier = Modifier.weight(0.8f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Undo", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Button(
                        onClick = onSaveAndExit,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentLime,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save & Apply", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}
