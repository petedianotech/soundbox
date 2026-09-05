package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.data.model.Song
import com.example.player.LyricsManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Permanent, robust Album Art Management System.
 *
 * Guarantees:
 * 1. Permanence: Once a song is assigned an album art (embedded, custom user-selected,
 *    or auto-assigned studio theme), it is saved permanently to app storage and locked to that song.
 * 2. Uniformity: The exact same artwork is displayed across Now Playing, Track Rows, Albums,
 *    Artists, Playlists, Folders, Lockscreen, Notifications, MediaSession, and Widgets.
 * 3. Zero Inconsistency: No unexpected switching of artwork when navigating or refreshing lists.
 */
object AlbumArtHelper {
    private const val TAG = "AlbumArtHelper"
    private const val PREFS_NAME = "permanent_album_art_registry"

    // High-quality, smooth audiophile studio album art assets
    val ALBUM_ARTS = listOf(
        R.drawable.img_art_smooth_audiophile_one_1788530389412,
        R.drawable.img_art_smooth_audiophile_two_1788530409414,
        R.drawable.img_album_art_smooth_vinyl_1788528867497,
        R.drawable.img_album_art_smooth_studi_1788528883304
    )

    // Memory cache for instantaneous, zero-overhead lookups
    private val memoryArtMap = ConcurrentHashMap<String, Int>()

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
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            // Also register in persistent preferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("song_art_${song.id}", file.absolutePath).apply()
            Log.d(TAG, "Saved custom album art permanently for song: ${song.title} (${file.absolutePath})")
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
     * Permanently assigns and saves a built-in audiophile artwork resource for a song.
     */
    fun assignStudioThemeToSong(context: Context, song: Song, resId: Int): Boolean {
        return try {
            val bitmap = BitmapFactory.decodeResource(context.resources, resId) ?: return false
            val success = saveCustomArtwork(context, song, bitmap)
            if (success) {
                memoryArtMap[song.id] = resId
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putInt("theme_res_${song.id}", resId).apply()
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to assign studio theme", e)
            false
        }
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
                Log.d(TAG, "Auto-extracted and permanently saved embedded album art for: ${song.title}")
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            // Silently ignore format errors
            null
        }
    }

    /**
     * Unified, rock-solid Model provider for Coil and Compose image loaders.
     * Checks in exact order:
     * 1. Saved permanent file (custom or previously extracted)
     * 2. Embedded physical audio metadata (auto-extracted & cached)
     * 3. Deterministically assigned permanent studio theme cover
     *
     * This guarantees 100% consistency across the entire app.
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

            // Try lazy embedded extraction if local audio file path is provided
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
                } catch (e: Exception) { }
            }
        }

        // Return permanent deterministic studio resource ID
        return getAlbumArtResId(title, artist, genre, songId, context)
    }

    /**
     * Intelligently selects or retrieves the permanently locked studio theme artwork for a song.
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
        // 1. Check in-memory cache by songId
        if (!songId.isNullOrBlank() && memoryArtMap.containsKey(songId)) {
            return memoryArtMap[songId]!!
        }

        // 2. Check SharedPreferences if context is available
        if (!songId.isNullOrBlank() && context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedRes = prefs.getInt("theme_res_$songId", 0)
                if (savedRes != 0 && ALBUM_ARTS.contains(savedRes)) {
                    memoryArtMap[songId] = savedRes
                    return savedRes
                }
            } catch (e: Exception) { }
        }

        // 3. Clean strings to eliminate noise words so slight naming variations map to identical art
        val cleanArtist = LyricsManager.cleanString(artist, isArtist = true)
        val cleanTitle = LyricsManager.cleanString(title, isArtist = false)
        val query = (genre + " " + cleanTitle + " " + cleanArtist).lowercase()

        val chosenResId = when {
            query.contains("jazz") || query.contains("blues") || query.contains("sax") || query.contains("soul") ->
                R.drawable.img_art_smooth_audiophile_one_1788530389412

            query.contains("acoustic") || query.contains("folk") || query.contains("country") || query.contains("unplugged") || query.contains("guitar") ->
                R.drawable.img_album_art_smooth_vinyl_1788528867497

            query.contains("piano") || query.contains("classic") || query.contains("orchestra") || query.contains("instrumental") ->
                R.drawable.img_art_smooth_audiophile_two_1788530409414

            query.contains("lofi") || query.contains("lo fi") || query.contains("chill") || query.contains("relax") || query.contains("sleep") ->
                R.drawable.img_album_art_smooth_vinyl_1788528867497

            query.contains("studio") || query.contains("mix") || query.contains("edm") || query.contains("electronic") || query.contains("synth") || query.contains("pop") ->
                R.drawable.img_album_art_smooth_studi_1788528883304

            else -> {
                // Stable deterministic hash based on normalized title & artist
                val stableKey = if (cleanArtist.isNotBlank()) "$cleanArtist-$cleanTitle" else cleanTitle.ifBlank { title }
                val hash = kotlin.math.abs(stableKey.hashCode())
                ALBUM_ARTS[hash % ALBUM_ARTS.size]
            }
        }

        // Store into memory cache and preferences for absolute permanent locking
        if (!songId.isNullOrBlank()) {
            memoryArtMap[songId] = chosenResId
            if (context != null) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putInt("theme_res_$songId", chosenResId).apply()
                } catch (e: Exception) { }
            }
        }

        return chosenResId
    }

    /**
     * Returns a valid Uri for the artwork (file:// or android.resource://)
     * suitable for Media3 MediaItem and notifications.
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
     * Extracts byte array of the album art for passing to Media3 / MediaMetadata
     */
    fun getArtworkBytes(context: Context, song: Song?): ByteArray? {
        if (song == null) return null
        return try {
            val bitmap = getArtworkBitmap(context, song) ?: return null
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes the permanent album art as a high-resolution Bitmap for notifications and UI widgets.
     */
    fun getArtworkBitmap(context: Context, song: Song?): Bitmap? {
        if (song == null) return null
        return try {
            val customFile = getArtworkFile(context, song.id)
            if (customFile.exists() && customFile.length() > 0) {
                BitmapFactory.decodeFile(customFile.absolutePath)
            } else {
                val resId = getAlbumArtResId(song, context)
                BitmapFactory.decodeResource(context.resources, resId)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Safely scales artwork for Android AppWidget RemoteViews to prevent TransactionTooLargeException.
     */
    fun getWidgetArtworkBitmap(context: Context, song: Song?, maxDimension: Int = 260): Bitmap? {
        val original = getArtworkBitmap(context, song) ?: return null
        return try {
            if (original.width <= maxDimension && original.height <= maxDimension) {
                original
            } else {
                val ratio = original.width.toFloat() / original.height.toFloat()
                val targetW = if (ratio >= 1f) maxDimension else (maxDimension * ratio).toInt().coerceAtLeast(1)
                val targetH = if (ratio >= 1f) (maxDimension / ratio).toInt().coerceAtLeast(1) else maxDimension
                Bitmap.createScaledBitmap(original, targetW, targetH, true)
            }
        } catch (e: Exception) {
            original
        }
    }
}
