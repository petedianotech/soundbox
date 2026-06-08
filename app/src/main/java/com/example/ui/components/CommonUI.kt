package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@Composable
fun SongImagePlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    size: Float = 48f
) {
    // Generate beautiful abstract nebula/light-streak layout instead of static icon
    val hash = title.hashCode().coerceAtLeast(0)
    val color1 = Color(0xFF1A237E)
    val color2 = Color(hash % 256, (hash / 256) % 256, 255)
    val color3 = com.example.ui.theme.CosmicTeal

    Box(
        modifier = modifier
            .size(size.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(listOf(color1, Color.Transparent)),
                radius = size.dp.toPx() * 0.8f,
                center = androidx.compose.ui.geometry.Offset(size.dp.toPx() * 0.2f, size.dp.toPx() * 0.2f)
            )
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(listOf(color2.copy(alpha=0.8f), Color.Transparent)),
                radius = size.dp.toPx() * 0.6f,
                center = androidx.compose.ui.geometry.Offset(size.dp.toPx() * 0.8f, size.dp.toPx() * 0.8f)
            )
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(listOf(color3.copy(alpha=0.6f), Color.Transparent)),
                radius = size.dp.toPx() * 0.5f,
                center = androidx.compose.ui.geometry.Offset(size.dp.toPx() * 0.5f, size.dp.toPx() * 0.4f)
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
    isCircle: Boolean = false
) {
    val artworkUri = remember(songId) {
        if (!songId.isNullOrEmpty()) {
            android.net.Uri.parse("content://media/external/audio/media/${songId}/albumart")
        } else {
            null
        }
    }

    val shape = if (isCircle) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
    ) {
        if (artworkUri != null) {
            coil.compose.SubcomposeAsyncImage(
                model = artworkUri,
                contentDescription = "Artwork Thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                loading = {
                    SongImagePlaceholder(title = title, modifier = Modifier.fillMaxSize(), size = size)
                },
                error = {
                    SongImagePlaceholder(title = title, modifier = Modifier.fillMaxSize(), size = size)
                }
            )
        } else {
            SongImagePlaceholder(title = title, modifier = Modifier.fillMaxSize(), size = size)
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) com.example.ui.theme.NebulaViolet.copy(alpha = 0.15f) else Color.Transparent)
            .padding(if (isSelected) 8.dp else 0.dp) // Subtle scale/padding interaction for active rows
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkThumbnail(
            songId = song.id,
            title = song.title,
            size = 48f
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitleText = if (!extraInfo.isNullOrEmpty()) {
                "${song.artist} • ${song.album} • $extraInfo"
            } else {
                "${song.artist} • ${song.album}"
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More Options"
            )
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
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        currentSong?.let { song ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    // Cosmic Pod capsule background and deep-purple drop shadow
                    .drawBehind { 
                        drawRoundRect(
                            color = com.example.ui.theme.NebulaViolet.copy(alpha = 0.4f),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(64f, 64f)
                        )
                    }
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.1f)) // Frosted glass overlay
                    .clickable { onOpenNowPlaying() }
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtworkThumbnail(
                        songId = song.id,
                        title = song.title,
                        size = 44f,
                        isCircle = true
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = com.example.ui.theme.CosmicTeal
                        )
                    }

                    IconButton(onClick = onSkipNext) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip Next",
                            tint = Color.White
                        )
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onActionClick) {
                Text(text = actionText)
            }
        }
    }
}
