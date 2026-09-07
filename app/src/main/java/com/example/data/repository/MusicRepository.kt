package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.MusicDatabase
import com.example.data.model.Playlist
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(private val context: Context) {

    private val database = MusicDatabase.getInstance(context)
    private val songDao = database.songDao()
    private val playlistDao = database.playlistDao()

    // Exposed Flows
    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val favoriteSongs: Flow<List<Song>> = songDao.getFavoriteSongs()
    val mostPlayedSongs: Flow<List<Song>> = songDao.getMostPlayedSongs()
    val recentlyPlayedSongs: Flow<List<Song>> = songDao.getRecentlyPlayedSongs()
    val recentlyAddedSongs: Flow<List<Song>> = songDao.getRecentlyAddedSongs()
    val topRatedSongs: Flow<List<Song>> = songDao.getTopRatedSongs()
    val distinctGenres: Flow<List<String>> = songDao.getDistinctGenres()
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    companion object {
        private const val TAG = "MusicRepository"

        @Volatile
        private var INSTANCE: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = MusicRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun toggleFavorite(songId: String, isCurrentlyFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            songDao.updateFavoriteStatus(songId, !isCurrentlyFavorite)
        }
    }

    suspend fun updateRating(songId: String, rating: Int) {
        withContext(Dispatchers.IO) {
            songDao.updateRating(songId, rating.coerceIn(0, 5))
        }
    }

    suspend fun updateSongMetadata(song: Song, newLyrics: String? = null) {
        withContext(Dispatchers.IO) {
            // 1. Write actual ID3v2 tags directly into the audio file & update MediaStore
            com.example.util.AudioTagWriter.writeTags(
                context = context,
                song = song,
                newTitle = song.title,
                newArtist = song.artist,
                newAlbum = song.album,
                newGenre = song.genre,
                newTrackNumber = song.trackNumber,
                newLyrics = newLyrics
            )
            // 2. Persist in local Room database
            songDao.updateSong(song)
        }
    }

    /**
     * Cuts audio file from startMs to endMs, replaces physical file on storage,
     * adjusts companion .lrc lyrics, updates MediaStore and Room database.
     */
    suspend fun cutAndReplaceSong(
        song: Song,
        startMs: Long,
        endMs: Long
    ): com.example.util.AudioCutter.CutResult {
        return cutSong(
            song = song,
            startMs = startMs,
            endMs = endMs,
            targetTitle = "${song.title} (Trimmed)",
            saveMode = com.example.util.AudioCutter.SaveMode.REPLACE_ORIGINAL
        )
    }

    suspend fun cutSong(
        song: Song,
        startMs: Long,
        endMs: Long,
        targetTitle: String,
        saveMode: com.example.util.AudioCutter.SaveMode
    ): com.example.util.AudioCutter.CutResult {
        return withContext(Dispatchers.IO) {
            val result = com.example.util.AudioCutter.cutSong(
                context = context,
                song = song,
                startMs = startMs,
                endMs = endMs,
                targetTitle = targetTitle,
                saveMode = saveMode
            )
            if (result.success) {
                if (result.savedSong != null) {
                    songDao.insertSongs(listOf(result.savedSong))
                } else if (saveMode == com.example.util.AudioCutter.SaveMode.REPLACE_ORIGINAL) {
                    val updatedSong = song.copy(
                        duration = result.newDurationMs,
                        size = result.newSizeBytes
                    )
                    songDao.updateSong(updatedSong)
                }
            }
            result
        }
    }

    suspend fun updateSongsBatch(songs: List<Song>) {
        withContext(Dispatchers.IO) {
            // Write physical audio tags for all updated songs
            songs.forEach { song ->
                com.example.util.AudioTagWriter.writeTags(
                    context = context,
                    song = song,
                    newTitle = song.title,
                    newArtist = song.artist,
                    newAlbum = song.album,
                    newGenre = song.genre
                )
            }
            songDao.updateSongs(songs)
        }
    }

    data class DeleteResult(
        val success: Boolean,
        val intentSender: IntentSender? = null,
        val deletedSongIds: List<String> = emptyList(),
        val pendingSongIds: List<String> = emptyList()
    )

    /**
     * Completely and permanently deletes audio files from device physical storage,
     * deletes companion .lrc lyrics, removes records from Android MediaStore,
     * and removes entries from the local Room database.
     *
     * On Android 10+ / 11+ (API 30+), if the files require Scoped Storage user authorization,
     * this returns an IntentSender so the Android system deletion confirmation dialog
     * can be displayed to permanently purge the files from physical disk.
     */
    suspend fun deleteSongsPermanently(songs: List<Song>): DeleteResult {
        return withContext(Dispatchers.IO) {
            val successfullyDeletedIds = mutableListOf<String>()
            val urisRequiringConsent = mutableListOf<Uri>()
            val songsRequiringConsent = mutableListOf<Song>()

            for (song in songs) {
                var physicalDeleted = false
                val file = if (song.path.startsWith("/") && !song.path.contains("://")) File(song.path) else null

                // 1. Direct POSIX file deletion
                if (file != null && file.exists()) {
                    try {
                        physicalDeleted = file.delete()
                        if (!physicalDeleted && file.canWrite()) {
                            try {
                                java.io.RandomAccessFile(file, "rw").setLength(0)
                                physicalDeleted = file.delete()
                            } catch (ignored: Exception) {}
                        }
                    } catch (ignored: Exception) {}
                }

                // 2. Delete companion .lrc lyrics files
                try {
                    com.example.player.LyricsManager.deleteLyrics(context, song)
                } catch (ignored: Exception) {}

                // 3. Delete from Android MediaStore
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
                    try {
                        val rows = context.contentResolver.delete(uri, null, null)
                        if (rows > 0) physicalDeleted = true
                    } catch (secEx: SecurityException) {
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && secEx is android.app.RecoverableSecurityException) {
                            return@withContext DeleteResult(
                                success = false,
                                intentSender = secEx.userAction.actionIntent.intentSender,
                                deletedSongIds = successfullyDeletedIds,
                                pendingSongIds = listOf(song.id)
                            )
                        } else {
                            urisRequiringConsent.add(uri)
                            songsRequiringConsent.add(song)
                        }
                    } catch (ignored: Exception) {}
                }

                // Try query DATA delete
                try {
                    context.contentResolver.delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Audio.Media.DATA} = ?",
                        arrayOf(song.path)
                    )
                } catch (ignored: Exception) {}

                // Scan file to inform Android OS
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context.applicationContext,
                        arrayOf(song.path),
                        null,
                        null
                    )
                } catch (ignored: Exception) {}

                if (physicalDeleted || (file != null && !file.exists())) {
                    successfullyDeletedIds.add(song.id)
                }
            }

            // Android 11+ (API 30+) Scoped Storage consent request
            if (urisRequiringConsent.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, urisRequiringConsent)
                    if (successfullyDeletedIds.isNotEmpty()) {
                        songDao.deleteSongsByIds(successfullyDeletedIds)
                    }
                    return@withContext DeleteResult(
                        success = false,
                        intentSender = pendingIntent.intentSender,
                        deletedSongIds = successfullyDeletedIds,
                        pendingSongIds = songsRequiringConsent.map { it.id }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "createDeleteRequest error: ${e.message}")
                }
            }

            // Remove all processed songs from Room
            val allToDelete = songs.map { it.id }
            if (allToDelete.isNotEmpty()) {
                songDao.deleteSongsByIds(allToDelete)
            }

            DeleteResult(
                success = true,
                deletedSongIds = allToDelete
            )
        }
    }

    suspend fun deleteSongsFromDatabase(songIds: List<String>) {
        withContext(Dispatchers.IO) {
            songDao.deleteSongsByIds(songIds)
        }
    }

    suspend fun deleteSongCompletely(song: Song): Boolean {
        val result = deleteSongsPermanently(listOf(song))
        return result.success
    }

    suspend fun deleteSongsBatch(songIds: List<String>) {
        withContext(Dispatchers.IO) {
            val songs = songDao.getSongsByIds(songIds)
            if (songs.isNotEmpty()) {
                deleteSongsPermanently(songs)
            } else {
                songDao.deleteSongsByIds(songIds)
            }
        }
    }

    suspend fun deleteSongsBatchCompletely(songs: List<Song>): DeleteResult {
        return deleteSongsPermanently(songs)
    }

    suspend fun incrementPlayCount(songId: String) {
        withContext(Dispatchers.IO) {
            songDao.incrementPlayCount(songId, System.currentTimeMillis())
        }
    }

    suspend fun getSongById(songId: String): Song? {
        return withContext(Dispatchers.IO) {
            songDao.getSongById(songId)
        }
    }

    // Playlist Operations
    suspend fun createPlaylist(name: String) {
        withContext(Dispatchers.IO) {
            playlistDao.insertPlaylist(Playlist(name = name))
        }
    }

    suspend fun createPlaylistWithSongs(name: String, songIds: List<String>): Long {
        return withContext(Dispatchers.IO) {
            playlistDao.insertPlaylist(Playlist(name = name, songIds = songIds))
        }
    }

    suspend fun deletePlaylist(playlistId: Long) {
        withContext(Dispatchers.IO) {
            playlistDao.deletePlaylistById(playlistId)
        }
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: String) {
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext
            if (!playlist.songIds.contains(songId)) {
                val updatedIds = playlist.songIds.toMutableList().apply { add(songId) }
                playlistDao.updatePlaylist(playlist.copy(songIds = updatedIds))
            }
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext
            if (playlist.songIds.contains(songId)) {
                val updatedIds = playlist.songIds.toMutableList().apply { remove(songId) }
                playlistDao.updatePlaylist(playlist.copy(songIds = updatedIds))
            }
        }
    }

    // Scanning Device for Audio Files safely without clearing existing tracks
    suspend fun scanStorage(): Int {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting silent media scan...")
            val fetchedSongs = mutableListOf<Song>()

            val projection = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DATE_ADDED
            )

            val hasGenreColumn = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
            if (hasGenreColumn) {
                projection.add(MediaStore.Audio.Media.GENRE)
            }

            // Filtering for only valid music sounds
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
            val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            try {
                context.contentResolver.query(
                    queryUri,
                    projection.toTypedArray(),
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val genreCol = if (hasGenreColumn) cursor.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol).toString()
                        val title = cursor.getString(titleCol) ?: "Unknown Track"
                        val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                        val album = cursor.getString(albumCol) ?: "Unknown Album"
                        val duration = cursor.getLong(durationCol)
                        val path = cursor.getString(dataCol) ?: ""
                        val size = cursor.getLong(sizeCol)
                        val trackNumber = cursor.getInt(trackCol)
                        val dateAdded = cursor.getLong(dateAddedCol) * 1000 // Convert sec to ms

                        var genre = if (genreCol >= 0) cursor.getString(genreCol) ?: "" else ""
                        if (genre.isBlank() || genre.equals("Unknown", ignoreCase = true)) {
                            // Extract genre using lightweight fallback
                            try {
                                val retriever = android.media.MediaMetadataRetriever()
                                retriever.setDataSource(path)
                                val extracted = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)
                                retriever.release()
                                if (!extracted.isNullOrBlank()) {
                                    genre = extracted.trim()
                                }
                            } catch (ignored: Exception) {}
                        }

                        if (genre.isBlank()) {
                            genre = "Music"
                        }

                        val file = File(path)
                        val folderPath = file.parent ?: "/storage/emulated/0/Music"
                        val folderName = file.parentFile?.name ?: "Music"

                        fetchedSongs.add(
                            Song(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                path = path,
                                size = size,
                                folderPath = folderPath,
                                folderName = folderName,
                                trackNumber = trackNumber,
                                genre = genre,
                                isFavorite = false,
                                dateAdded = dateAdded
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying MediaStore: ${e.message}", e)
            }

            Log.d(TAG, "Scan completed: Found ${fetchedSongs.size} songs on disk.")

            var newSongsCount = 0
            if (fetchedSongs.isNotEmpty()) {
                val existingIds = songDao.getAllSongIds().toSet()
                val newSongs = fetchedSongs.filter { it.id !in existingIds }
                newSongsCount = newSongs.size

                // Insert only active tracks
                songDao.insertSongs(fetchedSongs)

                // Clean up songs that are no longer present on physical storage
                val paths = fetchedSongs.map { it.path }
                songDao.deleteStaleSongs(paths)
            }
            // CRITICAL: If fetchedSongs is empty (e.g. MediaStore query returned 0 during app resume or test),
            // NEVER clear the database! Keep existing cached songs so the app never loses its library.

            newSongsCount
        }
    }
}
