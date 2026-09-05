package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.albums.AlbumsScreen
import com.example.ui.screens.artists.ArtistsScreen
import com.example.ui.screens.folders.FoldersScreen
import com.example.ui.screens.genres.GenresScreen
import com.example.ui.screens.playlists.PlaylistsScreen
import com.example.ui.screens.songs.SongsScreen
import com.example.ui.theme.Poweramp_Amber
import com.example.ui.theme.Poweramp_Cyan
import com.example.ui.theme.Poweramp_Lime
import com.example.ui.theme.SoundboxTheme
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
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToCleaner: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(HomeTab.SONGS) }
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val equalizerEnabled by viewModel.equalizerEnabled.collectAsState()

    val progress = remember(currentPosition, duration) {
        if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    }
    
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
                    val colors = SoundboxTheme.colors
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SOUNDBOX",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textPrimary
                        )
                    }
                },
                actions = {
                    val colors = SoundboxTheme.colors
                    // Insights quick launch
                    IconButton(onClick = onNavigateToInsights) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = "Soundbox Insights",
                            tint = colors.accentCyan
                        )
                    }
                    // Equalizer quick launch with live LED
                    IconButton(onClick = onNavigateToEqualizer) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                Icons.Default.Equalizer,
                                contentDescription = "Equalizer",
                                tint = if (equalizerEnabled) colors.accentCyan else colors.textSecondary
                            )
                            if (equalizerEnabled) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.accentLime)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search songs", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SoundboxTheme.colors.topBarBackground
                )
            )
        },
        bottomBar = {
            val colors = SoundboxTheme.colors
            NavigationBar(
                containerColor = colors.surface,
                tonalElevation = 6.dp
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
                                text = tab.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                                    letterSpacing = 0.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (colors.isDark) Color.Black else Color.White,
                            selectedTextColor = colors.accentCyan,
                            indicatorColor = colors.accentCyan,
                            unselectedIconColor = colors.textMuted,
                            unselectedTextColor = colors.textMuted
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
                .background(SoundboxTheme.colors.background)
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
            
            // Persistent Poweramp MiniPlayer
            MiniPlayer(
                currentSong = currentSong,
                isPlaying = isPlaying,
                onPlayPause = { viewModel.playPause() },
                onSkipNext = { viewModel.skipNext() },
                onOpenNowPlaying = onNavigateToNowPlaying,
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )
        }
    }
}


