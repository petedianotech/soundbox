package com.example.ui.screens.nowplaying

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.data.model.Song
import com.example.player.LyricsManager
import com.example.ui.components.ArtworkThumbnail
import com.example.ui.components.CosmicHoloSpectrumRing
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.EqualizerPanel
import com.example.ui.components.RealtimeAudioVisualizer
import com.example.ui.components.OnlineCoverDialog
import com.example.ui.components.SongDeleteDialog
import com.example.ui.components.SongImagePlaceholder
import com.example.ui.components.SongOptionsBottomSheet
import com.example.ui.components.SleekRoundSlider
import com.example.ui.components.ThumbnailPickerSheet
import com.example.ui.viewmodel.MusicViewModel
import com.example.util.ThumbnailExporter
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
    val sleepTimerLeft by viewModel.sleepTimerMillis.collectAsState()
    val eqEnabled by viewModel.equalizerEnabled.collectAsState()
    val eqPreset by viewModel.equalizerPreset.collectAsState()
    val eqBands by viewModel.equalizerBands.collectAsState()
    val bassStrength by viewModel.bassBoostStrength.collectAsState()
    val virtStrength by viewModel.virtualizerStrength.collectAsState()
    val audioSessionId by viewModel.audioSessionId.collectAsState()
    val showSpectrum by viewModel.showSpectrum.collectAsState()
    val globalThumbIndex by viewModel.globalThumbnailIndex.collectAsState()
    val songThumbMap by viewModel.songThumbnailMap.collectAsState()

    var showVisualizer by remember { mutableStateOf(true) }
    var showThumbnailPicker by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    var isLyricsViewActive by remember { mutableStateOf(false) }
    var showLyricsMenu by remember { mutableStateOf(false) }
    var lyricsShowOnlyEdit by remember { mutableStateOf(true) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedLyricsContent by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }
    var showTrackOptionsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showOnlineCoverDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    val content = reader.readText()
                    if (currentSong != null) {
                        LyricsManager.saveLyrics(context, currentSong!!, content)
                        refreshTrigger++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val deviceCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (currentSong != null) {
                coroutineScope.launch {
                    val success = com.example.util.OnlineCoverFetcher.saveUriAsCover(context, currentSong!!.id, it)
                    if (success) {
                        viewModel.refreshCurrentSongArtwork()
                        viewModel.scanStorage()
                        refreshTrigger++
                        Toast.makeText(context, "Album art updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            deviceCoverLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "Permission denied. Cannot select image.", Toast.LENGTH_SHORT).show()
        }
    }

    val requestCoverPermissionAndLaunch = {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            deviceCoverLauncher.launch("image/*")
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }

    if (currentSong == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyPlaceholder(
                title = "Nothing is playing",
                subtitle = "Select a song from your library to start listening.",
                icon = Icons.Default.MusicNote,
                actionText = "Back to Songs",
                onActionClick = onBackClick
            )
        }
    } else {
        val song = currentSong!!
        val lyrics = remember(song.id, duration, refreshTrigger) { getLyricsForSong(context, song, duration) }
        val hasTimestamps = remember(lyrics) { lyrics.any { it.isDynamic } }
        val activeVerseIndexByTime = if (hasTimestamps) lyrics.indexOfLast { position >= it.timeMs } else -1
        val currentActiveIndex = activeVerseIndexByTime.coerceAtLeast(0)
        val lyricsListState = rememberLazyListState()

        LaunchedEffect(currentActiveIndex, isLyricsViewActive) {
            if (isLyricsViewActive && hasTimestamps && currentActiveIndex >= 0 && lyrics.isNotEmpty()) {
                lyricsListState.animateScrollToItem(currentActiveIndex)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", modifier = Modifier.size(28.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.setShowSpectrum(!showSpectrum) }) {
                            Icon(
                                imageVector = if (showSpectrum) Icons.Default.GraphicEq else Icons.Default.VisibilityOff,
                                contentDescription = if (showSpectrum) "Hide Spectrum Visualizer" else "Show Spectrum Visualizer",
                                tint = if (showSpectrum) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(onClick = { showThumbnailPicker = true }) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Switch Thumbnail",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showEffectsSheet = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Sound & Effects", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { isLyricsViewActive = !isLyricsViewActive }) {
                            Icon(
                                imageVector = if (isLyricsViewActive) Icons.Filled.Lyrics else Icons.Outlined.Lyrics,
                                contentDescription = "Toggle Lyrics",
                                tint = if (isLyricsViewActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showTrackOptionsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
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
                    Spacer(modifier = Modifier.height(12.dp))

                    // Artwork or Lyrics View
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
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
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    "No lyrics found",
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    "Import a lyrics file or paste lyrics to read along.",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                                                Icon(Icons.Default.Image, contentDescription = "Show Artwork", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            IconButton(onClick = { lyricsShowOnlyEdit = true; showLyricsMenu = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Lyrics Options", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            } else {
                                CosmicHoloSpectrumRing(
                                    audioSessionId = audioSessionId,
                                    isPlaying = isPlaying,
                                    enabled = showSpectrum,
                                    innerRadiusDp = 127.5.dp,
                                    modifier = Modifier.size(310.dp)
                                ) {
                                    val artworkScale by animateFloatAsState(
                                        targetValue = if (isPlaying) 1.0f else 0.96f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                        label = "ArtworkScale"
                                    )

                                    Surface(
                                        modifier = Modifier
                                            .size(255.dp)
                                            .graphicsLayer {
                                                scaleX = artworkScale
                                                scaleY = artworkScale
                                            }
                                            .clickable { isLyricsViewActive = true },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shadowElevation = 8.dp,
                                        tonalElevation = 4.dp
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ArtworkThumbnail(
                                                songId = song.id,
                                                title = song.title,
                                                thumbnailIndex = songThumbMap[song.id] ?: globalThumbIndex,
                                                modifier = Modifier.fillMaxSize(),
                                                size = 255f,
                                                isCircle = true
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 20.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.Lyrics, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Text("LYRICS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title & Artist with Favorite Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
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

                        IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = if (song.isFavorite) "Liked" else "Like song",
                                tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.surfaceContainer,
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
                                        text = "Lyrics",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.OpenInFull,
                                        contentDescription = "Expand Lyrics",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                if (lyrics.isEmpty()) {
                                    Text(
                                        text = "Tap to add lyrics for this song",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    val activeLineText = lyrics.getOrNull(currentActiveIndex)?.text ?: ""
                                    Text(
                                        text = activeLineText.ifEmpty { "🎵 Instrumental..." },
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
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
                        .padding(bottom = 12.dp),
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
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = CircleShape
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Realtime Frequency Visualizer
                    if (showVisualizer && showSpectrum) {
                        RealtimeAudioVisualizer(
                            audioSessionId = audioSessionId,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            accentColor = MaterialTheme.colorScheme.primary,
                            secondaryColor = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Progress Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SleekRoundSlider(
                            value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth(),
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatPosition(position),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatPosition(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Playback Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.setShuffleMode(!shuffleMode) }) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(onClick = { viewModel.skipPrevious() }) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        FilledIconButton(
                            onClick = { viewModel.playPause() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        IconButton(onClick = { viewModel.skipNext() }) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = {
                                val nextMode = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                viewModel.setRepeatMode(nextMode)
                            }
                        ) {
                            val icon = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Repeat",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Action Buttons (Simple English)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { isLyricsViewActive = !isLyricsViewActive },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Lyrics, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lyrics", style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = { showTimerDialog = true },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Timer", style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = { showVolumeDialog = true },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Volume", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Volume Control Dialog
        if (showVolumeDialog) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val audioManager = remember {
                try {
                    context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                } catch (e: Exception) {
                    null
                }
            }
            var currentVolume by remember {
                mutableStateOf(
                    try {
                        audioManager?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: 0
                    } catch (e: Exception) {
                        0
                    }
                )
            }
            val maxVolume = remember {
                try {
                    audioManager?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
                } catch (e: Exception) {
                    15
                }
            }

            AlertDialog(
                onDismissRequest = { showVolumeDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (currentVolume == 0) Icons.Default.VolumeMute else if (currentVolume < maxVolume / 2) Icons.Default.VolumeDown else Icons.Default.VolumeUp,
                            contentDescription = "Volume",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text("Volume Control", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Media Volume", style = MaterialTheme.typography.bodyMedium)
                            Text("${(currentVolume * 100 / maxVolume)}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                        }
                        SleekRoundSlider(
                            value = currentVolume.toFloat(),
                            onValueChange = {
                                val targetVol = it.toInt()
                                audioManager?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                                currentVolume = targetVol
                            },
                            valueRange = 0f..maxVolume.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showVolumeDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }

        // Sleep Timer Dialog
        if (showTimerDialog) {
            AlertDialog(
                onDismissRequest = { showTimerDialog = false },
                title = { Text("Sleep Timer") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Turn off music automatically after:")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(5, 15, 30, 45, 60).forEach { mins ->
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

        // Sound & Effects Bottom Sheet
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

                    // Speed Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Playback Speed", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                        Text("${String.format(Locale.US, "%.2f", speed)}x", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                    }
                    SleekRoundSlider(
                        value = speed,
                        valueRange = 0.5f..2.0f,
                        onValueChange = { viewModel.setPlaybackRate(it, pitch) },
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pitch Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Audio Pitch", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                        Text("${String.format(Locale.US, "%.2f", pitch)}x", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                    }
                    SleekRoundSlider(
                        value = pitch,
                        valueRange = 0.5f..1.5f,
                        onValueChange = { viewModel.setPlaybackRate(speed, it) },
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Studio Equalizer Panel
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

        // Lyrics Options Dialog
        if (showLyricsMenu) {
            val hasSavedLyrics = LyricsManager.loadLyrics(context, song) != null
            AlertDialog(
                onDismissRequest = { showLyricsMenu = false },
                title = { Text(if (hasSavedLyrics) "Lyrics Options" else "Add Lyrics") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (hasSavedLyrics && lyricsShowOnlyEdit) {
                            // Only show Edit options first
                            FilledTonalButton(
                                onClick = {
                                    showLyricsMenu = false
                                    onNavigateToLyricsCreator()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Edit in Creator")
                            }
                            OutlinedButton(
                                onClick = {
                                    showLyricsMenu = false
                                    pastedLyricsContent = LyricsManager.loadLyrics(context, song) ?: ""
                                    showPasteDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Edit Text Directly")
                            }
                            OutlinedButton(
                                onClick = {
                                    lyricsShowOnlyEdit = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Other Details / Options")
                            }
                        } else {
                            // Show all options (either no lyrics exist yet, or user clicked "Other Details / Options")
                            FilledTonalButton(
                                onClick = {
                                    showLyricsMenu = false
                                    onNavigateToLyricsCreator()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (hasSavedLyrics) "Edit in Creator" else "Create Lyrics")
                            }
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
                                    showLyricsMenu = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Search on Google")
                            }
                            OutlinedButton(
                                onClick = {
                                    showLyricsMenu = false
                                    pastedLyricsContent = LyricsManager.loadLyrics(context, song) ?: ""
                                    showPasteDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (hasSavedLyrics) "Edit Text Directly" else "Paste Lyrics")
                            }
                            OutlinedButton(
                                onClick = {
                                    showLyricsMenu = false
                                    fileLauncher.launch("*/*")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Choose Lyrics File (.lrc / .txt)")
                            }
                            if (hasSavedLyrics) {
                                Button(
                                    onClick = {
                                        LyricsManager.saveLyrics(context, song, "")
                                        refreshTrigger++
                                        showLyricsMenu = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Delete Lyrics")
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showLyricsMenu = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

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
                        placeholder = { Text("Type or paste lyrics here...") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (pastedLyricsContent.isNotBlank()) {
                            LyricsManager.saveLyrics(context, song, pastedLyricsContent)
                            refreshTrigger++
                        }
                        showPasteDialog = false
                    }) {
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

        // Song Options Bottom Sheet
        if (showTrackOptionsSheet) {
            SongOptionsBottomSheet(
                song = song,
                onDismiss = { showTrackOptionsSheet = false },
                onPlayNext = { viewModel.playNext(song) },
                onAddToQueue = { viewModel.addToQueue(song) },
                onToggleFavorite = { viewModel.toggleFavorite(song) },
                onAddToPlaylist = { /* Playlist integration */ },
                onDownloadThumbnail = {
                    coroutineScope.launch {
                        ThumbnailExporter.exportSongThumbnail(context, song)
                    }
                },
                onDeleteSong = {
                    showDeleteConfirmDialog = true
                },
                onChangeThumbnail = {
                    showThumbnailPicker = true
                },
                onDownloadOnlineCover = {
                    showOnlineCoverDialog = true
                },
                onChooseFromDevice = requestCoverPermissionAndLaunch
            )
        }

        if (showOnlineCoverDialog) {
            OnlineCoverDialog(
                song = song,
                onDismissRequest = { showOnlineCoverDialog = false },
                onCoverUpdated = {
                    viewModel.refreshCurrentSongArtwork()
                    refreshTrigger++
                }
            )
        }

        // Thumbnail Picker Sheet
        if (showThumbnailPicker) {
            ThumbnailPickerSheet(
                currentSelection = songThumbMap[song.id] ?: globalThumbIndex,
                onThumbnailSelected = { selectedIndex ->
                    viewModel.setSongThumbnailIndex(song.id, selectedIndex)
                },
                onDismissRequest = { showThumbnailPicker = false },
                title = "Thumbnail for \"${song.title}\""
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmDialog) {
            SongDeleteDialog(
                song = song,
                onConfirm = {
                    viewModel.deleteSong(song)
                    showDeleteConfirmDialog = false
                    Toast.makeText(context, "Song deleted", Toast.LENGTH_SHORT).show()
                    onBackClick()
                },
                onDismiss = { showDeleteConfirmDialog = false }
            )
        }
    }
}

private fun formatPosition(durationMs: Long): String {
    val totalSecs = durationMs / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
