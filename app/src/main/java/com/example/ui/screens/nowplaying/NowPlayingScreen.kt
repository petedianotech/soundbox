package com.example.ui.screens.nowplaying

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import com.example.ui.theme.Poweramp_Cyan
import com.example.ui.theme.Poweramp_Lime
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
    val dynamicThemeGlowEnabled by settingsManager.dynamicThemeFromAlbumArt.collectAsState()

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
    val rawPalette = rememberSongPalette(song, context)
    // When dynamic glow theme is toggled off by user, gracefully fall back to sleek Soundbox Cyan & Obsidian palette
    val effectiveAccent = if (dynamicThemeGlowEnabled) rawPalette.primary else Poweramp_Cyan
    val effectiveSecondary = if (dynamicThemeGlowEnabled) rawPalette.secondary else Color(0xFF7C4DFF)
    val effectiveDarkBg = if (dynamicThemeGlowEnabled) rawPalette.darkBackground else Color(0xFF090D14)
    val effectiveSurfaceTint = if (dynamicThemeGlowEnabled) rawPalette.surfaceTint else Color(0x2A00E5FF)
    val effectiveTextColor = Color.White
    val effectiveTextMuted = if (dynamicThemeGlowEnabled) rawPalette.textSecondary else Color(0xFF94A3B8)

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

    val artworkModel = remember(song.id) {
        AlbumArtHelper.getArtworkModel(context, song.id, song.title, song.artist, song.genre, song.path)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.6.sp,
                                    fontSize = 10.sp
                                ),
                                color = effectiveAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = song.album.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) } ?: "Soundbox Audio",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = effectiveTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = onBackClick,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(start = 12.dp).size(44.dp)
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
                    // Lockscreen Player pill action
                    Surface(
                        onClick = {
                            val lockIntent = Intent(context, LockscreenPlayerActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(lockIntent)
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(end = 6.dp).size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.LockClock,
                                contentDescription = "Lockscreen Player",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Equalizer pill action
                    Surface(
                        onClick = onNavigateToEqualizer,
                        shape = CircleShape,
                        color = if (eqEnabled) effectiveAccent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(end = 6.dp).size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Equalizer,
                                contentDescription = "Equalizer",
                                tint = if (eqEnabled) Poweramp_Lime else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Song options menu
                    Surface(
                        onClick = { showSongOptionsSheet = true },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(end = 12.dp).size(42.dp)
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
        containerColor = effectiveDarkBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(effectiveDarkBg)
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
            // 1. DYNAMIC GLOW THEME & AMBIENT BLURRED BACKDROP
            if (dynamicThemeGlowEnabled) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artworkModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.40f }
                        .blur(radius = 60.dp)
                )

                // Atmospheric radial + vertical gradient lighting
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    effectiveDarkBg.copy(alpha = 0.65f),
                                    effectiveDarkBg.copy(alpha = 0.85f),
                                    effectiveDarkBg.copy(alpha = 0.98f)
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
                                    effectiveAccent.copy(alpha = 0.32f),
                                    effectiveSecondary.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                radius = 950f
                            )
                        )
                )
            } else {
                // Sleek static obsidian dark background with subtle radial aura
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Poweramp_Cyan.copy(alpha = 0.12f),
                                    Color(0xFF090E16),
                                    Color(0xFF06090F)
                                ),
                                radius = 900f
                            )
                        )
                )
            }

            // 2. MAIN PLAYER FOREGROUND CONTENT
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // CENTER STAGE: Album Art Card or Full Live Lyrics
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
                            // FULL SCROLLING SYNCHRONIZED KARAOKE LYRICS PANE
                            if (lyricsList.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = effectiveSurfaceTint,
                                    border = BorderStroke(1.dp, effectiveAccent.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.Lyrics,
                                            contentDescription = null,
                                            tint = effectiveAccent,
                                            modifier = Modifier.size(54.dp)
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            "No Synchronized Lyrics Yet",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Tap below to sync lyrics in the Lyrics Studio",
                                            color = effectiveTextMuted,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(18.dp))
                                        Button(
                                            onClick = onNavigateToLyricsCreator,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = effectiveAccent),
                                            modifier = Modifier.height(46.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Open Lyrics Studio", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = effectiveSurfaceTint,
                                    border = BorderStroke(1.dp, effectiveAccent.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 12.dp)
                                ) {
                                    LazyColumn(
                                        state = lyricsListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        itemsIndexed(lyricsList) { idx, line ->
                                            val isActive = idx == activeLyricIndex
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (isActive) effectiveAccent.copy(alpha = 0.18f) else Color.Transparent,
                                                border = if (isActive) BorderStroke(1.dp, effectiveAccent.copy(alpha = 0.45f)) else null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { viewModel.seekTo(line.timeMs) }
                                            ) {
                                                Text(
                                                    text = line.text,
                                                    color = if (isActive) effectiveAccent else Color.White.copy(alpha = 0.55f),
                                                    fontSize = if (isActive) 21.sp else 16.sp,
                                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // ALBUM ART STAGE WITH RESTRAINED SHADOW & CLEAN METADATA
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(276.dp)
                                        .shadow(
                                            elevation = if (dynamicThemeGlowEnabled) 18.dp else 10.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            ambientColor = effectiveAccent.copy(alpha = 0.55f),
                                            spotColor = effectiveAccent.copy(alpha = 0.55f)
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(effectiveSurfaceTint)
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
                                        size = 276f,
                                        isCircle = false,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(22.dp))

                                // Track title & Artist Header
                                Text(
                                    text = cleanSongTitle(song.title),
                                    color = effectiveTextColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 21.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .sharedSongTitle(song.id)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = song.artist.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) } ?: "Unknown Artist",
                                    color = effectiveTextMuted,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Restrained Soft Lyric Snippet Pill
                                Surface(
                                    onClick = { showInPlayerLyrics = true },
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.White.copy(alpha = 0.07f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.10f)),
                                    modifier = Modifier
                                        .fillMaxWidth(0.94f)
                                        .testTag("in_player_lyric_pill")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(effectiveAccent.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = effectiveAccent,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = currentLyricSnippet ?: "Tap to view live synchronized lyrics",
                                            color = if (currentLyricSnippet != null) Color.White else effectiveTextMuted,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.Lyrics,
                                            contentDescription = "Expand Lyrics",
                                            tint = effectiveAccent.copy(alpha = 0.9f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ELEGANT PROGRESS SLIDER (Clean Samsung Music-style direct backdrop placement)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
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
                            thumbColor = effectiveAccent,
                            activeTrackColor = effectiveAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nowplaying_slider")
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentDisplayPosition),
                            color = effectiveTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatDuration(duration),
                            color = effectiveTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SECONDARY ACTION BUTTONS (Soft, borderless controls with subtle active tint)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button
                    Surface(
                        onClick = { viewModel.setShuffleMode(!shuffleMode) },
                        shape = CircleShape,
                        color = if (shuffleMode) effectiveAccent.copy(alpha = 0.18f) else Color.Transparent,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffleMode) effectiveAccent else Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    // Favorite Button
                    Surface(
                        onClick = { viewModel.toggleFavorite(song) },
                        shape = CircleShape,
                        color = if (song.isFavorite) Color(0xFFEF4444).copy(alpha = 0.18f) else Color.Transparent,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (song.isFavorite) Color(0xFFEF4444) else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    // Toggle Lyrics Mode Button
                    Surface(
                        onClick = { showInPlayerLyrics = !showInPlayerLyrics },
                        shape = CircleShape,
                        color = if (showInPlayerLyrics) effectiveAccent.copy(alpha = 0.18f) else Color.Transparent,
                        modifier = Modifier.size(48.dp).testTag("toggle_lyrics_display_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (showInPlayerLyrics) Icons.Default.Album else Icons.Default.Lyrics,
                                contentDescription = "Toggle Lyrics",
                                tint = if (showInPlayerLyrics) effectiveAccent else Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    // Repeat Mode Button
                    Surface(
                        onClick = {
                            val next = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            viewModel.setRepeatMode(next)
                        },
                        shape = CircleShape,
                        color = if (repeatMode != Player.REPEAT_MODE_OFF) effectiveAccent.copy(alpha = 0.18f) else Color.Transparent,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) effectiveAccent else Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    // Sleep Timer Button
                    Surface(
                        onClick = { showTimerDialog = true },
                        shape = CircleShape,
                        color = if (sleepTimerLeft > 0) effectiveAccent.copy(alpha = 0.18f) else Color.Transparent,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Sleep timer",
                                tint = if (sleepTimerLeft > 0) effectiveAccent else Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // PRIMARY PLAYBACK CONTROLS (Samsung Music-inspired rounded icons & balanced proportions)
                val playIconTint = if (effectiveAccent.luminance() > 0.45f) Color(0xFF090D14) else Color.White
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Track Button
                    Surface(
                        onClick = { viewModel.skipPrevious() },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("nowplaying_prev_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = com.example.R.drawable.ic_sb_skip_prev),
                                contentDescription = "Previous",
                                modifier = Modifier.size(28.dp),
                                tint = Color.White
                            )
                        }
                    }

                    // Main Play / Pause Button (Prominent, clean, no harsh border or excessive glow)
                    Surface(
                        onClick = { viewModel.playPause() },
                        shape = CircleShape,
                        color = effectiveAccent,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("nowplaying_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) com.example.R.drawable.ic_sb_pause else com.example.R.drawable.ic_sb_play
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp),
                                tint = playIconTint
                            )
                        }
                    }

                    // Next Track Button
                    Surface(
                        onClick = { viewModel.skipNext() },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("nowplaying_next_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = com.example.R.drawable.ic_sb_skip_next),
                                contentDescription = "Next",
                                modifier = Modifier.size(28.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Swipe left/right to change track • Tap artwork for full lyrics",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
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

/**
 * Strips common messy file extension suffixes (e.g. "_Official_Lyrics", "__Lyrics", ".mp3", underscores)
 * to present titles cleanly in the premium Now Playing interface.
 */
private fun cleanSongTitle(raw: String): String {
    var cleaned = raw
    if (cleaned.endsWith(".mp3", ignoreCase = true) || cleaned.endsWith(".m4a", ignoreCase = true) || cleaned.endsWith(".flac", ignoreCase = true)) {
        cleaned = cleaned.substringBeforeLast(".")
    }
    cleaned = cleaned.replace("_", " ")
    cleaned = cleaned.replace(Regex("(?i)\\s*-\\s*official\\s*lyrics?"), "")
    cleaned = cleaned.replace(Regex("(?i)\\s*\\(official\\s*audio\\)"), "")
    cleaned = cleaned.replace(Regex("(?i)\\s*\\(official\\s*video\\)"), "")
    cleaned = cleaned.replace(Regex("(?i)\\s*\\[official\\s*video\\]"), "")
    cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
    return cleaned.ifEmpty { raw }
}
