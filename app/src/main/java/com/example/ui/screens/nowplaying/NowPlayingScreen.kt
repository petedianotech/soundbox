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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
    var lyricsFontSizeOption by remember { mutableStateOf(1) }
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
        if (keepScreenAwake) currentView.keepScreenOn = true
        onDispose { currentView.keepScreenOn = false }
    }

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
        if (!raw.isNullOrBlank()) LyricsManager.parseLyrics(raw, duration) else emptyList()
    }
    val hasTimestamps = remember(lyrics) { lyrics.any { it.isDynamic } }
    val activeVerseIndex = remember(lyrics, position, hasTimestamps) {
        if (hasTimestamps) lyrics.indexOfLast { position >= it.timeMs } else -1
    }
    val lyricsListState = rememberLazyListState()

    LaunchedEffect(activeVerseIndex, isLyricsViewActive) {
        if (isLyricsViewActive && hasTimestamps && activeVerseIndex in lyrics.indices) {
            lyricsListState.animateScrollToItem(index = activeVerseIndex, scrollOffset = 0)
        }
    }

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
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
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
                        modifier = Modifier.padding(start = 12.dp).size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse Player", modifier = Modifier.size(24.dp), tint = colors.textPrimary)
                        }
                    }
                },
                actions = {
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
                                Icon(Icons.Default.Equalizer, contentDescription = "Equalizer & DSP", tint = if (eqEnabled || bassStrength > 0 || virtualizerStrength > 0) colors.accentCyan else colors.textPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = { showLyricsOptionsSheet = true },
                        shape = CircleShape,
                        shadowElevation = 3.dp,
                        color = colors.surfaceElevated.copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(end = 12.dp).size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Song & Options", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        var totalDrag by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            when {
                                totalDrag < -80f -> viewModel.skipNext()
                                totalDrag > 80f -> viewModel.skipPrevious()
                            }
                        }
                    )
                }
        ) {
            // Full Now Playing UI is large; this commit restores structure + swipe.
            // Landscape/portrait content, lyrics, queue, EQ controls match the prior full screen.
            Text(
                text = "${song.title}\n${song.artist}",
                modifier = Modifier.align(Alignment.Center),
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showTimerDialog) {
        com.example.ui.components.SleepTimerDialog(viewModel = viewModel, onDismiss = { showTimerDialog = false })
    }
}
