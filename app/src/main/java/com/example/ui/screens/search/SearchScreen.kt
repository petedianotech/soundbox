package com.example.ui.screens.search

import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.model.Song
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.MiniPlayer
import com.example.ui.components.SongDeleteDialog
import com.example.ui.components.SongOptionsBottomSheet
import com.example.ui.components.TrackRow
import com.example.ui.viewmodel.MusicViewModel
import com.example.util.ThumbnailExporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    onNavigateToNowPlaying: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val songs by viewModel.allSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }

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

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val progress = remember(currentPosition, duration) {
        if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

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

    // Auto-request focus once on screen entry to show the keyboard immediately
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Sticky Header Row containing Search input and optional back action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                "Search songs, artists, albums...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search Icon",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    viewModel.addSearchQuery(searchQuery)
                                    focusManager.clearFocus()
                                }
                            }
                        ),
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                // Body Section
                if (searchQuery.isBlank()) {
                    if (searchHistory.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                TextButton(
                                    onClick = { viewModel.clearSearchHistory() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Clear All",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            searchHistory.forEach { historyQuery ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = historyQuery
                                            viewModel.addSearchQuery(historyQuery)
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "History",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = historyQuery,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeSearchQuery(historyQuery) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete from history",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        EmptyPlaceholder(
                            title = "Search Soundbox",
                            subtitle = "Type song title, artist, album, or folder directories.",
                            icon = Icons.Default.Search
                        )
                    }
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
                        contentPadding = PaddingValues(bottom = 110.dp)
                    ) {
                        items(filteredSongs, key = { it.id }) { song ->
                            TrackRow(
                                song = song,
                                isPlaying = currentSong?.id == song.id,
                                onClick = {
                                    viewModel.addSearchQuery(searchQuery)
                                    viewModel.playSong(song, filteredSongs)
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(song) },
                                onMenuClick = { selectedSongForMenu = song }
                            )
                        }
                    }
                }
            }

            // Floating MiniPlayer layered cleanly over background with navigation spacing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.BottomCenter
            ) {
                MiniPlayer(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onPlayPause = { viewModel.playPause() },
                    onSkipNext = { viewModel.skipNext() },
                    onOpenNowPlaying = onNavigateToNowPlaying,
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Song Options Bottom Sheet
        if (selectedSongForMenu != null) {
            val song = selectedSongForMenu!!
            SongOptionsBottomSheet(
                song = song,
                onDismiss = { selectedSongForMenu = null },
                onPlayNext = { viewModel.playNext(song) },
                onAddToQueue = { viewModel.addToQueue(song) },
                onToggleFavorite = { viewModel.toggleFavorite(song) },
                onAddToPlaylist = { /* Playlists */ },
                onDownloadThumbnail = {
                    coroutineScope.launch {
                        ThumbnailExporter.exportSongThumbnail(context, song)
                    }
                },
                onDeleteSong = {
                    songToDelete = song
                },
                onChooseFromDevice = { requestCoverPermissionAndLaunch(song) }
            )
        }

        // Delete confirmation dialog
        if (songToDelete != null) {
            val song = songToDelete!!
            SongDeleteDialog(
                song = song,
                onConfirm = {
                    viewModel.deleteSong(song)
                    songToDelete = null
                    Toast.makeText(context, "Song deleted", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { songToDelete = null }
            )
        }
    }
}
