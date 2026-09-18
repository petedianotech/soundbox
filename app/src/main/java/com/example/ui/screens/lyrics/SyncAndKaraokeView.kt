package com.example.ui.screens.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SoundboxTheme
import java.util.Locale

/**
 * UNIFIED 1-TAP SYNC & LIVE KARAOKE VIEW
 *
 * High-density layout displaying 4-6+ lines at once.
 * Direct user flow:
 * 1. Listen to audio.
 * 2. Hit Pause right when a lyric line is spoken/sung.
 * 3. Tap "Align" on that lyric line (or anywhere on the line).
 * 4. Entire song's timing shifts automatically by the calculated offset!
 * 5. Tap Play to verify in real-time Karaoke mode.
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
    var expandedLineIndex by remember { mutableStateOf<Int?>(null) }

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
        // COMPACT TOP CONTROL DECK (Single slider, compact buttons, maximum vertical room for lyrics)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(10.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top controls row: Replay 5s, Play/Pause, Forward 5s, Time, Cut Intro, Auto-scroll
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onSeekTo((currentPosition - 5000L).coerceAtLeast(0L)) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.Replay5,
                                contentDescription = "Back 5s",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = onPlayPause,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isPlaying) colors.accentLime else colors.accentCyan,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { onSeekTo((currentPosition + 5000L).coerceAtMost(songDuration)) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.Forward5,
                                contentDescription = "Forward 5s",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "${formatPositionTime(currentPosition)} / ${formatPositionTime(songDuration)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (isPlaying) colors.accentLime else colors.accentCyan
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAlignVideoIntro,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(26.dp),
                            border = BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                Icons.Default.ContentCut,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = colors.accentCyan
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "Cut Intro",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = colors.accentCyan
                            )
                        }

                        FilterChip(
                            selected = autoScrollEnabled,
                            onClick = { autoScrollEnabled = !autoScrollEnabled },
                            label = { Text("Scroll", fontSize = 10.sp) },
                            modifier = Modifier.height(26.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.surfaceVariant,
                                selectedLabelColor = colors.accentCyan
                            )
                        )
                    }
                }

                // Single Timeline Progress Slider
                if (songDuration > 0L) {
                    Slider(
                        value = (currentPosition.toFloat() / songDuration.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { frac ->
                            val target = (frac * songDuration).toLong()
                            onSeekTo(target)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = if (isPlaying) colors.accentLime else colors.accentCyan,
                            activeTrackColor = if (isPlaying) colors.accentLime else colors.accentCyan,
                            inactiveTrackColor = colors.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                    )
                }
            }
        }

        // LYRICS STREAM (HIGH DENSITY CARDS: 4-6+ LINES ON SCREEN, EXPANDABLE ON TAP)
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
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 8.dp)
            ) {
                itemsIndexed(linesList, key = { _, it -> it.index }) { index, line ->
                    val isCurrent = index == activeLyricIndex
                    val isExpanded = expandedLineIndex == index

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedLineIndex = if (isExpanded) null else index
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCurrent) colors.accentCyan.copy(alpha = 0.15f) else colors.surface,
                        border = BorderStroke(
                            width = if (isCurrent) 1.5.dp else 1.dp,
                            color = if (isCurrent) colors.accentCyan else colors.border
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            // Primary Row (Compact, ~40-44dp tall, shows index, timestamp, lyric text, and Align button)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = if (isCurrent) Color.Black else colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }

                                // Timestamp Label
                                if (line.timeMs != null) {
                                    Text(
                                        text = "@ ${formatLrcTimeLabel(line.timeMs)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        ),
                                        color = if (isCurrent) colors.accentCyan else colors.accentLime
                                    )
                                } else {
                                    Text(
                                        text = "[Untimed]",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = colors.textSecondary
                                    )
                                }

                                // Lyric Text (Main readable text)
                                Text(
                                    text = line.text.ifBlank { "—" },
                                    style = if (isCurrent) {
                                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    } else {
                                        MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                                    },
                                    color = if (isCurrent) colors.accentCyan else colors.textPrimary,
                                    maxLines = if (isExpanded) 4 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                // Direct Quick Align Button: ALIGN SONG HERE & SHIFT ALL!
                                Button(
                                    onClick = { onAnchorAndShiftAll(index, currentPosition) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentCyan,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Align",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    )
                                }

                                // Expand / Tools Toggle Icon
                                IconButton(
                                    onClick = {
                                        expandedLineIndex = if (isExpanded) null else index
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.MoreVert,
                                        contentDescription = "More tools",
                                        tint = if (isExpanded) colors.accentCyan else colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Expandable Tools Drawer for this specific line
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    HorizontalDivider(
                                        color = colors.border.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )

                                    // Shift Delta Preview if paused
                                    if (!isPlaying && line.timeMs != null) {
                                        val deltaMs = currentPosition - line.timeMs
                                        if (deltaMs != 0L) {
                                            val deltaSec = deltaMs / 1000.0
                                            val deltaSign = if (deltaMs >= 0) "+${String.format(Locale.US, "%.2f", deltaSec)}s" else "${String.format(Locale.US, "%.2f", deltaSec)}s"
                                            Text(
                                                text = "Aligning will shift entire song from here by $deltaSign",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace
                                                ),
                                                color = colors.accentCyan,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // 1. Tag only this single line
                                        OutlinedButton(
                                            onClick = { onTagSingleLine(index, currentPosition) },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp),
                                            border = BorderStroke(1.dp, colors.border)
                                        ) {
                                            Text(
                                                text = "Only Line",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = colors.textPrimary
                                            )
                                        }

                                        // 2. Micro nudges
                                        OutlinedButton(
                                            onClick = { onShiftSingle(index, -200L) },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp),
                                            border = BorderStroke(1.dp, colors.border)
                                        ) {
                                            Text("-0.2s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textSecondary)
                                        }
                                        OutlinedButton(
                                            onClick = { onShiftSingle(index, 200L) },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp),
                                            border = BorderStroke(1.dp, colors.border)
                                        ) {
                                            Text("+0.2s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textSecondary)
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        // 3. Test / Audition
                                        if (line.timeMs != null) {
                                            IconButton(
                                                onClick = { onAuditionLine(line.timeMs) },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Test 3s", tint = colors.accentCyan, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        // 4. Edit Line Text
                                        IconButton(
                                            onClick = { onEditLine(line) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit text", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                        }

                                        // 5. Delete Line
                                        IconButton(
                                            onClick = { onDeleteLine(index) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete line", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                        }

                                        // 6. Clear tag
                                        if (line.timeMs != null) {
                                            IconButton(
                                                onClick = { onClearLineTag(index) },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear tag", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                            }
                                        }
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
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Global Song Offset Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Offset:",
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
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onShowGlobalShiftDialog,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Custom shift", tint = colors.accentCyan, modifier = Modifier.size(15.dp))
                    }
                }

                // Action buttons (Add Line, Undo, Save & Exit)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddLine,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Add Line", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                    }

                    if (canUndo) {
                        OutlinedButton(
                            onClick = onUndo,
                            modifier = Modifier.weight(0.8f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Undo", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                        }
                    }

                    Button(
                        onClick = onSaveAndExit,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentLime,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Save & Apply", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp))
                    }
                }
            }
        }
    }
}
