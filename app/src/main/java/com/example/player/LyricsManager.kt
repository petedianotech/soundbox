package com.example.player

import android.content.Context
import android.util.Log
import com.example.data.model.Song
import java.io.File
import java.io.IOException

object LyricsManager {
    private const val TAG = "LyricsManager"

    fun getLyricsFile(context: Context, song: Song): File {
        val dir = File(context.filesDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        // Default private app file
        return File(dir, "${song.id}.lrc")
    }

    /**
     * Tries to save the LRC file beside the music track if possible,
     * otherwise falls back to writing to the safe app private lyrics folder.
     */
    fun saveLyrics(context: Context, song: Song, lyricsText: String) {
        // 1. Try to save beside the audio file if it is a local path
        if (song.path.startsWith("/") && !song.path.contains("://")) {
            try {
                val audioFile = File(song.path)
                val parentDir = audioFile.parentFile
                if (parentDir != null && parentDir.exists() && parentDir.canWrite()) {
                    val lrcFileName = audioFile.nameWithoutExtension + ".lrc"
                    val besideLrcFile = File(parentDir, lrcFileName)
                    besideLrcFile.writeText(lyricsText)
                    Log.d(TAG, "Saved lyrics beside audio file successfully: ${besideLrcFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not save LRC beside audio file (Scoped Storage restriction or missing permission). Saving to app private storage.", e)
            }
        }

        // 2. Always write to private app directory as a reliable fallback/cache
        try {
            val privateFile = getLyricsFile(context, song)
            privateFile.writeText(lyricsText)
            Log.d(TAG, "Saved lyrics into private storage: ${privateFile.absolutePath}")
            
            // Also store a secondary identifier name for general backup searches (artist - title.lrc)
            val dir = File(context.filesDir, "lyrics")
            val cleanArtist = cleanStringForFileName(song.artist)
            val cleanTitle = cleanStringForFileName(song.title)
            if (cleanArtist.isNotBlank() && cleanTitle.isNotBlank()) {
                val backupFile = File(dir, "${cleanArtist}_${cleanTitle}.lrc")
                backupFile.writeText(lyricsText)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save lyrics in private storage", e)
        }
    }

    /**
     * Loads lyrics with robust auto-detection rules:
     * 1. Check same folder as audio file (e.g. SongTitle.lrc)
     * 2. Check app private lyrics folder by ID (song.id.lrc)
     * 3. Check app private lyrics folder matching of the format CleanArtist_CleanTitle.lrc
     */
    fun loadLyrics(context: Context, song: Song): String? {
        // 1. Check beside audio file
        if (song.path.startsWith("/") && !song.path.contains("://")) {
            try {
                val audioFile = File(song.path)
                val lrcFileName = audioFile.nameWithoutExtension + ".lrc"
                val besideFile = File(audioFile.parent, lrcFileName)
                if (besideFile.exists()) {
                    val content = besideFile.readText()
                    if (content.isNotBlank()) {
                        Log.d(TAG, "Auto-detected lyrics beside audio file: ${besideFile.absolutePath}")
                        return content
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed reading beside file (ignore on standard build): ${e.message}")
            }
        }

        // 2. Check private folder by ID
        val privateFile = getLyricsFile(context, song)
        if (privateFile.exists()) {
            val content = privateFile.readText()
            if (content.isNotBlank()) {
                return content
            }
        }

        // 3. Check private folder by Artist & Title
        try {
            val dir = File(context.filesDir, "lyrics")
            if (dir.exists()) {
                val cleanArtist = cleanStringForFileName(song.artist)
                val cleanTitle = cleanStringForFileName(song.title)
                if (cleanArtist.isNotBlank() && cleanTitle.isNotBlank()) {
                    val backupFile = File(dir, "${cleanArtist}_${cleanTitle}.lrc")
                    if (backupFile.exists()) {
                        val content = backupFile.readText()
                        if (content.isNotBlank()) {
                            Log.d(TAG, "Auto-detected lyrics by Artist & Title matching: ${backupFile.name}")
                            return content
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed during artist-title lookup fallback", e)
        }

        return null
    }

    /**
     * Clean strings for filename compatibility (no spaces or special chars)
     */
    private fun cleanStringForFileName(input: String): String {
        return input.lowercase()
            .replace("[^a-z0-9]".toRegex(), "")
            .trim()
    }

    /**
     * Builds standard search query for Google search or lyrics search APIs
     * Filters common video conversion tags, features, video years and formats.
     */
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
            "high quality", "hq", "hd", "1080p", "720p", "4k", "mp3", "mp4", "wav", "m4a", "clip officiel"
        )
        for (kw in videoKeywords) {
            s = s.replace("(?i)\\b$kw\\b".toRegex(), "")
        }

        // 3. Remove years commonly attached to video titles (e.g., 2026, 2025, etc.)
        s = s.replace("(?i)\\b202\\d\\b".toRegex(), "")
        s = s.replace("(?i)\\b203\\d\\b".toRegex(), "")

        // 4. Clean up featuring artist markers
        if (isArtist) {
            // In artist, truncate everything from "ft", "feat", etc. forward to keep search queries simple
            s = s.replace("(?i)\\b(ft|feat|featuring|with|and|&)\\s+.*".toRegex(), "")
        } else {
            // In titles, if "ft" / "feat" is in the middle with a divider, try to remove just the feat segment
            s = s.replace("(?i)\\b(ft|feat|featuring)\\s+[A-Za-z0-9'\\s]+(?=[_\\-])".toRegex(), "")
            s = s.replace("(?i)\\b(ft|feat|featuring|with)\\s+.*".toRegex(), "")
        }

        // 5. Clean common typos/names from video converters (e.g. "nakupeda" -> often converted from "nakupenda")
        if (s.contains("nakupeda", ignoreCase = true)) {
            s = s.replace("(?i)nakupeda".toRegex(), "nakupenda")
        }

        // 6. Replace dashes, underscores, slashes, commas, and other formatting noise with spaces
        s = s.replace("[_\\-,./|+=~#@$%^&*]".toRegex(), " ")

        return s
    }

    /**
     * Converts lyric lines to standard LRC contents: [mm:ss.xx] Lyric text
     */
    fun generateLrcContent(lines: List<Pair<Long, String>>): String {
        val sb = StringBuilder()
        for (line in lines) {
            val timeMs = line.first
            val text = line.second
            
            val totalSeconds = timeMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hundredths = (timeMs % 1000) / 10
            
            val timestamp = String.format("[%02d:%02d.%02d]", minutes, seconds, hundredths)
            sb.append(timestamp).append(text).append("\n")
        }
        return sb.toString()
    }
}
