package com.example.ui.screens.artists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun ArtistsScreen(
    viewModel: MusicViewModel,
    onSongSelected: (Song) -> Unit
) {
    val artistMap by viewModel.artistList.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    var selectedArtist by remember { mutableStateOf<String?>(null) }
    val artistSongs = selectedArtist?.let { artistMap[it] } ?: emptyList()

    Box(modifier = Modifier.fillMaxSize()) {
        if (artistMap.isEmpty()) {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EmptyPlaceholder(
                    title = "No Artists Discovered",
                    subtitle = "Soundbox scanned your local files but did not locate meta IDs.",
                    icon = Icons.Default.Person,
                    actionText = "Refresh Scan",
                    onActionClick = { viewModel.scanStorage() }
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(artistMap.keys.toList()) { artistName ->
                    val tracksCount = artistMap[artistName]?.size ?: 0

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedArtist = artistName },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val songsInArtist = artistMap[artistName] ?: emptyList()
                        val firstSongId = songsInArtist.firstOrNull()?.id
                        com.example.ui.components.ArtworkThumbnail(
                            songId = firstSongId,
                            title = artistName,
                            modifier = Modifier.size(90.dp),
                            size = 90f,
                            isCircle = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (tracksCount == 1) "1 track" else "$tracksCount tracks",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Expanded artist detail dialog sheet view
        if (selectedArtist != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedArtist = null },
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
                        val firstSongId = artistSongs.firstOrNull()?.id
                        com.example.ui.components.ArtworkThumbnail(
                            songId = firstSongId,
                            title = selectedArtist!!,
                            modifier = Modifier.clip(CircleShape),
                            size = 64f,
                            isCircle = true
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = selectedArtist!!,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${artistSongs.size} Tracks",
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.secondary)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(artistSongs) { song ->
                            TrackRow(
                                song = song,
                                isPlaying = currentSong?.id == song.id,
                                onClick = {
                                    viewModel.playSong(song, artistSongs)
                                    onSongSelected(song)
                                    selectedArtist = null
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
