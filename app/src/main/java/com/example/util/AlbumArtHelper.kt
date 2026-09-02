package com.example.util

import com.example.R
import com.example.data.model.Song

object AlbumArtHelper {

    // 9 distinct high-quality album art assets generated specifically for music categories
    val ALBUM_ARTS = listOf(
        R.drawable.img_album_art_vinyl_1788360828062,
        R.drawable.img_album_art_headphones_1788360844519,
        R.drawable.img_album_art_acoustic_1788360862230,
        R.drawable.img_album_art_synthwave_1788360879358,
        R.drawable.img_album_art_jazz_1788360894012,
        R.drawable.img_album_art_piano_1788360909101,
        R.drawable.img_album_art_lofi_1788360925798,
        R.drawable.img_album_art_rock_1788360943637,
        R.drawable.img_album_art_edm_1788360958313
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
            query.contains("rock") || query.contains("metal") || query.contains("punk") || query.contains("guitar") ->
                R.drawable.img_album_art_rock_1788360943637

            query.contains("jazz") || query.contains("blues") || query.contains("sax") || query.contains("soul") ->
                R.drawable.img_album_art_jazz_1788360894012

            query.contains("acoustic") || query.contains("folk") || query.contains("country") || query.contains("unplugged") ->
                R.drawable.img_album_art_acoustic_1788360862230

            query.contains("piano") || query.contains("classic") || query.contains("orchestra") || query.contains("instrumental") ->
                R.drawable.img_album_art_piano_1788360909101

            query.contains("lofi") || query.contains("lo-fi") || query.contains("chill") || query.contains("relax") || query.contains("sleep") ->
                R.drawable.img_album_art_lofi_1788360925798

            query.contains("edm") || query.contains("dance") || query.contains("house") || query.contains("trance") || query.contains("techno") || query.contains("club") ->
                R.drawable.img_album_art_edm_1788360958313

            query.contains("synth") || query.contains("wave") || query.contains("retro") || query.contains("80s") || query.contains("pop") ->
                R.drawable.img_album_art_synthwave_1788360879358

            query.contains("hip hop") || query.contains("rap") || query.contains("trap") || query.contains("beat") || query.contains("bass") ->
                R.drawable.img_album_art_headphones_1788360844519

            else -> {
                // Consistent hash mapping to one of the 9 album arts
                val key = "$title-$artist"
                val hash = kotlin.math.abs(key.hashCode())
                ALBUM_ARTS[hash % ALBUM_ARTS.size]
            }
        }
    }
}
