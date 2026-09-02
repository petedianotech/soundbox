package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.about.AboutScreen
import com.example.ui.screens.equalizer.PowerampEqualizerScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.nowplaying.NowPlayingScreen
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.screens.lyrics.LyricsSyncEditorScreen
import com.example.ui.viewmodel.MusicViewModel

@Composable
fun SoundboxNavGraph(viewModel: MusicViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToNowPlaying = { navController.navigate(Routes.NOW_PLAYING) },
                onNavigateToEqualizer = { navController.navigate(Routes.EQUALIZER) }
            )
        }
        composable(Routes.NOW_PLAYING) {
            NowPlayingScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToLyricsCreator = { navController.navigate(Routes.LYRICS_CREATOR) },
                onNavigateToEqualizer = { navController.navigate(Routes.EQUALIZER) }
            )
        }
        composable(Routes.EQUALIZER) {
            PowerampEqualizerScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.LYRICS_CREATOR) {
            LyricsSyncEditorScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = viewModel,
                onNavigateToNowPlaying = { navController.navigate(Routes.NOW_PLAYING) },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) }
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

