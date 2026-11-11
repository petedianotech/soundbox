package com.example.ui.screens.nowplaying

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Song
import com.example.player.LyricLine
import com.example.player.LyricsManager
import com.example.ui.components.ArtworkThumbnail
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.navigation.sharedSongArt
import com.example.ui.navigation.sharedSongTitle
import com.example.ui.screens.lockscreen.LockscreenPlayerActivity
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel
import com.example.util.AlbumArtHelper
import com.example.util.rememberSongPalette

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

    val context = LocalContext.current
    val currentView = LocalView.current
    val settingsManager = remember { com.example.util.SettingsManager(context) }
    val keepScreenAwake by settingsManager.keepScreenOn.collectAsState()

    var showTimerDialog by remember { mutableStateOf(false) }
    var showSongOptionsSheet by remember { mutableStateOf(false) }
    var showInPlayerLyrics by remember { mutableStateOf(false) }
    var totalDrag by remember { mutableFloatStateOf(0f) }

    DisposableEffect(keepScreenAwake) {
        if (keepScreenAwake) currentView.keepScreenOn = true
        onDispose { currentView.keepScreenOn = false }
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
    // Extract dynamic primary and ambient palette colors from the album artwork
    val palette = rememberSongPalette(song, context)
    val accentColor = palette.primary

    val progress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderFraction by remember { mutableFloatStateOf(0f) }
    val currentDisplayFraction = if (isDraggingSlider) dragSliderFraction else progress
    val currentDisplayPosition = if (isDraggingSlider && duration > 0L) (dragSliderFraction * duration).toLong() else position

    // Load synchronized lyrics for live in-player display
    var lyricsList by remember(song.id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    LaunchedEffect(song.id) {
        val raw = LyricsManager.loadLyrics(context, song)
        lyricsList = if (!raw.isNullOrBlank()) {
            LyricsManager.parseLyrics(raw, duration)
        } else {
            emptyList()
        }
    }

    val activeLyricIndex = remember(lyricsList, position) {
        if (lyricsList.isEmpty()) -1
        else lyricsList.indexOfLast { it.timeMs <= position }
    }

    val currentLyricSnippet = if (activeLyricIndex in lyricsList.indices) {
        lyricsList[activeLyricIndex].text
    } else {
        null
    }

    val lyricsListState = rememberLazyListState()
    LaunchedEffect(activeLyricIndex, showInPlayerLyrics) {
        if (showInPlayerLyrics && activeLyricIndex >= 0 && activeLyricIndex < lyricsList.size) {
            val target = (activeLyricIndex - 2).coerceAtLeast(0)
            lyricsListState.animateScrollToItem(target)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = accentColor
                        )
                        Text(
                            text = song.album.ifEmpty { "Audio Library" },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = onBackClick,
                        shape = CircleShape,
                        color = palette.surfaceTint,
                        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(start = 12.dp).size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse",
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }
                },
                actions = {
                    // Lockscreen Player shortcut
                    Surface(
                        onClick = {
                            val lockIntent = Intent(context, LockscreenPlayerActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(lockIntent)
                        },
                        shape = CircleShape,
                        color = palette.surfaceTint,
                        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(end = 6.dp).size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.LockClock,
                                contentDescription = "Lockscreen Player",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Equalizer button
                    Surface(
                        onClick = onNavigateToEqualizer,
                        shape = CircleShape,
                        color = palette.surfaceTint,
                        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(end = 6.dp).size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Equalizer,
                                contentDescription = "Equalizer",
                                tint = if (eqEnabled) accentColor else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Song options menu
                    Surface(
                        onClick = { showSongOptionsSheet = true },
                        shape = CircleShape,
                        color = palette.surfaceTint,
                        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(end = 12.dp).size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Song options",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = palette.darkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.darkBackground)
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
            // 1. PALETTE BLURRED BACKDROP FOR ALBUM ART
            val artworkModel = remember(song.id) {
                AlbumArtHelper.getArtworkModel(context, song.id, song.title, song.artist, song.genre, song.path)
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkModel)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.45f }
                    .blur(radius = 55.dp)
            )

            // 2. LAYERED GRADIENT MASKS (Deep contrast & atmospheric primary glow)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                palette.darkBackground.copy(alpha = 0.72f),
                                palette.darkBackground.copy(alpha = 0.88f),
                                palette.darkBackground.copy(alpha = 0.98f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            radius = 900f
                        )
                    )
            )

            // 3. MAIN FOREGROUND CONTENT
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // CENTER STAGE: Toggle between Floating Album Art and Full Live Lyrics
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = showInPlayerLyrics,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "PlayerStageToggle"
                    ) { isLyricsShowing ->
                        if (isLyricsShowing) {
                            // FULL SCROLLING SYNCHRONIZED KARAOKE LYRICS
                            if (lyricsList.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Lyrics,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("No Synchronized Lyrics Yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Tap below to open Lyrics Creator", color = palette.textSecondary, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = onNavigateToLyricsCreator,
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                    ) {
                                        Text("Open Lyrics Creator", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = lyricsListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    itemsIndexed(lyricsList) { idx, line ->
                                        val isActive = idx == activeLyricIndex
                                        Text(
                                            text = line.text,
                                            color = if (isActive) accentColor else Color.White.copy(alpha = 0.5f),
                                            fontSize = if (isActive) 21.sp else 16.sp,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.seekTo(line.timeMs) }
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // ALBUM ART STAGE WITH PALETTE GLOW
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(270.dp)
                                        .shadow(
                                            elevation = 26.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            ambientColor = accentColor,
                                            spotColor = accentColor
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(palette.surfaceTint)
                                        .sharedSongArt(song.id)
                                        .clickable { showInPlayerLyrics = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ArtworkThumbnail(
                                        songId = song.id,
                                        title = song.title,
                                        artist = song.artist,
                                        genre = song.genre,
                                        path = song.path,
                                        size = 270f,
                                        isCircle = false,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Track title & Artist
                                Text(
                                    text = song.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .sharedSongTitle(song.id)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = song.artist,
                                    color = palette.textSecondary,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Floating Frosted Glass Live Lyric Snippet Pill
                                Surface(
                                    onClick = { showInPlayerLyrics = true },
                                    shape = RoundedCornerShape(18.dp),
                                    color = palette.surfaceTint,
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .testTag("in_player_lyric_pill")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = currentLyricSnippet ?: "Tap to view live synced lyrics",
                                            color = if (currentLyricSnippet != null) Color.White else palette.textSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Lyrics,
                                            contentDescription = "Expand Lyrics",
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // PROGRESS SLIDER WITH PALETTE ACCENT
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = currentDisplayFraction,
                        onValueChange = { frac ->
                            isDraggingSlider = true
                            dragSliderFraction = frac
                        },
                        onValueChangeFinished = {
                            if (duration > 0L) {
                                viewModel.seekTo((dragSliderFraction * duration).toLong())
                            }
                            isDraggingSlider = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nowplaying_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(currentDisplayPosition), color = palette.textSecondary, fontSize = 12.sp)
                        Text(formatDuration(duration), color = palette.textSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECONDARY ACTION BUTTONS (Shuffle, Favorite, Repeat, Live Lyrics Toggle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.setShuffleMode(!shuffleMode) }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (shuffleMode) accentColor else palette.textSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                        Icon(
                            if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) Color.Red else Color.White
                        )
                    }
                    IconButton(
                        onClick = { showInPlayerLyrics = !showInPlayerLyrics },
                        modifier = Modifier.testTag("toggle_lyrics_display_button")
                    ) {
                        Icon(
                            if (showInPlayerLyrics) Icons.Default.Album else Icons.Default.Lyrics,
                            contentDescription = "Toggle Lyrics",
                            tint = if (showInPlayerLyrics) accentColor else palette.textSecondary
                        )
                    }
                    IconButton(onClick = {
                        val next = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                        viewModel.setRepeatMode(next)
                    }) {
                        Icon(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) accentColor else palette.textSecondary
                        )
                    }
                    IconButton(onClick = { showTimerDialog = true }) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = "Sleep timer",
                            tint = if (sleepTimerLeft > 0) accentColor else palette.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // PRIMARY PLAYBACK CONTROLS (Prev, Glowing Big Play/Pause, Next)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.skipPrevious() },
                        modifier = Modifier.size(56.dp).testTag("nowplaying_prev_button")
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp), tint = Color.White)
                    }

                    Surface(
                        onClick = { viewModel.playPause() },
                        shape = CircleShape,
                        color = accentColor,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(72.dp).testTag("nowplaying_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(40.dp),
                                tint = Color.Black
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.skipNext() },
                        modifier = Modifier.size(56.dp).testTag("nowplaying_next_button")
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp), tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Swipe left/right to change track • Tap artwork for full lyrics",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showTimerDialog) {
        com.example.ui.components.SleepTimerDialog(viewModel = viewModel, onDismiss = { showTimerDialog = false })
    }

    if (showSongOptionsSheet) {
        com.example.ui.components.SongOptionsBottomSheet(
            song = song,
            viewModel = viewModel,
            onDismiss = { showSongOptionsSheet = false },
            onNavigateToLyrics = onNavigateToLyricsCreator
        )
    }
}
