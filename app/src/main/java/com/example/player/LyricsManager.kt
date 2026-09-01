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

    fun deleteLyrics(context: Context, song: Song) {
        try {
            val privateFile = getLyricsFile(context, song)
            if (privateFile.exists()) {
                privateFile.delete()
            }
            val dir = File(context.filesDir, "lyrics")
            val cleanArtist = cleanStringForFileName(song.artist)
            val cleanTitle = cleanStringForFileName(song.title)
            if (cleanArtist.isNotBlank() && cleanTitle.isNotBlank()) {
                val backupFile = File(dir, "${cleanArtist}_${cleanTitle}.lrc")
                if (backupFile.exists()) backupFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete lyrics", e)
        }
    }

    /**
     * Loads lyrics with robust auto-detection rules:
     * 1. Check beside audio file (SongName.lrc, SongName.txt, SongTitle.lrc)
     * 2. Check nearby lyrics/ directory beside audio file
     * 3. Check embedded ID3 lyrics in the audio file
     * 4. Check app private lyrics folder by ID (song.id.lrc)
     * 5. Check app private lyrics folder matching of the format CleanArtist_CleanTitle.lrc or CleanTitle.lrc
     */
    fun loadLyrics(context: Context, song: Song): String? {
        // 1. Check beside audio file (.lrc and .txt)
        if (song.path.startsWith("/") && !song.path.contains("://")) {
            try {
                val audioFile = File(song.path)
                val parent = audioFile.parentFile
                if (parent != null && parent.exists()) {
                    val nameWithoutExt = audioFile.nameWithoutExtension
                    val candidateNames = listOf(
                        "$nameWithoutExt.lrc",
                        "$nameWithoutExt.txt",
                        "${song.title}.lrc",
                        "${song.title}.txt",
                        "${song.artist} - ${song.title}.lrc",
                        "${song.artist} - ${song.title}.txt"
                    )
                    
                    for (candidate in candidateNames) {
                        val file = File(parent, candidate)
                        if (file.exists() && file.length() > 0) {
                            val content = file.readText()
                            if (content.isNotBlank()) {
                                Log.d(TAG, "Auto-detected lyrics beside audio file: ${file.absolutePath}")
                                return content
                            }
                        }
                    }

                    // Check subfolder "lyrics" or "Lyrics" beside audio file
                    val subLyricsDir = File(parent, "lyrics")
                    val subLyricsDirCap = File(parent, "Lyrics")
                    val lyricsFolders = listOfNotNull(
                        if (subLyricsDir.exists() && subLyricsDir.isDirectory) subLyricsDir else null,
                        if (subLyricsDirCap.exists() && subLyricsDirCap.isDirectory) subLyricsDirCap else null
                    )
                    for (dir in lyricsFolders) {
                        for (candidate in candidateNames) {
                            val file = File(dir, candidate)
                            if (file.exists() && file.length() > 0) {
                                val content = file.readText()
                                if (content.isNotBlank()) {
                                    Log.d(TAG, "Auto-detected lyrics in subfolder: ${file.absolutePath}")
                                    return content
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed reading beside file: ${e.message}")
            }
        }

        // 2. Check embedded lyrics via custom ID3 / metadata tag key
        try {
            val retriever = android.media.MediaMetadataRetriever()
            if (song.path.startsWith("content://")) {
                retriever.setDataSource(context, android.net.Uri.parse(song.path))
            } else {
                retriever.setDataSource(song.path)
            }
            // Key 1000 is used by FFmpeg/Android media frameworks for USLT/lyrics metadata
            val embeddedLyrics = retriever.extractMetadata(1000)
            retriever.release()
            if (!embeddedLyrics.isNullOrBlank()) {
                Log.d(TAG, "Auto-detected embedded lyrics in audio track metadata")
                return embeddedLyrics
            }
        } catch (e: Exception) {
            // Ignore retrieval errors for unsupported containers
        }

        // 3. Check private folder by ID
        val privateFile = getLyricsFile(context, song)
        if (privateFile.exists()) {
            val content = privateFile.readText()
            if (content.isNotBlank()) {
                return content
            }
        }

        // 4. Check private folder by Artist & Title or Title
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
                if (cleanTitle.isNotBlank()) {
                    val titleOnlyFile = File(dir, "${cleanTitle}.lrc")
                    if (titleOnlyFile.exists()) {
                        val content = titleOnlyFile.readText()
                        if (content.isNotBlank()) {
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

    /**
     * Builds an ultra-short, Google-optimized query: [Main Artist] [Core Word] lyrics
     * Ideal for Google Search (e.g., "Jason Derulo Jalebi lyrics")
     */
    fun buildCompactSearchQuery(song: Song): String {
        var mainArtist = song.artist
            .replace("(?i)\\b(x|&|feat|ft|featuring|with|,|and)\\b.*".toRegex(), "")
            .trim()
        if (mainArtist.isBlank()) mainArtist = song.artist

        var titleClean = song.title
            .replace("\\s*\\([^)]*\\)".toRegex(), "")
            .replace("\\s*\\[[^]]*\\]".toRegex(), "")
            .replace("(?i)\\b(official|music|video|audio|lyric|lyrics|remix|hd|4k|mp3|feat|ft)\\b".toRegex(), "")

        if (titleClean.contains("-")) {
            val parts = titleClean.split("-")
            titleClean = parts.lastOrNull { it.isNotBlank() } ?: parts[0]
        }
        if (titleClean.contains("|")) {
            titleClean = titleClean.substringBefore("|")
        }

        val words = titleClean.replace("[^a-zA-Z0-9\\s]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() && it.length > 1 }

        val shortTitle = if (words.size >= 2) "${words[0]} ${words[1]}" else words.firstOrNull() ?: song.title
        val queryArtist = cleanString(mainArtist, isArtist = true)
        return "$queryArtist $shortTitle lyrics".replace("\\s+".toRegex(), " ").trim()
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
