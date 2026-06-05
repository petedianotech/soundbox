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

    // Playlist Operations
    suspend fun createPlaylist(name: String) {
        withContext(Dispatchers.IO) {
            playlistDao.insertPlaylist(Playlist(name = name))
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

            val projection = arrayOf(
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

            // Filtering for only music sounds
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"

            val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            try {
                context.contentResolver.query(
                    queryUri,
                    projection,
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

            if (fetchedSongs.isEmpty()) {
                Log.d(TAG, "MediaStore is empty. Seeding with high-quality offline synthetic loop streams.")
                populateWithSyntheticSongs()
            } else {
                // Save physical songs to cache db
                songDao.insertSongs(fetchedSongs)
                
                // Clean up songs that are no longer present on storage
                val paths = fetchedSongs.map { it.path }
                if (paths.isNotEmpty()) {
                    songDao.deleteStaleSongs(paths)
                }
            }
        }
    }

    private suspend fun populateWithSyntheticSongs() {
        val synthetics = listOf(
            Song(
                id = "syn_1",
                title = "Acoustic Horizon (Test Loop)",
                artist = "Soundbox Studio",
                album = "Cosmic Slate Ambient",
                duration = 241000L,
                path = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                size = 4050000L,
                folderPath = "/storage/emulated/0/Music/Soundbox Demo",
                folderName = "Soundbox Demo",
                trackNumber = 1,
                genre = "Acoustic",
                isFavorite = false
            ),
            Song(
                id = "syn_2",
                title = "Neon Nights (Electric Drive)",
                artist = "Retro Synthwave",
                album = "Vapor Trails",
                duration = 312000L,
                path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                size = 5300000L,
                folderPath = "/storage/emulated/0/Music/Soundbox Demo/Synthwave",
                folderName = "Synthwave",
                trackNumber = 2,
                genre = "Synthwave",
                isFavorite = false
            ),
            Song(
                id = "syn_3",
                title = "Chill Lofi Guitar Breeze",
                artist = "Binaural Beats",
                album = "Coffee & Rain",
                duration = 210000L,
                path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                size = 3500000L,
                folderPath = "/storage/emulated/0/Music/Lofi",
                folderName = "Lofi",
                trackNumber = 3,
                genre = "Lofi",
                isFavorite = false
            ),
            Song(
                id = "syn_4",
                title = "Midnight Odyssey Soundscapes",
                artist = "Cosmo Dust Engine",
                album = "Galactic Echoes",
                duration = 285000L,
                path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                size = 4800000L,
                folderPath = "/storage/emulated/0/Music/Ambient Cosmic",
                folderName = "Ambient Cosmic",
                trackNumber = 4,
                genre = "Ambient",
                isFavorite = false
            )
        )
        songDao.insertSongs(synthetics)
    }
}
