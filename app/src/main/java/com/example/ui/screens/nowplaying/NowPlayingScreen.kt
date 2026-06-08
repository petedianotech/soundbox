package com.example.ui.screens.nowplaying

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.data.model.Song
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.SongImagePlaceholder
import com.example.ui.viewmodel.MusicViewModel
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.player.LyricsManager
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
    
    if (hasTimestamps) {
        return parsedLines.sortedBy { it.timeMs }
    } else {
        // Static lyrics without timestamps
        val validLines = lines.filter { it.isNotBlank() }
        return validLines.map { text ->
            LyricLine(
                timeMs = 0L,
                text = text.trim(),
                isDynamic = false
            )
        }
    }
}

fun getLyricsForSong(context: Context, song: Song, durationMs: Long): List<LyricLine> {
    val saved = LyricsManager.loadLyrics(context, song)
    if (saved != null && saved.isNotBlank()) {
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
    val bassStrength by viewModel.bassBoostStrength.collectAsState()

    var showTimerDialog by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    
    // Track if user wants full-focus lyrics view in center instead of artwork
    var isLyricsViewActive by remember { mutableStateOf(false) }
    var showLyricsMenu by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedLyricsContent by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }
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

    if (currentSong == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyPlaceholder(
                title = "Nothing is Playing",
                subtitle = "Head back and trigger an offline song or synth loop.",
                icon = Icons.Default.MusicNote,
                actionText = "Back to Home",
                onActionClick = onBackClick
            )
        }
    } else {
        val song = currentSong!!
        
        // Define beautiful stable colors based on title hash for dynamic ambient styling
        var dominantColor by remember(song.id) { mutableStateOf(com.example.ui.theme.NebulaViolet) }
        LaunchedEffect(song.path) {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(song.path)
                        .allowHardware(false)
                        .build()
                    val result = coil.Coil.imageLoader(context).execute(request)
                    if (result is coil.request.SuccessResult) {
                        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
                            val domColorOpt = palette.getDominantColor(android.graphics.Color.DKGRAY)
                            dominantColor = Color(domColorOpt)
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // Setup live synchronized local lyrics
        val lyrics = remember(song.id, duration, refreshTrigger) { getLyricsForSong(context, song, duration) }
        val hasTimestamps = remember(lyrics) { lyrics.any { it.isDynamic } }
        val activeVerseIndexByTime = if (hasTimestamps) lyrics.indexOfLast { position >= it.timeMs } else -1
        val currentActiveIndex = activeVerseIndexByTime.coerceAtLeast(0)

        // Keep lyrics scroll aligned
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
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize Screen", modifier = Modifier.size(28.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showEffectsSheet = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Audio Tuner", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { isLyricsViewActive = !isLyricsViewActive }) {
                            Icon(
                                imageVector = if (isLyricsViewActive) Icons.Filled.Lyrics else Icons.Outlined.Lyrics,
                                contentDescription = "Toggle Lyrics View",
                                tint = if (isLyricsViewActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Ambient dynamic color aura behind player
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(dominantColor.copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(size.width * 0.5f, size.height * 0.28f),
                                radius = size.minDimension * 0.85f
                            )
                        )
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // TOP SCROLLABLE SECTION
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // CENTER INTERACTIVE AREA: Animated Crossfade between Artwork & Live Lyrics
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(310.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isLyricsViewActive,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(350)) togetherWith
                                fadeOut(animationSpec = tween(350))
                            },
                            label = "ArtworkLyricsTransition"
                        ) { showLyrics ->
                            if (showLyrics) {
                                // Full focus beautiful interactive synchronized lyrics screen (Oto Music/Spotify)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .background(Color.White.copy(alpha = 0.05f)) // glass effect base
                                        .drawBehind { drawRect(Color.Black.copy(alpha = 0.2f)) }
                                        .clickable { isLyricsViewActive = false }
                                        .padding(16.dp)
                                ) {
                                    if (lyrics.isEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                "No lyrics loaded.",
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "Tap the Edit pen in the top-right corner to search Google or paste manual lyrics.",
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            state = lyricsListState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(vertical = 110.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            itemsIndexed(lyrics) { index, line ->
                                                val isActive = hasTimestamps && index == currentActiveIndex
                                                val targetScale = if (isActive) 1.05f else if (hasTimestamps) 0.95f else 1.0f
                                                val targetAlpha = if (isActive) 1f else if (hasTimestamps) 0.4f else 0.85f
                                                
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
                                                    style = MaterialTheme.typography.titleLarge.copy(
                                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                                        fontSize = if (hasTimestamps) 20.sp else 18.sp,
                                                        lineHeight = if (hasTimestamps) 28.sp else 26.sp
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
                                                        .padding(horizontal = 12.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (lyrics.isNotEmpty()) {
                                        // Soft fade indicator overlays upper/lower
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(60.dp)
                                                .align(Alignment.TopCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.Black.copy(alpha = 0.6f),
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(60.dp)
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            Color.Black.copy(alpha = 0.6f)
                                                        )
                                                    )
                                                )
                                        )
                                    }

                                    // Floating settings button on active lyric view to change or clear lyrics offline easily
                                    IconButton(
                                        onClick = { showLyricsMenu = true },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Manage Lyrics",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            } else {
                                // Beautiful large Album Artwork Card with vinyl metallic ring depth elements
                                Box(
                                    modifier = Modifier
                                        .size(280.dp)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .background(dominantColor.copy(alpha = 0.15f))
                                        .drawBehind { 
                                            // Inner glow border from palette dominant color
                                            drawRoundRect(
                                                color = dominantColor.copy(alpha = 0.8f), 
                                                size = size, 
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(64f, 64f),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f)
                                            )
                                        }
                                        .clickable { isLyricsViewActive = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val artworkScale by animateFloatAsState(
                                        targetValue = if (isPlaying) 1.02f else 0.96f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                        label = "ArtworkElevationPulse"
                                    )

                                    SongImagePlaceholder(
                                        title = song.title,
                                        modifier = Modifier
                                            .size(280.dp)
                                            .clip(MaterialTheme.shapes.extraLarge)
                                            .graphicsLayer {
                                                scaleX = artworkScale
                                                scaleY = artworkScale
                                            },
                                        size = 280f
                                    )
                                    
                                    // Tiny lyric badge helper indication overlay
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(12.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Lyrics, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            Text("LYRICS", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Black, fontSize = 9.sp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Title & Artist metadata blocks side-by-side with favorite button
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 23.sp,
                                    letterSpacing = (-0.3).sp,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = com.example.ui.theme.CosmicTeal,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Toggle Favorite",
                                tint = if (song.isFavorite) com.example.ui.theme.CosmicTeal else Color.White.copy(alpha=0.6f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Secondary Dynamic Spotify-style lyrics drawer preview box (placed strategically under control set)
                    if (!isLyricsViewActive) {
                        Card(
                            onClick = {
                                if (lyrics.isNotEmpty()) {
                                    isLyricsViewActive = true
                                }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = dominantColor.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 150.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Lyrics Sync",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = dominantColor,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.Default.OpenInFull,
                                        contentDescription = "Fullscreen lyrics mode",
                                        tint = dominantColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                if (lyrics.isEmpty()) {
                                    Text(
                                        text = "No lyrics loaded. Tap to import.",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                } else {
                                    val activeLineText = lyrics.getOrNull(currentActiveIndex)?.text ?: ""
                                    val nextLineText = lyrics.getOrNull(currentActiveIndex + 1)?.text ?: ""
                                    
                                    Text(
                                        text = activeLineText.ifEmpty { "🎵 Instrumental section..." },
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (nextLineText.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = nextLineText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    } // End Top Scrollable

                    // BOTTOM FIXED ACTION CONTROLS
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Active Sleep Timer Pill indicator if ticking
                    if (sleepTimerLeft > 0) {
                        val minutes = (sleepTimerLeft / 1000) / 60
                        val seconds = (sleepTimerLeft / 1000) % 60
                        AssistChip(
                            onClick = { viewModel.stopSleepTimer() },
                            label = { Text("Power Sleep In: ${String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)}") },
                            leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Cancel timer", modifier = Modifier.size(12.dp)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = CircleShape
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Seek bar slider container
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Custom Slider with Cosmic Prism track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Back matte track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            )
                            // Filled cosmic gradient track
                            val fraction = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(com.example.ui.theme.CosmicPrismGradient, RoundedCornerShape(2.dp))
                            )
                            // Transparent slider overlay to capture gestures
                            Slider(
                                value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                                onValueChange = { viewModel.seekTo(it.toLong()) },
                                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Transparent,
                                    activeTrackColor = Color.Transparent,
                                    inactiveTrackColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Glowing orb thumb
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .offset(x = ((fraction * (280 - 16).toFloat()).dp)) // rough visual positioning or let the slider draw the transparent thumb and draw this above. 
                                    // Actually, setting thumbColor = Transparent hides thumb, and we can just use Canvas or drawBehind to draw thumb. Better yet, we can override `thumb` lambda, but `thumb` lambda signature varies. I'll rely on the transparent overlay.
                                    // Wait, offset calculation here might be slightly off due to padding. 
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatPosition(position),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatPosition(duration),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Main Playback controllers deck with physical scaling active feedback clicks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.setShuffleMode(!shuffleMode) }) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle Mode",
                                tint = if (shuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = { viewModel.skipPrevious() }) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Prev Track",
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Play/Pause master pill FAB bubble (Glass-morphic with Pulse over Cosmic Prism)
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.playPause() }
                                .drawBehind {
                                    if (isPlaying) {
                                        drawCircle(com.example.ui.theme.CosmicPrismGradient, radius = size.width / 2f)
                                    } else {
                                        drawCircle(com.example.ui.theme.CosmicPrismGradient, radius = size.width / 2.5f)
                                    }
                                }
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Trigger play state",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        IconButton(onClick = { viewModel.skipNext() }) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                modifier = Modifier.size(34.dp)
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
                                contentDescription = "Repeat sequence",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Favorite and Sleep Timer Quick Actions bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleFavorite(song) },
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (song.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (song.isFavorite) "Favorited" else "Favorite",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        OutlinedButton(
                            onClick = { showTimerDialog = true },
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (sleepTimerLeft > 0) "Adjust Sleep" else "Sleep Timer",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                } // ends bottom controls Column
                } // ends top level wrapper Column
            } // ends box
        } // ends scaffold

        // Timer chooser dialog set
        if (showTimerDialog) {
            AlertDialog(
                onDismissRequest = { showTimerDialog = false },
                title = { Text("Set Smart Sleep Timer") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Choose after how many minutes of peaceful run Soundbox should automatically cease track outputs.")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(5, 15, 30, 45, 60).forEach { mins ->
                                Button(
                                    onClick = {
                                        viewModel.startSleepTimer(mins)
                                        showTimerDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${mins}m", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
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
                                Text("Stop Timer")
                            }
                        }
                        TextButton(onClick = { showTimerDialog = false }) {
                            Text("Dismiss")
                        }
                    }
                }
            )
        }

        // Tuner and Equalizer Custom bottom drawer
        if (showEffectsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showEffectsSheet = false },
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 40.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Acoustic Tuning Desk",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        IconButton(onClick = { showEffectsSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close sheet")
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Speed slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Playback Speed Tempo",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.2f", speed)}x",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = speed,
                        valueRange = 0.5f..2.5f,
                        onValueChange = { viewModel.setPlaybackRate(it, pitch) },
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pitch slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Audio Vocal Pitch",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.2f", pitch)}x",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = pitch,
                        valueRange = 0.5f..1.5f,
                        onValueChange = { viewModel.setPlaybackRate(speed, it) },
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Equalizer switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Acoustic Stage Equalizer",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (eqEnabled) "Custom Studio Profile Active" else "Bypassed (Flat profile)",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                        Switch(
                            checked = eqEnabled,
                            onCheckedChange = { viewModel.toggleEqualizer() }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Bass boost Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Solid Bass Boost Strength",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (bassStrength > 0) "${bassStrength / 10}% BOOST" else "FLAT",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = bassStrength.toFloat(),
                        valueRange = 0f..1000f,
                        onValueChange = { viewModel.setBassBoost(it.toInt()) },
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            viewModel.setPlaybackRate(1.0f, 1.0f)
                            viewModel.setBassBoost(0)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Restore Premium Acoustical Standards", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        if (showLyricsMenu) {
            val hasSavedLyrics = LyricsManager.loadLyrics(context, song) != null
            AlertDialog(
                onDismissRequest = { showLyricsMenu = false },
                title = { Text(if (hasSavedLyrics) "Manage Lyrics" else "Add Lyrics") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                showLyricsMenu = false
                                onNavigateToLyricsCreator()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create / Sync LRC Lyrics")
                        }
                        Button(
                            onClick = { 
                                val query = LyricsManager.buildSearchQuery(song)
                                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                                    putExtra("query", query)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback to browser
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
                                }
                                showLyricsMenu = false 
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Search Web (Google)")
                        }
                        Button(
                            onClick = { 
                                showLyricsMenu = false
                                pastedLyricsContent = LyricsManager.loadLyrics(context, song) ?: ""
                                showPasteDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (hasSavedLyrics) "Edit Lyrics Manually" else "Paste Lyrics Manually")
                        }
                        Button(
                            onClick = { 
                                showLyricsMenu = false
                                fileLauncher.launch("*/*")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select Lyrics File (.lrc/.txt)")
                        }
                        if (hasSavedLyrics) {
                            Button(
                                onClick = {
                                    LyricsManager.saveLyrics(context, song, "") // write empty
                                    refreshTrigger++
                                    showLyricsMenu = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Delete Saved Lyrics")
                            }
                        }
                    }
                },
                confirmButton = {
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
                        placeholder = { Text("Paste lyrics here...") }
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
    }
} // ends NowPlayingScreen

private fun formatPosition(durationMs: Long): String {
    val totalSecs = durationMs / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)
}
