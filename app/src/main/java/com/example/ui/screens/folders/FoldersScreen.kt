package com.example.ui.screens.folders

import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Song
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.SongDeleteDialog
import com.example.ui.components.SongOptionsBottomSheet
import com.example.ui.components.TrackRow
import com.example.ui.viewmodel.MusicViewModel
import com.example.util.ThumbnailExporter
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    viewModel: MusicViewModel,
    onSongSelected: (Song) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val folderMap by viewModel.folderList.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    var selectedFolderPath by remember { mutableStateOf<String?>(null) }
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
    val folderSongs = selectedFolderPath?.let { folderMap[it] } ?: emptyList()

    Box(modifier = Modifier.fillMaxSize()) {
        if (folderMap.isEmpty()) {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EmptyPlaceholder(
                    title = "No Folder Paths Indexed",
                    subtitle = "Folders are populated when local storage is indexed.",
                    icon = Icons.Default.Folder,
                    actionText = "Re-Scan Media",
                    onActionClick = { viewModel.scanStorage() }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(folderMap.keys.toList().sorted()) { path ->
                    val songsInFolder = folderMap[path] ?: emptyList()
                    val folderName = try {
                        val file = File(path)
                        file.name.ifEmpty { "External Storage" }
                    } catch (e: Exception) {
                        path.substringAfterLast("/")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFolderPath = path }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val firstSongId = songsInFolder.firstOrNull()?.id
                        com.example.ui.components.ArtworkThumbnail(
                            songId = firstSongId,
                            title = folderName,
                            size = 48f
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folderName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "${songsInFolder.size} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        // Selected folder contents drawer sheet
        if (selectedFolderPath != null) {
            val folderNameLabel = try {
                File(selectedFolderPath!!).name.ifEmpty { "External Storage" }
            } catch (e: Exception) {
                selectedFolderPath!!.substringAfterLast("/")
            }

            ModalBottomSheet(
                onDismissRequest = { selectedFolderPath = null },
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
                        val firstSongId = folderSongs.firstOrNull()?.id
                        com.example.ui.components.ArtworkThumbnail(
                            songId = firstSongId,
                            title = folderNameLabel,
                            size = 54f
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folderNameLabel,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = selectedFolderPath!!,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                if (folderSongs.isNotEmpty()) {
                                    viewModel.playSong(folderSongs.first(), folderSongs)
                                    onSongSelected(folderSongs.first())
                                    selectedFolderPath = null
                                }
                            }
                        ) {
                            Text("Play All")
                        }
                    }

                    HorizontalDivider()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(folderSongs) { song ->
                            TrackRow(
                                song = song,
                                isPlaying = currentSong?.id == song.id,
                                onClick = {
                                    viewModel.playSong(song, folderSongs)
                                    onSongSelected(song)
                                    selectedFolderPath = null
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(song) },
                                onMenuClick = { selectedSongForMenu = song }
                            )
                        }
                    }
                }
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
