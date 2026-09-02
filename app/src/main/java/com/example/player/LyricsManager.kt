package com.example.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.data.model.Song
import java.io.File
import java.io.IOException

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val isDynamic: Boolean = true,
    val durationMs: Long = 0L
)

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
                Log.w(TAG, "Could not save LRC beside audio file (Scoped Storage restriction). Saving to app private storage.", e)
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
     * Deletes saved lyrics files for a song.
     */
    fun deleteLyrics(context: Context, song: Song) {
        try {
            val privateFile = getLyricsFile(context, song)
            if (privateFile.exists()) privateFile.delete()

            val dir = File(context.filesDir, "lyrics")
            val cleanArtist = cleanStringForFileName(song.artist)
            val cleanTitle = cleanStringForFileName(song.title)
            if (cleanArtist.isNotBlank() && cleanTitle.isNotBlank()) {
                val backupFile = File(dir, "${cleanArtist}_${cleanTitle}.lrc")
                if (backupFile.exists()) backupFile.delete()
            }
            if (cleanTitle.isNotBlank()) {
                val titleOnlyFile = File(dir, "${cleanTitle}.lrc")
                if (titleOnlyFile.exists()) titleOnlyFile.delete()
            }
            // If beside file exists and is writable, clear or delete
            if (song.path.startsWith("/") && !song.path.contains("://")) {
                val audioFile = File(song.path)
                val parentDir = audioFile.parentFile
                if (parentDir != null && parentDir.exists()) {
                    val besideLrc = File(parentDir, audioFile.nameWithoutExtension + ".lrc")
                    if (besideLrc.exists() && besideLrc.canWrite()) {
                        besideLrc.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting lyrics for song", e)
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
     * Robust parser for LRC, ELRC, and plain text lyrics.
     * Supports:
     * - Standard LRC: [mm:ss.xx] or [mm:ss.xxx]
     * - Flexible timestamps: [m:ss], [mm:ss], [mm:ss:xx], [m:ss.xx]
     * - Multiple timestamps on same line: [00:12.34][01:45.67]Chorus line
     * - LRC header metadata tags: [offset:+500], [ar:Artist], [ti:Title], etc.
     * - Word tags cleanup: <00:12.34>
     */
    fun parseLyrics(content: String, trackDurationMs: Long = 0L): List<LyricLine> {
        if (content.isBlank()) return emptyList()

        val lines = content.lines()
        val parsedList = mutableListOf<LyricLine>()
        var globalOffsetMs = 0L

        // Regex to match timestamp tag: [mm:ss], [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx], [m:ss.xx], etc.
        val timeTagRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        // Regex for offset tag: [offset:+500] or [offset:-200]
        val offsetTagRegex = Regex("\\[offset:\\s*([+-]?\\d+)\\]", RegexOption.IGNORE_CASE)
        // Regex for meta headers
        val metaTagRegex = Regex("^\\[(ar|ti|al|by|length|re|ve|creator):.*\\]$", RegexOption.IGNORE_CASE)

        // 1. Scan for global offset tag
        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            val offsetMatch = offsetTagRegex.find(trimmed)
            if (offsetMatch != null) {
                globalOffsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
            }
        }

        var hasTimestamps = false

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            if (metaTagRegex.matches(trimmed) || offsetTagRegex.matches(trimmed)) continue

            val allTimeMatches = timeTagRegex.findAll(trimmed).toList()
            if (allTimeMatches.isNotEmpty()) {
                hasTimestamps = true
                // Clean the text from all timestamp tags
                var cleanText = trimmed.replace(timeTagRegex, "").trim()
                // Also clean up any word-by-word timestamp tags like <00:12.34>
                cleanText = cleanText.replace(Regex("<\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?>"), "").trim()

                for (match in allTimeMatches) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues[3]
                    val fractionMs = when (fractionStr.length) {
                        1 -> fractionStr.toLong() * 100
                        2 -> fractionStr.toLong() * 10
                        3 -> fractionStr.toLong()
                        else -> 0L
                    }

                    val rawTimeMs = (min * 60 * 1000) + (sec * 1000) + fractionMs + globalOffsetMs
                    val timeMs = rawTimeMs.coerceAtLeast(0L)
                    
                    // Display text or instrumental icon
                    val displayText = if (cleanText.isEmpty()) "🎵 Instrumental" else cleanText
                    parsedList.add(LyricLine(timeMs, displayText, isDynamic = true))
                }
            }
        }

        return if (hasTimestamps && parsedList.isNotEmpty()) {
            val sorted = parsedList.sortedBy { it.timeMs }
            // Compute duration of each line based on the next line's timestamp
            sorted.mapIndexed { index, line ->
                val nextTime = if (index < sorted.size - 1) sorted[index + 1].timeMs else trackDurationMs.coerceAtLeast(line.timeMs + 4000L)
                val lineDuration = (nextTime - line.timeMs).coerceAtLeast(1000L)
                line.copy(durationMs = lineDuration)
            }
        } else {
            // Plain text unsynced lyrics
            lines.filter { it.isNotBlank() && !metaTagRegex.matches(it.trim()) && !offsetTagRegex.matches(it.trim()) }.map { line ->
                LyricLine(timeMs = 0L, text = line.trim(), isDynamic = false)
            }
        }
    }

    /**
     * Shifts all timestamps in the song's lyrics by [offsetDeltaMs] (positive to delay, negative to advance)
     * and saves the updated content.
     */
    fun adjustLyricsOffset(context: Context, song: Song, offsetDeltaMs: Long): Boolean {
        val existing = loadLyrics(context, song) ?: return false
        val parsed = parseLyrics(existing)
        if (parsed.none { it.isDynamic }) return false

        val updatedLines = parsed.map { line ->
            val newTime = (line.timeMs + offsetDeltaMs).coerceAtLeast(0L)
            Pair(newTime, line.text)
        }
        val newContent = generateLrcContent(updatedLines)
        saveLyrics(context, song, newContent)
        return true
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
            s = s.replace("(?i)\\b(ft|feat|featuring|with|and|&)\\s+.*".toRegex(), "")
        } else {
            s = s.replace("(?i)\\b(ft|feat|featuring)\\s+[A-Za-z0-9'\\s]+(?=[_\\-])".toRegex(), "")
            s = s.replace("(?i)\\b(ft|feat|featuring|with)\\s+.*".toRegex(), "")
        }

        // 5. Clean common typos/names from video converters
        if (s.contains("nakupeda", ignoreCase = true)) {
            s = s.replace("(?i)nakupeda".toRegex(), "nakupenda")
        }

        // 6. Replace punctuation noise with spaces
        s = s.replace("[_\\-,./|+=~#@$%^&*]".toRegex(), " ")

        return s.trim()
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

    /**
     * Launches external web search or browser for song lyrics
     */
    fun searchLyricsWeb(context: Context, song: Song, provider: String = "google") {
        val query = buildSearchQuery(song)
        val url = when (provider.lowercase()) {
            "genius" -> "https://genius.com/search?q=${Uri.encode(query)}"
            "musixmatch" -> "https://www.musixmatch.com/search/${Uri.encode(query)}"
            "azlyrics" -> "https://search.azlyrics.com/search.php?q=${Uri.encode(query)}"
            else -> "https://www.google.com/search?q=${Uri.encode(query)}"
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copies lyrics to clipboard
     */
    fun copyLyricsToClipboard(context: Context, song: Song, content: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Lyrics for ${song.title}", content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Lyrics copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy lyrics", Toast.LENGTH_SHORT).show()
        }
    }
}

