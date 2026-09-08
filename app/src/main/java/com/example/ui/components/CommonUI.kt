package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.data.model.Song
import com.example.util.AlbumArtHelper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.example.ui.theme.SoundboxTheme

@Composable
fun SongImagePlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    size: Float = 48f,
    artist: String = "",
    genre: String = "",
    songId: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cornerRadius = when {
        size >= 120f -> 24.dp
        size >= 72f -> 16.dp
        else -> 12.dp
    }
    val shape = RoundedCornerShape(cornerRadius)
    val fallbackArtResId = remember(title, artist, genre, songId) {
        AlbumArtHelper.getAlbumArtResId(title, artist, genre, songId, context)
    }

    Surface(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = shape
            ),
        color = Color(0xFF0D121B),
        shape = shape
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = fallbackArtResId),
                contentDescription = "Album art for $title",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Soft studio ambient gradient to add warm audiophile depth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.24f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun ArtworkThumbnail(
    songId: String?,
    title: String,
    modifier: Modifier = Modifier,
    size: Float = 48f,
    isCircle: Boolean = false,
    artist: String = "",
    genre: String = "",
    path: String = ""
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val artworkModel = remember(songId, title, artist, genre, path) {
        AlbumArtHelper.getArtworkModel(context, songId, title, artist, genre, path)
    }

    val cornerRadius = when {
        size >= 120f -> 24.dp
        size >= 72f -> 16.dp
        else -> 12.dp
    }
    val shape = if (isCircle) CircleShape else RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = shape
            ),
        shape = shape,
        color = Color(0xFF0D121B)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            coil.compose.SubcomposeAsyncImage(
                model = artworkModel,
                contentDescription = "Song artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    SongImagePlaceholder(
                        title = title,
                        artist = artist,
                        genre = genre,
                        songId = songId,
                        modifier = Modifier.fillMaxSize(),
                        size = size
                    )
                },
                error = {
                    SongImagePlaceholder(
                        title = title,
                        artist = artist,
                        genre = genre,
                        songId = songId,
                        modifier = Modifier.fillMaxSize(),
                        size = size
                    )
                }
            )

            // Subtle studio vignette to give deep, luxurious vinyl black levels
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.20f)
                            )
                        )
                    )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    extraInfo: String? = null
) {
    val colors = SoundboxTheme.colors
    Surface(
        color = if (isSelected) {
            colors.accentCyan.copy(alpha = 0.18f)
        } else if (isPlaying) {
            colors.surfaceElevated
        } else {
            Color.Transparent
        },
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        val colors = SoundboxTheme.colors
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkThumbnail(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                genre = song.genre,
                path = song.path,
                size = 48f
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isPlaying) FontWeight.Black else FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    ),
                    color = if (isPlaying) colors.accentCyan else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.accentCyan.copy(alpha = 0.16f))
                                .border(0.5.dp, colors.accentCyan.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "PLAYING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 7.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = colors.accentCyan
                            )
                        }
                    }
                    val subtitleText = if (!extraInfo.isNullOrEmpty()) {
                        "${song.artist} • $extraInfo"
                    } else {
                        "${song.artist} • ${song.album}"
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (song.rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFD700).copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "${song.rating}★",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                ),
                                color = Color(0xFFFFD700)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = if (song.isFavorite) "Unlike song" else "Like song",
                    tint = if (song.isFavorite) colors.accentCyan else colors.textMuted,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Song options",
                    tint = colors.textMuted
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MiniPlayer(
    currentSong: Song?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipPrevious: (() -> Unit)? = null,
    onFavoriteToggle: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    progress: Float = 0f,
    onSeekProgress: ((Float) -> Unit)? = null
) {
    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        currentSong?.let { song ->
            val colors = SoundboxTheme.colors
            var offsetX by remember { mutableFloatStateOf(0f) }
            val swipeThreshold = 120f
            val context = androidx.compose.ui.platform.LocalContext.current

            // Pulsing animation for subtle waveform / glow
            val infiniteTransition = rememberInfiniteTransition(label = "miniWave")
            val wavePulse by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(650, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wavePulse"
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
                    .pointerInput(song.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (offsetX > swipeThreshold) {
                                    // Swiped Right -> Previous
                                    onSkipPrevious?.invoke()
                                } else if (offsetX < -swipeThreshold) {
                                    // Swiped Left -> Next
                                    onSkipNext()
                                }
                                offsetX = 0f
                            },
                            onDragCancel = { offsetX = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount).coerceIn(-250f, 250f)
                            }
                        )
                    }
                    .clickable { onOpenNowPlaying() },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = colors.miniPlayerBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.8f)),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // INTERACTIVE PROGRESS & MICRO-WAVEFORM BAR
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(colors.borderSubtle)
                            .pointerInput(onSeekProgress) {
                                if (onSeekProgress != null) {
                                    detectTapGestures { offset ->
                                        val newProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        onSeekProgress(newProgress)
                                    }
                                }
                            }
                    ) {
                        // Dynamic gradient fill for progress
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            colors.accentCyan,
                                            colors.accentLime
                                        )
                                    )
                                )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ARTWORK WITH LIVE PLAYING BADGE
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                        ) {
                            ArtworkThumbnail(
                                songId = song.id,
                                title = song.title,
                                artist = song.artist,
                                genre = song.genre,
                                path = song.path,
                                size = 48f,
                                isCircle = false
                            )

                            if (isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(3.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(colors.accentCyan)
                                        .border(1.5.dp, Color.Black, CircleShape)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // TRACK INFO & LIVE BITRATE / FORMAT PILL
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp
                                    ),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                // Micro Equalizer Waves indicator when active
                                if (isPlaying) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                                        verticalAlignment = Alignment.Bottom,
                                        modifier = Modifier.height(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height((6 * wavePulse + 3).dp)
                                                .background(colors.accentCyan, CircleShape)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height((8 * (1.35f - wavePulse) + 2).dp)
                                                .background(colors.accentLime, CircleShape)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height((7 * wavePulse + 2).dp)
                                                .background(colors.accentCyan, CircleShape)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                // Audio Quality Tag
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.accentCyan.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (song.path.endsWith(".flac", ignoreCase = true)) "FLAC 24-BIT" else "320 KBPS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        ),
                                        color = colors.accentCyan,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // OPTIONAL FAVORITE QUICK TOGGLE
                        if (onFavoriteToggle != null) {
                            IconButton(
                                onClick = onFavoriteToggle,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Toggle Favorite",
                                    tint = if (isFavorite) Color(0xFFFF5252) else colors.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // PLAY / PAUSE BUTTON (High Contrast)
                        FilledIconButton(
                            onClick = onPlayPause,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colors.accentCyan,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // SKIP NEXT BUTTON
                        FilledTonalIconButton(
                            onClick = onSkipNext,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.surfaceElevated,
                                contentColor = colors.textPrimary
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Song",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyPlaceholder(
    title: String,
    subtitle: String,
    icon: ImageVector,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onActionClick) {
                Text(text = actionText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StarRatingBar(
    rating: Int,
    onRatingChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxStars: Int = 5,
    starSize: Int = 28,
    activeColor: Color = Color(0xFFFFD700),
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
    readOnly: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 1..maxStars) {
            val isFilled = i <= rating
            val icon = if (isFilled) Icons.Default.Star else Icons.Default.StarOutline
            val tint = if (isFilled) activeColor else inactiveColor
            
            IconButton(
                onClick = {
                    if (!readOnly) {
                        if (rating == i) {
                            onRatingChanged(0) // Toggle off to 0
                        } else {
                            onRatingChanged(i)
                        }
                    }
                },
                enabled = !readOnly,
                modifier = Modifier.size((starSize + 12).dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "$i Star${if (i > 1) "s" else ""}",
                    tint = tint,
                    modifier = Modifier.size(starSize.dp)
                )
            }
        }
    }
}

