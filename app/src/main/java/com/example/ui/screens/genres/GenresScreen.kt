package com.example.ui.screens.genres

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.Song
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.TrackRow
import com.example.ui.viewmodel.MusicViewModel

@Composable
fun GenresScreen(
    viewModel: MusicViewModel,
    onSongSelected: (Song) -> Unit
) {
    val genreMap by viewModel.genreList.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    var expandedGenre by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (genreMap.isEmpty()) {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EmptyPlaceholder(
                    title = "No Genres Found",
                    subtitle = "Your tracks do not contain genre metadata.",
                    icon = Icons.Default.Category
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                genreMap.forEach { (genreTitle, songs) ->
                    item {
                        val isExpanded = expandedGenre == genreTitle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedGenre = if (isExpanded) null else genreTitle
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = genreTitle.ifEmpty { "Unknown Genre" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${songs.size} tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                    }
                    if (expandedGenre == genreTitle) {
                        items(songs) { song ->
                            TrackRow(
                                song = song,
                                isPlaying = currentSong?.id == song.id,
                                onClick = {
                                    viewModel.playSong(song, songs)
                                    onSongSelected(song)
                                },
                                onFavoriteToggle = {
                                    viewModel.toggleFavorite(song)
                                },
                                onMenuClick = {
                                    // Add to Playlist logic placeholder
                                },
                                modifier = Modifier.padding(start = 32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
