package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Playlist
import com.example.data.model.SmartPlaylist
import com.example.data.model.SmartPlaylistType
import com.example.data.model.Song
import com.example.data.repository.MusicRepository
import com.example.player.PlaybackManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.util.SettingsManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository.getInstance(application)
    private val playbackManager = PlaybackManager.getInstance(application)
    
    val settingsManager = SettingsManager(application)

    val searchHistory: StateFlow<List<String>> = settingsManager.searchHistoryFlow

    fun addSearchQuery(query: String) {
        settingsManager.addSearchQuery(query)
    }

    fun removeSearchQuery(query: String) {
        settingsManager.removeSearchQuery(query)
    }

    fun clearSearchHistory() {
        settingsManager.clearSearchHistory()
    }

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
    val equalizerPreset: StateFlow<String> = playbackManager.equalizerPreset
    val equalizerBands: StateFlow<List<Int>> = playbackManager.equalizerBands
    val bassBoostStrength: StateFlow<Int> = playbackManager.bassBoostStrength
    val virtualizerStrength: StateFlow<Int> = playbackManager.virtualizerStrength
    val crossfadeSeconds: StateFlow<Int> = playbackManager.crossfadeSeconds
    val audioSessionId: StateFlow<Int> = playbackManager.audioSessionId

    val globalThumbnailIndex: StateFlow<Int> = settingsManager.globalThumbnailIndexFlow
    val showSpectrum: StateFlow<Boolean> = settingsManager.showSpectrumFlow
    val dynamicArtworkColors: StateFlow<Boolean> = settingsManager.dynamicArtworkColorsFlow
    val songThumbnailMap: StateFlow<Map<String, Int>> = settingsManager.songThumbnailMapFlow

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    enum class SortOrder {
        A_TO_Z, Z_TO_A, DATE_ADDED, DURATION
    }

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    val allSongs: StateFlow<List<Song>> = combine(repository.allSongs, _sortOrder) { songs, order ->
        when (order) {
            SortOrder.A_TO_Z -> songs.sortedBy { it.title.lowercase() }
            SortOrder.Z_TO_A -> songs.sortedByDescending { it.title.lowercase() }
            SortOrder.DATE_ADDED -> songs.sortedByDescending { it.dateAdded }
            SortOrder.DURATION -> songs.sortedByDescending { it.duration }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedSongs: StateFlow<List<Song>> = repository.mostPlayedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedSongs: StateFlow<List<Song>> = repository.recentlyPlayedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAddedSongs: StateFlow<List<Song>> = repository.recentlyAddedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val smartPlaylists: StateFlow<List<SmartPlaylist>> = combine(
        repository.allSongs,
        repository.favoriteSongs,
        repository.mostPlayedSongs,
        repository.recentlyPlayedSongs,
        repository.recentlyAddedSongs
    ) { all, favs, mostPlayed, recentRuns, recentAdded ->
        val list = mutableListOf<SmartPlaylist>()

        list.add(
            SmartPlaylist(
                id = "smart_favorites",
                type = SmartPlaylistType.FAVORITES,
                title = "Liked Songs",
                description = if (favs.isNotEmpty()) "${favs.size} tracks you liked" else "No liked tracks yet",
                icon = Icons.Default.Favorite,
                tintColor = Color(0xFFE91E63),
                songs = favs
            )
        )

        val playedOnly = mostPlayed.filter { it.playCount > 0 }
        if (playedOnly.isNotEmpty()) {
            list.add(
                SmartPlaylist(
                    id = "smart_most_played",
                    type = SmartPlaylistType.MOST_PLAYED,
                    title = "Most Played",
                    description = "Your top listened tracks",
                    icon = Icons.Default.Whatshot,
                    tintColor = Color(0xFFFF9800),
                    songs = playedOnly
                )
            )
        }

        if (recentAdded.isNotEmpty()) {
            list.add(
                SmartPlaylist(
                    id = "smart_recently_added",
                    type = SmartPlaylistType.RECENTLY_ADDED,
                    title = "Recently Added",
                    description = "Newest music added to library",
                    icon = Icons.Default.NewReleases,
                    tintColor = Color(0xFF00ACC1),
                    songs = recentAdded
                )
            )
        }

        val recentPlayedOnly = recentRuns.filter { it.lastPlayedTime > 0 }
        if (recentPlayedOnly.isNotEmpty()) {
            list.add(
                SmartPlaylist(
                    id = "smart_recently_played",
                    type = SmartPlaylistType.RECENTLY_PLAYED,
                    title = "Recently Played",
                    description = "Tracks you listened to recently",
                    icon = Icons.Default.History,
                    tintColor = Color(0xFF5C6BC0),
                    songs = recentPlayedOnly
                )
            )
        }

        val longTracks = all.filter { it.duration >= 300_000 }
        if (longTracks.isNotEmpty()) {
            list.add(
                SmartPlaylist(
                    id = "smart_long_tracks",
                    type = SmartPlaylistType.LONG_TRACKS,
                    title = "Long Mixes",
                    description = "Tracks longer than 5 minutes",
                    icon = Icons.Default.Timer,
                    tintColor = Color(0xFF8E24AA),
                    songs = longTracks
                )
            )
        }

        val quickTracks = all.filter { it.duration in 10_000..180_000 }
        if (quickTracks.isNotEmpty()) {
            list.add(
                SmartPlaylist(
                    id = "smart_quick_tracks",
                    type = SmartPlaylistType.SHORT_TRACKS,
                    title = "Quick Hits",
                    description = "Short tracks under 3 minutes",
                    icon = Icons.Default.Bolt,
                    tintColor = Color(0xFFFDD835),
                    songs = quickTracks
                )
            )
        }

        val unplayed = all.filter { it.playCount == 0 }
        if (unplayed.isNotEmpty() && unplayed.size != all.size) {
            list.add(
                SmartPlaylist(
                    id = "smart_unplayed",
                    type = SmartPlaylistType.NEVER_PLAYED,
                    title = "Forgotten Gems",
                    description = "Songs you haven't played yet",
                    icon = Icons.Default.AutoAwesome,
                    tintColor = Color(0xFF43A047),
                    songs = unplayed
                )
            )
        }

        val genreGroups = all.groupBy { it.genre }.filter { it.key.isNotBlank() && !it.key.equals("Unknown", ignoreCase = true) && !it.key.equals("Music", ignoreCase = true) }
        val genrePalette = listOf(
            Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
            Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF009688),
            Color(0xFF4CAF50), Color(0xFFFF5722), Color(0xFF795548)
        )
        var colorIdx = 0
        genreGroups.forEach { (genreName, genreSongs) ->
            if (genreSongs.isNotEmpty()) {
                val color = genrePalette[colorIdx % genrePalette.size]
                colorIdx++
                list.add(
                    SmartPlaylist(
                        id = "smart_genre_${genreName.lowercase().replace(" ", "_")}",
                        type = SmartPlaylistType.GENRE,
                        title = "$genreName Radio",
                        description = "All $genreName tracks in library",
                        icon = Icons.Default.Category,
                        tintColor = color,
                        songs = genreSongs,
                        genreName = genreName
                    )
                )
            }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        scanStorage()
    }

    fun scanStorage() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repository.scanStorage()
            } catch (e: Exception) {
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun playSong(song: Song, customQueue: List<Song> = emptyList()) {
        playbackManager.playSong(song, customQueue)
    }

    fun playNext(song: Song) = playbackManager.playNext(song)
    fun addToQueue(song: Song) = playbackManager.addToQueue(song)
    fun removeFromQueue(index: Int) = playbackManager.removeFromQueue(index)
    fun removeFromQueue(song: Song) {
        val idx = queue.value.indexOfFirst { it.id == song.id }
        if (idx >= 0) playbackManager.removeFromQueue(idx)
    }
    fun clearQueue() = playbackManager.clearQueue()
    
    fun playPause() = playbackManager.playPause()
    fun skipNext() = playbackManager.skipNext()
    fun skipPrevious() = playbackManager.skipPrevious()
    fun seekTo(position: Long) = playbackManager.seekTo(position)
    fun setShuffleMode(enabled: Boolean) = playbackManager.setShuffleMode(enabled)
    fun toggleShuffle() = playbackManager.setShuffleMode(!shuffleMode.value)
    fun setRepeatMode(mode: Int) = playbackManager.setRepeatMode(mode)
    fun toggleRepeatMode() {
        val next = when (repeatMode.value) {
            androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
            androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
        }
        playbackManager.setRepeatMode(next)
    }
    fun setPlaybackRate(speed: Float, pitch: Float) = playbackManager.setPlaybackRate(speed, pitch)
    
    fun toggleEqualizer() = playbackManager.toggleEqualizer()
    fun setEqualizerBandLevel(bandIndex: Int, levelMb: Int) = playbackManager.setEqualizerBandLevel(bandIndex, levelMb)
    fun setEqualizerPreset(presetName: String) = playbackManager.setEqualizerPreset(presetName)
    fun setBassBoost(strength: Int) = playbackManager.setBassBoost(strength)
    fun setVirtualizer(strength: Int) = playbackManager.setVirtualizer(strength)
    fun setCrossfadeSeconds(seconds: Int) = playbackManager.setCrossfadeSeconds(seconds)
    fun setGlobalThumbnailIndex(index: Int) = settingsManager.setGlobalThumbnailIndex(index)
    fun setShowSpectrum(show: Boolean) = settingsManager.setShowSpectrum(show)
    fun setDynamicArtworkColors(enabled: Boolean) = settingsManager.setDynamicArtworkColors(enabled)
    fun setSongThumbnailIndex(songId: String, index: Int) = settingsManager.setSongThumbnailIndex(songId, index)
    fun setSongThumbnail(songId: String, index: Int) = settingsManager.setSongThumbnailIndex(songId, index)
    fun startSleepTimer(minutes: Int) = playbackManager.startSleepTimer(minutes)
    fun stopSleepTimer() = playbackManager.stopSleepTimer()
    fun refreshCurrentSongArtwork() = playbackManager.refreshCurrentSongArtwork()

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song.id, song.isFavorite)
        }
    }

    fun updateSongMetadata(updatedSong: Song) {
        viewModelScope.launch {
            repository.updateSongMetadata(updatedSong)
            playbackManager.refreshCurrentSongMetadata(updatedSong)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun createPlaylistWithSongs(name: String, songIds: List<String>) {
        viewModelScope.launch {
            repository.createPlaylistWithSongs(name, songIds)
        }
    }

    fun saveSmartPlaylistAsCustom(smartPlaylist: SmartPlaylist) {
        viewModelScope.launch {
            val songIds = smartPlaylist.songs.map { it.id }
            repository.createPlaylistWithSongs(smartPlaylist.title, songIds)
        }
    }

    fun autoGenerateSmartPlaylists() {
        viewModelScope.launch {
            val currentSmartPlaylists = smartPlaylists.value
            currentSmartPlaylists.forEach { sp ->
                if (sp.songs.isNotEmpty()) {
                    repository.createPlaylistWithSongs(sp.title, sp.songs.map { it.id })
                }
            }
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

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            repository.deleteSong(song)
            playbackManager.onSongDeleted(song.id)
        }
    }
}
