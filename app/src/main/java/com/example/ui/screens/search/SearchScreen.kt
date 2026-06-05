package com.example.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.Song
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.MiniPlayer
import com.example.ui.components.TrackRow
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    onNavigateToNowPlaying: () -> Unit
) {
    val songs by viewModel.allSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val isPlaying by viewModel.isPlaying.collectAsState()

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            songs.filter { song ->
                song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true) ||
                song.album.contains(searchQuery, ignoreCase = true) ||
                song.path.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search songs, artists, albums, folders...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        if (searchQuery.isBlank()) {
            EmptyPlaceholder(
                title = "Search Soundbox",
                subtitle = "Type song title, artist, album, or folder directories.",
                icon = Icons.Default.Search
            )
        } else if (filteredSongs.isEmpty()) {
            EmptyPlaceholder(
                title = "No Results Found",
                subtitle = "There are no tracks matching '$searchQuery' in your offline inventory.",
                icon = Icons.Default.MusicNote
            )
        } else {
            Text(
                text = "${filteredSongs.size} matching results",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredSongs, key = { it.id }) { song ->
                    TrackRow(
                        song = song,
                        isPlaying = currentSong?.id == song.id,
                        onClick = {
                            viewModel.playSong(song, filteredSongs)
                        },
                        onFavoriteToggle = { viewModel.toggleFavorite(song) },
                        onMenuClick = {}
                    )
                }
            }
        }
        
        // Persistent Floating MiniPlayer for Search screen
        Box(modifier = Modifier.fillMaxSize()) {
            MiniPlayer(
                currentSong = currentSong,
                isPlaying = isPlaying,
                onPlayPause = { viewModel.playPause() },
                onSkipNext = { viewModel.skipNext() },
                onOpenNowPlaying = onNavigateToNowPlaying,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
