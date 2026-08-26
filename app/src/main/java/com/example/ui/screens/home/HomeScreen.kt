package com.example.ui.screens.home

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.albums.AlbumsScreen
import com.example.ui.screens.artists.ArtistsScreen
import com.example.ui.screens.folders.FoldersScreen
import com.example.ui.screens.genres.GenresScreen
import com.example.ui.screens.playlists.PlaylistsScreen
import com.example.ui.screens.songs.SongsScreen
import com.example.ui.viewmodel.MusicViewModel

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
    
    LaunchedEffect(visibleTabsStrings) {
        if (selectedTab.name !in visibleTabsStrings) {
            val fallback = HomeTab.values().firstOrNull { it.name in visibleTabsStrings } ?: HomeTab.SONGS
            selectedTab = fallback
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Soundbox",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search songs")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                visibleTabs.forEach { tab ->
                    val selected = selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            
            // Persistent Floating MiniPlayer above bottom bar
            MiniPlayer(
                currentSong = currentSong,
                isPlaying = isPlaying,
                onPlayPause = { viewModel.playPause() },
                onSkipNext = { viewModel.skipNext() },
                onOpenNowPlaying = onNavigateToNowPlaying,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            )
        }
    }
}

