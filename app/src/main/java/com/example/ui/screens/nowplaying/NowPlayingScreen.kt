package com.example.ui.screens.nowplaying

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.data.model.Song
import com.example.player.LyricsManager
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.SongImagePlaceholder
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel

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
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

    val context = LocalContext.current
    val currentView = LocalView.current
    val settingsManager = remember { com.example.util.SettingsManager(context) }
    val keepScreenAwake by settingsManager.keepScreenOn.collectAsState()

    var showTimerDialog by remember { mutableStateOf(false) }
    var showSongOptionsSheet by remember { mutableStateOf(false) }
    var totalDrag by remember { mutableStateOf(0f) }

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
    val colors = SoundboxTheme.colors
    val accentColor = colors.accentCyan
    val progress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderFraction by remember { mutableFloatStateOf(0f) }
    val currentDisplayFraction = if (isDraggingSlider) dragSliderFraction else progress
    val currentDisplayPosition = if (isDraggingSlider && duration > 0L) (dragSliderFraction * duration).toLong() else position

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
                        color = colors.surfaceElevated.copy(alpha = 0.9f),
                        modifier = Modifier.padding(start = 12.dp).size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", modifier = Modifier.size(24.dp), tint = colors.textPrimary)
                        }
                    }
                },
                actions = {
                    Surface(
                        onClick = onNavigateToEqualizer,
                        shape = CircleShape,
                        color = colors.surfaceElevated.copy(alpha = 0.9f),
                        modifier = Modifier.padding(end = 12.dp).size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Equalizer,
                                contentDescription = "Equalizer",
                                tint = if (eqEnabled) accentColor else colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        onClick = onNavigateToLyricsCreator,
                        shape = CircleShape,
                        color = colors.surfaceElevated.copy(alpha = 0.9f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Lyrics,
                                contentDescription = "Lyrics Editor",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        onClick = { showSongOptionsSheet = true },
                        shape = CircleShape,
                        color = colors.surfaceElevated.copy(alpha = 0.9f),
                        modifier = Modifier.padding(end = 12.dp).size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Song options",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    com.example.ui.components.ArtworkThumbnail(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        genre = song.genre,
                        path = song.path,
                        size = 280f,
                        isCircle = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = song.title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = song.artist,
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(24.dp))

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
                            inactiveTrackColor = colors.surfaceElevated
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(currentDisplayPosition), color = colors.textSecondary, fontSize = 12.sp)
                        Text(formatDuration(duration), color = colors.textSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.setShuffleMode(!shuffleMode) }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (shuffleMode) accentColor else colors.textSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                        Icon(
                            if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) Color.Red else colors.textPrimary
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
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) accentColor else colors.textSecondary
                        )
                    }
                    IconButton(onClick = { showTimerDialog = true }) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = "Sleep timer",
                            tint = if (sleepTimerLeft > 0) accentColor else colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.skipPrevious() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp), tint = colors.textPrimary)
                    }

                    Surface(
                        onClick = { viewModel.playPause() },
                        shape = CircleShape,
                        color = accentColor,
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(72.dp)
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
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp), tint = colors.textPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Swipe left/right to change track",
                    color = colors.textSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
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
