package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.Song
import com.example.player.LyricsManager
import java.io.File
import java.util.ArrayList

/**
 * Robust sharing helper to share songs together with their synchronized .lrc lyrics file,
 * so recipients on other devices, apps, or platforms receive both the music track and the lyrics.
 */
object ShareHelper {
    private const val TAG = "ShareHelper"

    fun shareSongWithLyrics(context: Context, song: Song) {
        try {
            val audioFile = File(song.path)
            val urisToShare = ArrayList<Uri>()
            val authority = "${context.packageName}.fileprovider"

            var audioUri: Uri? = null
            if (audioFile.exists() && audioFile.canRead()) {
                audioUri = FileProvider.getUriForFile(context, authority, audioFile)
                urisToShare.add(audioUri)
            } else {
                // If direct file access not available, fallback to MediaStore Uri
                try {
                    val mediaUri = Uri.parse("content://media/external/audio/media/${song.id}")
                    urisToShare.add(mediaUri)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not obtain media URI: ${e.message}")
                }
            }

            // Locate or generate .lrc companion file
            var lrcFile: File? = null

            // 1. Check beside audio file
            if (audioFile.exists()) {
                val besideLrc = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
                if (besideLrc.exists() && besideLrc.length() > 0) {
                    lrcFile = besideLrc
                }
            }

            // 2. Check private app storage
            if (lrcFile == null) {
                val privateLrc = LyricsManager.getLyricsFile(context, song)
                if (privateLrc.exists() && privateLrc.length() > 0) {
                    lrcFile = privateLrc
                }
            }

            // 3. If lyrics text is available but no disk file, generate temporary .lrc in cache
            if (lrcFile == null) {
                val lyricsContent = LyricsManager.loadLyrics(context, song)
                if (!lyricsContent.isNullOrBlank()) {
                    val cacheDir = File(context.cacheDir, "shared_lyrics")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    val cleanFileName = cleanFileName("${song.artist} - ${song.title}.lrc")
                    val tempLrc = File(cacheDir, cleanFileName)
                    tempLrc.writeText(lyricsContent)
                    lrcFile = tempLrc
                }
            }

            if (lrcFile != null && lrcFile.exists()) {
                try {
                    val lrcUri = FileProvider.getUriForFile(context, authority, lrcFile)
                    urisToShare.add(lrcUri)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not generate FileProvider URI for LRC file: ${e.message}")
                }
            }

            if (urisToShare.isEmpty()) {
                Toast.makeText(context, "Audio file is not accessible for sharing", Toast.LENGTH_SHORT).show()
                return
            }

            val shareIntent = if (urisToShare.size > 1) {
                // Share both audio file AND synchronized LRC lyrics
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare)
                    putExtra(Intent.EXTRA_SUBJECT, "${song.title} - ${song.artist}")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "🎵 ${song.title} • ${song.artist}\n(Audio track + Synchronized .LRC Lyrics enclosed)"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                // Share single audio file
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, urisToShare[0])
                    putExtra(Intent.EXTRA_SUBJECT, "${song.title} - ${song.artist}")
                    putExtra(Intent.EXTRA_TEXT, "🎵 ${song.title} • ${song.artist}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(
                shareIntent,
                if (urisToShare.size > 1) "Share Track & .LRC Lyrics" else "Share Track"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing song: ${e.message}", e)
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanFileName(name: String): String {
        return name.replace("[^a-zA-Z0-9.\\-_ ]".toRegex(), "_").trim()
    }
}
