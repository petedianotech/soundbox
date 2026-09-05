package com.example.data.repository

import android.content.ContentUris
import android.content.Context
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
        return withContext(Dispatchers.IO) {
            val result = com.example.util.AudioCutter.cutAndReplaceSong(
                context = context,
                song = song,
                startMs = startMs,
                endMs = endMs
            )
            if (result.success) {
                val updatedSong = song.copy(
                    duration = result.newDurationMs,
                    size = result.newSizeBytes
                )
                songDao.updateSong(updatedSong)
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

    /**
     * Completely deletes audio files from device physical storage, deletes companion .lrc
     * lyrics, removes records from Android MediaStore, and removes from Room database.
     */
    suspend fun deleteSongCompletely(song: Song): Boolean {
        return withContext(Dispatchers.IO) {
            var physicalDeleted = false
            try {
                // 1. Delete physical audio file
                if (song.path.startsWith("/") && !song.path.contains("://")) {
                    val file = File(song.path)
                    if (file.exists()) {
                        physicalDeleted = file.delete()
                        Log.d(TAG, "Physical file deletion: ${file.absolutePath}, success=$physicalDeleted")
                    }
                }

                // 2. Delete companion .lrc lyrics files
                com.example.player.LyricsManager.deleteLyrics(context, song)

                // 3. Delete from Android MediaStore
                try {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        song.id.toLongOrNull() ?: 0L
                    )
                    context.contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    try {
                        context.contentResolver.delete(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            "${MediaStore.Audio.Media.DATA} = ?",
                            arrayOf(song.path)
                        )
                    } catch (ignored: Exception) {}
                }

                // 4. Force MediaScanner refresh
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context.applicationContext,
                        arrayOf(song.path),
                        null,
                        null
                    )
                } catch (ignored: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "Error during complete file deletion: ${e.message}", e)
            }

            // 5. Delete from Room database
            songDao.deleteSongsByIds(listOf(song.id))
            true
        }
    }

    suspend fun deleteSongsBatch(songIds: List<String>) {
        withContext(Dispatchers.IO) {
            songDao.deleteSongsByIds(songIds)
        }
    }

    suspend fun deleteSongsBatchCompletely(songs: List<Song>) {
        withContext(Dispatchers.IO) {
            songs.forEach { song ->
                deleteSongCompletely(song)
            }
        }
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
