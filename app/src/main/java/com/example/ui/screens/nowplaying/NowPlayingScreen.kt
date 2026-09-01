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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.R
import com.example.data.model.Song
import com.example.player.LyricsManager
import com.example.ui.components.ArtworkThumbnail
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.EqualizerPanel
import com.example.ui.components.OnlineCoverDialog
import com.example.ui.components.SleekCompactSlider
import com.example.ui.components.SongDeleteDialog
import com.example.ui.components.SongImagePlaceholder
import com.example.ui.components.SongOptionsBottomSheet
import com.example.ui.components.ThumbnailPickerSheet
import com.example.ui.viewmodel.MusicViewModel
import com.example.util.ThumbnailExporter
import com.example.util.rememberArtworkPalette
import kotlinx.coroutines.launch
import java.util.Locale

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val isDynamic: Boolean = true
)

fun parseLyrics(content: String, durationMs: Long): List<LyricLine> {
    val lines = content.lines()
    val parsedLines = mutableListOf<LyricLine>()
    val lrcRegex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")
    var hasTimestamps = false

    for (rawLine in lines) {
        val match = lrcRegex.find(rawLine)
        if (match != null) {
            hasTimestamps = true
            val min = match.groupValues[1].toLong()
            val sec = match.groupValues[2].toLong()
            val milliStr = match.groupValues[3]
            val ms = if (milliStr.length == 2) milliStr.toLong() * 10 else milliStr.toLong()
            val text = match.groupValues[4].trim()
            if (text.isNotEmpty() || hasTimestamps) {
                val totalMs = (min * 60 * 1000) + (sec * 1000) + ms
                parsedLines.add(LyricLine(totalMs, text, isDynamic = true))
            }
        }
    }

    return if (hasTimestamps) {
        parsedLines.sortedBy { it.timeMs }
    } else {
        lines.filter { it.isNotBlank() }.map { text ->
            LyricLine(timeMs = 0L, text = text.trim(), isDynamic = false)
        }
    }
}

fun getLyricsForSong(context: Context, song: Song, durationMs: Long): List<LyricLine> {
    val saved = LyricsManager.loadLyrics(context, song)
    if (!saved.isNullOrBlank()) {
        return parseLyrics(saved, durationMs)
    }
    return emptyList()
}

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
    val queue by viewModel.queue.collectAsState()
    val sleepTimerLeft by viewModel.sleepTimerMillis.collectAsState()
    val eqEnabled by viewModel.equalizerEnabled.collectAsState()
    val eqPreset by viewModel.equalizerPreset.collectAsState()
    val eqBands by viewModel.equalizerBands.collectAsState()
    val bassStrength by viewModel.bassBoostStrength.collectAsState()
    val virtStrength by viewModel.virtualizerStrength.collectAsState()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsState()
    val globalThumbIndex by viewModel.globalThumbnailIndex.collectAsState()
    val songThumbMap by viewModel.songThumbnailMap.collectAsState()
    val dynamicColorsEnabled by viewModel.dynamicArtworkColors.collectAsState()
    val currentTheme by viewModel.settingsManager.themeFlow.collectAsState()

    val isSystemDark = isSystemInDarkTheme()
    val isDark = currentTheme == "DARK" || currentTheme == "MIDNIGHT" || (currentTheme == "SYSTEM" && isSystemDark)

    var showThumbnailPicker by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var isLyricsViewActive by remember { mutableStateOf(false) }
    var showLyricsMenu by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedLyricsContent by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }
    var showTrackOptionsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showOnlineCoverDialog by remember { mutableStateOf(false) }

    // Smooth Interactive Progress Scrubbing
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    val displayPosition = if (isScrubbing) scrubPosition.toLong() else position

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && currentSong != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader().use { it.readText() }
                    LyricsManager.saveLyrics(context, currentSong!!, content)
                    refreshTrigger++
                    Toast.makeText(context, "Lyrics loaded successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load lyrics file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (currentSong == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Now Playing") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", modifier = Modifier.size(28.dp))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyPlaceholder(
                    title = "No Track Playing",
                    subtitle = "Select a song from your library to start listening",
                    icon = Icons.Default.MusicNote,
                    actionText = "Go to Library",
                    onActionClick = onBackClick
                )
            }
        }
    } else {
        val song = currentSong!!
        val currentThumbIdx = songThumbMap[song.id] ?: globalThumbIndex
        val artworkPalette = rememberArtworkPalette(
            song = song,
            enabled = dynamicColorsEnabled,
            thumbnailIndex = currentThumbIdx,
            isDarkTheme = isDark
        )

        // Animated transition colors for smooth gradient crossfade
        val animGrad0 by animateColorAsState(
            targetValue = if (dynamicColorsEnabled) artworkPalette.gradientColors.getOrElse(0) { MaterialTheme.colorScheme.surface } else MaterialTheme.colorScheme.surface,
            animationSpec = tween(650, easing = FastOutSlowInEasing),
            label = "animGrad0"
        )
        val animGrad1 by animateColorAsState(
            targetValue = if (dynamicColorsEnabled) artworkPalette.gradientColors.getOrElse(1) { MaterialTheme.colorScheme.surfaceContainer } else MaterialTheme.colorScheme.surface,
            animationSpec = tween(650, easing = FastOutSlowInEasing),
            label = "animGrad1"
        )
        val animGrad2 by animateColorAsState(
            targetValue = if (dynamicColorsEnabled) artworkPalette.gradientColors.getOrElse(2) { MaterialTheme.colorScheme.surfaceContainerLowest } else MaterialTheme.colorScheme.surface,
            animationSpec = tween(650, easing = FastOutSlowInEasing),
            label = "animGrad2"
        )
        val animAccent by animateColorAsState(
            targetValue = if (dynamicColorsEnabled) artworkPalette.vibrant else MaterialTheme.colorScheme.primary,
            animationSpec = tween(650, easing = FastOutSlowInEasing),
            label = "animAccent"
        )
        val animOnBackground by animateColorAsState(
            targetValue = if (dynamicColorsEnabled) (if (isDark) Color(0xFFF2F4F8) else Color(0xFF191C1E)) else MaterialTheme.colorScheme.onSurface,
            animationSpec = tween(650),
            label = "animOnBg"
        )
        val animOnBackgroundVariant by animateColorAsState(
            targetValue = if (dynamicColorsEnabled) (if (isDark) Color(0xFFB4B9C4) else Color(0xFF43474E)) else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(650),
            label = "animOnBgVar"
        )
        val animSurfaceContainer by animateColorAsState(
            targetValue = if (dynamicColorsEnabled) {
                if (isDark) Color(0x33FFFFFF) else Color(0x40FFFFFF)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            animationSpec = tween(650),
            label = "animSurfCont"
        )

        val lyrics = remember(song.id, duration, refreshTrigger) { getLyricsForSong(context, song, duration) }
        val hasTimestamps = remember(lyrics) { lyrics.any { it.isDynamic } }
        val activeVerseIndexByTime = if (hasTimestamps) lyrics.indexOfLast { (if (isScrubbing) scrubPosition.toLong() else position) >= it.timeMs } else -1
        val currentActiveIndex = activeVerseIndexByTime.coerceAtLeast(0)
        val lyricsListState = rememberLazyListState()

        LaunchedEffect(currentActiveIndex) {
            if (isLyricsViewActive && hasTimestamps && currentActiveIndex >= 0 && lyrics.isNotEmpty()) {
                lyricsListState.animateScrollToItem((currentActiveIndex - 2).coerceAtLeast(0))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (dynamicColorsEnabled) {
                        Brush.verticalGradient(listOf(animGrad0, animGrad1, animGrad2))
                    } else {
                        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
                    }
                )
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "NOW PLAYING",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.4.sp
                                    ),
                                    color = animOnBackgroundVariant
                                )
                                Text(
                                    text = song.album.ifEmpty { "Soundbox Music" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = animOnBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(30.dp),
                                    tint = animOnBackground
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showThumbnailPicker = true }) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = "Switch Artwork Style",
                                    tint = animOnBackgroundVariant
                                )
                            }
                            IconButton(onClick = { isLyricsViewActive = !isLyricsViewActive }) {
                                Icon(
                                    imageVector = if (isLyricsViewActive) Icons.Filled.Lyrics else Icons.Outlined.Lyrics,
                                    contentDescription = "Toggle Lyrics",
                                    tint = if (isLyricsViewActive) animAccent else animOnBackgroundVariant
                                )
                            }
                            IconButton(onClick = { showTrackOptionsSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = animOnBackgroundVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                containerColor = Color.Transparent
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Scrollable Top Content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Artwork or Lyrics View
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(310.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = isLyricsViewActive,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(300)) togetherWith
                                    fadeOut(animationSpec = tween(300))
                                },
                                label = "ArtworkLyricsTransition"
                            ) { showLyrics ->
                                if (showLyrics) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(28.dp),
                                        color = if (dynamicColorsEnabled) animSurfaceContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        tonalElevation = 2.dp
                                    ) {
                                        Box(modifier = Modifier.padding(16.dp)) {
                                            if (lyrics.isEmpty()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(16.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Lyrics,
                                                        contentDescription = null,
                                                        tint = animAccent,
                                                        modifier = Modifier.size(44.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Text(
                                                        "No lyrics found",
                                                        color = animOnBackground,
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        "Import a synchronized .lrc file or paste lyrics to read along.",
                                                        color = animOnBackgroundVariant,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        FilledTonalButton(
                                                            onClick = { fileLauncher.launch("*/*") },
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("Pick File")
                                                        }
                                                        OutlinedButton(
                                                            onClick = {
                                                                pastedLyricsContent = ""
                                                                showPasteDialog = true
                                                            },
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("Paste")
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        OutlinedButton(
                                                            onClick = {
                                                                val query = LyricsManager.buildCompactSearchQuery(song)
                                                                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                                                                    putExtra("query", query)
                                                                }
                                                                try {
                                                                    context.startActivity(intent)
                                                                } catch (e: Exception) {
                                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
                                                                }
                                                            },
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("Google")
                                                        }
                                                        OutlinedButton(
                                                            onClick = onNavigateToLyricsCreator,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("Create")
                                                        }
                                                    }
                                                }
                                            } else {
                                                LazyColumn(
                                                    state = lyricsListState,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentPadding = PaddingValues(vertical = 80.dp),
                                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    itemsIndexed(lyrics) { index, line ->
                                                        val isActive = hasTimestamps && index == currentActiveIndex
                                                        val targetScale = if (isActive) 1.05f else if (hasTimestamps) 0.95f else 1.0f
                                                        val targetAlpha = if (isActive) 1f else if (hasTimestamps) 0.45f else 0.85f

                                                        val scale by animateFloatAsState(
                                                            targetValue = targetScale,
                                                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                                                            label = "LyricScale"
                                                        )
                                                        val alpha by animateFloatAsState(
                                                            targetValue = targetAlpha,
                                                            animationSpec = tween(280),
                                                            label = "LyricAlpha"
                                                        )

                                                        Text(
                                                            text = line.text,
                                                            style = MaterialTheme.typography.titleMedium.copy(
                                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                                fontSize = if (hasTimestamps) 19.sp else 16.sp
                                                            ),
                                                            textAlign = TextAlign.Center,
                                                            color = if (isActive) animAccent else animOnBackground,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .graphicsLayer {
                                                                    scaleX = scale
                                                                    scaleY = scale
                                                                    this.alpha = alpha
                                                                }
                                                                .clickable(enabled = line.isDynamic) {
                                                                    viewModel.seekTo(line.timeMs)
                                                                }
                                                                .padding(horizontal = 8.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.align(Alignment.TopEnd),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(onClick = { isLyricsViewActive = false }) {
                                                    Icon(Icons.Default.Image, contentDescription = "Show Artwork", tint = animOnBackgroundVariant)
                                                }
                                                IconButton(onClick = { showLyricsMenu = true }) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = "Lyrics Options", tint = animAccent)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // High-Fidelity Artwork Presentation
                                    val artworkScale by animateFloatAsState(
                                        targetValue = if (isPlaying) 1.0f else 0.94f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                        label = "ArtworkScale"
                                    )

                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Ambient Soft Glow behind artwork
                                        if (dynamicColorsEnabled) {
                                            Box(
                                                modifier = Modifier
                                                    .size(280.dp)
                                                    .graphicsLayer {
                                                        scaleX = if (isPlaying) 1.08f else 0.96f
                                                        scaleY = if (isPlaying) 1.08f else 0.96f
                                                    }
                                                    .background(
                                                        brush = Brush.radialGradient(
                                                            colors = listOf(
                                                                animAccent.copy(alpha = if (isPlaying) 0.40f else 0.15f),
                                                                Color.Transparent
                                                            )
                                                        ),
                                                        shape = CircleShape
                                                    )
                                            )
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .size(270.dp)
                                                .graphicsLayer {
                                                    scaleX = artworkScale
                                                    scaleY = artworkScale
                                                }
                                                .clickable { isLyricsViewActive = true },
                                            shape = RoundedCornerShape(28.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shadowElevation = 10.dp,
                                            tonalElevation = 4.dp
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                SongImagePlaceholder(
                                                    title = song.title,
                                                    thumbnailIndex = songThumbMap[song.id] ?: globalThumbIndex,
                                                    modifier = Modifier.fillMaxSize(),
                                                    size = 270f
                                                )

                                                // Mini lyrics badge
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.Lyrics, contentDescription = null, modifier = Modifier.size(12.dp), tint = animAccent)
                                                        Text("LYRICS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurface)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Title & Artist with Favorite Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = animOnBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${song.artist} • ${song.album}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = animOnBackgroundVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                                Icon(
                                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (song.isFavorite) "In Favorites" else "Add to favorites",
                                    tint = if (song.isFavorite) Color(0xFFE91E63) else animOnBackgroundVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Lyrics preview card when artwork is shown
                        if (!isLyricsViewActive) {
                            Surface(
                                onClick = { isLyricsViewActive = true },
                                shape = RoundedCornerShape(16.dp),
                                color = if (dynamicColorsEnabled) animSurfaceContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Lyrics Preview",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = animAccent
                                        )
                                        Icon(
                                            imageVector = Icons.Default.OpenInFull,
                                            contentDescription = "Expand Lyrics",
                                            tint = animAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (lyrics.isEmpty()) {
                                        Text(
                                            text = "Tap to view or add lyrics for this song",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = animOnBackgroundVariant
                                        )
                                    } else {
                                        val activeLineText = lyrics.getOrNull(currentActiveIndex)?.text ?: ""
                                        Text(
                                            text = activeLineText.ifEmpty { "🎵 Instrumental..." },
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = animOnBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Fixed Bottom Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Sleep Timer chip if active
                        if (sleepTimerLeft > 0) {
                            val minutes = (sleepTimerLeft / 1000) / 60
                            val seconds = (sleepTimerLeft / 1000) % 60
                            AssistChip(
                                onClick = { viewModel.stopSleepTimer() },
                                label = { Text("Stopping in ${String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)}") },
                                leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(12.dp)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (dynamicColorsEnabled) animSurfaceContainer else MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = if (dynamicColorsEnabled) animOnBackground else MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = CircleShape
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Smooth Scrubbing Progress Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = (if (isScrubbing) scrubPosition else position.toFloat()).coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                                onValueChange = {
                                    isScrubbing = true
                                    scrubPosition = it
                                },
                                onValueChangeFinished = {
                                    viewModel.seekTo(scrubPosition.toLong())
                                    isScrubbing = false
                                },
                                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = animAccent,
                                    activeTrackColor = animAccent,
                                    inactiveTrackColor = if (dynamicColorsEnabled) animOnBackground.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatPosition(displayPosition),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = animOnBackgroundVariant
                                )
                                Text(
                                    text = formatPosition(duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = animOnBackgroundVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Primary Playback Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Shuffle Button
                            IconButton(onClick = { viewModel.toggleShuffle() }) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (shuffleMode) animAccent else animOnBackgroundVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Skip 10s Rewind
                            IconButton(onClick = { viewModel.seekTo((position - 10000).coerceAtLeast(0)) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_replay_10),
                                    contentDescription = "Rewind 10s",
                                    tint = animOnBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Skip Previous
                            IconButton(onClick = { viewModel.skipPrevious() }) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Song",
                                    modifier = Modifier.size(36.dp),
                                    tint = animOnBackground
                                )
                            }

                            // Big Play/Pause Button
                            Surface(
                                onClick = { viewModel.playPause() },
                                shape = CircleShape,
                                color = animAccent,
                                shadowElevation = 6.dp,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = if (dynamicColorsEnabled && !isDark) Color.White else MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            // Skip Next
                            IconButton(onClick = { viewModel.skipNext() }) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "Next Song",
                                    modifier = Modifier.size(36.dp),
                                    tint = animOnBackground
                                )
                            }

                            // Skip 10s Forward
                            IconButton(onClick = { viewModel.seekTo((position + 10000).coerceAtMost(duration)) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_forward_10),
                                    contentDescription = "Forward 10s",
                                    tint = animOnBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Repeat Mode Button
                            IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                                val repeatIcon = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                                    else -> Icons.Default.Repeat
                                }
                                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                                Icon(
                                    imageVector = repeatIcon,
                                    contentDescription = "Repeat Mode",
                                    tint = if (isRepeatActive) animAccent else animOnBackgroundVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bottom Quick Action Bar: Queue, Equalizer, Speed, Timer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Queue / Up Next
                            FilledTonalButton(
                                onClick = { showQueueSheet = true },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (dynamicColorsEnabled) animSurfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = animOnBackground
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Queue (${queue.size})", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            // Equalizer & FX
                            IconButton(onClick = { showEffectsSheet = true }) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Equalizer & Sound Effects",
                                    tint = if (eqEnabled) animAccent else animOnBackgroundVariant
                                )
                            }

                            // Playback Speed & Pitch
                            IconButton(onClick = { showSpeedSheet = true }) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = if (speed != 1.0f) animAccent else animOnBackgroundVariant
                                )
                            }

                            // Sleep Timer
                            IconButton(onClick = { showTimerDialog = true }) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = "Sleep Timer",
                                    tint = if (sleepTimerLeft > 0) animAccent else animOnBackgroundVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // UP NEXT / QUEUE BOTTOM SHEET
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Up Next",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${queue.size} songs in play queue",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.toggleShuffle() }
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Shuffle")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                    if (queue.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Queue is empty",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            itemsIndexed(queue) { index, queueSong ->
                                val isCurrent = queueSong.id == song.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                                        .clickable {
                                            viewModel.playSong(queueSong, queue)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            Icons.Default.GraphicEq,
                                            contentDescription = "Now Playing",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    ArtworkThumbnail(
                                        songId = queueSong.id,
                                        title = queueSong.title,
                                        size = 44f
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = queueSong.title,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

                                    IconButton(
                                        onClick = { viewModel.removeFromQueue(queueSong) }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove from queue",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // SPEED, PITCH & CROSSFADE SHEET
        if (showSpeedSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSpeedSheet = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Playback Tempo & Pitch",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    // Speed presets
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speed Velocity", style = MaterialTheme.typography.titleSmall)
                            Text(String.format(Locale.getDefault(), "%.2fx", speed), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { rate ->
                                FilterChip(
                                    selected = (speed == rate),
                                    onClick = { viewModel.setPlaybackRate(rate, pitch) },
                                    label = { Text("${rate}x") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Pitch shifting
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tone & Pitch", style = MaterialTheme.typography.titleSmall)
                            Text(String.format(Locale.getDefault(), "%.2fx", pitch), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Slider(
                            value = pitch,
                            onValueChange = { viewModel.setPlaybackRate(speed, it) },
                            valueRange = 0.5f..1.5f,
                            steps = 9
                        )
                    }

                    // Crossfade
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Gapless & Crossfade Overlap", style = MaterialTheme.typography.titleSmall)
                            Text(if (crossfadeSeconds > 0) "${crossfadeSeconds}s" else "Disabled", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        SleekCompactSlider(
                            value = crossfadeSeconds.toFloat(),
                            valueRange = 0f..12f,
                            onValueChange = { viewModel.setCrossfadeSeconds(it.toInt()) }
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.setPlaybackRate(1.0f, 1.0f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("Reset to Normal (1.0x)")
                    }
                }
            }
        }

        // EQUALIZER BOTTOM SHEET
        if (showEffectsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showEffectsSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    EqualizerPanel(
                        enabled = eqEnabled,
                        preset = eqPreset,
                        bands = eqBands,
                        bassBoost = bassStrength,
                        virtualizer = virtStrength,
                        onToggleEnabled = { viewModel.toggleEqualizer() },
                        onPresetSelected = { viewModel.setEqualizerPreset(it) },
                        onBandLevelChanged = { index, level -> viewModel.setEqualizerBandLevel(index, level) },
                        onBassBoostChanged = { viewModel.setBassBoost(it) },
                        onVirtualizerChanged = { viewModel.setVirtualizer(it) }
                    )
                }
            }
        }

        // SLEEP TIMER DIALOG
        if (showTimerDialog) {
            AlertDialog(
                onDismissRequest = { showTimerDialog = false },
                title = { Text("Set Sleep Timer") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Automatically pause playback after selected duration.")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(5, 15, 30, 45, 60).forEach { mins ->
                                Button(
                                    onClick = {
                                        viewModel.startSleepTimer(mins)
                                        showTimerDialog = false
                                    }
                                ) {
                                    Text("${mins}m")
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    if (sleepTimerLeft > 0) {
                        TextButton(onClick = {
                            viewModel.stopSleepTimer()
                            showTimerDialog = false
                        }) {
                            Text("Disable Timer")
                        }
                    } else {
                        TextButton(onClick = { showTimerDialog = false }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }

        // THUMBNAIL PICKER SHEET
        if (showThumbnailPicker) {
            ThumbnailPickerSheet(
                currentSelection = songThumbMap[song.id] ?: globalThumbIndex,
                onThumbnailSelected = { index ->
                    viewModel.setSongThumbnail(song.id, index)
                    showThumbnailPicker = false
                },
                onDismissRequest = { showThumbnailPicker = false }
            )
        }

        // TRACK OPTIONS SHEET
        if (showTrackOptionsSheet) {
            SongOptionsBottomSheet(
                song = song,
                onDismiss = { showTrackOptionsSheet = false },
                onPlayNext = {
                    viewModel.addToQueue(song)
                    showTrackOptionsSheet = false
                    Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                },
                onAddToQueue = {
                    viewModel.addToQueue(song)
                    showTrackOptionsSheet = false
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                },
                onToggleFavorite = {
                    viewModel.toggleFavorite(song)
                },
                onAddToPlaylist = {
                    showTrackOptionsSheet = false
                },
                onDownloadThumbnail = {
                    showTrackOptionsSheet = false
                    coroutineScope.launch {
                        ThumbnailExporter.exportSongThumbnail(context, song)
                    }
                },
                onDeleteSong = {
                    showTrackOptionsSheet = false
                    showDeleteConfirmDialog = true
                },
                onChangeThumbnail = {
                    showTrackOptionsSheet = false
                    showThumbnailPicker = true
                },
                onDownloadOnlineCover = {
                    showTrackOptionsSheet = false
                    showOnlineCoverDialog = true
                }
            )
        }

        // DELETE CONFIRM DIALOG
        if (showDeleteConfirmDialog) {
            SongDeleteDialog(
                song = song,
                onDismiss = { showDeleteConfirmDialog = false },
                onConfirm = {
                    viewModel.deleteSong(song)
                    showDeleteConfirmDialog = false
                    onBackClick()
                }
            )
        }

        // ONLINE COVER DIALOG
        if (showOnlineCoverDialog) {
            OnlineCoverDialog(
                song = song,
                onDismissRequest = { showOnlineCoverDialog = false },
                onCoverUpdated = {
                    showOnlineCoverDialog = false
                    viewModel.setSongThumbnail(song.id, -1)
                }
            )
        }

        // LYRICS OPTIONS POPUP MENU
        if (showLyricsMenu) {
            DropdownMenu(
                expanded = showLyricsMenu,
                onDismissRequest = { showLyricsMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Pick .lrc File") },
                    onClick = {
                        showLyricsMenu = false
                        fileLauncher.launch("*/*")
                    },
                    leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Paste Text Lyrics") },
                    onClick = {
                        showLyricsMenu = false
                        pastedLyricsContent = ""
                        showPasteDialog = true
                    },
                    leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Search on Google") },
                    onClick = {
                        showLyricsMenu = false
                        val query = LyricsManager.buildCompactSearchQuery(song)
                        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra("query", query)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Create & Sync Lyrics") },
                    onClick = {
                        showLyricsMenu = false
                        onNavigateToLyricsCreator()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                if (lyrics.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Clear Lyrics", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showLyricsMenu = false
                            LyricsManager.deleteLyrics(context, song)
                            refreshTrigger++
                            Toast.makeText(context, "Lyrics cleared", Toast.LENGTH_SHORT).show()
                        },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }

        // PASTE LYRICS DIALOG
        if (showPasteDialog) {
            AlertDialog(
                onDismissRequest = { showPasteDialog = false },
                title = { Text("Paste Lyrics") },
                text = {
                    OutlinedTextField(
                        value = pastedLyricsContent,
                        onValueChange = { pastedLyricsContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("Paste raw lyrics or timestamped LRC format here...") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (pastedLyricsContent.isNotBlank()) {
                                LyricsManager.saveLyrics(context, song, pastedLyricsContent)
                                refreshTrigger++
                                Toast.makeText(context, "Lyrics saved", Toast.LENGTH_SHORT).show()
                            }
                            showPasteDialog = false
                        }
                    ) {
                        Text("Save")
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
}

private fun formatPosition(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
