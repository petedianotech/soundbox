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
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.Song
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.TrackRow
import com.example.ui.components.StarRatingBar
import com.example.ui.viewmodel.MusicViewModel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.util.ShareHelper
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    viewModel: MusicViewModel,
    onSongSelected: (Song) -> Unit
) {
    val context = LocalContext.current
    val songs by viewModel.allSongs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanNotification by viewModel.scanNotification.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()

    var songToManage by remember { mutableStateOf<Song?>(null) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    var showBatchTagEditor by remember { mutableStateOf(false) }

    var editTitle by remember { mutableStateOf("") }
    var editArtist by remember { mutableStateOf("") }
    var editAlbum by remember { mutableStateOf("") }
    var editGenre by remember { mutableStateOf("") }
    var editRating by remember { mutableIntStateOf(0) }

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
            editRating = songToManage!!.rating
        }
    }

    var selectedSongIds by remember { mutableStateOf(setOf<String>()) }
    val inSelectionMode = selectedSongIds.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        if (songs.isEmpty()) {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                EmptyPlaceholder(
                    title = "No songs found",
                    subtitle = "Tap below to scan your device for music files.",
                    icon = Icons.Default.MusicNote,
                    actionText = "Scan for Music",
                    onActionClick = { viewModel.scanStorage() }
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Subtle silent background scan progress bar
                if (isScanning) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // Non-distracting floating scan status pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = scanNotification != null,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = scanNotification ?: "",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                // Header action row
                if (inSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedSongIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showBatchTagEditor = true }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Batch Edit Tags",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(onClick = {
                                selectedSongIds.forEach { id ->
                                    val songToAdd = songs.find { s -> s.id == id }
                                    if (songToAdd != null) viewModel.addToQueue(songToAdd)
                                }
                                selectedSongIds = emptySet()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = "Add all to Queue",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(onClick = {
                                showBatchDeleteConfirm = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete Selected from Device",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            TextButton(onClick = { selectedSongIds = emptySet() }) {
                                Text("Done", color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                            text = "${songs.size} songs",
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
                                        text = { Text("Highest Rated (5★)") },
                                        onClick = { viewModel.setSortOrder(MusicViewModel.SortOrder.RATING); showSortMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Most Played") },
                                        onClick = { viewModel.setSortOrder(MusicViewModel.SortOrder.MOST_PLAYED); showSortMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Date Added") },
                                        onClick = { viewModel.setSortOrder(MusicViewModel.SortOrder.DATE_ADDED); showSortMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("A to Z") },
                                        onClick = { viewModel.setSortOrder(MusicViewModel.SortOrder.A_TO_Z); showSortMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Z to A") },
                                        onClick = { viewModel.setSortOrder(MusicViewModel.SortOrder.Z_TO_A); showSortMenu = false }
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
                                    Icon(Icons.Default.Refresh, contentDescription = "Scan Storage")
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
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = songToManage!!.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${songToManage!!.artist} • ${songToManage!!.album}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Track Rating:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            StarRatingBar(
                                rating = songToManage!!.rating,
                                onRatingChanged = { newRating ->
                                    viewModel.updateSongRating(songToManage!!, newRating)
                                    songToManage = songToManage!!.copy(rating = newRating)
                                },
                                starSize = 24
                            )
                        }
                    }
                    Divider()
                    ListItem(
                        headlineContent = { Text("Song Info & Specs") },
                        leadingContent = { Icon(Icons.Default.Info, null) },
                        modifier = Modifier.clickable {
                            showActionSheet = false
                            showDetailsDialog = true
                        }
                    )
                    ListItem(
                        headlineContent = { Text(if (songToManage!!.isFavorite) "Remove from Liked Songs" else "Add to Liked Songs") },
                        leadingContent = { 
                            Icon(
                                imageVector = if (songToManage!!.isFavorite) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp, 
                                contentDescription = null, 
                                tint = if (songToManage!!.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
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
                        headlineContent = { Text("Edit Song Info") },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        modifier = Modifier.clickable {
                            showActionSheet = false
                            showTagEditor = true
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Share Track & .LRC Lyrics") },
                        supportingContent = { Text("Bundles audio file with synced lyrics", fontSize = 11.sp) },
                        leadingContent = { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            val song = songToManage
                            showActionSheet = false
                            songToManage = null
                            if (song != null) {
                                ShareHelper.shareSongWithLyrics(context, song)
                            }
                        }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { 
                            Text(
                                "Delete from Device", 
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            ) 
                        },
                        supportingContent = { 
                            Text(
                                "Permanently erase file from phone storage", 
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            ) 
                        },
                        leadingContent = { 
                            Icon(
                                Icons.Default.DeleteForever, 
                                null, 
                                tint = MaterialTheme.colorScheme.error
                            ) 
                        },
                        modifier = Modifier.clickable {
                            val target = songToManage
                            showActionSheet = false
                            songToManage = null
                            songToDelete = target
                        }
                    )
                }
            }
        }

        // Single Song Permanent Delete Confirmation Dialog
        if (songToDelete != null) {
            val song = songToDelete!!
            AlertDialog(
                onDismissRequest = { songToDelete = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        "Delete Song Permanently?",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Are you sure you want to delete \"${song.title}\" by ${song.artist}?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ This will permanently remove the audio file and its matching .lrc lyrics file from your device storage. This action cannot be undone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = songToDelete!!
                            songToDelete = null
                            viewModel.deleteSongFromDevice(target) {
                                Toast.makeText(context, "Song permanently deleted from storage", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Permanently", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { songToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Batch Delete Confirmation Dialog
        if (showBatchDeleteConfirm && inSelectionMode) {
            val selectedSongs = songs.filter { selectedSongIds.contains(it.id) }
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        "Delete ${selectedSongs.size} Songs Permanently?",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "You are about to permanently delete ${selectedSongs.size} selected songs from device storage.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ All audio files and companion .lrc lyrics files will be permanently erased from your storage. This action cannot be undone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBatchDeleteConfirm = false
                            selectedSongIds = emptySet()
                            viewModel.deleteSongsBatchFromDevice(selectedSongs) {
                                Toast.makeText(context, "${selectedSongs.size} songs permanently deleted from storage", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete All Permanently", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Playlist associations popup dialog
        if (showPlaylistDialog && songToManage != null) {
            AlertDialog(
                onDismissRequest = {
                    showPlaylistDialog = false
                    songToManage = null
                },
                title = { Text("Add Song to Playlist") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        if (playlists.isEmpty()) {
                            Text(
                                "No playlists found. Create one from the Playlists tab.",
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

        // Single Tag Editor Dialog
        if (showTagEditor && songToManage != null) {
            AlertDialog(
                onDismissRequest = {
                    showTagEditor = false
                    songToManage = null
                },
                title = { Text("Edit Song Info & Rating") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💾 Physical Tagging: Changes are written directly to audio ID3v2 tags on storage and synced with .lrc lyrics, so modified details stay intact when sent to others.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

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

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Score (1-5 Stars):",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            StarRatingBar(
                                rating = editRating,
                                onRatingChanged = { editRating = it },
                                starSize = 24
                            )
                        }

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
                                genre = editGenre,
                                rating = editRating
                            )
                            viewModel.updateSongMetadata(updatedSong)
                            Toast.makeText(context, "ID3 tags and details saved permanently to file", Toast.LENGTH_SHORT).show()
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

        // Batch Tag Editor Dialog
        if (showBatchTagEditor && inSelectionMode) {
            val selectedSongs = songs.filter { selectedSongIds.contains(it.id) }
            var batchArtist by remember { mutableStateOf("") }
            var applyArtist by remember { mutableStateOf(false) }
            var batchAlbum by remember { mutableStateOf("") }
            var applyAlbum by remember { mutableStateOf(false) }
            var batchGenre by remember { mutableStateOf("") }
            var applyGenre by remember { mutableStateOf(false) }
            var batchRating by remember { mutableIntStateOf(0) }
            var applyRating by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showBatchTagEditor = false },
                title = { 
                    Text(
                        "Batch Tag Editor (${selectedSongs.size} Tracks)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Select the tags you want to update simultaneously across all ${selectedSongs.size} selected tracks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Artist Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = applyArtist, onCheckedChange = { applyArtist = it })
                            OutlinedTextField(
                                value = batchArtist,
                                onValueChange = { 
                                    batchArtist = it
                                    if (it.isNotEmpty()) applyArtist = true
                                },
                                label = { Text("Artist Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                enabled = applyArtist
                            )
                        }

                        // Album Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = applyAlbum, onCheckedChange = { applyAlbum = it })
                            OutlinedTextField(
                                value = batchAlbum,
                                onValueChange = { 
                                    batchAlbum = it
                                    if (it.isNotEmpty()) applyAlbum = true
                                },
                                label = { Text("Album Title") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                enabled = applyAlbum
                            )
                        }

                        // Genre Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = applyGenre, onCheckedChange = { applyGenre = it })
                            OutlinedTextField(
                                value = batchGenre,
                                onValueChange = { 
                                    batchGenre = it
                                    if (it.isNotEmpty()) applyGenre = true
                                },
                                label = { Text("Genre Tag") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                enabled = applyGenre
                            )
                        }

                        // 5-Star Rating Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = applyRating, onCheckedChange = { applyRating = it })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Assign 5-Star Score",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (applyRating) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                StarRatingBar(
                                    rating = batchRating,
                                    onRatingChanged = { 
                                        batchRating = it
                                        applyRating = true
                                    },
                                    starSize = 22,
                                    readOnly = !applyRating
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.batchUpdateMetadata(
                                songsToUpdate = selectedSongs,
                                artist = if (applyArtist && batchArtist.isNotBlank()) batchArtist else null,
                                album = if (applyAlbum && batchAlbum.isNotBlank()) batchAlbum else null,
                                genre = if (applyGenre && batchGenre.isNotBlank()) batchGenre else null,
                                rating = if (applyRating) batchRating else null
                            )
                            Toast.makeText(context, "Tags updated permanently across ${selectedSongs.size} tracks on storage", Toast.LENGTH_SHORT).show()
                            showBatchTagEditor = false
                            selectedSongIds = emptySet()
                        }
                    ) {
                        Text("Apply to All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchTagEditor = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Song Details Dialog
        if (showDetailsDialog && songToManage != null) {
            val song = songToManage!!
            val formattedDuration = remember(song.duration) {
                val totalSec = song.duration / 1000
                val min = totalSec / 60
                val sec = totalSec % 60
                String.format("%02d:%02d", min, sec)
            }
            val formattedSize = remember(song.size) {
                if (song.size <= 0) {
                    "0 B"
                } else {
                    val units = arrayOf("B", "KB", "MB", "GB")
                    val digitGroups = (Math.log10(song.size.toDouble()) / Math.log10(1024.0)).toInt()
                    String.format("%.2f %s", song.size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
                }
            }

            AlertDialog(
                onDismissRequest = {
                    showDetailsDialog = false
                    songToManage = null
                },
                title = { Text("Track Specifications", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        DetailItem(label = "Title", value = song.title)
                        DetailItem(label = "Artist", value = song.artist)
                        DetailItem(label = "Album", value = song.album)
                        DetailItem(label = "Genre", value = song.genre.ifBlank { "Unknown" })
                        DetailItem(label = "Rating Score", value = if (song.rating > 0) "${song.rating} / 5 Stars" else "Unrated")
                        DetailItem(label = "Audio Quality / Bitrate", value = "${song.bitrateKbps} kbps (Hi-Res Audio)")
                        DetailItem(label = "Play Count", value = "${song.playCount} times")
                        DetailItem(label = "Duration", value = formattedDuration)
                        DetailItem(label = "File Size", value = formattedSize)
                        DetailItem(label = "Location Path", value = song.path, isCode = true)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDetailsDialog = false
                            songToManage = null
                        }
                    ) {
                        Text("Dismiss")
                    }
                }
            )
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String, isCode: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = if (isCode) {
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
