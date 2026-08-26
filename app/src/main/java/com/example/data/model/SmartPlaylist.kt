package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class SmartPlaylistType {
    RECENTLY_ADDED,
    MOST_PLAYED,
    RECENTLY_PLAYED,
    FAVORITES,
    GENRE,
    LONG_TRACKS,
    SHORT_TRACKS,
    NEVER_PLAYED
}

data class SmartPlaylist(
    val id: String,
    val type: SmartPlaylistType,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tintColor: Color,
    val songs: List<Song>,
    val genreName: String? = null
) {
    val trackCount: Int
        get() = songs.size

    val totalDurationMs: Long
        get() = songs.sumOf { it.duration }
}
