package com.example.ui.screens.songs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.Song
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.TrackRow
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    viewModel: MusicViewModel,
    onSongSelected: (Song) -> Unit
) {
    val songs by viewModel.allSongs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    if (songs.isEmpty()) {
        if (isScanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            EmptyPlaceholder(
                title = "No Songs Found",
                subtitle = "Scan your device storage to find music files.",
                icon = Icons.Default.MusicNote,
                actionText = "Scan Now",
                onActionClick = { viewModel.scanStorage() }
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                TrackRow(
                    song = song,
                    isPlaying = currentSong?.id == song.id,
                    onClick = {
                        viewModel.playSong(song, songs)
                        onSongSelected(song)
                    },
                    onFavoriteToggle = { viewModel.toggleFavorite(song) },
                    onMenuClick = { }
                )
            }
        }
    }
}
