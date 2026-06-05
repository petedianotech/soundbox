package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Playlist
import com.example.data.model.Song
import com.example.data.repository.MusicRepository
import com.example.player.PlaybackManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository.getInstance(application)
    private val playbackManager = PlaybackManager.getInstance(application)

    // Player state mapping
    val currentSong: StateFlow<Song?> = playbackManager.currentSong
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val currentPosition: StateFlow<Long> = playbackManager.currentPosition
    val duration: StateFlow<Long> = playbackManager.duration
    val shuffleMode: StateFlow<Boolean> = playbackManager.shuffleMode
    val repeatMode: StateFlow<Int> = playbackManager.repeatMode
    val playbackSpeed: StateFlow<Float> = playbackManager.playbackSpeed
    val playbackPitch: StateFlow<Float> = playbackManager.playbackPitch
    val queue: StateFlow<List<Song>> = playbackManager.queue
    val sleepTimerMillis: StateFlow<Long> = playbackManager.sleepTimerMillis
    val equalizerEnabled: StateFlow<Boolean> = playbackManager.equalizerEnabled
    val bassBoostStrength: StateFlow<Int> = playbackManager.bassBoostStrength

    // Scanning states
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Core dataset flows
    val allSongs: StateFlow<List<Song>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedSongs: StateFlow<List<Song>> = repository.mostPlayedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedSongs: StateFlow<List<Song>> = repository.recentlyPlayedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI grouping states (Folder-based navigation, album grouping, and artist metadata projection)
    val folderList: StateFlow<Map<String, List<Song>>> = repository.allSongs
        .map { songs -> songs.groupBy { it.folderPath } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val albumList: StateFlow<Map<String, List<Song>>> = repository.allSongs
        .map { songs -> songs.groupBy { it.album } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val artistList: StateFlow<Map<String, List<Song>>> = repository.allSongs
        .map { songs -> songs.groupBy { it.artist } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val genreList: StateFlow<Map<String, List<Song>>> = repository.allSongs
        .map { songs -> songs.groupBy { it.genre } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // Run first local scan to populate music database
        scanStorage()
    }

    fun scanStorage() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repository.scanStorage()
            } catch (e: Exception) {
                // Squelch and handle scan anomalies
            } finally {
                _isScanning.value = false
            }
        }
    }

    // Playback Commands
    fun playSong(song: Song, customQueue: List<Song> = emptyList()) {
        playbackManager.playSong(song, customQueue)
    }

    fun playNext(song: Song) = playbackManager.playNext(song)
    fun addToQueue(song: Song) = playbackManager.addToQueue(song)
    fun removeFromQueue(index: Int) = playbackManager.removeFromQueue(index)
    fun clearQueue() = playbackManager.clearQueue()
    
    fun playPause() = playbackManager.playPause()
    fun skipNext() = playbackManager.skipNext()
    fun skipPrevious() = playbackManager.skipPrevious()
    fun seekTo(position: Long) = playbackManager.seekTo(position)
    fun setShuffleMode(enabled: Boolean) = playbackManager.setShuffleMode(enabled)
    fun setRepeatMode(mode: Int) = playbackManager.setRepeatMode(mode)
    fun setPlaybackRate(speed: Float, pitch: Float) = playbackManager.setPlaybackRate(speed, pitch)
    
    fun toggleEqualizer() = playbackManager.toggleEqualizer()
    fun setBassBoost(strength: Int) = playbackManager.setBassBoost(strength)
    fun startSleepTimer(minutes: Int) = playbackManager.startSleepTimer(minutes)
    fun stopSleepTimer() = playbackManager.stopSleepTimer()

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song.id, song.isFavorite)
        }
    }

    // Playlist Commands
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }
}
