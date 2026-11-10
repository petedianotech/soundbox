package com.example.util

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SongPaletteColors(
    val primary: Color,
    val secondary: Color,
    val darkBackground: Color,
    val surfaceTint: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

object PaletteHelper {
    private val cache = mutableMapOf<String, SongPaletteColors>()

    suspend fun extractColors(context: Context, song: Song?): SongPaletteColors = withContext(Dispatchers.IO) {
        if (song == null) return@withContext defaultColors()
        val cached = cache[song.id]
        if (cached != null) return@withContext cached

        val bitmap: Bitmap? = AlbumArtHelper.getArtworkBitmap(context, song)
        if (bitmap == null) return@withContext defaultColors()

        try {
            val palette = Palette.from(bitmap)
                .maximumColorCount(16)
                .clearFilters()
                .generate()

            val dominant = palette.dominantSwatch?.rgb
            val vibrant = palette.vibrantSwatch?.rgb ?: dominant
            val darkVibrant = palette.darkVibrantSwatch?.rgb ?: palette.darkMutedSwatch?.rgb
            val lightVibrant = palette.lightVibrantSwatch?.rgb ?: palette.lightMutedSwatch?.rgb
            val muted = palette.mutedSwatch?.rgb

            val primaryColor = if (vibrant != null) Color(vibrant) else Color(0xFF00E5FF)
            val accentColor = if (lightVibrant != null) Color(lightVibrant) else primaryColor
            
            // Build rich deep backdrop base color from dark swatch
            val darkBg = if (darkVibrant != null) {
                val c = Color(darkVibrant)
                Color(c.red * 0.22f, c.green * 0.22f, c.blue * 0.22f, 1f)
            } else if (dominant != null) {
                val c = Color(dominant)
                Color(c.red * 0.18f, c.green * 0.18f, c.blue * 0.18f, 1f)
            } else {
                Color(0xFF070B12)
            }

            val surface = if (dominant != null) Color(dominant).copy(alpha = 0.25f) else Color(0x3300E5FF)

            val result = SongPaletteColors(
                primary = primaryColor,
                secondary = if (muted != null) Color(muted) else Color(0xFF7C4DFF),
                darkBackground = darkBg,
                surfaceTint = surface,
                accent = accentColor,
                textPrimary = Color.White,
                textSecondary = Color(0xFFB0B8C4)
            )
            cache[song.id] = result
            result
        } catch (e: Exception) {
            defaultColors()
        }
    }

    fun defaultColors(): SongPaletteColors {
        return SongPaletteColors(
            primary = Color(0xFF00E5FF),
            secondary = Color(0xFF7C4DFF),
            darkBackground = Color(0xFF070B12),
            surfaceTint = Color(0x3300E5FF),
            accent = Color(0xFF00E5FF),
            textPrimary = Color.White,
            textSecondary = Color(0xFFB0B8C4)
        )
    }
}

@Composable
fun rememberSongPalette(song: Song?, context: Context): SongPaletteColors {
    var rawPalette by remember(song?.id) { mutableStateOf(PaletteHelper.defaultColors()) }

    LaunchedEffect(song?.id) {
        if (song != null) {
            rawPalette = PaletteHelper.extractColors(context, song)
        } else {
            rawPalette = PaletteHelper.defaultColors()
        }
    }

    val animatedPrimary by animateColorAsState(rawPalette.primary, animationSpec = tween(500), label = "animPrimary")
    val animatedSecondary by animateColorAsState(rawPalette.secondary, animationSpec = tween(500), label = "animSecondary")
    val animatedDarkBg by animateColorAsState(rawPalette.darkBackground, animationSpec = tween(500), label = "animDarkBg")
    val animatedAccent by animateColorAsState(rawPalette.accent, animationSpec = tween(500), label = "animAccent")
    val animatedSurface by animateColorAsState(rawPalette.surfaceTint, animationSpec = tween(500), label = "animSurface")

    return SongPaletteColors(
        primary = animatedPrimary,
        secondary = animatedSecondary,
        darkBackground = animatedDarkBg,
        surfaceTint = animatedSurface,
        accent = animatedAccent,
        textPrimary = rawPalette.textPrimary,
        textSecondary = rawPalette.textSecondary
    )
}
