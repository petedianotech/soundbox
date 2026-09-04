package com.example.util

import com.example.R
import com.example.data.model.Song

object AlbumArtHelper {

    // Distinct high-quality, smooth studio album art assets with soft lighting and audiophile aesthetics
    val ALBUM_ARTS = listOf(
        R.drawable.img_art_smooth_audiophile_one_1788530389412,
        R.drawable.img_art_smooth_audiophile_two_1788530409414,
        R.drawable.img_album_art_smooth_vinyl_1788528867497,
        R.drawable.img_album_art_smooth_studi_1788528883304
    )

    /**
     * Intelligently select a thematic music album art based on song metadata,
     * or deterministically hash the title and artist so each song consistently gets a unique cover.
     */
    fun getAlbumArtResId(song: Song?): Int {
        if (song == null) return ALBUM_ARTS[0]
        return getAlbumArtResId(song.title, song.artist, song.genre)
    }

    fun getAlbumArtResId(title: String, artist: String = "", genre: String = ""): Int {
        val query = (genre + " " + title + " " + artist).lowercase()

        return when {
            query.contains("jazz") || query.contains("blues") || query.contains("sax") || query.contains("soul") ->
                R.drawable.img_art_smooth_audiophile_one_1788530389412

            query.contains("acoustic") || query.contains("folk") || query.contains("country") || query.contains("unplugged") || query.contains("guitar") ->
                R.drawable.img_album_art_smooth_vinyl_1788528867497

            query.contains("piano") || query.contains("classic") || query.contains("orchestra") || query.contains("instrumental") ->
                R.drawable.img_art_smooth_audiophile_two_1788530409414

            query.contains("lofi") || query.contains("lo-fi") || query.contains("chill") || query.contains("relax") || query.contains("sleep") ->
                R.drawable.img_album_art_smooth_vinyl_1788528867497

            query.contains("studio") || query.contains("mix") || query.contains("edm") || query.contains("electronic") || query.contains("synth") || query.contains("pop") ->
                R.drawable.img_album_art_smooth_studi_1788528883304

            else -> {
                // Consistent hash mapping to one of the smooth studio album arts
                val key = "$title-$artist"
                val hash = kotlin.math.abs(key.hashCode())
                ALBUM_ARTS[hash % ALBUM_ARTS.size]
            }
        }
    }

    /**
     * Extracts byte array of the album art drawable for passing to Media3 / MediaMetadata
     */
    fun getArtworkBytes(context: android.content.Context, song: Song?): ByteArray? {
        val resId = getAlbumArtResId(song)
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, resId)
            if (bitmap != null) {
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, stream)
                stream.toByteArray()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes the album art drawable as a high-resolution Bitmap for notifications and UI widgets
     */
    fun getArtworkBitmap(context: android.content.Context, song: Song?): android.graphics.Bitmap? {
        val resId = getAlbumArtResId(song)
        return try {
            android.graphics.BitmapFactory.decodeResource(context.resources, resId)
        } catch (e: Exception) {
            null
        }
    }
}
