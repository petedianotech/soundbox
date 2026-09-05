package com.example.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.model.Song
import com.example.player.LyricsManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * High-performance, robust physical audio tag writer and MediaStore synchronizer.
 * Writes actual ID3v2 metadata frames directly into the physical audio file on storage,
 * updates Android MediaStore database records, and triggers system media scanning so
 * metadata remains permanent even when shared or transferred to other devices.
 */
object AudioTagWriter {
    private const val TAG = "AudioTagWriter"

    /**
     * Updates physical file tags, companion LRC lyrics, and MediaStore entry.
     */
    fun writeTags(
        context: Context,
        song: Song,
        newTitle: String? = null,
        newArtist: String? = null,
        newAlbum: String? = null,
        newGenre: String? = null,
        newTrackNumber: Int? = null,
        newLyrics: String? = null
    ): Boolean {
        val title = newTitle?.trim() ?: song.title
        val artist = newArtist?.trim() ?: song.artist
        val album = newAlbum?.trim() ?: song.album
        val genre = newGenre?.trim() ?: song.genre
        val track = newTrackNumber ?: song.trackNumber

        var physicalSuccess = false

        // 1. Write physical ID3 tags to the audio file if it is a local file
        if (song.path.startsWith("/") && !song.path.contains("://")) {
            val audioFile = File(song.path)
            if (audioFile.exists() && audioFile.canWrite()) {
                val ext = audioFile.extension.lowercase()
                if (ext == "mp3") {
                    physicalSuccess = writeMp3Id3v2Tags(
                        audioFile = audioFile,
                        title = title,
                        artist = artist,
                        album = album,
                        genre = genre,
                        track = track,
                        lyrics = newLyrics
                    )
                }
            } else {
                Log.w(TAG, "Audio file is not directly writable or does not exist: ${song.path}")
            }
        }

        // 2. Synchronize companion .lrc file if lyrics are provided or already exist
        try {
            val lyricsToSave = newLyrics ?: LyricsManager.loadLyrics(context, song)
            if (!lyricsToSave.isNullOrBlank()) {
                LyricsManager.saveLyrics(context, song.copy(title = title, artist = artist, album = album), lyricsToSave)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not update companion LRC: ${e.message}")
        }

        // 3. Update Android MediaStore records
        updateMediaStore(context, song, title, artist, album, genre, track)

        // 4. Force system MediaScanner refresh on the file
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(song.path),
                arrayOf("audio/*")
            ) { path, uri ->
                Log.d(TAG, "MediaScanner finished for $path -> $uri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaScanner trigger failed: ${e.message}")
        }

        return physicalSuccess
    }

    /**
     * Writes ID3v2.3 tag frames (TIT2, TPE1, TALB, TCON, TRCK, USLT) directly to MP3 file.
     * Uses atomic temporary file swapping to prevent any potential file corruption.
     */
    private fun writeMp3Id3v2Tags(
        audioFile: File,
        title: String,
        artist: String,
        album: String,
        genre: String,
        track: Int,
        lyrics: String?
    ): Boolean {
        return try {
            val tempFile = File(audioFile.parentFile, ".tmp_tag_${System.currentTimeMillis()}_${audioFile.name}")
            val id3TagBytes = buildId3v2Tag(title, artist, album, genre, track, lyrics)

            val originalLength = audioFile.length()
            if (originalLength <= 0) return false

            var audioDataStart = 0L
            val raf = RandomAccessFile(audioFile, "r")
            try {
                val header = ByteArray(10)
                raf.readFully(header)
                if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    // Existing ID3v2 tag present
                    val size = ((header[6].toInt() and 0x7F) shl 21) or
                            ((header[7].toInt() and 0x7F) shl 14) or
                            ((header[8].toInt() and 0x7F) shl 7) or
                            (header[9].toInt() and 0x7F)
                    audioDataStart = (10 + size).toLong()
                }
            } finally {
                raf.close()
            }

            FileOutputStream(tempFile).use { out ->
                // Write new ID3v2 tag at the very beginning
                out.write(id3TagBytes)

                // Copy remaining audio frames from original file
                FileInputStream(audioFile).use { input ->
                    if (audioDataStart > 0) {
                        input.skip(audioDataStart)
                    }
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
            }

            // Replace original file with updated file atomically
            val backupFile = File(audioFile.parentFile, ".bak_${audioFile.name}")
            if (backupFile.exists()) backupFile.delete()

            if (audioFile.renameTo(backupFile)) {
                if (tempFile.renameTo(audioFile)) {
                    backupFile.delete()
                    Log.d(TAG, "ID3v2 tags successfully written to: ${audioFile.absolutePath}")
                    true
                } else {
                    backupFile.renameTo(audioFile)
                    tempFile.delete()
                    false
                }
            } else {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing physical ID3v2 tags: ${e.message}", e)
            false
        }
    }

    /**
     * Builds ID3v2.3 tag binary header and frames.
     */
    private fun buildId3v2Tag(
        title: String,
        artist: String,
        album: String,
        genre: String,
        track: Int,
        lyrics: String?
    ): ByteArray {
        val framesOut = ByteArrayOutputStream()

        fun writeTextFrame(id: String, text: String) {
            if (text.isBlank()) return
            val framePayload = ByteArrayOutputStream()
            framePayload.write(0x01) // Encoding: UTF-16 with BOM
            val textBytes = text.toByteArray(Charset.forName("UTF-16"))
            framePayload.write(textBytes)

            val payload = framePayload.toByteArray()
            val frameHeader = ByteArray(10)
            System.arraycopy(id.toByteArray(Charsets.ISO_8859_1), 0, frameHeader, 0, 4)
            val len = payload.size
            frameHeader[4] = ((len shr 24) and 0xFF).toByte()
            frameHeader[5] = ((len shr 16) and 0xFF).toByte()
            frameHeader[6] = ((len shr 8) and 0xFF).toByte()
            frameHeader[7] = (len and 0xFF).toByte()
            frameHeader[8] = 0 // Flags
            frameHeader[9] = 0

            framesOut.write(frameHeader)
            framesOut.write(payload)
        }

        fun writeLyricsFrame(lyricsText: String) {
            if (lyricsText.isBlank()) return
            val framePayload = ByteArrayOutputStream()
            framePayload.write(0x01) // Encoding: UTF-16
            framePayload.write("eng".toByteArray(Charsets.ISO_8859_1)) // Language
            // Content descriptor (empty UTF-16 with BOM + null terminator)
            val emptyDesc = "\uFEFF".toByteArray(Charset.forName("UTF-16"))
            framePayload.write(emptyDesc)
            framePayload.write(0x00)
            framePayload.write(0x00)
            // Lyric text
            framePayload.write(lyricsText.toByteArray(Charset.forName("UTF-16")))

            val payload = framePayload.toByteArray()
            val frameHeader = ByteArray(10)
            System.arraycopy("USLT".toByteArray(Charsets.ISO_8859_1), 0, frameHeader, 0, 4)
            val len = payload.size
            frameHeader[4] = ((len shr 24) and 0xFF).toByte()
            frameHeader[5] = ((len shr 16) and 0xFF).toByte()
            frameHeader[6] = ((len shr 8) and 0xFF).toByte()
            frameHeader[7] = (len and 0xFF).toByte()
            frameHeader[8] = 0
            frameHeader[9] = 0

            framesOut.write(frameHeader)
            framesOut.write(payload)
        }

        writeTextFrame("TIT2", title)
        writeTextFrame("TPE1", artist)
        writeTextFrame("TALB", album)
        writeTextFrame("TCON", genre)
        if (track > 0) {
            writeTextFrame("TRCK", track.toString())
        }
        if (!lyrics.isNullOrBlank()) {
            writeLyricsFrame(lyrics)
        }

        val allFrames = framesOut.toByteArray()
        val tagSize = allFrames.size

        // ID3v2 10-byte header
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 3 // ID3v2.3
        header[4] = 0 // Revision
        header[5] = 0 // Flags

        // Size in 7-bit synchsafe integers
        header[6] = ((tagSize shr 21) and 0x7F).toByte()
        header[7] = ((tagSize shr 14) and 0x7F).toByte()
        header[8] = ((tagSize shr 7) and 0x7F).toByte()
        header[9] = (tagSize and 0x7F).toByte()

        val fullTag = ByteArrayOutputStream()
        fullTag.write(header)
        fullTag.write(allFrames)
        return fullTag.toByteArray()
    }

    /**
     * Updates MediaStore provider metadata entries.
     */
    private fun updateMediaStore(
        context: Context,
        song: Song,
        title: String,
        artist: String,
        album: String,
        genre: String,
        track: Int
    ) {
        try {
            val contentUri: Uri = try {
                val longId = song.id.toLong()
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, longId)
            } catch (e: Exception) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                if (track > 0) {
                    put(MediaStore.Audio.Media.TRACK, track)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.GENRE, genre)
                }
            }

            if (contentUri != MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) {
                context.contentResolver.update(contentUri, values, null, null)
            } else {
                context.contentResolver.update(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    values,
                    "${MediaStore.Audio.Media.DATA} = ?",
                    arrayOf(song.path)
                )
            }
            Log.d(TAG, "MediaStore metadata record updated for song: ${song.id}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not update MediaStore row: ${e.message}")
        }
    }
}
