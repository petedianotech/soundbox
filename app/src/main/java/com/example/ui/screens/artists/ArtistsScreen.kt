package com.example.ui.screens.artists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Song
import com.example.ui.components.EmptyPlaceholder
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
                    title = "No Artists Found",
                    subtitle = "Artists will appear after your library is scanned.",
                    icon = Icons.Default.Person,
                    actionText = "Refresh Scan",
                    onActionClick = { viewModel.scanStorage() }
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

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
                    Text(
                        text = selectedArtist ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
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
                                onMenuClick = { }
                            )
                        }
                    }
                }
            }
        }
    }
}
