package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.data.model.Song
import com.example.util.AlbumArtHelper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@Composable
fun SongImagePlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    size: Float = 48f,
    artist: String = "",
    genre: String = ""
) {
    val shape = RoundedCornerShape((size * 0.22f).dp)
    val fallbackArtResId = remember(title, artist, genre) {
        AlbumArtHelper.getAlbumArtResId(title, artist, genre)
    }

    Surface(
        modifier = modifier
            .size(size.dp)
            .clip(shape),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = shape
    ) {
        Image(
            painter = painterResource(id = fallbackArtResId),
            contentDescription = "Album art for $title",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
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
    genre: String = ""
) {
    val artworkUri = remember(songId) {
        if (!songId.isNullOrEmpty()) {
            android.net.Uri.parse("content://media/external/audio/media/${songId}/albumart")
        } else {
            null
        }
    }

    val shape = if (isCircle) CircleShape else RoundedCornerShape((size * 0.2f).dp)

    Surface(
        modifier = modifier
            .size(size.dp)
            .clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (artworkUri != null) {
            coil.compose.SubcomposeAsyncImage(
                model = artworkUri,
                contentDescription = "Song artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    SongImagePlaceholder(title = title, artist = artist, genre = genre, modifier = Modifier.fillMaxSize(), size = size)
                },
                error = {
                    SongImagePlaceholder(title = title, artist = artist, genre = genre, modifier = Modifier.fillMaxSize(), size = size)
                }
            )
        } else {
            SongImagePlaceholder(title = title, artist = artist, genre = genre, modifier = Modifier.fillMaxSize(), size = size)
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
    Surface(
        color = if (isSelected) {
            Color(0xFF00E5FF).copy(alpha = 0.18f)
        } else if (isPlaying) {
            Color(0xFF142436)
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
                    color = if (isPlaying) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface,
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
                                .background(Color(0xFF00E5FF))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "PLAYING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 7.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.Black
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
                        color = Color(0xFF8A99AD),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (song.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (song.isFavorite) Color(0xFFFF5252) else Color(0xFF67778D),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Song options",
                    tint = Color(0xFF67778D)
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenNowPlaying() },
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = Color(0xFF0F1521),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2B3E)),
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
                            color = Color(0xFF00E5FF),
                            trackColor = Color(0xFF141D2B)
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
                                color = Color.White,
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
                                color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledIconButton(
                            onClick = onPlayPause,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF00E5FF),
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

                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Song",
                                tint = Color.White
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

