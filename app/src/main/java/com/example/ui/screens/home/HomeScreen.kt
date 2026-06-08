package com.example.ui.screens.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
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
import androidx.compose.ui.draw.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.albums.AlbumsScreen
import com.example.ui.screens.artists.ArtistsScreen
import com.example.ui.screens.folders.FoldersScreen
import com.example.ui.screens.playlists.PlaylistsScreen
import com.example.ui.screens.songs.SongsScreen
import com.example.ui.viewmodel.MusicViewModel

import com.example.ui.screens.genres.GenresScreen

enum class HomeTab(val title: String, val icon: ImageVector) {
    SONGS("Songs", Icons.Default.MusicNote),
    ALBUMS("Albums", Icons.Default.Album),
    ARTISTS("Artists", Icons.Default.Person),
    GENRES("Genres", Icons.Default.Category),
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
    
    val visibleTabsStrings by viewModel.settingsManager.visibleTabsFlow.collectAsState()
    val visibleTabs = remember(visibleTabsStrings) {
        HomeTab.values().filter { it.name in visibleTabsStrings }
    }
    
    androidx.compose.runtime.LaunchedEffect(visibleTabsStrings) {
        if (selectedTab.name !in visibleTabsStrings) {
            val fallback = HomeTab.values().firstOrNull { it.name in visibleTabsStrings } ?: HomeTab.SONGS
            selectedTab = fallback
        }
    }

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
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind { drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)) }
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha=0.08f))
                    .padding(vertical = 12.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                ) {
                    visibleTabs.forEach { tab ->
                        val selected = selectedTab == tab
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) { selectedTab = tab }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier
                                    .size(26.dp)
                                    .drawWithCache {
                                        onDrawWithContent {
                                            drawContent()
                                            if (selected) {
                                                drawRect(com.example.ui.theme.CosmicPrismGradient, blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn)
                                            }
                                        }
                                    },
                                tint = if (selected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.White.copy(alpha=0.5f)
                            )
                            if (selected) {
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(width = 18.dp, height = 3.dp)
                                        .background(com.example.ui.theme.CosmicPrismGradient, androidx.compose.foundation.shape.RoundedCornerShape(1.5.dp))
                                )
                            }
                        }
                    }
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
                    onSongSelected = { }
                )
                HomeTab.ALBUMS -> AlbumsScreen(
                    viewModel = viewModel,
                    onSongSelected = { }
                )
                HomeTab.ARTISTS -> ArtistsScreen(
                    viewModel = viewModel,
                    onSongSelected = { }
                )
                HomeTab.GENRES -> GenresScreen(
                    viewModel = viewModel,
                    onSongSelected = { }
                )
                HomeTab.FOLDERS -> FoldersScreen(
                    viewModel = viewModel,
                    onSongSelected = { }
                )
                HomeTab.PLAYLISTS -> PlaylistsScreen(
                    viewModel = viewModel,
                    onSongSelected = { }
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
