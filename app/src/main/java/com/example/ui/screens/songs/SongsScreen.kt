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
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var showSortMenu by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }

    var editTitle by remember { mutableStateOf("") }
    var editArtist by remember { mutableStateOf("") }
    var editAlbum by remember { mutableStateOf("") }
    var editGenre by remember { mutableStateOf("") }

    val genreMap by viewModel.genreList.collectAsState()
    val existingGenres = remember(genreMap) {
        genreMap.keys.filter { it.isNotBlank() && it.lowercase() != "unknown" }
    }

    LaunchedEffect(songToManage, showTagEditor) {
        if (showTagEditor && songToManage != null) {
            editTitle = songToManage!!.title
            editArtist = songToManage!!.artist
            editAlbum = songToManage!!.album
            editGenre = songToManage!!.genre
        }
    }

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("A to Z") },
                                        onClick = { viewModel.setSortOrder(MusicViewModel.SortOrder.A_TO_Z); showSortMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Z to A") },
                                        onClick = { viewModel.setSortOrder(MusicViewModel.SortOrder.Z_TO_A); showSortMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Date Added") },
                                        onClick = { viewModel.setSortOrder(MusicViewModel.SortOrder.DATE_ADDED); showSortMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duration") },
                                        onClick = { viewModel.setSortOrder(MusicViewModel.SortOrder.DURATION); showSortMenu = false }
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.scanStorage() }) {
                                if (isScanning) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Scan")
                                }
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
                        headlineContent = { Text(if (songToManage!!.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                        leadingContent = { 
                            Icon(
                                imageVector = if (songToManage!!.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                                contentDescription = null, 
                                tint = if (songToManage!!.isFavorite) androidx.compose.ui.graphics.Color.Red else LocalContentColor.current
                            ) 
                        },
                        modifier = Modifier.clickable {
                            viewModel.toggleFavorite(songToManage!!)
                            showActionSheet = false
                            songToManage = null
                        }
                    )
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
                    ListItem(
                        headlineContent = { Text("Edit Metadata Tags") },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        modifier = Modifier.clickable {
                            showActionSheet = false
                            showTagEditor = true
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

        // Tag Editor Dialog
        if (showTagEditor && songToManage != null) {
            AlertDialog(
                onDismissRequest = {
                    showTagEditor = false
                    songToManage = null
                },
                title = { Text("Edit Metadata Tags") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editArtist,
                            onValueChange = { editArtist = it },
                            label = { Text("Artist") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editAlbum,
                            onValueChange = { editAlbum = it },
                            label = { Text("Album") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editGenre,
                            onValueChange = { editGenre = it },
                            label = { Text("Genre") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (existingGenres.isNotEmpty()) {
                            Text(
                                text = "Quick Auto-Fill Genres:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                existingGenres.forEach { genre ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (editGenre.lowercase().trim() == genre.lowercase().trim()) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (editGenre.lowercase().trim() == genre.lowercase().trim()) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            }
                                        ),
                                        modifier = Modifier.clickable { editGenre = genre }
                                    ) {
                                        Text(
                                            text = genre,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            color = if (editGenre.lowercase().trim() == genre.lowercase().trim()) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val updatedSong = songToManage!!.copy(
                                title = editTitle,
                                artist = editArtist,
                                album = editAlbum,
                                genre = editGenre
                            )
                            viewModel.updateSongMetadata(updatedSong)
                            showTagEditor = false
                            songToManage = null
                        }
                    ) {
                        Text("Save Changes")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showTagEditor = false
                            songToManage = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
