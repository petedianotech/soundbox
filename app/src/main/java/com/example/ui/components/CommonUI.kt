package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Song
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.outlined.ThumbUp

data class AppThumbnail(val id: Int, val name: String, val resId: Int)

val APP_THUMBNAILS = listOf(
    AppThumbnail(0, "Vinyl Record", R.drawable.music_thumb_vinyl_1787984574642),
    AppThumbnail(1, "Synth Waves", R.drawable.music_thumb_waves_1787984590655),
    AppThumbnail(2, "Acoustic Vibes", R.drawable.music_thumb_acoustic_1787984612814),
    AppThumbnail(3, "Grand Piano", R.drawable.music_thumb_piano_1787984626537),
    AppThumbnail(4, "Studio Headphones", R.drawable.music_thumb_headphones_1787984641678)
)

fun getDefaultThumbnailResId(key: String, explicitIndex: Int = -1): Int {
    if (explicitIndex in APP_THUMBNAILS.indices) {
        return APP_THUMBNAILS[explicitIndex].resId
    }
    val index = kotlin.math.abs(key.hashCode()) % APP_THUMBNAILS.size
    return APP_THUMBNAILS[index].resId
}

@Composable
fun SongImagePlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    size: Float = 48f,
    thumbnailIndex: Int = -1
) {
    val shape = RoundedCornerShape((size * 0.22f).dp)
    val thumbRes = remember(title, thumbnailIndex) { getDefaultThumbnailResId(title, thumbnailIndex) }

    Surface(
        modifier = modifier
            .size(size.dp)
            .clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Image(
            painter = painterResource(id = thumbRes),
            contentDescription = "Music artwork for $title",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
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
    thumbnailIndex: Int = -1
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val customCoverFile = remember(songId) {
        if (!songId.isNullOrEmpty()) com.example.util.OnlineCoverFetcher.getSavedCoverFile(context, songId) else null
    }
    val hasCustomCover = remember(customCoverFile) {
        customCoverFile?.exists() == true
    }

    val artworkModel = remember(songId, hasCustomCover) {
        if (hasCustomCover && customCoverFile != null) {
            customCoverFile
        } else if (!songId.isNullOrEmpty()) {
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
        if (artworkModel != null) {
            coil.compose.SubcomposeAsyncImage(
                model = artworkModel,
                contentDescription = "Song artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                loading = {
                    SongImagePlaceholder(title = title, modifier = Modifier.fillMaxSize(), size = size, thumbnailIndex = thumbnailIndex)
                },
                error = {
                    SongImagePlaceholder(title = title, modifier = Modifier.fillMaxSize(), size = size, thumbnailIndex = thumbnailIndex)
                }
            )
        } else {
            SongImagePlaceholder(title = title, modifier = Modifier.fillMaxSize(), size = size, thumbnailIndex = thumbnailIndex)
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
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else if (isPlaying) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
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
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitleText = if (!extraInfo.isNullOrEmpty()) {
                    "${song.artist} • $extraInfo"
                } else {
                    "${song.artist} • ${song.album}"
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = if (song.isFavorite) "Liked" else "Like song",
                    tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Song options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (progress in 0f..1f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkThumbnail(
                            songId = song.id,
                            title = song.title,
                            size = 44f,
                            isCircle = false
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledIconButton(
                            onClick = onPlayPause,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
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
                                tint = MaterialTheme.colorScheme.onSurface
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsBottomSheet(
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownloadThumbnail: () -> Unit,
    onDeleteSong: () -> Unit,
    onEditSong: (() -> Unit)? = null,
    onSongDetails: (() -> Unit)? = null,
    onChangeThumbnail: (() -> Unit)? = null,
    onDownloadOnlineCover: (() -> Unit)? = null,
    onChooseFromDevice: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
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
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} • ${song.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            if (onSongDetails != null) {
                ListItem(
                    headlineContent = { Text("Song Info") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onSongDetails()
                    }
                )
            }

            ListItem(
                headlineContent = { Text(if (song.isFavorite) "Unlike Song" else "Like Song") },
                leadingContent = {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = null,
                        tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    onToggleFavorite()
                }
            )

            if (onDownloadOnlineCover != null) {
                ListItem(
                    headlineContent = { Text("Download Online Cover Art") },
                    supportingContent = { Text("Search & fetch HD album covers from web") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onDownloadOnlineCover()
                    }
                )
            }

            if (onChooseFromDevice != null) {
                ListItem(
                    headlineContent = { Text("Choose Cover from Device") },
                    supportingContent = { Text("Select an image from local storage") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onChooseFromDevice()
                    }
                )
            }

            if (onChangeThumbnail != null) {
                ListItem(
                    headlineContent = { Text("Change Preset Artwork") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onChangeThumbnail()
                    }
                )
            }

            ListItem(
                headlineContent = { Text("Play Next") },
                leadingContent = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onPlayNext()
                }
            )

            ListItem(
                headlineContent = { Text("Add to Queue") },
                leadingContent = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onAddToQueue()
                }
            )

            ListItem(
                headlineContent = { Text("Add to Playlist") },
                leadingContent = { Icon(Icons.Default.List, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onAddToPlaylist()
                }
            )

            if (onEditSong != null) {
                ListItem(
                    headlineContent = { Text("Edit Song Info") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onEditSong()
                    }
                )
            }

            ListItem(
                headlineContent = { Text("Download Thumbnail") },
                supportingContent = { Text("Save album artwork to device Pictures") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    onDownloadThumbnail()
                }
            )

            ListItem(
                headlineContent = {
                    Text(
                        text = "Delete Song",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                supportingContent = {
                    Text(
                        text = "Permanently remove from device storage",
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    onDeleteSong()
                }
            )
        }
    }
}

@Composable
fun SongDeleteDialog(
    song: Song,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("Delete Song?") },
        text = {
            Text("Are you sure you want to delete \"${song.title}\"? This will permanently remove the audio file from your device and library.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThumbnailPickerSheet(
    currentSelection: Int,
    onThumbnailSelected: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    title: String = "Choose Artwork Thumbnail"
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select a built-in artwork thumbnail created for the app",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Option: Auto-Dynamic (Default)
            Surface(
                onClick = {
                    onThumbnailSelected(-1)
                    onDismissRequest()
                },
                shape = RoundedCornerShape(16.dp),
                color = if (currentSelection == -1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (currentSelection == -1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Auto-Dynamic (Default)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (currentSelection == -1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Hashes title to pick dynamic artwork",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (currentSelection == -1) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Grid of 5 App Thumbnails
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                items(APP_THUMBNAILS) { item ->
                    val isSelected = currentSelection == item.id
                    Card(
                        onClick = {
                            onThumbnailSelected(item.id)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = item.resId),
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SleekRoundSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    var widthPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .onGloballyPositioned { widthPx = it.size.width.toFloat() }
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    if (widthPx > 0) {
                        val fraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (widthPx > 0) {
                        val fraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val w = size.width
            val centerY = h / 2f
            val trackHeight = 4.dp.toPx()

            val norm = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val thumbX = w * norm

            // Background Track
            drawRoundRect(
                color = inactiveTrackColor,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(w, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )

            // Active Track
            if (thumbX > 0) {
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(thumbX, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )
            }

            // Beautiful compact round thumb
            drawCircle(
                color = thumbColor,
                radius = 7.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
        }
    }
}



