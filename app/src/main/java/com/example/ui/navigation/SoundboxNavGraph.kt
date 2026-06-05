package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.nowplaying.NowPlayingScreen
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.viewmodel.MusicViewModel

@Composable
fun SoundboxNavGraph() {
    val navController = rememberNavController()
    val viewModel: MusicViewModel = viewModel()

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
                onNavigateToNowPlaying = { navController.navigate(Routes.NOW_PLAYING) }
            )
        }
        composable(Routes.NOW_PLAYING) {
            NowPlayingScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = viewModel,
                onSongSelected = { navController.navigate(Routes.NOW_PLAYING) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel
            )
        }
    }
}

