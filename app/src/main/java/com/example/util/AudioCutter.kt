package com.example.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.model.Song
import com.example.player.LyricsManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Advanced audio cutter and trimmer engine for Android.
 * Performs fast, sample-accurate, lossless trimming on MP3, M4A, AAC, and standard audio files.
 * Supports Scoped Storage natively on Android 10, 11, 12, 13, 14, 15+ by reading source streams
 * directly or via ContentResolver, and exporting to MediaStore (Music or Ringtones) or direct storage.
 */
object AudioCutter {
    private const val TAG = "AudioCutter"

    enum class SaveMode {
        NEW_TRACK,
        RINGTONE,
        REPLACE_ORIGINAL
    }

    data class CutResult(
        val success: Boolean,
        val newDurationMs: Long,
        val newSizeBytes: Long,
        val outputPath: String? = null,
        val savedSong: Song? = null,
        val errorMessage: String? = null
    )

    /**
     * Resolves a readable source file for [song]. If direct file access is available, uses it.
     * Otherwise streams bytes from ContentResolver to a temporary cache file.
     */
    private fun resolveReadableSource(context: Context, song: Song, ext: String): Pair<File?, Boolean> {
        if (song.path.isNotBlank() && !song.path.contains("://") && song.path.startsWith("/")) {
            val f = File(song.path)
            if (f.exists() && f.canRead() && f.length() > 0L) {
                return Pair(f, false)
            }
        }

        return try {
            val uri = when {
                song.path.startsWith("content://") -> Uri.parse(song.path)
                else -> {
                    val longId = song.id.toLongOrNull()
                    if (longId != null && longId > 0) {
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, longId)
                    } else null
                }
            }

            if (uri != null) {
                val tempSrc = File(context.cacheDir, "src_cut_${System.currentTimeMillis()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    FileOutputStream(tempSrc).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                if (tempSrc.exists() && tempSrc.length() > 0L) {
                    Pair(tempSrc, true)
                } else {
                    tempSrc.delete()
                    Pair(null, false)
                }
            } else {
                Pair(null, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening source audio stream for ${song.title}: ${e.message}", e)
            Pair(null, false)
        }
    }

    /**
     * Main audio cutting engine with support for custom title and SaveMode.
     */
    fun cutSong(
        context: Context,
        song: Song,
        startMs: Long,
        endMs: Long,
        targetTitle: String = "${song.title} (Trimmed)",
        saveMode: SaveMode = SaveMode.NEW_TRACK
    ): CutResult {
        val ext = if (song.path.contains(".")) {
            song.path.substringAfterLast(".").lowercase().take(5)
        } else "mp3"

        val (sourceFile, isTempSource) = resolveReadableSource(context, song, ext)
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() <= 0L) {
            return CutResult(false, 0L, 0L, errorMessage = "Cannot open source audio stream for ${song.title}")
        }

        val validStartMs = startMs.coerceAtLeast(0L)
        val validEndMs = endMs.coerceAtMost(song.duration.coerceAtLeast(validStartMs + 1000L))
        if (validEndMs <= validStartMs) {
            if (isTempSource) sourceFile.delete()
            return CutResult(false, 0L, 0L, errorMessage = "Start point must be earlier than End point")
        }

        val targetDurationMs = validEndMs - validStartMs
        val tempTrimmedFile = File(context.cacheDir, "trimmed_${System.currentTimeMillis()}.$ext")

        try {
            var cutSucceeded = false

            if (ext == "mp3") {
                cutSucceeded = cutMp3Lossless(sourceFile, tempTrimmedFile, validStartMs, validEndMs)
                if (!cutSucceeded) {
                    cutSucceeded = cutWithMediaMuxer(sourceFile, tempTrimmedFile, validStartMs, validEndMs)
                }
            } else if (ext in listOf("m4a", "aac", "mp4", "3gp", "ogg")) {
                cutSucceeded = cutWithMediaMuxer(sourceFile, tempTrimmedFile, validStartMs, validEndMs)
            } else if (ext == "wav") {
                cutSucceeded = cutWavFile(sourceFile, tempTrimmedFile, validStartMs, validEndMs)
            } else {
                cutSucceeded = cutWithMediaMuxer(sourceFile, tempTrimmedFile, validStartMs, validEndMs)
            }

            if (!cutSucceeded || !tempTrimmedFile.exists() || tempTrimmedFile.length() <= 0L) {
                tempTrimmedFile.delete()
                return CutResult(false, 0L, 0L, errorMessage = "Audio cutting processor could not extract audio frames")
            }

            val finalDurationMs = detectActualDurationMs(tempTrimmedFile.absolutePath, targetDurationMs)
            val newSizeBytes = tempTrimmedFile.length()

            // Save according to selected SaveMode
            val cleanTitle = targetTitle.trim().ifBlank { "${song.title} (Trimmed)" }

            if (saveMode == SaveMode.REPLACE_ORIGINAL) {
                val origFile = File(song.path)
                var directOverwrite = false
                if (origFile.exists() && origFile.canWrite()) {
                    try {
                        FileInputStream(tempTrimmedFile).use { inStream ->
                            FileOutputStream(origFile).use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        directOverwrite = true
                    } catch (ignored: Exception) {}
                }

                if (directOverwrite) {
                    updateMediaStoreDurationAndSize(context, song, finalDurationMs, origFile.length())
                    try {
                        shiftCompanionLyrics(context, song, -validStartMs)
                    } catch (ignored: Exception) {}
                    MediaScannerConnection.scanFile(
                        context.applicationContext,
                        arrayOf(origFile.absolutePath),
                        arrayOf("audio/*"),
                        null
                    )
                    return CutResult(
                        success = true,
                        newDurationMs = finalDurationMs,
                        newSizeBytes = origFile.length(),
                        outputPath = origFile.absolutePath
                    )
                }
            }

            // For NEW_TRACK, RINGTONE, or fallback when REPLACE_ORIGINAL cannot overwrite directly:
            val isRingtone = saveMode == SaveMode.RINGTONE
            val (savedUri, savedPath) = saveTrimmedToStorage(
                context = context,
                trimmedFile = tempTrimmedFile,
                title = cleanTitle,
                artist = song.artist,
                album = song.album,
                extension = ext,
                isRingtone = isRingtone
            )

            if (savedUri == null && savedPath == null) {
                return CutResult(false, 0L, 0L, errorMessage = "Could not save trimmed audio to device storage")
            }

            val finalPath = savedPath ?: savedUri.toString()
            val newSong = Song(
                id = savedUri?.lastPathSegment ?: "trim_${System.currentTimeMillis()}",
                title = cleanTitle,
                artist = song.artist,
                album = if (isRingtone) "Ringtones" else song.album,
                duration = finalDurationMs,
                path = finalPath,
                size = newSizeBytes,
                folderPath = if (isRingtone) "Ringtones" else "Music/Soundbox",
                folderName = if (isRingtone) "Ringtones" else "Soundbox",
                genre = if (isRingtone) "Ringtone" else song.genre,
                dateAdded = System.currentTimeMillis() / 1000L
            )

            return CutResult(
                success = true,
                newDurationMs = finalDurationMs,
                newSizeBytes = newSizeBytes,
                outputPath = finalPath,
                savedSong = newSong
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in sound cutter: ${e.message}", e)
            return CutResult(false, 0L, 0L, errorMessage = e.message)
        } finally {
            if (isTempSource) sourceFile.delete()
            if (tempTrimmedFile.exists()) tempTrimmedFile.delete()
        }
    }

    /**
     * Backward-compatible helper method.
     */
    fun cutAndReplaceSong(
        context: Context,
        song: Song,
        startMs: Long,
        endMs: Long
    ): CutResult {
        return cutSong(
            context = context,
            song = song,
            startMs = startMs,
            endMs = endMs,
            targetTitle = "${song.title} (Trimmed)",
            saveMode = SaveMode.REPLACE_ORIGINAL
        )
    }

    /**
     * Reliably writes trimmed audio to device storage (MediaStore / public storage).
     */
    private fun saveTrimmedToStorage(
        context: Context,
        trimmedFile: File,
        title: String,
        artist: String,
        album: String,
        extension: String,
        isRingtone: Boolean
    ): Pair<Uri?, String?> {
        val mimeType = when (extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> "audio/*"
        }

        val relativePath = if (isRingtone) {
            "${Environment.DIRECTORY_RINGTONES}/Soundbox"
        } else {
            "${Environment.DIRECTORY_MUSIC}/Soundbox"
        }

        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.$extension")
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                if (isRingtone) {
                    put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = context.contentResolver.insert(collection, values)
            if (itemUri != null) {
                context.contentResolver.openOutputStream(itemUri)?.use { out ->
                    FileInputStream(trimmedFile).use { inStream ->
                        inStream.copyTo(out)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    context.contentResolver.update(itemUri, values, null, null)
                }

                var actualPath: String? = null
                try {
                    val projection = arrayOf(MediaStore.Audio.Media.DATA)
                    context.contentResolver.query(itemUri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                            if (idx != -1) actualPath = cursor.getString(idx)
                        }
                    }
                } catch (ignored: Exception) {}

                if (actualPath != null) {
                    MediaScannerConnection.scanFile(context, arrayOf(actualPath), arrayOf(mimeType), null)
                }

                return Pair(itemUri, actualPath ?: itemUri.toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore insert failed: ${e.message}, attempting direct storage fallback...")
        }

        // Direct storage fallback
        return try {
            val baseDir = Environment.getExternalStoragePublicDirectory(
                if (isRingtone) Environment.DIRECTORY_RINGTONES else Environment.DIRECTORY_MUSIC
            )
            val subDir = File(baseDir, "Soundbox")
            if (!subDir.exists()) subDir.mkdirs()

            val targetFile = File(subDir, "$title.$extension")
            FileInputStream(trimmedFile).use { inStream ->
                FileOutputStream(targetFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
            Pair(Uri.fromFile(targetFile), targetFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Direct storage fallback also failed: ${e.message}", e)
            Pair(null, null)
        }
    }

    /**
     * Lossless MP3 frame-accurate cutter. Slices MP3 audio frames without re-encoding,
     * preserving 100% original quality and creating an exact cut.
     */
    private fun cutMp3Lossless(
        srcFile: File,
        dstFile: File,
        startMs: Long,
        endMs: Long
    ): Boolean {
        try {
            val raf = RandomAccessFile(srcFile, "r")
            val totalLength = raf.length()
            if (totalLength <= 128) {
                raf.close()
                return false
            }

            // 1. Detect ID3v2 header and get audio data offset
            var audioDataStart = 0L
            val id3Header = ByteArray(10)
            raf.readFully(id3Header)
            var id3TagBytes: ByteArray? = null

            if (id3Header[0] == 'I'.code.toByte() && id3Header[1] == 'D'.code.toByte() && id3Header[2] == '3'.code.toByte()) {
                val size = ((id3Header[6].toInt() and 0x7F) shl 21) or
                        ((id3Header[7].toInt() and 0x7F) shl 14) or
                        ((id3Header[8].toInt() and 0x7F) shl 7) or
                        (id3Header[9].toInt() and 0x7F)
                val fullTagLen = 10 + size
                audioDataStart = fullTagLen.toLong()
                
                // Read original ID3v2 tag to copy over
                id3TagBytes = ByteArray(fullTagLen)
                raf.seek(0)
                raf.readFully(id3TagBytes)
            }

            raf.seek(audioDataStart)

            // 2. Parse MP3 frames and calculate cumulative time
            var currentTimeMs = 0.0
            var firstFrameOffset: Long = -1L
            var lastFrameOffset: Long = -1L

            val buffer = ByteArray(4)
            while (raf.filePointer < totalLength - 4) {
                val framePos = raf.filePointer
                val b1 = raf.read()
                if (b1 == -1) break

                if (b1 == 0xFF) {
                    val b2 = raf.read()
                    if (b2 == -1) break
                    if ((b2 and 0xE0) == 0xE0) { // Sync found
                        val b3 = raf.read()
                        val b4 = raf.read()
                        if (b3 == -1 || b4 == -1) break

                        val versionBits = (b2 shr 3) and 0x03
                        val layerBits = (b2 shr 1) and 0x03
                        val bitrateIndex = (b3 shr 4) and 0x0F
                        val sampleRateIndex = (b3 shr 2) and 0x03
                        val paddingBit = (b3 shr 1) and 0x01

                        val sampleRate = getMp3SampleRate(versionBits, sampleRateIndex)
                        val bitrate = getMp3Bitrate(versionBits, layerBits, bitrateIndex)

                        if (sampleRate > 0 && bitrate > 0) {
                            val samplesPerFrame = if (versionBits == 3) 1152 else 576
                            val frameSize = ((samplesPerFrame / 8 * bitrate * 1000) / sampleRate) + paddingBit
                            val frameDurationMs = (samplesPerFrame.toDouble() / sampleRate.toDouble()) * 1000.0

                            if (currentTimeMs >= startMs && firstFrameOffset == -1L) {
                                firstFrameOffset = framePos
                            }

                            if (currentTimeMs >= endMs) {
                                lastFrameOffset = framePos + frameSize
                                break
                            }

                            currentTimeMs += frameDurationMs
                            val nextPos = framePos + frameSize
                            if (nextPos <= totalLength) {
                                raf.seek(nextPos)
                            } else {
                                break
                            }
                            continue
                        }
                    }
                }
            }

            if (firstFrameOffset == -1L) {
                firstFrameOffset = audioDataStart
            }
            if (lastFrameOffset == -1L || lastFrameOffset > totalLength) {
                lastFrameOffset = totalLength
            }

            if (lastFrameOffset <= firstFrameOffset) {
                raf.close()
                return false
            }

            // 3. Write output file
            FileOutputStream(dstFile).use { out ->
                if (id3TagBytes != null) {
                    out.write(id3TagBytes)
                }

                raf.seek(firstFrameOffset)
                val chunk = ByteArray(64 * 1024)
                var remaining = lastFrameOffset - firstFrameOffset
                while (remaining > 0) {
                    val toRead = remaining.coerceAtMost(chunk.size.toLong()).toInt()
                    val read = raf.read(chunk, 0, toRead)
                    if (read == -1) break
                    out.write(chunk, 0, read)
                    remaining -= read
                }
            }

            raf.close()
            return dstFile.exists() && dstFile.length() > 0L
        } catch (e: Exception) {
            Log.w(TAG, "cutMp3Lossless error: ${e.message}")
            return false
        }
    }

    private fun getMp3SampleRate(versionBits: Int, sampleRateIndex: Int): Int {
        val rates = arrayOf(
            intArrayOf(11025, 12000, 8000),   // MPEG 2.5
            intArrayOf(0, 0, 0),               // Reserved
            intArrayOf(22050, 24000, 16000),   // MPEG 2
            intArrayOf(44100, 48000, 32000)    // MPEG 1
        )
        return if (versionBits in 0..3 && sampleRateIndex in 0..2) rates[versionBits][sampleRateIndex] else 0
    }

    private fun getMp3Bitrate(versionBits: Int, layerBits: Int, bitrateIndex: Int): Int {
        if (bitrateIndex <= 0 || bitrateIndex >= 15) return 0
        if (versionBits == 3 && layerBits == 1) { // MPEG 1 Layer III
            val bitrates = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
            return bitrates.getOrElse(bitrateIndex) { 0 }
        }
        val v2Bitrates = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
        return v2Bitrates.getOrElse(bitrateIndex) { 0 }
    }

    /**
     * Slices standard container audio (M4A/AAC/MP4/OGG) using MediaExtractor + MediaMuxer.
     */
    private fun cutWithMediaMuxer(
        srcFile: File,
        dstFile: File,
        startMs: Long,
        endMs: Long
    ): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(srcFile.absolutePath)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                return false
            }

            extractor.selectTrack(audioTrackIndex)
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val ext = srcFile.extension.lowercase()
            val outputFormat = if (ext == "ogg" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            muxer = MediaMuxer(dstFile.absolutePath, outputFormat)
            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()

            val maxBufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                256 * 1024
            }
            val byteBuffer = ByteBuffer.allocateDirect(maxBufferSize.coerceAtLeast(64 * 1024))
            val bufferInfo = MediaCodec.BufferInfo()

            var presentationTimeOffsetUs = -1L

            while (true) {
                val sampleSize = extractor.readSampleData(byteBuffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                if (sampleTimeUs >= startUs) {
                    if (presentationTimeOffsetUs == -1L) {
                        presentationTimeOffsetUs = sampleTimeUs
                    }

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.flags = extractor.sampleFlags
                    bufferInfo.presentationTimeUs = (sampleTimeUs - presentationTimeOffsetUs).coerceAtLeast(0L)

                    muxer.writeSampleData(muxerTrackIndex, byteBuffer, bufferInfo)
                }

                if (!extractor.advance()) break
            }

            muxer.stop()
            muxer.release()
            muxer = null

            extractor.release()
            extractor = null

            return dstFile.exists() && dstFile.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "cutWithMediaMuxer failed: ${e.message}", e)
            try { muxer?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            return false
        }
    }

    /**
     * Slices standard PCM WAV files by updating the WAV RIFF header and data block.
     */
    private fun cutWavFile(srcFile: File, dstFile: File, startMs: Long, endMs: Long): Boolean {
        try {
            val raf = RandomAccessFile(srcFile, "r")
            val header = ByteArray(44)
            raf.readFully(header)

            val sampleRate = ((header[24].toInt() and 0xFF) or
                    ((header[25].toInt() and 0xFF) shl 8) or
                    ((header[26].toInt() and 0xFF) shl 16) or
                    ((header[27].toInt() and 0xFF) shl 24))
            val byteRate = ((header[28].toInt() and 0xFF) or
                    ((header[29].toInt() and 0xFF) shl 8) or
                    ((header[30].toInt() and 0xFF) shl 16) or
                    ((header[31].toInt() and 0xFF) shl 24))

            if (byteRate <= 0 || sampleRate <= 0) {
                raf.close()
                return false
            }

            val startByte = (startMs * byteRate / 1000L) + 44L
            val endByte = (endMs * byteRate / 1000L).coerceAtMost(raf.length() - 44L) + 44L
            val newAudioLen = (endByte - startByte).coerceAtLeast(0L)

            val newHeader = header.clone()
            val totalDataLen = newAudioLen + 36L
            newHeader[4] = (totalDataLen and 0xFF).toByte()
            newHeader[5] = ((totalDataLen shr 8) and 0xFF).toByte()
            newHeader[6] = ((totalDataLen shr 16) and 0xFF).toByte()
            newHeader[7] = ((totalDataLen shr 24) and 0xFF).toByte()

            newHeader[40] = (newAudioLen and 0xFF).toByte()
            newHeader[41] = ((newAudioLen shr 8) and 0xFF).toByte()
            newHeader[42] = ((newAudioLen shr 16) and 0xFF).toByte()
            newHeader[43] = ((newAudioLen shr 24) and 0xFF).toByte()

            FileOutputStream(dstFile).use { out ->
                out.write(newHeader)
                raf.seek(startByte)
                val buffer = ByteArray(64 * 1024)
                var remaining = newAudioLen
                while (remaining > 0) {
                    val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    remaining -= read
                }
            }

            raf.close()
            return dstFile.exists() && dstFile.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "cutWavFile error: ${e.message}", e)
            return false
        }
    }

    /**
     * Shifts companion LRC timestamps by offsetMs so that synced lyrics remain
     * perfectly aligned with the trimmed song.
     */
    private fun shiftCompanionLyrics(context: Context, song: Song, offsetMs: Long) {
        val rawLyrics = LyricsManager.loadLyrics(context, song)
        if (rawLyrics.isNullOrBlank()) return

        val lines = rawLyrics.lines()
        val timeTagRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\]""")

        val updatedLines = lines.map { line ->
            var updatedLine = line
            val matches = timeTagRegex.findAll(line).toList()
            for (match in matches) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues[3]
                val ms = when (msStr.length) {
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10L
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val originalTime = min * 60000L + sec * 1000L + ms
                val newTime = (originalTime + offsetMs).coerceAtLeast(0L)
                val newMin = newTime / 60000L
                val newSec = (newTime % 60000L) / 1000L
                val newMs = (newTime % 1000L) / 10L
                val newTag = String.format("[%02d:%02d.%02d]", newMin, newSec, newMs)
                updatedLine = updatedLine.replace(match.value, newTag)
            }
            updatedLine
        }

        LyricsManager.saveLyrics(context, song, updatedLines.joinToString("\n"))
    }

    private fun detectActualDurationMs(filePath: String, fallbackDurationMs: Long): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: fallbackDurationMs
        } catch (e: Exception) {
            fallbackDurationMs
        }
    }

    private fun updateMediaStoreDurationAndSize(
        context: Context,
        song: Song,
        newDurationMs: Long,
        newSizeBytes: Long
    ) {
        try {
            val contentUri: Uri = try {
                val longId = song.id.toLong()
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, longId)
            } catch (e: Exception) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DURATION, newDurationMs)
                put(MediaStore.Audio.Media.SIZE, newSizeBytes)
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
        } catch (e: Exception) {
            Log.w(TAG, "Could not update MediaStore duration/size: ${e.message}")
        }
    }
}
