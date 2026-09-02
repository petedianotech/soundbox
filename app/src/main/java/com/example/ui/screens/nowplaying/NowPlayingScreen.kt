package com.example.ui.screens.nowplaying

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.data.model.Song
import com.example.player.LyricLine
import com.example.player.LyricsManager
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.RealtimeAudioVisualizer
import com.example.ui.components.SongImagePlaceholder
import com.example.ui.components.TrackRow
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    onBackClick: () -> Unit,
    onNavigateToLyricsCreator: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleMode by viewModel.shuffleMode.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val pitch by viewModel.playbackPitch.collectAsState()
    val sleepTimerLeft by viewModel.sleepTimerMillis.collectAsState()
    val eqEnabled by viewModel.equalizerEnabled.collectAsState()
    val bassStrength by viewModel.bassBoostStrength.collectAsState()
    val audioSessionId by viewModel.audioSessionId.collectAsState()
    val queue by viewModel.queue.collectAsState()

    // View & Overlay states
    var isLyricsViewActive by remember { mutableStateOf(false) }
    var showVisualizer by remember { mutableStateOf(true) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsOptionsSheet by remember { mutableStateOf(false) }
    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedLyricsContent by remember { mutableStateOf("") }
    var showRemainingTime by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Lyrics configuration states
    var lyricsFontSizeOption by remember { mutableStateOf(1) } // 0: Small, 1: Medium, 2: Large
    var userScrolledAway by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // File Picker for LRC / TXT
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    val content = reader.readText()
                    if (currentSong != null) {
                        LyricsManager.saveLyrics(context, currentSong!!, content)
                        refreshTrigger++
                        Toast.makeText(context, "Lyrics file imported successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (currentSong == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyPlaceholder(
                title = "Nothing is playing",
                subtitle = "Select a song from your library to start listening.",
                icon = Icons.Default.MusicNote,
                actionText = "Back to Library",
                onActionClick = onBackClick
            )
        }
        return
    }

    val song = currentSong!!
    val lyrics = remember(song.id, duration, refreshTrigger) {
        val raw = LyricsManager.loadLyrics(context, song)
        if (!raw.isNullOrBlank()) {
            LyricsManager.parseLyrics(raw, duration)
        } else {
            emptyList()
        }
    }

    val hasTimestamps = remember(lyrics) { lyrics.any { it.isDynamic } }
    val activeVerseIndex = remember(lyrics, position, hasTimestamps) {
        if (hasTimestamps) lyrics.indexOfLast { position >= it.timeMs } else -1
    }

    val lyricsListState = rememberLazyListState()

    // Auto-scroll when active lyric line changes (unless user actively scrolled away)
    LaunchedEffect(activeVerseIndex, isLyricsViewActive, userScrolledAway) {
        if (isLyricsViewActive && hasTimestamps && activeVerseIndex >= 0 && lyrics.isNotEmpty() && !userScrolledAway) {
            lyricsListState.animateScrollToItem(
                index = activeVerseIndex,
                scrollOffset = -180
            )
        }
    }

    // Reset user scroll state after 4.5 seconds of no interaction
    LaunchedEffect(lyricsListState.isScrollInProgress) {
        if (lyricsListState.isScrollInProgress) {
            userScrolledAway = true
        } else if (userScrolledAway) {
            delay(4500)
            userScrolledAway = false
        }
    }

    // Dynamic Atmosphere Palette
    val dynamicSongColor = remember(song.title, song.artist) {
        val hash = (song.title + song.artist).hashCode()
        val hues = listOf(
            Color(0xFF6C5CE7), Color(0xFF00CEC9), Color(0xFFFF7675), Color(0xFFFD79A8),
            Color(0xFF0984E3), Color(0xFF00B894), Color(0xFFE17055), Color(0xFFF39C12),
            Color(0xFF8E44AD), Color(0xFF2980B9), Color(0xFF27AE60), Color(0xFFD35400)
        )
        hues[kotlin.math.abs(hash) % hues.size]
    }

    val dynamicSecondaryColor = remember(dynamicSongColor) {
        dynamicSongColor.copy(alpha = 0.7f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isLyricsViewActive) "LYRICS" else "NOW PLAYING",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = song.album.ifEmpty { "Audio Library" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse Player",
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Queue Sheet
                    IconButton(onClick = { showQueueSheet = true }) {
                        BadgedBox(badge = {
                            if (queue.isNotEmpty()) {
                                Badge { Text("${queue.size}") }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Playback Queue",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Equalizer & Audio Effects
                    IconButton(onClick = { showEffectsSheet = true }) {
                        BadgedBox(badge = {
                            if (eqEnabled || bassStrength > 0 || speed != 1.0f) {
                                Badge { Text("FX") }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Sound & Effects",
                                tint = if (eqEnabled || bassStrength > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Lyrics Toggle Button
                    IconButton(onClick = { isLyricsViewActive = !isLyricsViewActive }) {
                        Icon(
                            imageVector = if (isLyricsViewActive) Icons.Filled.Lyrics else Icons.Outlined.Lyrics,
                            contentDescription = "Toggle Lyrics View",
                            tint = if (isLyricsViewActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // More Menu
                    IconButton(onClick = { showLyricsOptionsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Song & Lyrics Options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            dynamicSongColor.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Main Upper Stage (Center Content: Artwork vs Lyrics View)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isLyricsViewActive,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(320)) togetherWith
                            fadeOut(animationSpec = tween(320))
                        },
                        label = "MainContentSwitch"
                    ) { showLyrics ->
                        if (showLyrics) {
                            // FULLSCREEN / DEDICATED LYRICS STAGE
                            FullLyricsStage(
                                song = song,
                                lyrics = lyrics,
                                hasTimestamps = hasTimestamps,
                                activeVerseIndex = activeVerseIndex,
                                lyricsListState = lyricsListState,
                                userScrolledAway = userScrolledAway,
                                fontSizeOption = lyricsFontSizeOption,
                                onSeekTo = { viewModel.seekTo(it) },
                                onJumpToCurrent = {
                                    userScrolledAway = false
                                    if (activeVerseIndex >= 0 && lyrics.isNotEmpty()) {
                                        coroutineScope.launch {
                                            lyricsListState.animateScrollToItem(activeVerseIndex, scrollOffset = -180)
                                        }
                                    }
                                },
                                onAddLyricsClick = { showLyricsOptionsSheet = true },
                                onSearchWebClick = { LyricsManager.searchLyricsWeb(context, song) },
                                onPasteClick = {
                                    pastedLyricsContent = ""
                                    showPasteDialog = true
                                },
                                onPickFileClick = { fileLauncher.launch("*/*") },
                                onCreateLyricsClick = onNavigateToLyricsCreator,
                                accentColor = dynamicSongColor
                            )
                        } else {
                            // HERO ARTWORK & SONG DETAILS VIEW
                            ArtworkMainStage(
                                song = song,
                                isPlaying = isPlaying,
                                lyrics = lyrics,
                                hasTimestamps = hasTimestamps,
                                activeVerseIndex = activeVerseIndex,
                                onToggleLyrics = { isLyricsViewActive = true },
                                onToggleFavorite = { viewModel.toggleFavorite(song) },
                                accentColor = dynamicSongColor
                            )
                        }
                    }
                }

                // Bottom Controls Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Sleep Timer Active Pill
                    if (sleepTimerLeft > 0) {
                        val minutes = (sleepTimerLeft / 1000) / 60
                        val seconds = (sleepTimerLeft / 1000) % 60
                        AssistChip(
                            onClick = { showTimerDialog = true },
                            label = { Text("Stopping in ${String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)}") },
                            leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.stopSleepTimer() },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel Timer", modifier = Modifier.size(12.dp))
                                }
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = CircleShape
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Audio Visualizer
                    if (showVisualizer && !isLyricsViewActive) {
                        RealtimeAudioVisualizer(
                            audioSessionId = audioSessionId,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            accentColor = dynamicSongColor,
                            secondaryColor = dynamicSecondaryColor
                        )
                    }

                    // Progress Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val sliderRatio = if (duration > 0) {
                            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Slider(
                            value = sliderRatio,
                            onValueChange = { targetRatio ->
                                val targetMs = (targetRatio * duration).toLong()
                                viewModel.seekTo(targetMs)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = dynamicSongColor,
                                activeTrackColor = dynamicSongColor,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration(position),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Tap to switch between total duration and remaining duration
                            Text(
                                text = if (showRemainingTime) {
                                    "-${formatDuration((duration - position).coerceAtLeast(0L))}"
                                } else {
                                    formatDuration(duration)
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Core Playback Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle Button
                        IconButton(onClick = { viewModel.setShuffleMode(!shuffleMode) }) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (shuffleMode) dynamicSongColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                if (shuffleMode) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .align(Alignment.BottomCenter)
                                            .offset(y = 12.dp)
                                            .background(dynamicSongColor, CircleShape)
                                    )
                                }
                            }
                        }

                        // Skip Previous
                        IconButton(
                            onClick = { viewModel.skipPrevious() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Song",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Hero Play/Pause Button
                        Surface(
                            onClick = { viewModel.playPause() },
                            shape = CircleShape,
                            color = dynamicSongColor,
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                val playIconScale by animateFloatAsState(
                                    targetValue = if (isPlaying) 1.1f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.5f),
                                    label = "PlayIconScale"
                                )
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .graphicsLayer {
                                            scaleX = playIconScale
                                            scaleY = playIconScale
                                        }
                                )
                            }
                        }

                        // Skip Next
                        IconButton(
                            onClick = { viewModel.skipNext() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Song",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Repeat Button
                        IconButton(
                            onClick = {
                                val nextMode = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                viewModel.setRepeatMode(nextMode)
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val icon = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "Repeat Mode",
                                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) dynamicSongColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                if (repeatMode != Player.REPEAT_MODE_OFF) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .align(Alignment.BottomCenter)
                                            .offset(y = 12.dp)
                                            .background(dynamicSongColor, CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Action Dock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { isLyricsViewActive = !isLyricsViewActive },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isLyricsViewActive) dynamicSongColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isLyricsViewActive) Icons.Filled.Lyrics else Icons.Outlined.Lyrics,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isLyricsViewActive) dynamicSongColor else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Lyrics",
                                color = if (isLyricsViewActive) dynamicSongColor else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isLyricsViewActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        FilledTonalButton(
                            onClick = { showQueueSheet = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Queue")
                        }

                        FilledTonalButton(
                            onClick = { showTimerDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (sleepTimerLeft > 0) dynamicSongColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (sleepTimerLeft > 0) dynamicSongColor else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Timer")
                        }
                    }
                }
            }
        }
    }

    // QUEUE BOTTOM SHEET
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Playing Queue",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${queue.size} songs in queue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (queue.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearQueue() }) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                if (queue.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Queue is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(queue) { index, queueSong ->
                            val isCurrent = queueSong.id == song.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) dynamicSongColor.copy(alpha = 0.15f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.playSong(queueSong, queue)
                                        showQueueSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isCurrent) dynamicSongColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(28.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = queueSong.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isCurrent) dynamicSongColor else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = queueSong.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = "Playing",
                                            tint = dynamicSongColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        IconButton(onClick = { viewModel.removeFromQueue(index) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
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

    // LYRICS & SONG OPTIONS BOTTOM SHEET
    if (showLyricsOptionsSheet) {
        val hasSavedLyrics = remember(song.id, refreshTrigger) { LyricsManager.loadLyrics(context, song) != null }
        ModalBottomSheet(
            onDismissRequest = { showLyricsOptionsSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Song & Lyrics Options",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { showLyricsOptionsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Font Size Switcher for Lyrics
                Text(
                    text = "Lyrics Font Size",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Compact", "Standard", "Large").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = lyricsFontSizeOption == idx,
                            onClick = { lyricsFontSizeOption = idx },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time Offset Adjuster (if synced lyrics available)
                if (hasTimestamps) {
                    Text(
                        text = "Sync Timing Calibration",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val ok = LyricsManager.adjustLyricsOffset(context, song, -500L)
                                if (ok) {
                                    refreshTrigger++
                                    Toast.makeText(context, "Shifted lyrics -0.5s (Earlier)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("-0.5s")
                        }

                        OutlinedButton(
                            onClick = {
                                val ok = LyricsManager.adjustLyricsOffset(context, song, 500L)
                                if (ok) {
                                    refreshTrigger++
                                    Toast.makeText(context, "Shifted lyrics +0.5s (Later)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+0.5s")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Action List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            showLyricsOptionsSheet = false
                            onNavigateToLyricsCreator()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create & Sync in LRC Editor")
                    }

                    OutlinedButton(
                        onClick = {
                            showLyricsOptionsSheet = false
                            LyricsManager.searchLyricsWeb(context, song, "google")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search on Google")
                    }

                    OutlinedButton(
                        onClick = {
                            showLyricsOptionsSheet = false
                            LyricsManager.searchLyricsWeb(context, song, "genius")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search on Genius")
                    }

                    OutlinedButton(
                        onClick = {
                            showLyricsOptionsSheet = false
                            pastedLyricsContent = LyricsManager.loadLyrics(context, song) ?: ""
                            showPasteDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (hasSavedLyrics) "Edit / Paste Raw Lyrics" else "Paste Lyrics Text")
                    }

                    OutlinedButton(
                        onClick = {
                            showLyricsOptionsSheet = false
                            fileLauncher.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose Lyrics File (.lrc / .txt)")
                    }

                    if (hasSavedLyrics) {
                        val rawContent = LyricsManager.loadLyrics(context, song) ?: ""
                        OutlinedButton(
                            onClick = {
                                LyricsManager.copyLyricsToClipboard(context, song, rawContent)
                                showLyricsOptionsSheet = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Lyrics to Clipboard")
                        }

                        Button(
                            onClick = {
                                LyricsManager.deleteLyrics(context, song)
                                refreshTrigger++
                                showLyricsOptionsSheet = false
                                Toast.makeText(context, "Lyrics deleted", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Saved Lyrics")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            showLyricsOptionsSheet = false
                            showTrackInfoDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Track Details")
                    }
                }
            }
        }
    }

    // TRACK DETAILS DIALOG
    if (showTrackInfoDialog) {
        AlertDialog(
            onDismissRequest = { showTrackInfoDialog = false },
            title = { Text("Track Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Title: ${song.title}", fontWeight = FontWeight.SemiBold)
                    Text("Artist: ${song.artist}")
                    Text("Album: ${song.album}")
                    Text("Genre: ${song.genre.ifEmpty { "Audio Track" }}")
                    Text("Duration: ${formatDuration(song.duration)}")
                    Text("Location: ${song.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackInfoDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // SLEEP TIMER DIALOG
    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("Sleep Timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Turn off audio playback automatically after:")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45, 60).forEach { mins ->
                            FilledTonalButton(
                                onClick = {
                                    viewModel.startSleepTimer(mins)
                                    showTimerDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("${mins}m", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 90, 120).forEach { mins ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.startSleepTimer(mins)
                                    showTimerDialog = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("${mins}m")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (sleepTimerLeft > 0) {
                        TextButton(
                            onClick = {
                                viewModel.stopSleepTimer()
                                showTimerDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Turn Off Timer")
                        }
                    }
                    TextButton(onClick = { showTimerDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // SOUND & EFFECTS BOTTOM SHEET
    if (showEffectsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEffectsSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sound & Effects",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { showEffectsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Playback Speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Playback Speed", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text("${String.format(Locale.US, "%.2f", speed)}x", style = MaterialTheme.typography.bodyMedium.copy(color = dynamicSongColor, fontWeight = FontWeight.Bold))
                }
                Slider(
                    value = speed,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { viewModel.setPlaybackRate(it, pitch) },
                    colors = SliderDefaults.colors(thumbColor = dynamicSongColor, activeTrackColor = dynamicSongColor)
                )

                // Quick speed presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { preset ->
                        FilterChip(
                            selected = kotlin.math.abs(speed - preset) < 0.01f,
                            onClick = { viewModel.setPlaybackRate(preset, pitch) },
                            label = { Text("${preset}x") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Audio Pitch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Audio Pitch", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text("${String.format(Locale.US, "%.2f", pitch)}x", style = MaterialTheme.typography.bodyMedium.copy(color = dynamicSongColor, fontWeight = FontWeight.Bold))
                }
                Slider(
                    value = pitch,
                    valueRange = 0.5f..1.5f,
                    onValueChange = { viewModel.setPlaybackRate(speed, it) },
                    colors = SliderDefaults.colors(thumbColor = dynamicSongColor, activeTrackColor = dynamicSongColor)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Equalizer Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Equalizer Engine", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            Text(
                                if (eqEnabled) "Equalizer is enabled" else "Equalizer is off",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = eqEnabled,
                            onCheckedChange = { viewModel.toggleEqualizer() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bass Boost
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bass Boost", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        if (bassStrength > 0) "${bassStrength / 10}%" else "Off",
                        style = MaterialTheme.typography.bodyMedium.copy(color = dynamicSongColor, fontWeight = FontWeight.Bold)
                    )
                }
                Slider(
                    value = bassStrength.toFloat(),
                    valueRange = 0f..1000f,
                    onValueChange = { viewModel.setBassBoost(it.toInt()) },
                    colors = SliderDefaults.colors(thumbColor = dynamicSongColor, activeTrackColor = dynamicSongColor)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.setPlaybackRate(1.0f, 1.0f)
                        viewModel.setBassBoost(0)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Audio to Default")
                }
            }
        }
    }

    // PASTE LYRICS DIALOG
    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("Paste or Edit Lyrics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste lyrics text or standard LRC format [mm:ss.xx] line-by-line:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pastedLyricsContent,
                        onValueChange = { pastedLyricsContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        placeholder = { Text("Paste lyrics here...") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pastedLyricsContent.isNotBlank()) {
                        LyricsManager.saveLyrics(context, song, pastedLyricsContent)
                        refreshTrigger++
                        Toast.makeText(context, "Lyrics saved successfully", Toast.LENGTH_SHORT).show()
                    }
                    showPasteDialog = false
                }) {
                    Text("Save Lyrics")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * HERO ARTWORK & SONG DETAILS VIEW
 */
@Composable
private fun ArtworkMainStage(
    song: Song,
    isPlaying: Boolean,
    lyrics: List<LyricLine>,
    hasTimestamps: Boolean,
    activeVerseIndex: Int,
    onToggleLyrics: () -> Unit,
    onToggleFavorite: () -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Large Album Artwork Card with vinyl breathing animation
        val artworkScale by animateFloatAsState(
            targetValue = if (isPlaying) 1.0f else 0.94f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
            label = "ArtworkBreathingScale"
        )

        Surface(
            modifier = Modifier
                .size(280.dp)
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                }
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp))
                .clickable { onToggleLyrics() },
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                SongImagePlaceholder(
                    title = song.title,
                    modifier = Modifier.fillMaxSize(),
                    size = 280f
                )

                // Lossless / Format Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "HD AUDIO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Interactive Lyrics Badge Indicator
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = accentColor
                        )
                        Text(
                            text = if (lyrics.isNotEmpty()) "LYRICS AVAILABLE" else "TAP FOR LYRICS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Song Title & Artist Header with Favorite Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Animated Favorite Heart
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(48.dp)
            ) {
                val heartScale by animateFloatAsState(
                    targetValue = if (song.isFavorite) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.4f),
                    label = "HeartScale"
                )
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (song.isFavorite) "Liked" else "Add to favorites",
                    tint = if (song.isFavorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            scaleX = heartScale
                            scaleY = heartScale
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Mini Live Lyrics Strip (Shows active verse and expands on tap)
        Surface(
            onClick = onToggleLyrics,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )

                    Column {
                        Text(
                            text = "LYRICS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = accentColor
                        )
                        if (lyrics.isEmpty()) {
                            Text(
                                text = "Tap to search or add lyrics",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val activeText = if (hasTimestamps && activeVerseIndex >= 0) {
                                lyrics.getOrNull(activeVerseIndex)?.text ?: ""
                            } else {
                                lyrics.firstOrNull()?.text ?: ""
                            }
                            Text(
                                text = activeText.ifEmpty { "🎵 Instrumental section..." },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Expand Lyrics",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * FULLSCREEN / DEDICATED LYRICS STAGE
 * Immersive scrolling lyrics view with centered active verse, karaoke typography, and user scroll awareness.
 */
@Composable
private fun FullLyricsStage(
    song: Song,
    lyrics: List<LyricLine>,
    hasTimestamps: Boolean,
    activeVerseIndex: Int,
    lyricsListState: androidx.compose.foundation.lazy.LazyListState,
    userScrolledAway: Boolean,
    fontSizeOption: Int,
    onSeekTo: (Long) -> Unit,
    onJumpToCurrent: () -> Unit,
    onAddLyricsClick: () -> Unit,
    onSearchWebClick: () -> Unit,
    onPasteClick: () -> Unit,
    onPickFileClick: () -> Unit,
    onCreateLyricsClick: () -> Unit,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
    ) {
        if (lyrics.isEmpty()) {
            // Empty Lyrics State with direct actionable buttons
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "No lyrics found",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Find, import, or sync lyrics for \"${song.title}\" to sing along in real time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSearchWebClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search Web")
                    }

                    FilledTonalButton(
                        onClick = onPasteClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickFileClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick File")
                    }

                    Button(
                        onClick = onCreateLyricsClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Editor")
                    }
                }
            }
        } else {
            // Live Lyrics Stream
            val baseFontSize = when (fontSizeOption) {
                0 -> 16.sp
                2 -> 24.sp
                else -> 20.sp
            }

            LazyColumn(
                state = lyricsListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 100.dp, bottom = 120.dp, start = 20.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isActive = hasTimestamps && index == activeVerseIndex
                    val isPast = hasTimestamps && index < activeVerseIndex
                    val targetScale = if (isActive) 1.06f else 0.98f
                    val targetAlpha = if (isActive) 1.0f else if (isPast) 0.45f else 0.65f

                    val scale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                        label = "LyricScaleAnim"
                    )

                    val alpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = tween(300),
                        label = "LyricAlphaAnim"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isActive) accentColor.copy(alpha = 0.18f) else Color.Transparent
                            )
                            .clickable(enabled = line.isDynamic) {
                                onSeekTo(line.timeMs)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = if (isActive) (baseFontSize.value + 4).sp else baseFontSize
                            ),
                            textAlign = TextAlign.Center,
                            color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                        )
                    }
                }
            }

            // Top Status Overlay (Options & Header)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (hasTimestamps) Icons.Default.Speed else Icons.Default.Notes,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accentColor
                    )
                    Text(
                        text = if (hasTimestamps) "Time-Synced Lyrics" else "Plain Text Lyrics",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onAddLyricsClick) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Lyrics Settings",
                        tint = accentColor
                    )
                }
            }

            // Floating "Jump to Current" pill when user scrolled away
            AnimatedVisibility(
                visible = userScrolledAway && hasTimestamps && activeVerseIndex >= 0,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                ElevatedButton(
                    onClick = onJumpToCurrent,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    shape = CircleShape,
                    elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Jump to Current Line", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSecs = (durationMs / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
