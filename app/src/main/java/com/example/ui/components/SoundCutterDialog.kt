package com.example.ui.components

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Song
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sin

/**
 * Modern, tactile, and precision Sound Cutter UI dialog.
 * Enables users to trim unwanted intro/outro/dialogue (e.g. from downloaded video-to-audio files),
 * preview the cut in real-time, and atomically replace the physical audio file with the trimmed version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundCutterDialog(
    song: Song,
    currentPlaybackPosition: Long,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = SoundboxTheme.colors
    val totalDurationMs = song.duration.coerceAtLeast(5000L)

    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(totalDurationMs) }
    var isProcessing by remember { mutableStateOf(false) }

    // Preview Player State
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPositionMs by remember { mutableLongStateOf(0L) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Initialize MediaPlayer for preview
    fun setupPreviewPlayer() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(song.path)
                prepare()
                seekTo(startMs.toInt())
                setOnCompletionListener {
                    isPreviewPlaying = false
                    previewPositionMs = startMs
                }
            }
        } catch (e: Exception) {
            mediaPlayer = null
        }
    }

    LaunchedEffect(song.id) {
        setupPreviewPlayer()
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (ignored: Exception) {}
        }
    }

    // Live preview tracking loop
    LaunchedEffect(isPreviewPlaying) {
        while (isPreviewPlaying) {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying) {
                val current = mp.currentPosition.toLong()
                previewPositionMs = current
                if (current >= endMs) {
                    mp.pause()
                    mp.seekTo(startMs.toInt())
                    isPreviewPlaying = false
                    previewPositionMs = startMs
                }
            }
            delay(50L)
        }
    }

    fun playOrPausePreview() {
        val mp = mediaPlayer ?: return
        try {
            if (isPreviewPlaying) {
                mp.pause()
                isPreviewPlaying = false
            } else {
                if (previewPositionMs < startMs || previewPositionMs >= endMs) {
                    mp.seekTo(startMs.toInt())
                    previewPositionMs = startMs
                }
                mp.start()
                isPreviewPlaying = true
            }
        } catch (e: Exception) {
            setupPreviewPlayer()
        }
    }

    fun seekPreviewTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(startMs, endMs)
        previewPositionMs = clamped
        try {
            mediaPlayer?.seekTo(clamped.toInt())
        } catch (ignored: Exception) {}
    }

    fun formatMs(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millisFraction = (ms % 1000) / 100
        return String.format("%02d:%02d.%d", minutes, seconds, millisFraction)
    }

    Dialog(
        onDismissRequest = {
            if (!isProcessing) {
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                } catch (ignored: Exception) {}
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(24.dp)),
            color = colors.dialogBackground,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = colors.accentCyan.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = null,
                                    tint = colors.accentCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sound Cutter",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary
                            )
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (!isProcessing) {
                                try {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                } catch (ignored: Exception) {}
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Explanatory Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.accentCyan.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = colors.accentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Trim video intro or ending noise. When saved, the physical audio file is permanently replaced and lyrics stay synced.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary
                            )
                        }
                    }

                    // 1. WAVEFORM & RANGE TIMELINE
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AUDIO TIMELINE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colors.accentCyan
                                )
                                Text(
                                    text = "Preview: ${formatMs(previewPositionMs)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colors.textMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Simulated Waveform Bars with active cut highlighting
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val barCount = 36
                                    for (i in 0 until barCount) {
                                        val barFraction = i.toFloat() / barCount.toFloat()
                                        val barMs = (barFraction * totalDurationMs).toLong()
                                        val isInCutRange = barMs in startMs..endMs
                                        val isPastPreview = barMs <= previewPositionMs

                                        // Deterministic wave pattern based on index
                                        val heightFraction = (0.35f + 0.65f * Math.abs(sin(i * 0.45f + 0.2f))).toFloat()

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 1.dp)
                                                .fillMaxHeight(heightFraction)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    when {
                                                        !isInCutRange -> colors.textMuted.copy(alpha = 0.2f)
                                                        isPastPreview -> colors.accentLime
                                                        else -> colors.accentCyan
                                                    }
                                                )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Dual Range Slider
                            RangeSlider(
                                value = startMs.toFloat()..endMs.toFloat(),
                                onValueChange = { range ->
                                    val newStart = range.start.toLong().coerceAtLeast(0L)
                                    val newEnd = range.endInclusive.toLong().coerceAtMost(totalDurationMs)
                                    if (newEnd - newStart >= 1000L) {
                                        startMs = newStart
                                        endMs = newEnd
                                        if (previewPositionMs < startMs || previewPositionMs > endMs) {
                                            seekPreviewTo(startMs)
                                        }
                                    }
                                },
                                valueRange = 0f..totalDurationMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accentCyan,
                                    activeTrackColor = colors.accentCyan,
                                    inactiveTrackColor = colors.surfaceVariant
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("00:00", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                Text(
                                    text = "Cut Length: ${formatMs(endMs - startMs)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.accentCyan
                                )
                                Text(formatMs(totalDurationMs), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            }
                        }
                    }

                    // 2. PRECISION FINE-TUNING CARDS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // START CUT POINT CARD
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "START CUT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colors.accentCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatMs(startMs),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colors.textPrimary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Fine tune buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            startMs = (startMs - 1000L).coerceAtLeast(0L)
                                            seekPreviewTo(startMs)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(2.dp)
                                    ) {
                                        Text("-1s", fontSize = 11.sp)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            startMs = (startMs + 1000L).coerceAtMost(endMs - 1000L)
                                            seekPreviewTo(startMs)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(2.dp)
                                    ) {
                                        Text("+1s", fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedButton(
                                    onClick = {
                                        startMs = currentPlaybackPosition.coerceIn(0L, endMs - 1000L)
                                        seekPreviewTo(startMs)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Use Playhead", fontSize = 10.sp)
                                }
                            }
                        }

                        // END CUT POINT CARD
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "END CUT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colors.accentLime
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatMs(endMs),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colors.textPrimary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Fine tune buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            endMs = (endMs - 1000L).coerceAtLeast(startMs + 1000L)
                                            seekPreviewTo(endMs - 2000L)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(2.dp)
                                    ) {
                                        Text("-1s", fontSize = 11.sp)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            endMs = (endMs + 1000L).coerceAtMost(totalDurationMs)
                                            seekPreviewTo(endMs - 2000L)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(2.dp)
                                    ) {
                                        Text("+1s", fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedButton(
                                    onClick = {
                                        endMs = currentPlaybackPosition.coerceIn(startMs + 1000L, totalDurationMs)
                                        seekPreviewTo(endMs - 2000L)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Use Playhead", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // 3. QUICK ONE-TAP PRESETS
                    Text(
                        text = "QUICK TRIM PRESETS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.textSecondary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Cut Intro 5s" to { startMs = 5000L.coerceAtMost(endMs - 1000L); seekPreviewTo(startMs) },
                            "Cut Intro 10s" to { startMs = 10000L.coerceAtMost(endMs - 1000L); seekPreviewTo(startMs) },
                            "Cut Intro 20s" to { startMs = 20000L.coerceAtMost(endMs - 1000L); seekPreviewTo(startMs) },
                            "Cut Intro 30s" to { startMs = 30000L.coerceAtMost(endMs - 1000L); seekPreviewTo(startMs) },
                            "Cut Outro 10s" to { endMs = (totalDurationMs - 10000L).coerceAtLeast(startMs + 1000L) },
                            "Reset Full Song" to { startMs = 0L; endMs = totalDurationMs; seekPreviewTo(0L) }
                        ).forEach { (label, action) ->
                            SuggestionChip(
                                onClick = action,
                                label = { Text(label, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = colors.surfaceVariant
                                )
                            )
                        }
                    }

                    // 4. INTERACTIVE PREVIEW PLAYER
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isPreviewPlaying) colors.accentLime else colors.accentCyan,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clickable { playOrPausePreview() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPreviewPlaying) "Pause Preview" else "Play Preview",
                                            tint = Color.Black,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = if (isPreviewPlaying) "Listening to Trimmed Cut" else "Preview Trimmed Cut",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "${formatMs(previewPositionMs)} / ${formatMs(endMs)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    seekPreviewTo(startMs)
                                    if (!isPreviewPlaying) playOrPausePreview()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = "Restart Cut Preview",
                                    tint = colors.accentCyan
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons (Cancel / Replace & Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!isProcessing) {
                                try {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                } catch (ignored: Exception) {}
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing
                    ) {
                        Text("Cancel", color = colors.textSecondary)
                    }

                    Button(
                        onClick = {
                            if (isProcessing) return@Button
                            isProcessing = true
                            try {
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                                mediaPlayer = null
                            } catch (ignored: Exception) {}

                            viewModel.cutAndReplaceSong(
                                song = song,
                                startMs = startMs,
                                endMs = endMs
                            ) { success, errorMsg ->
                                isProcessing = false
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "Track trimmed successfully! File updated.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Failed to trim audio: ${errorMsg ?: "Unknown error"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan),
                        enabled = !isProcessing && (startMs > 0 || endMs < totalDurationMs)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = if (colors.isDark) Color.Black else Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Replacing Audio...", color = if (colors.isDark) Color.Black else Color.White)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = if (colors.isDark) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Replace & Save", color = if (colors.isDark) Color.Black else Color.White)
                        }
                    }
                }
            }
        }
    }
}
