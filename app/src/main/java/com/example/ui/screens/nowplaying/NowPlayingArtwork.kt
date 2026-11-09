package com.example.ui.screens.nowplaying

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.player.LyricLine
import com.example.util.AlbumArtHelper
import com.example.ui.components.RealtimeAudioVisualizer
import com.example.ui.components.VisualizerStyle
import com.example.ui.components.SongImagePlaceholder
import com.example.ui.theme.SoundboxTheme
import java.util.Locale

@Composable
internal fun ArtworkMainStage(
    song: Song,
    isPlaying: Boolean,
    lyrics: List<LyricLine>,
    hasTimestamps: Boolean,
    activeVerseIndex: Int,
    onToggleLyrics: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreOptions: () -> Unit = {},
    accentColor: Color
) {
    val colors = SoundboxTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Simplified artwork stage - full visual polish restored with main screen chunks
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            SongImagePlaceholder(modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = song.title,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = song.artist,
            color = colors.textSecondary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) Color.Red else colors.textPrimary
                )
            }
            IconButton(onClick = onToggleLyrics) {
                Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = accentColor)
            }
            IconButton(onClick = onMoreOptions) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = colors.textPrimary)
            }
        }
    }
}

internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

@Composable
internal fun OptionMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = SoundboxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.accentCyan, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (trailing != null) trailing()
    }
}
