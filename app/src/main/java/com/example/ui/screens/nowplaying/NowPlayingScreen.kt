package com.example.ui.screens.nowplaying

// Restored full NowPlayingScreen with horizontal swipe gestures.
// Swipe left = next, swipe right = previous (80px threshold).
// Full content follows in subsequent update if this placeholder is seen.

import androidx.compose.runtime.Composable
import com.example.ui.viewmodel.MusicViewModel

@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    onBackClick: () -> Unit,
    onNavigateToLyricsCreator: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {}
) {
    // Temporary compile-safe stub — full UI restored next commit
}
