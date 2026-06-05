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
        val cleanArtist = cleanString(song.artist, isArtist = true)
        val cleanTitle = cleanString(song.title, isArtist = false)
        return "$cleanArtist $cleanTitle lyrics".replace("\\s+".toRegex(), " ").trim()
    }

    private fun cleanString(input: String, isArtist: Boolean): String {
        var s = input

        // 1. Remove anything in parentheses (e.g., "(Official Video)") and brackets (e.g., "[Official Audio]")
        s = s.replace("\\s*\\([^)]*\\)".toRegex(), "")
        s = s.replace("\\s*\\[[^]]*\\]".toRegex(), "")

        // 2. Remove common video converter tags and features (case-insensitive)
        val videoKeywords = listOf(
            "official video", "official music video", "music video", "official audio", "audio only",
            "lyrics video", "lyric video", "lyrics", "lyric", "video", "visualizer", "official lyric video",
            "high quality", "hq", "hd", "1080p", "720p", "4k", "mp3", "mp4", "wav", "m4a", "clip officiel",
            "video 202\\d", "video 203\\d", "202\\d video", "203\\d video", "202\\d", "203\\d"
        )
        for (kw in videoKeywords) {
            s = s.replace("(?i)\\b$kw\\b".toRegex(), "")
        }

        // 3. Clean up featuring artist markers
        if (isArtist) {
            // In artist, truncate everything from "ft", "feat", etc. forward to keep search queries simple
            s = s.replace("(?i)\\b(ft|feat|featuring|with)\\s+.*".toRegex(), "")
        } else {
            // In titles, if "ft" / "feat" is in the middle with a divider, try to remove just the feat segment
            s = s.replace("(?i)\\b(ft|feat|featuring)\\s+[A-Za-z0-9'\\s]+(?=[_\\-])".toRegex(), "")
            s = s.replace("(?i)\\b(ft|feat|featuring|with)\\s+.*".toRegex(), "")
        }

        // 4. Clean common typography/typos from video converters (e.g. "nakupeda" -> often converted from "nakupenda")
        if (s.contains("nakupeda", ignoreCase = true)) {
            s = s.replace("(?i)nakupeda".toRegex(), "nakupenda")
        }

        // 5. Replace dashes, underscores, slashes, commas, and formatting noise with space
        s = s.replace("[_\\-,./|+=~#@$%^&*]".toRegex(), " ")

        return s
    }
}
