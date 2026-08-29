package com.example.ui.screens.songs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.Image
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import com.example.ui.components.OnlineCoverDialog
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Song
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.TrackRow
import com.example.ui.viewmodel.MusicViewModel
import com.example.util.ThumbnailExporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    viewModel: MusicViewModel,
    onSongSelected: (Song) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val songs by viewModel.allSongs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var songForDeviceCover by remember { mutableStateOf<Song?>(null) }

    val deviceCoverLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val song = songForDeviceCover
            if (song != null) {
                coroutineScope.launch {
                    val success = com.example.util.OnlineCoverFetcher.saveUriAsCover(context, song.id, it)
                    if (success) {
                        viewModel.refreshCurrentSongArtwork()
                        viewModel.scanStorage()
                        android.widget.Toast.makeText(context, "Album art updated", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to load image", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    songForDeviceCover = null
                }
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            deviceCoverLauncher.launch("image/*")
        } else {
            android.widget.Toast.makeText(context, "Permission denied. Cannot select image.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val requestCoverPermissionAndLaunch = { song: Song ->
        songForDeviceCover = song
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            deviceCoverLauncher.launch("image/*")
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }
    val currentSong by viewModel.currentSong.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()

    var songToManage by remember { mutableStateOf<Song?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var onlineCoverSongTarget by remember { mutableStateOf<Song?>(null) }

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
                    title = "No songs found",
                    subtitle = "Tap below to scan your device for music files.",
                    icon = Icons.Default.MusicNote,
                    actionText = "Scan for Music",
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
                    Text(
                        text = songToManage!!.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text("Song Info") },
                        leadingContent = { Icon(Icons.Default.Info, null) },
                        modifier = Modifier.clickable {
                            showActionSheet = false
                            showDetailsDialog = true
                        }
                    )
                    ListItem(
                        headlineContent = { Text(if (songToManage!!.isFavorite) "Unlike Song" else "Like Song") },
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
                        headlineContent = { Text("Download Online Cover Art") },
                        supportingContent = { Text("Search & fetch HD album covers from web") },
                        leadingContent = { 
                            Icon(
                                imageVector = Icons.Default.CloudDownload, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        modifier = Modifier.clickable {
                            val target = songToManage
                            showActionSheet = false
                            songToManage = null
                            if (target != null) {
                                onlineCoverSongTarget = target
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Choose Cover from Device") },
                        supportingContent = { Text("Select an image from local storage") },
                        leadingContent = { 
                            Icon(
                                imageVector = Icons.Default.Image, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        modifier = Modifier.clickable {
                            val target = songToManage
                            showActionSheet = false
                            songToManage = null
                            if (target != null) {
                                requestCoverPermissionAndLaunch(target)
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Export Artwork to Gallery") },
                        supportingContent = { Text("Save album art to device Pictures") },
                        leadingContent = { 
                            Icon(
                                imageVector = Icons.Default.Download, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        modifier = Modifier.clickable {
                            val target = songToManage
                            showActionSheet = false
                            songToManage = null
                            if (target != null) {
                                coroutineScope.launch {
                                    ThumbnailExporter.exportSongThumbnail(context, target)
                                }
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { 
                            Text(
                                text = "Delete Song", 
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            ) 
                        },
                        supportingContent = { 
                            Text(
                                text = "Permanently remove from device storage",
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            ) 
                        },
                        leadingContent = { 
                            Icon(
                                imageVector = Icons.Default.DeleteOutline, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.error
                            ) 
                        },
                        modifier = Modifier.clickable {
                            showActionSheet = false
                            showDeleteConfirmDialog = true
                        }
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmDialog && songToManage != null) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteConfirmDialog = false
                    songToManage = null
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = { Text("Delete Song?") },
                text = {
                    Text("Are you sure you want to delete \"${songToManage!!.title}\"? This will permanently remove the audio file from your device and library.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val songToDelete = songToManage
                            showDeleteConfirmDialog = false
                            songToManage = null
                            if (songToDelete != null) {
                                viewModel.deleteSong(songToDelete)
                                Toast.makeText(context, "Song deleted", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteConfirmDialog = false
                        songToManage = null
                    }) {
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

        // Tag Editor Dialog
        if (showTagEditor && songToManage != null) {
            AlertDialog(
                onDismissRequest = {
                    showTagEditor = false
                    songToManage = null
                },
                title = { Text("Edit Song Info") },
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
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        DetailItem(label = "Title", value = song.title)
                        DetailItem(label = "Artist", value = song.artist)
                        DetailItem(label = "Album", value = song.album)
                        DetailItem(label = "Genre", value = song.genre.ifBlank { "Unknown" })
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

        if (onlineCoverSongTarget != null) {
            OnlineCoverDialog(
                song = onlineCoverSongTarget!!,
                onDismissRequest = { onlineCoverSongTarget = null },
                onCoverUpdated = {
                    viewModel.refreshCurrentSongArtwork()
                    viewModel.scanStorage()
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
