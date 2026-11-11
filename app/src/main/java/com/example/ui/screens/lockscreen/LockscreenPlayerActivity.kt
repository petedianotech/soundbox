package com.example.ui.screens.lockscreen

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Song
import com.example.player.LyricLine
import com.example.player.LyricsManager
import com.example.player.PlaybackManager
import com.example.ui.components.ArtworkThumbnail
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SoundboxTheme
import com.example.util.AlbumArtHelper
import com.example.util.PaletteHelper
import com.example.util.rememberSongPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class LockscreenPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        enableEdgeToEdge()

        setContent {
            val playbackManager = remember { PlaybackManager.getInstance(applicationContext) }
            val currentSong by playbackManager.currentSong.collectAsState()
            val isPlaying by playbackManager.isPlaying.collectAsState()
            val position by playbackManager.currentPosition.collectAsState()
            val duration by playbackManager.duration.collectAsState()
            val shuffleMode by playbackManager.shuffleMode.collectAsState()
            val repeatMode by playbackManager.repeatMode.collectAsState()

            MyApplicationTheme {
                LockscreenPlayerContent(
                    song = currentSong,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    shuffleMode = shuffleMode,
                    repeatMode = repeatMode,
                    onPlayPause = { playbackManager.playPause() },
                    onSkipNext = { playbackManager.skipNext() },
                    onSkipPrevious = { playbackManager.skipPrevious() },
                    onSeekTo = { playbackManager.seekTo(it) },
                    onToggleFavorite = { currentSong?.let { playbackManager.toggleFavorite(it) } },
                    onRewind10 = {
                        val target = (position - 10000L).coerceAtLeast(0L)
                        playbackManager.seekTo(target)
                    },
                    onForward10 = {
                        val target = (position + 10000L).coerceAtMost(if (duration > 0) duration else position + 10000L)
                        playbackManager.seekTo(target)
                    },
                    onDismiss = { dismissLockscreen() }
                )
            }
        }
    }

    private fun dismissLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        }
        finish()
    }
}

@Composable
fun LockscreenPlayerContent(
    song: Song?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    shuffleMode: Boolean,
    repeatMode: Int,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = rememberSongPalette(song, context)
    var showFullLyrics by remember { mutableStateOf(false) }

    // Live clock and date state
    var currentTimeString by remember { mutableStateOf("") }
    var currentDateString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTimeString = timeFormat.format(now)
            currentDateString = dateFormat.format(now)
            delay(1000L)
        }
    }

    // Lyrics parsing
    var lyricsList by remember(song?.id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    LaunchedEffect(song?.id) {
        if (song != null) {
            val raw = LyricsManager.loadLyrics(context, song)
            if (!raw.isNullOrBlank()) {
                lyricsList = LyricsManager.parseLyrics(raw, duration)
            } else {
                lyricsList = emptyList()
            }
        } else {
            lyricsList = emptyList()
        }
    }

    val activeLyricIndex = remember(lyricsList, position) {
        if (lyricsList.isEmpty()) -1
        else lyricsList.indexOfLast { it.timeMs <= position }
    }

    val currentLyricText = if (activeLyricIndex in lyricsList.indices) {
        lyricsList[activeLyricIndex].text
    } else {
        song?.album?.ifBlank { "Soundbox Audio" } ?: "Soundbox"
    }

    val listState = rememberLazyListState()
    LaunchedEffect(activeLyricIndex, showFullLyrics) {
        if (showFullLyrics && activeLyricIndex >= 0 && activeLyricIndex < lyricsList.size) {
            val scrollTarget = (activeLyricIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(scrollTarget)
        }
    }

    var verticalDragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.darkBackground)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { verticalDragAccumulator = 0f },
                    onVerticalDrag = { _, dragAmount -> verticalDragAccumulator += dragAmount },
                    onDragEnd = {
                        if (verticalDragAccumulator < -120f || verticalDragAccumulator > 120f) {
                            onDismiss()
                        }
                    }
                )
            }
    ) {
        // 1. Full-screen Album Art Blurred Backdrop
        if (song != null) {
            val artModel = remember(song.id) {
                AlbumArtHelper.getArtworkModel(context, song.id, song.title, song.artist, song.genre, song.path)
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artModel)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.45f }
                    .blur(radius = 55.dp)
            )
        }

        // 2. Immersive Atmospheric Gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.darkBackground.copy(alpha = 0.65f),
                            palette.darkBackground.copy(alpha = 0.82f),
                            palette.darkBackground.copy(alpha = 0.96f)
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
                            palette.primary.copy(alpha = 0.32f),
                            Color.Transparent
                        ),
                        radius = 850f
                    )
                )
        )

        // 3. Lockscreen Player Scaffold / Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HEADER: Clock, Date, and Unlock affordance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = currentTimeString,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = currentDateString,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(20.dp),
                    color = palette.surfaceTint,
                    border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("lockscreen_dismiss_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = "Unlock",
                            tint = palette.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Unlock",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CENTER STAGE: Toggleable between Artwork and Live Synced Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (song == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = palette.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Song Playing", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    AnimatedContent(
                        targetState = showFullLyrics,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "StageAnimation"
                    ) { isLyricsView ->
                        if (isLyricsView) {
                            // FULL LIVE SCROLLING KARAOKE LYRICS VIEW
                            if (lyricsList.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Lyrics,
                                        contentDescription = null,
                                        tint = palette.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No Synchronized Lyrics Found", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Tap Artwork to return", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    itemsIndexed(lyricsList) { idx, line ->
                                        val isActive = idx == activeLyricIndex
                                        Text(
                                            text = line.text,
                                            color = if (isActive) palette.primary else Color.White.copy(alpha = 0.5f),
                                            fontSize = if (isActive) 20.sp else 15.sp,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onSeekTo(line.timeMs) }
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // ARTWORK & LIVE FLOATING LYRIC STAGE
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Glowing centered album art card
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .shadow(
                                            elevation = 24.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            ambientColor = palette.primary,
                                            spotColor = palette.primary
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(palette.surfaceTint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ArtworkThumbnail(
                                        songId = song.id,
                                        title = song.title,
                                        artist = song.artist,
                                        genre = song.genre,
                                        path = song.path,
                                        size = 240f,
                                        isCircle = false,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Song metadata
                                Text(
                                    text = song.title,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = song.artist,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Floating Frosted Glass Live Lyric Bar
                                Surface(
                                    onClick = { showFullLyrics = true },
                                    shape = RoundedCornerShape(16.dp),
                                    color = palette.surfaceTint,
                                    border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .testTag("lockscreen_lyric_pill")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = "Lyric",
                                            tint = palette.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = currentLyricText,
                                            color = Color.White,
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
                                            tint = palette.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // CONTROLS DECK
            if (song != null) {
                // Progress Slider
                val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progress,
                        onValueChange = { frac ->
                            if (duration > 0) onSeekTo((frac * duration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = palette.primary,
                            activeTrackColor = palette.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("lockscreen_slider")
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(position), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(formatDuration(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action icons row (Shuffle, Favorite, Lyrics Toggle, Seek)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { showFullLyrics = !showFullLyrics },
                        shape = com.example.ui.theme.SoundboxButtonShapes.CompactIcon,
                        color = if (showFullLyrics) palette.primary.copy(alpha = 0.22f) else palette.surfaceTint,
                        border = if (showFullLyrics) BorderStroke(1.2.dp, palette.primary) else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.size(42.dp).testTag("lockscreen_toggle_lyrics_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (showFullLyrics) Icons.Default.Album else Icons.Default.Lyrics,
                                contentDescription = "Toggle Lyrics",
                                tint = if (showFullLyrics) palette.primary else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            onClick = onRewind10,
                            shape = com.example.ui.theme.SoundboxButtonShapes.CompactIcon,
                            color = palette.surfaceTint,
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Surface(
                            onClick = onForward10,
                            shape = com.example.ui.theme.SoundboxButtonShapes.CompactIcon,
                            color = palette.surfaceTint,
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Surface(
                        onClick = onToggleFavorite,
                        shape = com.example.ui.theme.SoundboxButtonShapes.CompactIcon,
                        color = if (song.isFavorite) Color.Red.copy(alpha = 0.2f) else palette.surfaceTint,
                        border = if (song.isFavorite) BorderStroke(1.2.dp, Color.Red) else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (song.isFavorite) Color.Red else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Playback Controls (Prev, Big Play/Pause, Next)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onSkipPrevious,
                        shape = com.example.ui.theme.SoundboxButtonShapes.LargeAction,
                        color = palette.surfaceTint,
                        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("lockscreen_prev_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }

                    Surface(
                        onClick = onPlayPause,
                        shape = CircleShape,
                        color = palette.primary,
                        shadowElevation = 10.dp,
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("lockscreen_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onSkipNext,
                        shape = com.example.ui.theme.SoundboxButtonShapes.LargeAction,
                        color = palette.surfaceTint,
                        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("lockscreen_next_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Swipe up or down to dismiss lockscreen",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
