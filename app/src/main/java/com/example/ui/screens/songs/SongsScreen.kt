package com.example.ui.screens.songs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
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
    val playlists by viewModel.allPlaylists.collectAsState()

    var songToManage by remember { mutableStateOf<Song?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }

    var selectedSongIds by remember { mutableStateOf(setOf<String>()) }
    val inSelectionMode = selectedSongIds.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        if (songs.isEmpty()) {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EmptyPlaceholder(
                    title = "No Tracks Discovered",
                    subtitle = "Soundbox scanned your local memory but did not locate audio files.",
                    icon = Icons.Default.MusicNote,
                    actionText = "Seed Test Loops",
                    onActionClick = { viewModel.scanStorage() }
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header action row
                if (inSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedSongIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row {
                            IconButton(onClick = {
                                selectedSongIds.forEach { id ->
                                    val songToAdd = songs.find { s -> s.id == id }
                                    if (songToAdd != null) viewModel.addToQueue(songToAdd)
                                }
                                selectedSongIds = emptySet()
                            }) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = "Add all to Queue")
                            }
                            IconButton(onClick = { selectedSongIds = emptySet() }) {
                                Text("Clear")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${songs.size} local tracks found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = { viewModel.scanStorage() }) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Scan")
                            }
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(songs, key = { it.id }) { song ->
                        val isSelected = selectedSongIds.contains(song.id)
                        TrackRow(
                            song = song,
                            isPlaying = currentSong?.id == song.id,
                            isSelected = isSelected,
                            onLongClick = {
                                if (!inSelectionMode) {
                                    selectedSongIds = selectedSongIds + song.id
                                }
                            },
                            onClick = {
                                if (inSelectionMode) {
                                    if (isSelected) {
                                        selectedSongIds = selectedSongIds - song.id
                                    } else {
                                        selectedSongIds = selectedSongIds + song.id
                                    }
                                } else {
                                    viewModel.playSong(song, songs)
                                    onSongSelected(song)
                                }
                            },
                            onFavoriteToggle = {
                                viewModel.toggleFavorite(song)
                            },
                            onMenuClick = {
                                songToManage = song
                                showActionSheet = true
                            }
                        )
                    }
                }
            }
        }

        // Action Sheet
        if (showActionSheet && songToManage != null) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showActionSheet = false 
                    songToManage = null
                }
            ) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        text = songToManage!!.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text("Play Next") },
                        leadingContent = { Icon(Icons.Default.MusicNote, null) },
                        modifier = Modifier.clickable {
                            viewModel.playNext(songToManage!!)
                            showActionSheet = false
                            songToManage = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Add to Queue") },
                        leadingContent = { Icon(Icons.Default.PlaylistAdd, null) },
                        modifier = Modifier.clickable {
                            viewModel.addToQueue(songToManage!!)
                            showActionSheet = false
                            songToManage = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Add to Playlist") },
                        leadingContent = { Icon(Icons.Default.List, null) },
                        modifier = Modifier.clickable {
                            showActionSheet = false
                            showPlaylistDialog = true
                        }
                    )
                }
            }
        }

        // Playlist associations popup dialog
        if (showPlaylistDialog && songToManage != null) {
            AlertDialog(
                onDismissRequest = {
                    showPlaylistDialog = false
                    songToManage = null
                },
                title = { Text("Add Track to Playlist") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        if (playlists.isEmpty()) {
                            Text(
                                "No custom playlists found. Create one from the Playlists tab.",
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn {
                                items(playlists) { playlist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.addSongToPlaylist(
                                                    playlist.id,
                                                    songToManage!!.id
                                                )
                                                showPlaylistDialog = false
                                                songToManage = null
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.List, contentDescription = null)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showPlaylistDialog = false
                        songToManage = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
