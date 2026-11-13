package com.example.ui.screens.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Song
import com.example.ui.components.ArtworkThumbnail
import com.example.ui.components.EmptyPlaceholder
import com.example.ui.components.SongOptionsBottomSheet
import com.example.ui.components.TrackRow
import com.example.ui.theme.SoundboxTheme
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: MusicViewModel,
    onSongSelected: (Song) -> Unit
) {
    val albumMap by viewModel.albumList.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val colors = SoundboxTheme.colors

    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    val albumSongs = selectedAlbum?.let { albumMap[it] } ?: emptyList()

    Box(modifier = Modifier.fillMaxSize()) {
        if (albumMap.isEmpty()) {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentCyan)
                }
            } else {
                EmptyPlaceholder(
                    title = "No Albums Found",
                    subtitle = "Albums will appear once your music library is scanned.",
                    icon = Icons.Default.Album,
                    actionText = "Scan Now",
                    onActionClick = { viewModel.scanStorage() }
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 155.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(albumMap.keys.toList()) { albumName ->
                    val songsInAlbum = albumMap[albumName] ?: emptyList()
                    val firstSong = songsInAlbum.firstOrNull()
                    val artistName = firstSong?.artist?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) } ?: "Unknown Artist"
                    val trackCount = songsInAlbum.size

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { selectedAlbum = albumName },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.surfaceElevated
                        ),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.borderSubtle),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                ArtworkThumbnail(
                                    songId = firstSong?.id,
                                    title = albumName,
                                    artist = artistName,
                                    genre = firstSong?.genre ?: "",
                                    path = firstSong?.path ?: "",
                                    isCircle = false,
                                    modifier = Modifier.fillMaxSize(),
                                    size = 160f
                                )

                                // Soft vinyl studio vignette for luxury audiophile sheen
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.35f)
                                                )
                                            )
                                        )
                                )

                                // Floating track count glass pill
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(7.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.55f)
                                ) {
                                    Text(
                                        text = if (trackCount == 1) "1 song" else "$trackCount songs",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // Quick Play floating FAB on album cover
                                Surface(
                                    onClick = {
                                        if (songsInAlbum.isNotEmpty()) {
                                            viewModel.playSong(songsInAlbum.first(), songsInAlbum)
                                            onSongSelected(songsInAlbum.first())
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(34.dp),
                                    shape = CircleShape,
                                    color = colors.accentCyan,
                                    shadowElevation = 4.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_sb_play),
                                            contentDescription = "Play Album",
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = albumName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    letterSpacing = 0.1.sp
                                ),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Expanded album track detail drawer sheet
        if (selectedAlbum != null) {
            val firstSong = albumSongs.firstOrNull()
            ModalBottomSheet(
                onDismissRequest = { selectedAlbum = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkThumbnail(
                            songId = firstSong?.id,
                            title = selectedAlbum!!,
                            artist = firstSong?.artist ?: "",
                            genre = firstSong?.genre ?: "",
                            path = firstSong?.path ?: "",
                            size = 72f,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedAlbum!!,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val sheetArtist = firstSong?.artist?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) } ?: "Unknown Artist"
                            Text(
                                text = "$sheetArtist • ${albumSongs.size} tracks",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.textSecondary
                                )
                            )
                        }
                    }

                    // Quick Action Buttons (Play All & Shuffle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (albumSongs.isNotEmpty()) {
                                    viewModel.playSong(albumSongs.first(), albumSongs)
                                    selectedAlbum = null
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = com.example.ui.theme.SoundboxButtonShapes.StandardAction,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_sb_play), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play all", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (albumSongs.isNotEmpty()) {
                                    val shuffled = albumSongs.shuffled()
                                    viewModel.playSong(shuffled.first(), shuffled)
                                    selectedAlbum = null
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = com.example.ui.theme.SoundboxButtonShapes.StandardAction,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.textPrimary
                            )
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.accentCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(albumSongs) { song ->
                            TrackRow(
                                song = song,
                                isPlaying = currentSong?.id == song.id,
                                onClick = {
                                    viewModel.playSong(song, albumSongs)
                                    onSongSelected(song)
                                    selectedAlbum = null
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(song) },
                                onMenuClick = { selectedSongForMenu = song }
                            )
                        }
                    }
                }
            }
        }

        if (selectedSongForMenu != null) {
            SongOptionsBottomSheet(
                song = selectedSongForMenu!!,
                viewModel = viewModel,
                onDismiss = { selectedSongForMenu = null }
            )
        }
    }
}
