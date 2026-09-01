package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.example.data.model.Song
import com.example.ui.components.getDefaultThumbnailResId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Data class representing color swatches dynamically extracted from album artwork.
 */
data class ArtworkPalette(
    val dominant: Color,
    val vibrant: Color,
    val darkVibrant: Color,
    val lightVibrant: Color,
    val muted: Color,
    val darkMuted: Color,
    val lightMuted: Color,
    val onDominant: Color,
    val gradientColors: List<Color>,
    val isLight: Boolean
) {
    companion object {
        fun default(isDarkTheme: Boolean = true): ArtworkPalette {
            val primary = if (isDarkTheme) Color(0xFF00F2FE) else Color(0xFF00668B)
            return ArtworkPalette(
                dominant = primary,
                vibrant = primary,
                darkVibrant = if (isDarkTheme) Color(0xFF0D1B2A) else Color(0xFFD6E3FF),
                lightVibrant = if (isDarkTheme) Color(0xFFBBE9FF) else Color(0xFF00344A),
                muted = if (isDarkTheme) Color(0xFF49454F) else Color(0xFF79747E),
                darkMuted = if (isDarkTheme) Color(0xFF1B1B22) else Color(0xFFE7E0EC),
                lightMuted = if (isDarkTheme) Color(0xFFE6E1E5) else Color(0xFF313033),
                onDominant = if (isDarkTheme) Color.White else Color.Black,
                gradientColors = if (isDarkTheme) {
                    listOf(
                        Color(0xFF0F2027),
                        Color(0xFF203A43),
                        Color(0xFF121216)
                    )
                } else {
                    listOf(
                        Color(0xFFE2F3F9),
                        Color(0xFFF5F8FA),
                        Color(0xFFFFFFFF)
                    )
                },
                isLight = !isDarkTheme
            )
        }
    }
}

/**
 * High-performance background extractor for generating Palette colors from audio track artwork.
 */
object ArtworkPaletteExtractor {
    private val memoryCache = mutableMapOf<String, ArtworkPalette>()

    suspend fun extractPalette(
        context: Context,
        song: Song?,
        thumbnailIndex: Int = -1,
        isDarkTheme: Boolean = true
    ): ArtworkPalette = withContext(Dispatchers.IO) {
        if (song == null) return@withContext ArtworkPalette.default(isDarkTheme)

        val cacheKey = "${song.id}_${thumbnailIndex}_$isDarkTheme"
        memoryCache[cacheKey]?.let { return@withContext it }

        var bitmap: Bitmap? = null

        // 1. Try saved online cover file
        try {
            val customCoverFile = OnlineCoverFetcher.getSavedCoverFile(context, song.id)
            if (customCoverFile.exists()) {
                bitmap = decodeSampledBitmap(customCoverFile.absolutePath, 160, 160)
            }
        } catch (e: Exception) { }

        // 2. Try embedded ID3 picture
        if (bitmap == null && song.path.isNotBlank()) {
            try {
                val retriever = MediaMetadataRetriever()
                if (song.path.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(song.path))
                } else {
                    retriever.setDataSource(song.path)
                }
                val artBytes = retriever.embeddedPicture
                retriever.release()
                if (artBytes != null) {
                    bitmap = decodeSampledBitmapFromByteArray(artBytes, 160, 160)
                }
            } catch (e: Exception) { }
        }

        // 3. Try MediaStore album art URI
        if (bitmap == null && song.id.isNotBlank()) {
            try {
                val artUri = Uri.parse("content://media/external/audio/media/${song.id}/albumart")
                context.contentResolver.openInputStream(artUri)?.use { input ->
                    bitmap = BitmapFactory.decodeStream(input)
                }
            } catch (e: Exception) { }
        }

        // 4. Fallback to app default thumbnail resource
        if (bitmap == null) {
            try {
                val thumbRes = getDefaultThumbnailResId(song.title, thumbnailIndex)
                bitmap = BitmapFactory.decodeResource(context.resources, thumbRes)
            } catch (e: Exception) { }
        }

        val finalBitmap = bitmap
        val result = if (finalBitmap != null) {
            try {
                val scaled = if (finalBitmap.width > 120 || finalBitmap.height > 120) {
                    Bitmap.createScaledBitmap(finalBitmap, 120, 120, false)
                } else {
                    finalBitmap
                }
                val palette = Palette.from(scaled).maximumColorCount(16).generate()
                buildArtworkPalette(palette, isDarkTheme)
            } catch (e: Exception) {
                ArtworkPalette.default(isDarkTheme)
            }
        } else {
            ArtworkPalette.default(isDarkTheme)
        }

        memoryCache[cacheKey] = result
        result
    }

    private fun decodeSampledBitmap(filePath: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(filePath, options)
    }

    private fun decodeSampledBitmapFromByteArray(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun buildArtworkPalette(palette: Palette, isDarkTheme: Boolean): ArtworkPalette {
        val def = ArtworkPalette.default(isDarkTheme)

        val dominantInt = palette.getDominantColor(def.dominant.toArgb())
        val vibrantInt = palette.getVibrantColor(dominantInt)
        val darkVibrantInt = palette.getDarkVibrantColor(palette.getDarkMutedColor(def.darkVibrant.toArgb()))
        val lightVibrantInt = palette.getLightVibrantColor(palette.getLightMutedColor(def.lightVibrant.toArgb()))
        val mutedInt = palette.getMutedColor(dominantInt)
        val darkMutedInt = palette.getDarkMutedColor(def.darkMuted.toArgb())
        val lightMutedInt = palette.getLightMutedColor(def.lightMuted.toArgb())

        val dominantColor = Color(dominantInt)
        val vibrantColor = Color(vibrantInt)
        val darkVibrantColor = Color(darkVibrantInt)
        val lightVibrantColor = Color(lightVibrantInt)
        val mutedColor = Color(mutedInt)
        val darkMutedColor = Color(darkMutedInt)
        val lightMutedColor = Color(lightMutedInt)

        val luminance = ColorUtils.calculateLuminance(dominantInt)
        val onDominant = if (luminance > 0.5) Color.Black else Color.White

        val gradientColors = if (isDarkTheme) {
            // Dark immersive background: top is a deep tinted rich hue blending down to deep obsidian
            val topGlow = Color(
                ColorUtils.blendARGB(
                    if (darkVibrantInt != dominantInt) darkVibrantInt else dominantInt,
                    android.graphics.Color.BLACK,
                    0.50f
                )
            )
            val midGlow = Color(
                ColorUtils.blendARGB(
                    if (darkMutedInt != dominantInt) darkMutedInt else darkVibrantInt,
                    android.graphics.Color.BLACK,
                    0.75f
                )
            )
            val bottomSurface = Color(0xFF0C0D12)
            listOf(topGlow, midGlow, bottomSurface)
        } else {
            // Light immersive background: pastel tinted glow blending down to crisp clean white/surface
            val topGlow = Color(
                ColorUtils.blendARGB(
                    if (lightVibrantInt != dominantInt) lightVibrantInt else vibrantInt,
                    android.graphics.Color.WHITE,
                    0.72f
                )
            )
            val midGlow = Color(
                ColorUtils.blendARGB(
                    if (lightMutedInt != dominantInt) lightMutedInt else dominantInt,
                    android.graphics.Color.WHITE,
                    0.88f
                )
            )
            val bottomSurface = Color(0xFFFBFBFE)
            listOf(topGlow, midGlow, bottomSurface)
        }

        return ArtworkPalette(
            dominant = dominantColor,
            vibrant = vibrantColor,
            darkVibrant = darkVibrantColor,
            lightVibrant = lightVibrantColor,
            muted = mutedColor,
            darkMuted = darkMutedColor,
            lightMuted = lightMutedColor,
            onDominant = onDominant,
            gradientColors = gradientColors,
            isLight = !isDarkTheme
        )
    }
}

/**
 * Composable helper that dynamically remembers and asynchronously updates
 * the extracted ArtworkPalette with smooth state updates.
 */
@Composable
fun rememberArtworkPalette(
    song: Song?,
    enabled: Boolean,
    thumbnailIndex: Int = -1,
    isDarkTheme: Boolean = true
): ArtworkPalette {
    val context = androidx.compose.ui.platform.LocalContext.current
    var palette by remember(song?.id, enabled, thumbnailIndex, isDarkTheme) {
        mutableStateOf(ArtworkPalette.default(isDarkTheme))
    }

    LaunchedEffect(song?.id, enabled, thumbnailIndex, isDarkTheme) {
        if (enabled && song != null) {
            palette = ArtworkPaletteExtractor.extractPalette(context, song, thumbnailIndex, isDarkTheme)
        } else {
            palette = ArtworkPalette.default(isDarkTheme)
        }
    }

    return palette
}
