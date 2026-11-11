package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.Song
import com.example.player.LyricsManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight, crash-proof Album Art Management System.
 *
 * Guarantees:
 * 1. 5 Ultra-lightweight vector artworks in place of heavy raster JPEGs.
 * 2. Zero OOM or TransactionTooLargeExceptions during playlist playback initialization.
 * 3. Fast caching of custom/embedded artwork when present.
 * 4. Deterministic fallback vectors matching genre and title keywords.
 */
object AlbumArtHelper {
    private const val TAG = "AlbumArtHelper"
    private const val PREFS_NAME = "permanent_album_art_registry"

    // 5 Lightweight, neon cyber audiophile vector artworks
    val ALBUM_ARTS = listOf(
        R.drawable.ic_art_soundbox_neon,
        R.drawable.ic_art_vinyl_pulse,
        R.drawable.ic_art_headphones_wave,
        R.drawable.ic_art_cyber_synth,
        R.drawable.ic_art_audiophile_eq
    )

    // Memory cache for instantaneous, zero-overhead lookups
    private val memoryArtMap = ConcurrentHashMap<String, Int>()

    fun clearMemoryCache() {
        memoryArtMap.clear()
    }

    private fun getArtDir(context: Context): File {
        val dir = File(context.filesDir, "album_art")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns the dedicated permanent artwork file for a song ID.
     */
    fun getArtworkFile(context: Context, songId: String): File {
        val cleanId = songId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        return File(getArtDir(context), "${cleanId}.jpg")
    }

    /**
     * Checks if a physical or user-assigned custom artwork file already exists for this song.
     */
    fun hasCustomArtwork(context: Context, songId: String): Boolean {
        if (songId.isBlank()) return false
        val file = getArtworkFile(context, songId)
        return file.exists() && file.length() > 0
    }

    /**
     * Permanently saves a Bitmap as the album art for a song.
     */
    fun saveCustomArtwork(context: Context, song: Song, bitmap: Bitmap): Boolean {
        return try {
            val file = getArtworkFile(context, song.id)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("song_art_${song.id}", file.absolutePath).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom album art for ${song.title}", e)
            false
        }
    }

    /**
     * Permanently saves an image from an InputStream / Uri as the album art for a song.
     */
    fun saveCustomArtwork(context: Context, song: Song, inputStream: InputStream): Boolean {
        return try {
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return false
            saveCustomArtwork(context, song, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom artwork stream", e)
            false
        }
    }

    /**
     * Permanently assigns and saves a built-in vector artwork for a song.
     */
    fun assignStudioThemeToSong(context: Context, song: Song, resId: Int): Boolean {
        memoryArtMap[song.id] = resId
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("theme_res_${song.id}", resId).apply()
        return true
    }

    /**
     * Automatically extracts physical embedded ID3/APIC artwork from the audio file
     * and permanently saves it to the song's private artwork file if present.
     */
    fun extractAndPersistEmbeddedArt(context: Context, song: Song): File? {
        val targetFile = getArtworkFile(context, song.id)
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile
        }

        if (song.path.isBlank()) return null

        return try {
            val retriever = MediaMetadataRetriever()
            if (song.path.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(song.path))
            } else {
                val f = File(song.path)
                if (!f.exists() || !f.canRead()) return null
                retriever.setDataSource(song.path)
            }

            val pictureBytes = retriever.embeddedPicture
            retriever.release()

            if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                FileOutputStream(targetFile).use { out ->
                    out.write(pictureBytes)
                }
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Unified Model provider for Coil and Compose image loaders.
     */
    fun getArtworkModel(
        context: Context,
        songId: String?,
        title: String,
        artist: String = "",
        genre: String = "",
        path: String = ""
    ): Any {
        if (!songId.isNullOrBlank()) {
            val customFile = getArtworkFile(context, songId)
            if (customFile.exists() && customFile.length() > 0) {
                return customFile
            }

            if (path.isNotBlank() && (path.startsWith("/") || path.startsWith("content://"))) {
                try {
                    val dummySong = Song(
                        id = songId,
                        title = title,
                        artist = artist,
                        album = "",
                        duration = 0L,
                        path = path,
                        size = 0L,
                        folderPath = "",
                        folderName = ""
                    )
                    val extracted = extractAndPersistEmbeddedArt(context, dummySong)
                    if (extracted != null && extracted.exists()) {
                        return extracted
                    }
                } catch (_: Exception) { }
            }
        }

        return getAlbumArtResId(title, artist, genre, songId, context)
    }

    /**
     * Intelligently selects or retrieves the locked vector artwork for a song.
     */
    fun getAlbumArtResId(song: Song?, context: Context? = null): Int {
        if (song == null) return ALBUM_ARTS[0]
        return getAlbumArtResId(song.title, song.artist, song.genre, song.id, context)
    }

    fun getAlbumArtResId(
        title: String,
        artist: String = "",
        genre: String = "",
        songId: String? = null,
        context: Context? = null
    ): Int {
        // 1. Check in-memory cache
        if (!songId.isNullOrBlank() && memoryArtMap.containsKey(songId)) {
            return memoryArtMap[songId]!!
        }

        // 2. Check SharedPreferences
        if (!songId.isNullOrBlank() && context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedRes = prefs.getInt("theme_res_$songId", 0)
                if (savedRes != 0 && ALBUM_ARTS.contains(savedRes)) {
                    memoryArtMap[songId] = savedRes
                    return savedRes
                }
            } catch (_: Exception) { }
        }

        // 3. Keyword matching to our 5 vector artworks
        val cleanArtist = LyricsManager.cleanString(artist, isArtist = true)
        val cleanTitle = LyricsManager.cleanString(title, isArtist = false)
        val query = (genre + " " + cleanTitle + " " + cleanArtist).lowercase()

        val chosenResId = when {
            // Vinyl / Retro / Acoustic / Jazz
            query.contains("vinyl") || query.contains("retro") || query.contains("jazz") ||
            query.contains("blues") || query.contains("acoustic") || query.contains("turntable") ||
            query.contains("folk") || query.contains("country") || query.contains("unplugged") ->
                R.drawable.ic_art_vinyl_pulse

            // Headphones / Vocals / Podcast / Chill / Lofi / Pop
            query.contains("headphone") || query.contains("vocal") || query.contains("pop") ||
            query.contains("podcast") || query.contains("lofi") || query.contains("chill") ||
            query.contains("relax") || query.contains("piano") || query.contains("classic") ->
                R.drawable.ic_art_headphones_wave

            // Synthwave / Cyber / EDM / Dance / Electronic / Techno / Rock
            query.contains("synth") || query.contains("cyber") || query.contains("edm") ||
            query.contains("dance") || query.contains("electronic") || query.contains("wave") ||
            query.contains("techno") || query.contains("rock") || query.contains("metal") ->
                R.drawable.ic_art_cyber_synth

            // Audiophile / Studio / Master / Hi-Res / Gold / Bass / EQ
            query.contains("audiophile") || query.contains("studio") || query.contains("hi-res") ||
            query.contains("flac") || query.contains("master") || query.contains("eq") ||
            query.contains("bass") || query.contains("gold") ->
                R.drawable.ic_art_audiophile_eq

            // Default Soundbox Neon Prism
            else -> {
                val stableKey = if (cleanArtist.isNotBlank()) "$cleanArtist-$cleanTitle" else cleanTitle.ifBlank { title }
                val hash = kotlin.math.abs(stableKey.hashCode())
                ALBUM_ARTS[hash % ALBUM_ARTS.size]
            }
        }

        if (!songId.isNullOrBlank()) {
            memoryArtMap[songId] = chosenResId
            if (context != null) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putInt("theme_res_$songId", chosenResId).apply()
                } catch (_: Exception) { }
            }
        }

        return chosenResId
    }

    /**
     * Returns a lightweight Uri for the artwork (file:// or android.resource://).
     */
    fun getArtworkUri(context: Context, song: Song?): Uri {
        if (song == null) {
            return Uri.parse("android.resource://${context.packageName}/${ALBUM_ARTS[0]}")
        }

        val customFile = getArtworkFile(context, song.id)
        if (customFile.exists() && customFile.length() > 0) {
            return Uri.fromFile(customFile)
        }

        val resId = getAlbumArtResId(song, context)
        return Uri.parse("android.resource://${context.packageName}/$resId")
    }

    /**
     * Extracts byte array only for custom embedded cover if present;
     * returns null for vector resources to completely eliminate memory strain and IPC overflow.
     */
    fun getArtworkBytes(context: Context, song: Song?): ByteArray? {
        if (song == null) return null
        return try {
            val customFile = getArtworkFile(context, song.id)
            if (customFile.exists() && customFile.length() > 0) {
                val bitmap = BitmapFactory.decodeFile(customFile.absolutePath) ?: return null
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                stream.toByteArray()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Safely decodes artwork into a small Bitmap when requested by Palette or Widgets.
     * Supports both files and vector drawables with size bounding.
     */
    fun getArtworkBitmap(context: Context, song: Song?, maxDimension: Int = 250): Bitmap? {
        if (song == null) return null
        return try {
            val customFile = getArtworkFile(context, song.id)
            if (customFile.exists() && customFile.length() > 0) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(customFile.absolutePath, options)
                var sampleSize = 1
                while (options.outWidth / (sampleSize * 2) >= maxDimension && options.outHeight / (sampleSize * 2) >= maxDimension) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                BitmapFactory.decodeFile(customFile.absolutePath, decodeOptions)
            } else {
                val resId = getAlbumArtResId(song, context)
                val drawable = ContextCompat.getDrawable(context, resId) ?: return null
                val width = maxDimension
                val height = maxDimension
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "getArtworkBitmap failed: ${e.message}")
            null
        }
    }

    /**
     * Safely scales artwork for Android AppWidget RemoteViews to prevent TransactionTooLargeException.
     */
    fun getWidgetArtworkBitmap(context: Context, song: Song?, maxDimension: Int = 200): Bitmap? {
        return getArtworkBitmap(context, song, maxDimension)
    }
}
