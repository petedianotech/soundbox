package com.example.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.albums.AlbumsScreen
import com.example.ui.screens.artists.ArtistsScreen
import com.example.ui.screens.folders.FoldersScreen
import com.example.ui.screens.playlists.PlaylistsScreen
import com.example.ui.screens.songs.SongsScreen
import com.example.ui.viewmodel.MusicViewModel

enum class HomeTab(val title: String, val icon: ImageVector) {
    SONGS("Songs", Icons.Default.MusicNote),
    ALBUMS("Albums", Icons.Default.Album),
    ARTISTS("Artists", Icons.Default.Person),
    FOLDERS("Folders", Icons.Default.Folder),
    PLAYLISTS("Playlists", Icons.Default.PlaylistPlay)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNowPlaying: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(HomeTab.SONGS) }
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Soundbox") },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                HomeTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                HomeTab.SONGS -> SongsScreen(
                    viewModel = viewModel,
                    onSongSelected = { onNavigateToNowPlaying() }
                )
                HomeTab.ALBUMS -> AlbumsScreen(
                    viewModel = viewModel,
                    onSongSelected = { onNavigateToNowPlaying() }
                )
                HomeTab.ARTISTS -> ArtistsScreen(
                    viewModel = viewModel,
                    onSongSelected = { onNavigateToNowPlaying() }
                )
                HomeTab.FOLDERS -> FoldersScreen(
                    viewModel = viewModel,
                    onSongSelected = { onNavigateToNowPlaying() }
                )
                HomeTab.PLAYLISTS -> PlaylistsScreen(
                    viewModel = viewModel,
                    onSongSelected = { onNavigateToNowPlaying() }
                )
            }
            
            // Persistent Floating MiniPlayer
            MiniPlayer(
                currentSong = currentSong,
                isPlaying = isPlaying,
                onPlayPause = { viewModel.playPause() },
                onSkipNext = { viewModel.skipNext() },
                onOpenNowPlaying = onNavigateToNowPlaying,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
