package com.example.player

import android.content.Context
import com.example.data.model.Song
import java.io.File

object LyricsManager {
    fun getLyricsFile(context: Context, song: Song): File {
        val dir = File(context.filesDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${song.id}.txt")
    }

    fun saveLyrics(context: Context, song: Song, lyricsText: String) {
        val file = getLyricsFile(context, song)
        file.writeText(lyricsText)
    }

    fun loadLyrics(context: Context, song: Song): String? {
        val file = getLyricsFile(context, song)
        return if (file.exists()) file.readText() else null
    }

    fun buildSearchQuery(song: Song): String {
        return "${song.artist} ${song.title} lyrics".trim()
    }
}
