package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedSongArt(songId: String?): Modifier {
    if (songId.isNullOrBlank()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animScope = LocalNavAnimatedVisibilityScope.current ?: return this

    return with(sharedScope) {
        this@sharedSongArt.sharedElement(
            state = rememberSharedContentState(key = "album_art_$songId"),
            animatedVisibilityScope = animScope,
            boundsTransform = { _, _ ->
                spring(dampingRatio = 0.85f, stiffness = 380f)
            }
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedSongTitle(songId: String?): Modifier {
    if (songId.isNullOrBlank()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animScope = LocalNavAnimatedVisibilityScope.current ?: return this

    return with(sharedScope) {
        this@sharedSongTitle.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "song_title_$songId"),
            animatedVisibilityScope = animScope,
            boundsTransform = { _, _ ->
                spring(dampingRatio = 0.85f, stiffness = 380f)
            }
        )
    }
}
