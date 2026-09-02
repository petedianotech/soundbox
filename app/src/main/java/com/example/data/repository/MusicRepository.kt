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

    suspend fun updateSongMetadata(song: Song) {
        withContext(Dispatchers.IO) {
            songDao.updateSong(song)
        }
    }

    suspend fun deleteSong(song: Song): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (song.path.startsWith("content://")) {
                    context.contentResolver.delete(android.net.Uri.parse(song.path), null, null)
                } else {
                    val file = File(song.path)
                    if (file.exists()) {
                        file.delete()
                    }
                    val songIdLong = song.id.toLongOrNull()
                    if (songIdLong != null) {
                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songIdLong)
                        context.contentResolver.delete(uri, null, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting physical file for song ${song.title}: ${e.message}")
            }

            // Remove from room cache database
            songDao.deleteSongById(song.id)
            true
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

    // Scanning Device for Audio Files
    suspend fun scanStorage() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting media scan...")
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

            // Filtering for only music sounds
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

            // Delete any legacy synthetic/placeholder songs
            songDao.deleteSyntheticSongs()

            if (fetchedSongs.isNotEmpty()) {
                // Save physical songs to cache db
                songDao.insertSongs(fetchedSongs)
                
                // Clean up songs that are no longer present on storage
                val paths = fetchedSongs.map { it.path }
                songDao.deleteStaleSongs(paths)
            } else {
                // Clear any cached songs if storage is empty
                songDao.clearAllSongs()
            }
        }
    }
}
