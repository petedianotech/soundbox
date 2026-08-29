package com.example.data.local

import androidx.room.*
import com.example.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedTime DESC LIMIT 50")
    fun getMostPlayedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY lastPlayedTime DESC LIMIT 50")
    fun getRecentlyPlayedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC LIMIT 50")
    fun getRecentlyAddedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title ASC")
    fun getSongsByGenre(genre: String): Flow<List<Song>>

    @Query("SELECT DISTINCT genre FROM songs WHERE genre != '' AND genre != 'Unknown' ORDER BY genre ASC")
    fun getDistinctGenres(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE path = :path LIMIT 1")
    suspend fun getSongByPath(path: String): Song?

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: String): Song?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<Song>)

    @Update
    suspend fun updateSong(song: Song)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedTime = :timestamp WHERE id = :id")
    suspend fun incrementPlayCount(id: String, timestamp: Long)

    @Query("DELETE FROM songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongById(id: String)

    @Query("DELETE FROM songs WHERE id LIKE 'syn_%'")
    suspend fun deleteSyntheticSongs()

    @Query("DELETE FROM songs WHERE path NOT IN (:activePaths)")
    suspend fun deleteStaleSongs(activePaths: List<String>)
}
