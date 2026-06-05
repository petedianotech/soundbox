package com.example.ui.screens.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Playlist
import com.example.data.model.Song
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.SongImagePlaceholder
import com.example.ui.components.TrackRow
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: MusicViewModel,
    onSongSelected: (Song) -> Unit
) {
    val playlists by viewModel.allPlaylists.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }

    // Navigation and expandable states inside lists
    var activeListName by remember { mutableStateOf<String?>(null) }
    var activeListSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var activeCustomPlaylistId by remember { mutableStateOf<Long?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp)
        ) {
            // Quick smart categories
            item {
                Text(
                    text = "Smart Playlists",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmartPlaylistCard(
                        title = "Favorites",
                        count = favorites.size,
                        icon = Icons.Default.Favorite,
                        tint = Color.Red,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeListName = "Favorites"
                            activeListSongs = favorites
                            activeCustomPlaylistId = null
                        }
                    )
                    SmartPlaylistCard(
                        title = "Recent Runs",
                        count = recentlyPlayed.filter { it.playCount > 0 }.size,
                        icon = Icons.Default.History,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeListName = "Recently Played"
                            activeListSongs = recentlyPlayed.filter { it.playCount > 0 }
                            activeCustomPlaylistId = null
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmartPlaylistCard(
                        title = "Most Played",
                        count = mostPlayed.size,
                        icon = Icons.Default.Whatshot,
                        tint = Color(0xFFFFA000),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeListName = "Most Played"
                            activeListSongs = mostPlayed
                            activeCustomPlaylistId = null
                        }
                    )
                    // Custom action to create lists
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showCreateDialog = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .height(90.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Create Playlist",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Custom lists
            item {
                Text(
                    text = "My Personal Playlists",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (playlists.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No Custom Playlists",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            } else {
                items(playlists) { playlist ->
                    // Resolve list songs from database Song ID list
                    val playlistSongs = playlist.songIds.mapNotNull { lid ->
                        allSongs.find { it.id == lid }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                activeListName = playlist.name
                                activeListSongs = playlistSongs
                                activeCustomPlaylistId = playlist.id
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SongImagePlaceholder(title = playlist.name, size = 48f)

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${playlistSongs.size} tracks",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }

                        IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Playlist",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }

        // Custom list creation popup dialogue
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCreateDialog = false
                    playlistNameInput = ""
                },
                title = { Text("Create Custom Playlist") },
                text = {
                    OutlinedTextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (playlistNameInput.isNotBlank()) {
                                viewModel.createPlaylist(playlistNameInput)
                                showCreateDialog = false
                                playlistNameInput = ""
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCreateDialog = false
                        playlistNameInput = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Detailed Playlist bottoms sheet drawer
        if (activeListName != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    activeListName = null
                    activeCustomPlaylistId = null
                },
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
                        SongImagePlaceholder(title = activeListName!!, size = 64f)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeListName!!,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${activeListSongs.size} Tracks",
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                            )
                        }
                        if (activeListSongs.isNotEmpty()) {
                            Button(onClick = {
                                viewModel.playSong(activeListSongs.first(), activeListSongs)
                                onSongSelected(activeListSongs.first())
                                activeListName = null
                            }) {
                                Text("Play All")
                            }
                        }
                    }

                    HorizontalDivider()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(activeListSongs) { song ->
                            TrackRow(
                                song = song,
                                isPlaying = currentSong?.id == song.id,
                                onClick = {
                                    viewModel.playSong(song, activeListSongs)
                                    onSongSelected(song)
                                    activeListName = null
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(song) },
                                onMenuClick = {
                                    // Let custom lists support track extraction
                                    if (activeCustomPlaylistId != null) {
                                        viewModel.removeSongFromPlaylist(activeCustomPlaylistId!!, song.id)
                                        // Update state directly for swift responsive feed
                                        activeListSongs = activeListSongs.toMutableList().apply { remove(song) }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartPlaylistCard(
    title: String,
    count: Int,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(90.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "$count Songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
