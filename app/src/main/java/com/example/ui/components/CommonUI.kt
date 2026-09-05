package com.example.ui.components

import androidx.compose.animation.*
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
    progress: Float = 0f
) {
    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        currentSong?.let { song ->
            val colors = SoundboxTheme.colors
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenNowPlaying() },
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = colors.miniPlayerBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (progress in 0f..1f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = colors.accentCyan,
                            trackColor = colors.borderSubtle
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkThumbnail(
                            songId = song.id,
                            title = song.title,
                            artist = song.artist,
                            genre = song.genre,
                            path = song.path,
                            size = 46f,
                            isCircle = false
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.3.sp
                                ),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${song.artist} • ${if (song.path.endsWith(".flac")) "FLAC 24-bit" else "320 kbps"}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                color = colors.accentCyan.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledIconButton(
                            onClick = onPlayPause,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colors.accentCyan,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        FilledTonalIconButton(
                            onClick = onSkipNext,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.surfaceElevated,
                                contentColor = colors.textPrimary
                            ),
                            modifier = Modifier.size(40.dp)
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

