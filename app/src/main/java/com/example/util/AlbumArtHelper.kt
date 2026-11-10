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
 * Fallback art is pure vector (Deep Space / glass-morphism) so APK stays tiny.
 */
object AlbumArtHelper {
    private const val TAG = "AlbumArtHelper"
    private const val PREFS_NAME = "permanent_album_art_registry"

    // Lightweight vector Deep Space studio themes (no large PNGs)
    val ALBUM_ARTS = listOf(
        R.drawable.img_art_smooth_audiophile_one,
        R.drawable.img_art_smooth_audiophile_two,
        R.drawable.img_album_art_smooth_vinyl,
        R.drawable.img_album_art_smooth_studi
    )

    private val memoryArtMap = ConcurrentHashMap<String, Int>()

    fun clearMemoryCache() {
        memoryArtMap.clear()
    }

    private fun getArtDir(context: Context): File {
        val dir = File(context.filesDir, "album_art")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getArtworkFile(context: Context, songId: String): File {
        val cleanId = songId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        return File(getArtDir(context), "${cleanId}.jpg")
    }

    fun hasCustomArtwork(context: Context, songId: String): Boolean {
        if (songId.isBlank()) return false
        val file = getArtworkFile(context, songId)
        return file.exists() && file.length() > 0
    }

    fun saveCustomArtwork(context: Context, song: Song, bitmap: Bitmap): Boolean {
        return try {
            val file = getArtworkFile(context, song.id)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("song_art_${song.id}", file.absolutePath).apply()
            Log.d(TAG, "Saved custom album art permanently for song: ${song.title}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom album art for ${song.title}", e)
            false
        }
    }

    fun saveCustomArtwork(context: Context, song: Song, inputStream: InputStream): Boolean {
        return try {
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return false
            saveCustomArtwork(context, song, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom artwork stream", e)
            false
        }
    }

    fun decodeResourceToBitmap(context: Context, resId: Int, width: Int = 512, height: Int = 512): Bitmap? {
        return try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, resId) ?: return null
            if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap
            }
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else width
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else height
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render resource $resId to bitmap", e)
            null
        }
    }

    fun assignStudioThemeToSong(context: Context, song: Song, resId: Int): Boolean {
        return try {
            val bitmap = decodeResourceToBitmap(context, resId) ?: return false
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

    fun extractAndPersistEmbeddedArt(context: Context, song: Song): File? {
        val targetFile = getArtworkFile(context, song.id)
        if (targetFile.exists() && targetFile.length() > 0) return targetFile
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
                FileOutputStream(targetFile).use { out -> out.write(pictureBytes) }
                targetFile
            } else null
        } catch (e: Exception) {
            null
        }
    }

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
            if (customFile.exists() && customFile.length() > 0) return customFile
            if (path.isNotBlank() && (path.startsWith("/") || path.startsWith("content://"))) {
                try {
                    val dummySong = Song(
                        id = songId, title = title, artist = artist, album = "",
                        duration = 0L, path = path, size = 0L, folderPath = "", folderName = ""
                    )
                    val extracted = extractAndPersistEmbeddedArt(context, dummySong)
                    if (extracted != null && extracted.exists()) return extracted
                } catch (e: Exception) { }
            }
        }
        return getAlbumArtResId(title, artist, genre, songId, context)
    }

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
        if (!songId.isNullOrBlank() && memoryArtMap.containsKey(songId)) {
            return memoryArtMap[songId]!!
        }
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

        val cleanArtist = LyricsManager.cleanString(artist, isArtist = true)
        val cleanTitle = LyricsManager.cleanString(title, isArtist = false)
        val query = (genre + " " + cleanTitle + " " + cleanArtist).lowercase()

        val chosenResId = when {
            query.contains("jazz") || query.contains("blues") || query.contains("sax") || query.contains("soul") ->
                R.drawable.img_art_smooth_audiophile_one
            query.contains("acoustic") || query.contains("folk") || query.contains("country") || query.contains("unplugged") || query.contains("guitar") ->
                R.drawable.img_album_art_smooth_vinyl
            query.contains("piano") || query.contains("classic") || query.contains("orchestra") || query.contains("instrumental") ->
                R.drawable.img_art_smooth_audiophile_two
            query.contains("lofi") || query.contains("lo fi") || query.contains("chill") || query.contains("relax") || query.contains("sleep") ->
                R.drawable.img_album_art_smooth_vinyl
            query.contains("studio") || query.contains("mix") || query.contains("edm") || query.contains("electronic") || query.contains("synth") || query.contains("pop") ->
                R.drawable.img_album_art_smooth_studi
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
                } catch (e: Exception) { }
            }
        }
        return chosenResId
    }

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

    fun getArtworkBitmap(context: Context, song: Song?): Bitmap? {
        if (song == null) return null
        return try {
            val customFile = getArtworkFile(context, song.id)
            if (customFile.exists() && customFile.length() > 0) {
                BitmapFactory.decodeFile(customFile.absolutePath)
            } else {
                val resId = getAlbumArtResId(song, context)
                decodeResourceToBitmap(context, resId)
            }
        } catch (e: Exception) {
            null
        }
    }

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
