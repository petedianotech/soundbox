package com.example.ui.screens.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Song
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.SongImagePlaceholder
import com.example.ui.components.TrackRow
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: MusicViewModel,
    onSongSelected: (Song) -> Unit
) {
    val albumMap by viewModel.albumList.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    val albumSongs = selectedAlbum?.let { albumMap[it] } ?: emptyList()

    Box(modifier = Modifier.fillMaxSize()) {
        if (albumMap.isEmpty()) {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EmptyPlaceholder(
                    title = "No Albums Found",
                    subtitle = "Indices will appear once storage tracks are scanned.",
                    icon = Icons.Default.Album,
                    actionText = "Scan Now",
                    onActionClick = { viewModel.scanStorage() }
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(albumMap.keys.toList()) { albumName ->
                    val songsInAlbum = albumMap[albumName] ?: emptyList()
                    val artistName = songsInAlbum.firstOrNull()?.artist ?: "Unknown Artist"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAlbum = albumName },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val firstSongId = songsInAlbum.firstOrNull()?.id
                            com.example.ui.components.ArtworkThumbnail(
                                songId = firstSongId,
                                title = albumName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                size = 120f
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = albumName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.Start)
                            )
                        }
                    }
                }
            }
        }

        // Expanded album track detail drawer sheet
        if (selectedAlbum != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedAlbum = null },
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
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val firstSongId = albumSongs.firstOrNull()?.id
                        com.example.ui.components.ArtworkThumbnail(
                            songId = firstSongId,
                            title = selectedAlbum!!,
                            size = 64f
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = selectedAlbum!!,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${albumSongs.size} Tracks",
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(albumSongs) { song ->
                            TrackRow(
                                song = song,
                                isPlaying = currentSong?.id == song.id,
                                onClick = {
                                    viewModel.playSong(song, albumSongs)
                                    onSongSelected(song)
                                    selectedAlbum = null
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(song) },
                                onMenuClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
