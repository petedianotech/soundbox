package com.example.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Playlist
import com.example.data.model.SmartPlaylist
import com.example.data.model.Song
import com.example.ui.components.ArtworkThumbnail
import com.example.ui.components.EmptyPlaceholder
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
    val currentSong by viewModel.currentSong.collectAsState()
    val smartPlaylists by viewModel.smartPlaylists.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: All, 1: Smart Playlists, 2: My Playlists

    // Active detail playlist sheet
    var activeListName by remember { mutableStateOf<String?>(null) }
    var activeListDescription by remember { mutableStateOf<String?>(null) }
    var activeListSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var activeCustomPlaylistId by remember { mutableStateOf<Long?>(null) }
    var activeSmartPlaylist by remember { mutableStateOf<SmartPlaylist?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Create New Playlist") },
                text = { Text("New Playlist", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp)
        ) {
            // Category filter tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Smart Playlists (${smartPlaylists.size})") }
                    )
                    FilterChip(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text("My Playlists (${playlists.size})") }
                    )
                }
            }

            // SMART PLAYLISTS SECTION
            if (selectedTab == 0 || selectedTab == 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Smart Playlists",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Auto-created from your music and habits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (smartPlaylists.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    viewModel.autoGenerateSmartPlaylists()
                                    snackbarMessage = "Saved all smart playlists to My Playlists"
                                }
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save All")
                            }
                        }
                    }
                }

                if (smartPlaylists.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = "Add songs to your library to generate smart playlists automatically.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Smart Playlist Grid/Cards
                    val chunked = smartPlaylists.chunked(2)
                    items(chunked) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (item in pair) {
                                SmartPlaylistGridCard(
                                    smartPlaylist = item,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        activeListName = item.title
                                        activeListDescription = item.description
                                        activeListSongs = item.songs
                                        activeCustomPlaylistId = null
                                        activeSmartPlaylist = item
                                    },
                                    onPlayClick = {
                                        if (item.songs.isNotEmpty()) {
                                            viewModel.playSong(item.songs.first(), item.songs)
                                            onSongSelected(item.songs.first())
                                        }
                                    }
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // MY CUSTOM PLAYLISTS SECTION
            if (selectedTab == 0 || selectedTab == 2) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "My Playlists",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Custom collections created by you",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = "Create Playlist",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (playlists.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { showCreateDialog = true },
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
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No Custom Playlists Yet",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Tap here or the + button to make one",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(playlists) { playlist ->
                        val playlistSongs = playlist.songIds.mapNotNull { lid ->
                            allSongs.find { it.id == lid }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    activeListName = playlist.name
                                    activeListDescription = "${playlistSongs.size} custom tracks"
                                    activeListSongs = playlistSongs
                                    activeCustomPlaylistId = playlist.id
                                    activeSmartPlaylist = null
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val firstSongId = playlistSongs.firstOrNull()?.id
                                ArtworkThumbnail(songId = firstSongId, title = playlist.name, size = 52f)

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlistSongs.size} songs",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (playlistSongs.isNotEmpty()) {
                                    IconButton(onClick = {
                                        viewModel.playSong(playlistSongs.first(), playlistSongs)
                                        onSongSelected(playlistSongs.first())
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = "Play Playlist",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Playlist",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom list creation popup dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCreateDialog = false
                    playlistNameInput = ""
                },
                title = { Text("Create New Playlist") },
                text = {
                    OutlinedTextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it },
                        label = { Text("Playlist Name") },
                        placeholder = { Text("e.g. My Workout Songs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (playlistNameInput.isNotBlank()) {
                                viewModel.createPlaylist(playlistNameInput.trim())
                                showCreateDialog = false
                                playlistNameInput = ""
                                snackbarMessage = "Playlist created"
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

        // Detailed Playlist bottom sheet drawer
        if (activeListName != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    activeListName = null
                    activeListDescription = null
                    activeCustomPlaylistId = null
                    activeSmartPlaylist = null
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
                        val firstSongId = activeListSongs.firstOrNull()?.id
                        ArtworkThumbnail(songId = firstSongId, title = activeListName!!, size = 64f)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeListName!!,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeListDescription != null) {
                                Text(
                                    text = activeListDescription!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${activeListSongs.size} Songs",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        // Save Smart Playlist as custom button if in smart playlist mode
                        if (activeSmartPlaylist != null) {
                            IconButton(onClick = {
                                viewModel.saveSmartPlaylistAsCustom(activeSmartPlaylist!!)
                                snackbarMessage = "Saved '${activeSmartPlaylist!!.title}' to My Playlists"
                            }) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = "Save to My Playlists",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (activeListSongs.isNotEmpty()) {
                            Button(onClick = {
                                viewModel.playSong(activeListSongs.first(), activeListSongs)
                                onSongSelected(activeListSongs.first())
                                activeListName = null
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play All")
                            }
                        }
                    }

                    HorizontalDivider()

                    if (activeListSongs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No songs found in this playlist.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            items(activeListSongs) { song ->
                                val extraInfo = when (activeListName) {
                                    "Most Played" -> if (song.playCount == 1) "1 play" else "${song.playCount} plays"
                                    "Recently Played" -> formatRelativeTime(song.lastPlayedTime)
                                    else -> song.genre.takeIf { it.isNotBlank() && it != "Unknown" }
                                }
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
                                        if (activeCustomPlaylistId != null) {
                                            viewModel.removeSongFromPlaylist(activeCustomPlaylistId!!, song.id)
                                            activeListSongs = activeListSongs.toMutableList().apply { remove(song) }
                                        }
                                    },
                                    extraInfo = extraInfo
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartPlaylistGridCard(
    smartPlaylist: SmartPlaylist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
                .height(130.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(smartPlaylist.tintColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = smartPlaylist.icon,
                        contentDescription = null,
                        tint = smartPlaylist.tintColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (smartPlaylist.songs.isNotEmpty()) {
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = "Quick Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = smartPlaylist.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${smartPlaylist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatRelativeTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 0) return "Just now"
    val diffSecs = diff / 1000
    if (diffSecs < 60) return "Just now"
    val diffMins = diffSecs / 60
    if (diffMins < 60) return "${diffMins}m ago"
    val diffHours = diffMins / 60
    if (diffHours < 24) return "${diffHours}h ago"
    val diffDays = diffHours / 24
    if (diffDays == 1L) return "Yesterday"
    if (diffDays < 7) return "$diffDays days ago"
    val sdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
