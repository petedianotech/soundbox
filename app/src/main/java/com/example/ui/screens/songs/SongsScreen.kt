package com.example.ui.screens.songs

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.ui.components.TrackRow
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    viewModel: MusicViewModel,
    onSongSelected: (Song) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = SoundboxTheme.colors

    val allSongs by viewModel.allSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // TOP CONTROL HEADER: Song count, Play All, Shuffle All, Sort button
        Surface(
            color = colors.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ALL TRACKS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${allSongs.size} tracks • ${allSongs.sumOf { it.duration } / 60000} mins",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Sort Button
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort order",
                                    tint = colors.accentCyan
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(colors.surfaceElevated)
                            ) {
                                MusicViewModel.SortOrder.values().forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = order.displayName,
                                                fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                                color = if (sortOrder == order) colors.accentCyan else colors.textPrimary
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Rescan Media Button
                        IconButton(
                            onClick = { viewModel.scanStorage() },
                            enabled = !isScanning
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = colors.accentCyan,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Rescan tracks",
                                    tint = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // PLAY ALL & SHUFFLE BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (allSongs.isNotEmpty()) {
                                viewModel.playSong(allSongs.first(), allSongs)
                            }
                        },
                        enabled = allSongs.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PLAY ALL",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (allSongs.isNotEmpty()) {
                                val shuffled = allSongs.shuffled()
                                viewModel.playSong(shuffled.first(), shuffled)
                            }
                        },
                        enabled = allSongs.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = colors.accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SHUFFLE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }
        }

        Divider(color = colors.border, thickness = 0.5.dp)

        // SONGS LIST OR EMPTY STATE
        if (allSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = colors.accentCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No tracks found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap below to scan your device storage for audio files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.scanStorage() },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Storage", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp) // Room for MiniPlayer
            ) {
                items(
                    items = allSongs,
                    key = { it.id }
                ) { song ->
                    val isCurrent = currentSong?.id == song.id
                    TrackRow(
                        song = song,
                        isPlaying = isCurrent && isPlaying,
                        onClick = {
                            viewModel.playSong(song, allSongs)
                            onSongSelected(song)
                        },
                        onFavoriteToggle = { viewModel.toggleFavorite(song) },
                        onMenuClick = { selectedSongForMenu = song }
                    )
                }
            }
        }
    }

    // SONG OPTIONS BOTTOM SHEET / DIALOG
    if (selectedSongForMenu != null) {
        com.example.ui.components.SongOptionsBottomSheet(
            song = selectedSongForMenu!!,
            viewModel = viewModel,
            onDismiss = { selectedSongForMenu = null }
        )
    }
}
