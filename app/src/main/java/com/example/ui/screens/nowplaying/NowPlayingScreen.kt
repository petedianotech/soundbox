package com.example.ui.screens.nowplaying

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import com.example.R
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalView
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
import com.example.util.AlbumArtHelper
import com.example.ui.components.ArtworkThumbnail
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.RealtimeAudioVisualizer
import com.example.ui.components.VisualizerStyle
import com.example.ui.components.SongImagePlaceholder
import com.example.ui.components.SoundCutterDialog
import com.example.ui.components.TrackRow
import com.example.ui.components.poweramp.PowerampHiResBanner
import com.example.ui.components.poweramp.PowerampRotaryKnob
import com.example.ui.components.poweramp.PowerampWaveformBar
import com.example.ui.theme.Poweramp_Amber
import com.example.ui.theme.Poweramp_Cyan
import com.example.ui.theme.Poweramp_Lime
import com.example.ui.theme.SoundboxTheme
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
    onNavigateToLyricsCreator: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {}
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleMode by viewModel.shuffleMode.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val sleepTimerLeft by viewModel.sleepTimerMillis.collectAsState()
    val eqEnabled by viewModel.equalizerEnabled.collectAsState()
    val bassStrength by viewModel.bassBoostStrength.collectAsState()
    val virtualizerStrength by viewModel.virtualizerStrength.collectAsState()
    val audioSessionId by viewModel.audioSessionId.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

    val context = LocalContext.current
    val currentView = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val settingsManager = remember { com.example.util.SettingsManager(context) }
    val keepScreenAwake by settingsManager.keepScreenOn.collectAsState()
    val visualizerEnabled by settingsManager.visualizerEnabled.collectAsState()
    val visualizerStyle by settingsManager.visualizerStyle.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()

    // View & Overlay states
    var isLyricsViewActive by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSoundCutterDialog by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsOptionsSheet by remember { mutableStateOf(false) }
    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedLyricsContent by remember { mutableStateOf("") }
    var showRemainingTime by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var isDownloadingLyricsOnline by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Lyrics configuration states
    var lyricsFontSizeOption by remember { mutableStateOf(1) } // 0: Small, 1: Medium, 2: Large
    var userScrolledAway by remember { mutableStateOf(false) }

    fun triggerDownloadLyricsOnline() {
        val song = currentSong ?: return
        coroutineScope.launch {
            isDownloadingLyricsOnline = true
            try {
                val fetched = LyricsManager.fetchLyricsOnline(song)
                if (!fetched.isNullOrBlank()) {
                    LyricsManager.saveLyrics(context, song, fetched)
                    refreshTrigger++
                    Toast.makeText(context, "Lyrics downloaded & synced successfully!", Toast.LENGTH_SHORT).show()
                    isLyricsViewActive = true
                } else {
                    Toast.makeText(context, "No exact online match found. Opening web search...", Toast.LENGTH_SHORT).show()
                    LyricsManager.searchLyricsWeb(context, song, "google")
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isDownloadingLyricsOnline = false
            }
        }
    }

    DisposableEffect(keepScreenAwake) {
        if (keepScreenAwake) {
            currentView.keepScreenOn = true
        }
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    // Permanent Album Art State & Picker
    var artRefreshTrigger by remember { mutableIntStateOf(0) }
    val albumArtPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { pickedUri ->
            if (currentSong != null) {
                try {
                    context.contentResolver.openInputStream(pickedUri)?.use { stream ->
                        val success = AlbumArtHelper.saveCustomArtwork(context, currentSong!!, stream)
                        if (success) {
                            artRefreshTrigger++
                            Toast.makeText(context, "Permanent album art saved for this song", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Could not process selected image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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

    // Auto-center lyrics when active lyric line changes or lyrics view is opened
    LaunchedEffect(activeVerseIndex, isLyricsViewActive) {
        if (isLyricsViewActive && hasTimestamps && activeVerseIndex in lyrics.indices) {
            lyricsListState.animateScrollToItem(
                index = activeVerseIndex,
                scrollOffset = 0
            )
        }
    }

    // Cohesive, Stable Theme Palette
    val colors = SoundboxTheme.colors
    val accentColor = colors.accentCyan
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
                            color = accentColor
                        )
                        Text(
                            text = song.album.ifEmpty { "Audio Library" },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = onBackClick,
                        shape = CircleShape,
                        shadowElevation = 3.dp,
                        color = colors.surfaceElevated.copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse Player",
                                modifier = Modifier.size(24.dp),
                                tint = colors.textPrimary
                            )
                        }
                    }
                },
                actions = {
                    // Equalizer & Audio Effects
                    Surface(
                        onClick = onNavigateToEqualizer,
                        shape = CircleShape,
                        shadowElevation = 3.dp,
                        color = colors.surfaceElevated.copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.4f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            BadgedBox(badge = {
                                if (eqEnabled || bassStrength > 0 || virtualizerStrength > 0) {
                                    Badge(containerColor = colors.accentLime) { Text("DSP", color = Color.Black) }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Equalizer,
                                    contentDescription = "Equalizer & DSP",
                                    tint = if (eqEnabled || bassStrength > 0 || virtualizerStrength > 0) colors.accentCyan else colors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // More Menu (Song & Playlist Options)
                    Surface(
                        onClick = { showLyricsOptionsSheet = true },
                        shape = CircleShape,
                        shadowElevation = 3.dp,
                        color = colors.surfaceElevated.copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Song & Options",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding)
        ) {
            if (isLandscape) {
                // High-End Studio 2-Pane Console for Landscape
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Pane: Artwork Stage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        key(artRefreshTrigger, song.id) {
                            ArtworkMainStage(
                                song = song,
                                isPlaying = isPlaying,
                                lyrics = lyrics,
                                hasTimestamps = hasTimestamps,
                                activeVerseIndex = activeVerseIndex,
                                onToggleLyrics = { isLyricsViewActive = !isLyricsViewActive },
                                onToggleFavorite = { viewModel.toggleFavorite(song) },
                                accentColor = accentColor
                            )
                        }
                    }

                    // Right Pane: Lyrics or Controls
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLyricsViewActive) {
                            FullLyricsStage(
                                song = song,
                                lyrics = lyrics,
                                hasTimestamps = hasTimestamps,
                                activeVerseIndex = activeVerseIndex,
                                lyricsListState = lyricsListState,
                                userScrolledAway = userScrolledAway,
                                fontSizeOption = lyricsFontSizeOption,
                                isDownloadingOnline = isDownloadingLyricsOnline,
                                onDownloadOnlineClick = { triggerDownloadLyricsOnline() },
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
                                onDismissClick = { isLyricsViewActive = false },
                                accentColor = accentColor
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Waveform seekbar
                                PowerampWaveformBar(
                                    currentPosition = position,
                                    duration = duration,
                                    isPlaying = isPlaying,
                                    onSeek = { targetMs -> viewModel.seekTo(targetMs) },
                                    accentColor = colors.accentCyan,
                                    seedKey = song.id.toString(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
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
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = colors.accentCyan
                                    )
                                    Text(
                                        text = if (showRemainingTime) "-${formatDuration((duration - position).coerceAtLeast(0L))}" else formatDuration(duration),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = colors.textSecondary,
                                        modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                                    )
                                }

                                // Secondary controls in Landscape
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = { viewModel.setShuffleMode(!shuffleMode) },
                                        shape = CircleShape,
                                        shadowElevation = 3.dp,
                                        color = if (shuffleMode) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (shuffleMode) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Shuffle,
                                                contentDescription = "Shuffle",
                                                tint = if (shuffleMode) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = { showSpeedDialog = true },
                                        shape = CircleShape,
                                        shadowElevation = 3.dp,
                                        color = if (playbackSpeed != 1.0f) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (playbackSpeed != 1.0f) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${String.format(Locale.getDefault(), "%.1f", playbackSpeed)}x", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Surface(
                                        onClick = { showTimerDialog = true },
                                        shape = CircleShape,
                                        shadowElevation = 3.dp,
                                        color = if (sleepTimerLeft > 0) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (sleepTimerLeft > 0) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (sleepTimerLeft > 0) accentColor else MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (sleepTimerLeft > 0) "${(sleepTimerLeft / 60000)}m" else "Timer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            val nextMode = when (repeatMode) {
                                                 Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                                 Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                                 else -> Player.REPEAT_MODE_OFF
                                            }
                                            viewModel.setRepeatMode(nextMode)
                                        },
                                        shape = CircleShape,
                                        shadowElevation = 3.dp,
                                        color = if (repeatMode != Player.REPEAT_MODE_OFF) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (repeatMode != Player.REPEAT_MODE_OFF) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            val icon = when (repeatMode) {
                                                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                                else -> Icons.Default.Repeat
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = "Repeat Mode",
                                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Primary Circular Playback Controls Row (Landscape: SkipBack, Prev, Hero Play/Pause, Next, SkipForward)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Skip Back (-10s) Circular Button
                                    Surface(
                                        onClick = { viewModel.seekBackward(10000L) },
                                        shape = CircleShape,
                                        shadowElevation = 3.dp,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.5f)),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_replay_10),
                                                contentDescription = "Rewind 10s",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Previous Song Circular Button
                                    Surface(
                                        onClick = { viewModel.skipPrevious() },
                                        shape = CircleShape,
                                        shadowElevation = 5.dp,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.6f)),
                                        modifier = Modifier.size(50.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.SkipPrevious,
                                                contentDescription = "Previous Song",
                                                modifier = Modifier.size(26.dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    // Play/Pause Hero Circular Button (Largest in center)
                                    Surface(
                                        onClick = { viewModel.playPause() },
                                        shape = CircleShape,
                                        shadowElevation = 8.dp,
                                        color = accentColor,
                                        modifier = Modifier.size(66.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }

                                    // Next Song Circular Button
                                    Surface(
                                        onClick = { viewModel.skipNext() },
                                        shape = CircleShape,
                                        shadowElevation = 5.dp,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.6f)),
                                        modifier = Modifier.size(50.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.SkipNext,
                                                contentDescription = "Next Song",
                                                modifier = Modifier.size(26.dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    // Skip Forward (+10s) Circular Button
                                    Surface(
                                        onClick = { viewModel.seekForward(10000L) },
                                        shape = CircleShape,
                                        shadowElevation = 3.dp,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.5f)),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_forward_10),
                                                contentDescription = "Forward 10s",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // Quick Action Dock
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { showQueueSheet = true },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                        modifier = Modifier.height(36.dp).weight(1f)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Queue", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }

                                    FilledTonalButton(
                                        onClick = { showTimerDialog = true },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (sleepTimerLeft > 0) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                        modifier = Modifier.height(36.dp).weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Timer,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (sleepTimerLeft > 0) accentColor else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Timer", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
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
                                isDownloadingOnline = isDownloadingLyricsOnline,
                                onDownloadOnlineClick = { triggerDownloadLyricsOnline() },
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
                                onDismissClick = { isLyricsViewActive = false },
                                accentColor = accentColor
                            )
                        } else {
                            // HERO ARTWORK & SONG DETAILS VIEW
                            key(artRefreshTrigger, song.id) {
                                ArtworkMainStage(
                                    song = song,
                                    isPlaying = isPlaying,
                                    lyrics = lyrics,
                                    hasTimestamps = hasTimestamps,
                                    activeVerseIndex = activeVerseIndex,
                                    onToggleLyrics = { isLyricsViewActive = !isLyricsViewActive },
                                    onToggleFavorite = { viewModel.toggleFavorite(song) },
                                    accentColor = accentColor
                                )
                            }
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
                    if (visualizerEnabled && !isLyricsViewActive) {
                        RealtimeAudioVisualizer(
                            audioSessionId = audioSessionId,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            accentColor = accentColor,
                            secondaryColor = secondaryColor,
                            onDismiss = {
                                settingsManager.setVisualizerEnabled(false)
                            }
                        )
                    }

                    // Poweramp Pro-Audio Interactive Waveform Seekbar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        PowerampWaveformBar(
                            currentPosition = position,
                            duration = duration,
                            isPlaying = isPlaying,
                            onSeek = { targetMs -> viewModel.seekTo(targetMs) },
                            accentColor = colors.accentCyan,
                            seedKey = song.id.toString(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration(position),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                ),
                                color = colors.accentCyan
                            )

                            // Tap to switch between total duration and remaining duration
                            Text(
                                text = if (showRemainingTime) {
                                    "-${formatDuration((duration - position).coerceAtLeast(0L))}"
                                } else {
                                    formatDuration(duration)
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                ),
                                color = colors.textSecondary,
                                modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Controls: Shuffle, Speed, Sleep Timer, Repeat (Modern Circular / Pill Controls)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle Button (Fully Circular Pill with Elevation)
                        Surface(
                            onClick = { viewModel.setShuffleMode(!shuffleMode) },
                            shape = CircleShape,
                            shadowElevation = 3.dp,
                            color = if (shuffleMode) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (shuffleMode) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (shuffleMode) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Playback Speed Pill Button
                        Surface(
                            onClick = { showSpeedDialog = true },
                            shape = CircleShape,
                            shadowElevation = 3.dp,
                            color = if (playbackSpeed != 1.0f) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (playbackSpeed != 1.0f) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = if (playbackSpeed != 1.0f) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${String.format(Locale.getDefault(), "%.1f", playbackSpeed)}x",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (playbackSpeed != 1.0f) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Sleep Timer Pill Button
                        Surface(
                            onClick = { showTimerDialog = true },
                            shape = CircleShape,
                            shadowElevation = 3.dp,
                            color = if (sleepTimerLeft > 0) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (sleepTimerLeft > 0) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Sleep Timer",
                                    tint = if (sleepTimerLeft > 0) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (sleepTimerLeft > 0) "${(sleepTimerLeft / 60000)}m" else "Timer",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (sleepTimerLeft > 0) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Repeat Button (Fully Circular with Elevation)
                        Surface(
                            onClick = {
                                val nextMode = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                viewModel.setRepeatMode(nextMode)
                            },
                            shape = CircleShape,
                            shadowElevation = 3.dp,
                            color = if (repeatMode != Player.REPEAT_MODE_OFF) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (repeatMode != Player.REPEAT_MODE_OFF) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val icon = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "Repeat Mode",
                                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Playback Controls Deck: 5 Circular Buttons with Elevation
                    // [Skip Back 10s] - [Previous] - [PLAY/PAUSE HERO] - [Next] - [Skip Forward 10s]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Skip Back (-10s) Circular Button
                        Surface(
                            onClick = { viewModel.seekBackward(10000L) },
                            shape = CircleShape,
                            shadowElevation = 4.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.5f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_replay_10),
                                    contentDescription = "Rewind 10 Seconds",
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 2. Previous Song Circular Button
                        Surface(
                            onClick = { viewModel.skipPrevious() },
                            shape = CircleShape,
                            shadowElevation = 6.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.6f)),
                            modifier = Modifier.size(58.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Song",
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 3. Play/Pause Hero Circular Button (Largest in center)
                        Surface(
                            onClick = { viewModel.playPause() },
                            shape = CircleShape,
                            shadowElevation = 10.dp,
                            color = accentColor,
                            modifier = Modifier.size(78.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                val playIconScale by animateFloatAsState(
                                    targetValue = if (isPlaying) 1.08f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.5f),
                                    label = "PlayIconScale"
                                )
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .graphicsLayer {
                                            scaleX = playIconScale
                                            scaleY = playIconScale
                                        }
                                )
                            }
                        }

                        // 4. Next Song Circular Button
                        Surface(
                            onClick = { viewModel.skipNext() },
                            shape = CircleShape,
                            shadowElevation = 6.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.6f)),
                            modifier = Modifier.size(58.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Song",
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 5. Skip Forward (+10s) Circular Button
                        Surface(
                            onClick = { viewModel.seekForward(10000L) },
                            shape = CircleShape,
                            shadowElevation = 4.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.5f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_forward_10),
                                    contentDescription = "Forward 10 Seconds",
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bottom Capsule Action Pill Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = { showQueueSheet = true },
                            shape = CircleShape,
                            shadowElevation = 3.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .height(40.dp)
                                .weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Queue (${queue.size})", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Surface(
                            onClick = { isLyricsViewActive = !isLyricsViewActive },
                            shape = CircleShape,
                            shadowElevation = 3.dp,
                            color = if (isLyricsViewActive) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isLyricsViewActive) accentColor else colors.border.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .height(40.dp)
                                .weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Lyrics,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isLyricsViewActive) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lyrics", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
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
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp)
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                if (queue.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
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
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(queue) { index, queueSong ->
                            val isCurrent = queueSong.id == song.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) accentColor.copy(alpha = 0.15f) else Color.Transparent,
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
                                        color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(28.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = queueSong.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
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
                                            tint = accentColor,
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

    // PLAYBACK & TRACK OPTIONS BOTTOM SHEET (3-DOT OVERFLOW MENU)
    if (showLyricsOptionsSheet) {
        val hasCustomArt = remember(song.id, artRefreshTrigger) {
            AlbumArtHelper.hasCustomArtwork(context, song.id)
        }
        val hasSavedLyrics = remember(song.id, refreshTrigger) { LyricsManager.loadLyrics(context, song) != null }

        ModalBottomSheet(
            onDismissRequest = { showLyricsOptionsSheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 28.dp)
            ) {
                // Track Header Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        ) {
                            key(artRefreshTrigger, song.id) {
                                ArtworkThumbnail(
                                    songId = song.id,
                                    title = song.title,
                                    artist = song.artist,
                                    genre = song.genre,
                                    path = song.path,
                                    size = 52f
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${song.artist} • ${song.album}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { showLyricsOptionsSheet = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Clean Modern Action Menu Items (Simplified and Focused on Track Customization)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Change Album Cover
                    OptionMenuItem(
                        icon = Icons.Default.PhotoLibrary,
                        iconTint = accentColor,
                        title = if (hasCustomArt) "Change Custom Cover Art" else "Set Custom Album Cover",
                        subtitle = "Pick a photo from gallery for this song",
                        onClick = {
                            showLyricsOptionsSheet = false
                            albumArtPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    if (hasCustomArt) {
                        OptionMenuItem(
                            icon = Icons.Default.Restore,
                            iconTint = MaterialTheme.colorScheme.error,
                            title = "Reset Cover Art",
                            subtitle = "Revert back to default embedded artwork",
                            onClick = {
                                val customFile = AlbumArtHelper.getArtworkFile(context, song.id)
                                if (customFile.exists()) customFile.delete()
                                artRefreshTrigger++
                                Toast.makeText(context, "Reverted to default artwork", Toast.LENGTH_SHORT).show()
                                showLyricsOptionsSheet = false
                            }
                        )
                    }

                    // Option 2: Sound Cutter Tool
                    OptionMenuItem(
                        icon = Icons.Default.ContentCut,
                        iconTint = colors.accentLime,
                        title = "Sound Cutter Tool",
                        subtitle = "Trim audio files and export custom clips",
                        onClick = {
                            showLyricsOptionsSheet = false
                            showSoundCutterDialog = true
                        }
                    )

                    // Option 3: Lyrics & LRC Studio (Intro Cutter & Karaoke Sync)
                    OptionMenuItem(
                        icon = Icons.Default.EditNote,
                        iconTint = colors.accentCyan,
                        title = "Lyrics & LRC Sync Studio",
                        subtitle = "Edit lines, align video intros & test karaoke live",
                        onClick = {
                            showLyricsOptionsSheet = false
                            onNavigateToLyricsCreator()
                        }
                    )

                    // Option 4: Share Track
                    OptionMenuItem(
                        icon = Icons.Default.Share,
                        iconTint = colors.accentLime,
                        title = "Share Track",
                        subtitle = "Share song details via messaging apps",
                        onClick = {
                            showLyricsOptionsSheet = false
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Now playing \"${song.title}\" by ${song.artist} on Soundbox Pro")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
                        }
                    )

                    // Option 5: Track Info & Details
                    OptionMenuItem(
                        icon = Icons.Default.Info,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Track Details & Metadata",
                        subtitle = "Path, audio duration and file information",
                        onClick = {
                            showLyricsOptionsSheet = false
                            showTrackInfoDialog = true
                        }
                    )
                }
            }
        }
    }

    // ADD TO PLAYLIST DIALOG
    if (showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = {
                Text(
                    text = "Add Track to Playlist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showAddToPlaylistDialog = false
                            showCreatePlaylistDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create New Playlist")
                    }

                    if (allPlaylists.isEmpty()) {
                        Text(
                            text = "No user playlists found. Create one above to add songs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(allPlaylists) { pl ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addSongToPlaylist(pl.id, song.id)
                                            Toast.makeText(context, "Added to \"${pl.name}\"", Toast.LENGTH_SHORT).show()
                                            showAddToPlaylistDialog = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pl.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = "${pl.songIds.size} tracks",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // CREATE PLAYLIST DIALOG
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = {
                Text(
                    text = "New Playlist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            viewModel.createPlaylistWithSongs(name, listOf(song.id))
                            Toast.makeText(context, "Created \"$name\" with track", Toast.LENGTH_SHORT).show()
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    enabled = newPlaylistName.trim().isNotEmpty()
                ) {
                    Text("Create & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
        com.example.ui.components.SleepTimerDialog(
            viewModel = viewModel,
            onDismiss = { showTimerDialog = false }
        )
    }

    // PLAYBACK SPEED DIALOG
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Text(
                    "Playback Speed",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speeds.take(4).forEach { speed ->
                            val isSelected = (playbackSpeed == speed)
                            Surface(
                                onClick = {
                                    viewModel.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                },
                                shape = CircleShape,
                                shadowElevation = if (isSelected) 4.dp else 1.dp,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${speed}x",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speeds.drop(4).forEach { speed ->
                            val isSelected = (playbackSpeed == speed)
                            Surface(
                                onClick = {
                                    viewModel.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                },
                                shape = CircleShape,
                                shadowElevation = if (isSelected) 4.dp else 1.dp,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${speed}x",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Surface(
                    onClick = { showSpeedDialog = false },
                    shape = CircleShape,
                    shadowElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.height(36.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                        Text("Close", style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // SOUND CUTTER DIALOG
    if (showSoundCutterDialog) {
        SoundCutterDialog(
            song = song,
            currentPlaybackPosition = position,
            viewModel = viewModel,
            onDismiss = { showSoundCutterDialog = false }
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
                        style = MaterialTheme.typography.bodyMedium.copy(color = accentColor, fontWeight = FontWeight.Bold)
                    )
                }
                Slider(
                    value = bassStrength.toFloat(),
                    valueRange = 0f..1000f,
                    onValueChange = { viewModel.setBassBoost(it.toInt()) },
                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
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
    onMoreOptions: () -> Unit = {},
    accentColor: Color
) {
    val colors = SoundboxTheme.colors
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
                ArtworkThumbnail(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    genre = song.genre,
                    path = song.path,
                    modifier = Modifier.fillMaxSize(),
                    size = 280f
                )

                // Quick Tap-for-Lyrics Hint Badge
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lyrics,
                            contentDescription = "Tap artwork for lyrics",
                            tint = if (lyrics.isNotEmpty()) accentColor else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Lyrics",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Capsule Pill Action Row: Like/Dislike joined pill on Left + 3-Dot More Circle on Right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Joined Capsule Pill for Thumbs Up / Thumbs Down
            Surface(
                shape = CircleShape,
                color = colors.surfaceElevated.copy(alpha = 0.95f),
                shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    val likeScale by animateFloatAsState(
                        targetValue = if (song.isFavorite) 1.2f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.4f),
                        label = "LikeScale"
                    )
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = if (song.isFavorite) "Liked" else "Like song",
                            tint = if (song.isFavorite) accentColor else colors.textSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    scaleX = likeScale
                                    scaleY = likeScale
                                }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(colors.border.copy(alpha = 0.6f))
                    )

                    IconButton(
                        onClick = { /* Dislike feedback */ },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ThumbDown,
                            contentDescription = "Dislike song",
                            tint = colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Circular More Options Button
            Surface(
                onClick = onMoreOptions,
                shape = CircleShape,
                color = colors.surfaceElevated.copy(alpha = 0.95f),
                shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.5f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Song Title & Artist Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${song.artist} • ${song.album.ifEmpty { "Single" }}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

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
    isDownloadingOnline: Boolean,
    onDownloadOnlineClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onJumpToCurrent: () -> Unit,
    onAddLyricsClick: () -> Unit,
    onSearchWebClick: () -> Unit,
    onPasteClick: () -> Unit,
    onPickFileClick: () -> Unit,
    onCreateLyricsClick: () -> Unit,
    onDismissClick: () -> Unit,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
    ) {
        if (lyrics.isEmpty()) {
            // Empty Lyrics State with direct actionable buttons & smart match
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "No lyrics found",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                val parsed = remember(song.id) { LyricsManager.extractArtistAndTitle(song) }
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Smart Match: ${parsed.artist.ifBlank { "Unknown" }} — ${parsed.title}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary 1-Tap Online Download
                Button(
                    onClick = onDownloadOnlineClick,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    enabled = !isDownloadingOnline
                ) {
                    if (isDownloadingOnline) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Searching Online...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Lyrics (Auto-Sync)", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSearchWebClick,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Search Web", maxLines = 1, fontWeight = FontWeight.Medium)
                    }

                    FilledTonalButton(
                        onClick = onPasteClick,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste", maxLines = 1, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickFileClick,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick File", maxLines = 1, fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = onCreateLyricsClick,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync Editor", maxLines = 1, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismissClick,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back to Album Art", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            // Live Lyrics Stream with Automatic Centering
            val baseFontSize = when (fontSizeOption) {
                0 -> 16.sp
                2 -> 24.sp
                else -> 20.sp
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val verticalCenterPadding = remember(maxHeight) {
                    (maxHeight / 2) - 36.dp
                }

                LazyColumn(
                    state = lyricsListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = verticalCenterPadding.coerceAtLeast(60.dp),
                        bottom = verticalCenterPadding.coerceAtLeast(60.dp),
                        start = 16.dp,
                        end = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(lyrics) { index, line ->
                        val isActive = hasTimestamps && index == activeVerseIndex
                        val isPast = hasTimestamps && index < activeVerseIndex
                        val targetScale = if (isActive) 1.08f else 0.94f
                        val targetAlpha = if (isActive) 1.0f else if (isPast) 0.38f else 0.58f

                        val scale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                            label = "LyricScaleAnim"
                        )

                        val alpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(280),
                            label = "LyricAlphaAnim"
                        )

                        Surface(
                            shape = CircleShape,
                            color = if (isActive) accentColor.copy(alpha = 0.18f) else Color.Transparent,
                            border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = line.isDynamic) {
                                    onSeekTo(line.timeMs)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = if (isActive) FontWeight.Black else FontWeight.SemiBold,
                                        fontSize = if (isActive) (baseFontSize.value + 4).sp else baseFontSize,
                                        letterSpacing = if (isActive) 0.5.sp else 0.sp
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
                }
            }

            // Top Status Overlay (Options, Header & 1-Tap Return to Album Art)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { onDismissClick() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = "Return to Album Art",
                            modifier = Modifier.size(15.dp),
                            tint = accentColor
                        )
                        Text(
                            text = if (hasTimestamps) "Synced Lyrics • Tap for Art" else "Lyrics • Tap for Art",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCreateLyricsClick) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Full-Screen LRC & Sync Studio",
                            tint = accentColor
                        )
                    }
                    IconButton(onClick = onAddLyricsClick) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Lyrics Settings",
                            tint = accentColor
                        )
                    }
                    IconButton(onClick = onDismissClick) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Lyrics",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

@Composable
private fun OptionMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
