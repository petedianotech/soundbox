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
import java.util.Locale

data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * Dynamically computes a beautiful synchronized lyric set mathematical to track length.
 */
fun getLyricsForSong(song: Song, durationMs: Long): List<LyricLine> {
    val title = song.title.lowercase()
    val startOffset = 3000L
    val finalDuration = if (durationMs <= 0) 180000L else durationMs
    
    val baseLyrics = when {
        title.contains("ambient") || title.contains("synth") || title.contains("loop") || title.contains("beat") -> listOf(
            "🎵 [Instrumental Synthesizer Intro]",
            "Deep resonance spreading through the air...",
            "Waves of analog oscillators rising...",
            "A cosmic heartbeat pulsing in slow motion.",
            "Filtering the cutoff frequencies from negative peaks...",
            "Sub-bass rattling the physical foundations.",
            "🌌 [Shimmering Echo Patterns]",
            "Modulated audio delays creating endless virtual space.",
            "Harmonic overtones blending into a wash of pink noise...",
            "Sinking deep into the offline soundscape floor.",
            "🎛️ [Filter Sweep Resonance Peak]",
            "The synthetic breeze cools down.",
            "Fading out into pure, isolated offline silence."
        )
        title.contains("acoustic") || title.contains("guitar") || title.contains("folk") || title.contains("live") -> listOf(
            "🎸 [Soft Acoustic Fingerpicking Intro]",
            "Cold wind blowing through the open mountain pines...",
            "I found an old guitar under the dust of forgotten times.",
            "No cloud servers reaching where we stand,",
            "Just raw solid wood and copper string within my hand.",
            "Every acoustic vibration tells a beautiful past legacy,",
            "A tactile custom frequency that is built to last.",
            "🍃 [Warm Melodic Chorus]",
            "Oh, Soundbox, humming in the twilight gray,",
            "Carry these acoustic memories from yesterday.",
            "We don't need the server, we don't need the line,",
            "This local sandbox keeps our chords aligned.",
            "🍂 [Chuckle and Harmonic Tap Outro]",
            "The stars are settling in a quiet offline array.",
            "Fading like wildfire smoke, we drift away."
        )
        else -> listOf(
            "🎶 [Acoustic Melodic Intro]",
            "Walking through the neon lit offline streets,",
            "Feel the direct connection as the hardware tempo beats.",
            "No telemetry trackers following, no central cloud constraint,",
            "Just raw offline storage, with nothing left to paint.",
            "You ask for premium styling, you ask to break the chain,",
            "We build this local temple in the offline rain.",
            "🔥 [Swell of Resonance Chorus]",
            "Oh, Soundbox calling, clear and loud!",
            "Wander isolated, far away from any crowd.",
            "With custom dynamic themes, and bass boost tuned so fine,",
            "A masterwork of code, aligned in design offline.",
            "Let the high-fidelity frequencies engage,",
            "We write our names upon the premium page.",
            "✨ [Guitar Solo Reverb Outro]",
            "Feel the beautiful drift, peaceful energy,",
            "Ending in a sweet offline acoustic harmony."
        )
    }

    val activeTime = (finalDuration - startOffset - 5000L).coerceAtLeast(10000L)
    val interval = if (baseLyrics.size > 1) activeTime / (baseLyrics.size - 1) else 0L

    return baseLyrics.mapIndexed { index, text ->
        LyricLine(
            timeMs = startOffset + (index * interval),
            text = text
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    onBackClick: () -> Unit
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
        val colors = listOf(
            Color(0xFFFF8A80), Color(0xFFFF80AB), Color(0xFFEA80FC), Color(0xFFB388FF),
            Color(0xFF82B1FF), Color(0xFF80D8FF), Color(0xFF84FFFF), Color(0xFFA7FFEB),
            Color(0xFFB9F6CA), Color(0xFFFFE57F), Color(0xFFFFD180), Color(0xFFFF9E80)
        )
        val colorIndex = song.title.hashCode().coerceAtLeast(0) % colors.size
        val dominantColor = colors[colorIndex]

        // Setup live synchronized local lyrics
        val lyrics = remember(song.id, duration) { getLyricsForSong(song, duration) }
        val activeVerseIndexByTime = lyrics.indexOfLast { position >= it.timeMs }
        val currentActiveIndex = activeVerseIndexByTime.coerceAtLeast(0)

        // Keep lyrics scroll aligned
        val lyricsListState = rememberLazyListState()
        LaunchedEffect(currentActiveIndex, isLyricsViewActive) {
            if (isLyricsViewActive && currentActiveIndex >= 0 && lyrics.isNotEmpty()) {
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
                    modifier = Modifier
                        .fillMaxSize()
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
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                        .clickable { isLyricsViewActive = false }
                                        .padding(16.dp)
                                ) {
                                    LazyColumn(
                                        state = lyricsListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 110.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        itemsIndexed(lyrics) { index, line ->
                                            val isActive = index == currentActiveIndex
                                            val scale by animateFloatAsState(
                                                targetValue = if (isActive) 1.05f else 0.95f,
                                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                                label = "LyricScale"
                                            )
                                            val alpha by animateFloatAsState(
                                                targetValue = if (isActive) 1f else 0.4f,
                                                animationSpec = tween(280),
                                                label = "LyricAlpha"
                                            )
                                            
                                            Text(
                                                text = line.text,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                                    fontSize = 20.sp,
                                                    lineHeight = 28.sp
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
                                                    .padding(horizontal = 12.dp)
                                            )
                                        }
                                    }
                                    
                                    // Soft fade indicator overlays upper/lower
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .align(Alignment.TopCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color.Transparent,
                                                        MaterialTheme.colorScheme.background.copy(alpha = 0.8f)
                                                    )
                                                )
                                            )
                                    )
                                }
                            } else {
                                // Beautiful large Album Artwork Card with vinyl metallic ring depth elements
                                Box(
                                    modifier = Modifier
                                        .size(280.dp)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .background(dominantColor.copy(alpha = 0.1f))
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

                    // Title & Artist metadata blocks centered (Premium sliding effect placeholders)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 23.sp,
                                letterSpacing = (-0.3).sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
                        Slider(
                            value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(position),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDuration(duration),
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

                        // Play/Pause master pill FAB bubble
                        Surface(
                            onClick = { viewModel.playPause() },
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .size(76.dp, 76.dp)
                                .clip(RoundedCornerShape(26.dp))
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Trigger play state",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
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

                    // Secondary Dynamic Spotify-style lyrics drawer preview box (placed strategically under control set)
                    if (!isLyricsViewActive && lyrics.isNotEmpty()) {
                        Card(
                            onClick = { isLyricsViewActive = true },
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
                                        text = "Lyrics Live Sync",
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
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Favorite and Sleep Timer Quick Actions bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
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
                }
            }
        }

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
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSecs = durationMs / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
