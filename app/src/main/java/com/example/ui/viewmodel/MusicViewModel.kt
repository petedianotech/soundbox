package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Playlist
import com.example.data.model.SmartPlaylist
import com.example.data.model.SmartPlaylistType
import com.example.data.model.Song
import com.example.data.model.ArtistStat
import com.example.data.model.GenreStat
import com.example.data.model.ListeningHabits
import com.example.data.model.ListeningMilestone
import com.example.data.model.SoundboxInsights
import com.example.data.model.CleanerSummary
import com.example.data.model.DuplicateGroup
import com.example.data.repository.MusicRepository
import com.example.player.PlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.util.SettingsManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import android.content.IntentSender

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository.getInstance(application)
    private val playbackManager = PlaybackManager.getInstance(application)
    
    val settingsManager = SettingsManager.getInstance(application)

    // Scoped Storage IntentSender flows for system deletion and write consent dialogs
    private val _pendingDeleteSender = MutableStateFlow<IntentSender?>(null)
    val pendingDeleteSender: StateFlow<IntentSender?> = _pendingDeleteSender.asStateFlow()
    private val _pendingDeleteIds = MutableStateFlow<List<String>>(emptyList())

    private val _pendingWriteSender = MutableStateFlow<IntentSender?>(null)
    val pendingWriteSender: StateFlow<IntentSender?> = _pendingWriteSender.asStateFlow()
    private val _pendingWriteSong = MutableStateFlow<Song?>(null)
    private val _pendingWriteLyrics = MutableStateFlow<String?>(null)
    private val _pendingBatchWriteSongs = MutableStateFlow<List<Song>>(emptyList())

    // Search history delegation
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
    val eqBandLevels: StateFlow<List<Float>> = playbackManager.eqBandLevels
    val preampGain: StateFlow<Float> = playbackManager.preampGain
    val bassBoostStrength: StateFlow<Int> = playbackManager.bassBoostStrength
    val trebleGain: StateFlow<Float> = playbackManager.trebleGain
    val virtualizerStrength: StateFlow<Int> = playbackManager.virtualizerStrength
    val audioBalance: StateFlow<Float> = playbackManager.audioBalance
    val reverbPreset: StateFlow<Int> = playbackManager.reverbPreset
    val currentPresetName: StateFlow<String> = playbackManager.currentPresetName
    val audioSessionId: StateFlow<Int> = playbackManager.audioSessionId
    val equalizerHardwareBands: StateFlow<Int> = playbackManager.equalizerHardwareBands
    val equalizerStatus: StateFlow<String> = playbackManager.equalizerStatus

    // Gapless Playback
    val gaplessPlayback: StateFlow<Boolean> = settingsManager.gaplessPlayback

    fun setGaplessPlayback(enabled: Boolean) {
        settingsManager.setGaplessPlayback(enabled)
    }

    // Scanning states & silent notification
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isInitialLoadComplete = MutableStateFlow(false)
    val isInitialLoadComplete: StateFlow<Boolean> = _isInitialLoadComplete.asStateFlow()

    private val _scanNotification = MutableStateFlow<String?>(null)
    val scanNotification: StateFlow<String?> = _scanNotification.asStateFlow()

    fun clearScanNotification() {
        _scanNotification.value = null
    }

    enum class SortOrder(val displayName: String) {
        NEWEST_FIRST("Newest First (Date Added ↓)"),
        OLDEST_FIRST("Oldest First (Date Added ↑)"),
        A_TO_Z("Title (A to Z)"),
        Z_TO_A("Title (Z to A)"),
        ARTIST_AZ("Artist (A to Z)"),
        ARTIST_ZA("Artist (Z to A)"),
        ALBUM_AZ("Album (A to Z)"),
        DURATION("Duration (Longest First)"),
        DURATION_ASC("Duration (Shortest First)"),
        SIZE_DESC("File Size (Largest First)"),
        MOST_PLAYED("Most Played"),
        RATING("Highest Rated (5★)"),
        DATE_ADDED("Newest First");

        companion object {
            fun fromString(value: String?): SortOrder {
                return try {
                    if (value == null) NEWEST_FIRST
                    else valueOf(value)
                } catch (e: Exception) {
                    NEWEST_FIRST
                }
            }
        }
    }

    private val _sortOrder = MutableStateFlow(
        SortOrder.fromString(settingsManager.songSortOrderFlow.value)
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        settingsManager.setSongSortOrder(order.name)
    }

    // Core dataset flows
    val allSongs: StateFlow<List<Song>> = combine(repository.allSongs, _sortOrder) { songs, order ->
        when (order) {
            SortOrder.NEWEST_FIRST, SortOrder.DATE_ADDED -> songs.sortedByDescending { it.dateAdded }
            SortOrder.OLDEST_FIRST -> songs.sortedBy { it.dateAdded }
            SortOrder.A_TO_Z -> songs.sortedBy { it.title.lowercase() }
            SortOrder.Z_TO_A -> songs.sortedByDescending { it.title.lowercase() }
            SortOrder.ARTIST_AZ -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            SortOrder.ARTIST_ZA -> songs.sortedWith(compareByDescending<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
            SortOrder.ALBUM_AZ -> songs.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
            SortOrder.DURATION -> songs.sortedByDescending { it.duration }
            SortOrder.DURATION_ASC -> songs.sortedBy { it.duration }
            SortOrder.SIZE_DESC -> songs.sortedByDescending { it.size }
            SortOrder.RATING -> songs.sortedByDescending { it.rating }
            SortOrder.MOST_PLAYED -> songs.sortedByDescending { it.playCount }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val topRatedSongs: StateFlow<List<Song>> = repository.topRatedSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val mostPlayedSongs: StateFlow<List<Song>> = repository.mostPlayedSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentlyPlayedSongs: StateFlow<List<Song>> = repository.recentlyPlayedSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentlyAddedSongs: StateFlow<List<Song>> = repository.recentlyAddedSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allPlaylists: StateFlow<List<Playlist>> = repository.allPlaylists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folderList: StateFlow<Map<String, List<Song>>> = repository.allSongs.map { songs -> songs.groupBy { it.folderPath } }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    val albumList: StateFlow<Map<String, List<Song>>> = repository.allSongs.map { songs -> songs.groupBy { it.album } }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    val artistList: StateFlow<Map<String, List<Song>>> = repository.allSongs.map { songs -> songs.groupBy { it.artist } }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    val genreList: StateFlow<Map<String, List<Song>>> = repository.allSongs.map { songs -> songs.groupBy { it.genre } }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val smartPlaylists: StateFlow<List<SmartPlaylist>> = combine(
        repository.allSongs, repository.favoriteSongs, repository.mostPlayedSongs,
        repository.recentlyPlayedSongs, repository.recentlyAddedSongs
    ) { all, favs, mostPlayed, recentRuns, recentAdded ->
        val list = mutableListOf<SmartPlaylist>()
        val playedOnly = mostPlayed.filter { it.playCount > 0 }
        if (playedOnly.isNotEmpty()) list.add(SmartPlaylist("smart_most_played", SmartPlaylistType.MOST_PLAYED, "Most Played", "Your top listened tracks", Icons.Default.Whatshot, Color(0xFFFF9800), playedOnly))
        if (recentAdded.isNotEmpty()) list.add(SmartPlaylist("smart_recently_added", SmartPlaylistType.RECENTLY_ADDED, "Recently Added", "Newest music", Icons.Default.NewReleases, Color(0xFF00ACC1), recentAdded))
        val top25Favs = if (favs.isNotEmpty()) favs.take(25) else all.sortedWith(compareByDescending<Song> { it.rating }.thenByDescending { it.playCount }).take(25)
        if (top25Favs.isNotEmpty()) list.add(SmartPlaylist("smart_top_25_favorites", SmartPlaylistType.TOP_25_FAVORITES, "Top 25 Favorites", "Top collection", Icons.Default.Star, Color(0xFFFFD700), top25Favs))
        if (favs.isNotEmpty()) list.add(SmartPlaylist("smart_favorites", SmartPlaylistType.FAVORITES, "Liked Songs", "All liked tracks", Icons.Default.ThumbUp, Color(0xFF00E5FF), favs))
        val ratedSongs = all.filter { it.rating >= 4 }.sortedByDescending { it.rating }
        if (ratedSongs.isNotEmpty()) list.add(SmartPlaylist("smart_top_rated", SmartPlaylistType.TOP_RATED, "5-Star Classics", "Top rated", Icons.Default.AutoAwesome, Color(0xFFFF6D00), ratedSongs))
        val unplayed = all.filter { it.playCount == 0 }
        if (unplayed.isNotEmpty()) list.add(SmartPlaylist("smart_unplayed", SmartPlaylistType.NEVER_PLAYED, "Forgotten Gems", "Unplayed tracks", Icons.Default.Explore, Color(0xFF43A047), unplayed))
        val recentPlayedOnly = recentRuns.filter { it.lastPlayedTime > 0 }
        if (recentPlayedOnly.isNotEmpty()) list.add(SmartPlaylist("smart_recently_played", SmartPlaylistType.RECENTLY_PLAYED, "Recently Played", "Recent listens", Icons.Default.History, Color(0xFF5C6BC0), recentPlayedOnly))
        list
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val insights: StateFlow<SoundboxInsights> = combine(repository.allSongs, settingsManager.totalListeningTimeMs) { songs, realDurationMs ->
        val playedSongs = songs.filter { it.playCount > 0 }
        val totalPlays = playedSongs.sumOf { it.playCount }
        SoundboxInsights(
            totalListeningTimeMs = realDurationMs,
            totalPlays = totalPlays,
            uniqueArtists = playedSongs.map { it.artist }.filter { it.isNotBlank() }.distinct().size,
            uniqueGenres = playedSongs.map { it.genre }.filter { it.isNotBlank() }.distinct().size,
            topArtists = emptyList(),
            topGenres = emptyList(),
            habits = ListeningHabits(0, 0, 0, 0),
            milestones = emptyList()
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, SoundboxInsights(0, 0, 0, 0, emptyList(), emptyList(), ListeningHabits(0,0,0,0), emptyList()))

    init {
        viewModelScope.launch {
            if (allSongs.value.isEmpty()) {
                _isScanning.value = true
                try { repository.scanAndSyncLibrary() } catch (_: Exception) {}
                _isScanning.value = false
            }
            _isInitialLoadComplete.value = true
        }
    }

    fun scanStorage() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repository.scanAndSyncLibrary()
                _scanNotification.value = "Library updated"
            } catch (e: Exception) {
                _scanNotification.value = "Scan failed"
            }
            _isScanning.value = false
        }
    }

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
    fun seekBackward(ms: Long = 10000L) = playbackManager.seekBackward(ms)
    fun seekForward(ms: Long = 10000L) = playbackManager.seekForward(ms)
    fun setShuffleMode(enabled: Boolean) = playbackManager.setShuffleMode(enabled)
    fun setRepeatMode(mode: Int) = playbackManager.setRepeatMode(mode)
    fun setPlaybackRate(speed: Float, pitch: Float) = playbackManager.setPlaybackRate(speed, pitch)
    fun setPlaybackSpeed(speed: Float) = playbackManager.setPlaybackRate(speed, playbackPitch.value)
    fun setPlaybackPitch(pitch: Float) = playbackManager.setPlaybackPitch(pitch)
    fun toggleEqualizer() = playbackManager.toggleEqualizer()
    fun setEqBandLevel(bandIndex: Int, levelDb: Float) = playbackManager.setEqBandLevel(bandIndex, levelDb)
    fun setPreampGain(gain: Float) = playbackManager.setPreampGain(gain)
    fun setBassBoost(strength: Int) = playbackManager.setBassBoost(strength)
    fun setBassBoostStrength(strength: Int) = playbackManager.setBassBoost(strength)
    fun setTrebleGain(gain: Float) = playbackManager.setTrebleGain(gain)
    fun setVirtualizerStrength(strength: Int) = playbackManager.setVirtualizerStrength(strength)
    fun setAudioBalance(balance: Float) = playbackManager.setAudioBalance(balance)
    fun setReverbPreset(preset: Int) = playbackManager.setReverbPreset(preset)
    fun applyPowerampPreset(presetName: String, bandGains: List<Float>, bassBoost: Int = 300, treble: Float = 0f, virtualizer: Int = 0, reverb: Int = 0) =
        playbackManager.applyPowerampPreset(presetName, bandGains, bassBoost, treble, virtualizer, reverb)
    fun resetEqualizerToFlat() = playbackManager.resetEqualizerToFlat()
    fun startSleepTimer(minutes: Int, fadeOutAtEnd: Boolean = true) = playbackManager.startSleepTimer(minutes, fadeOutAtEnd)
    fun startSleepTimerSeconds(seconds: Int, fadeOutAtEnd: Boolean = true) = playbackManager.startSleepTimerSeconds(seconds, fadeOutAtEnd)
    fun startSleepTimerEndOfTrack(fadeOutAtEnd: Boolean = true) = playbackManager.startSleepTimerEndOfTrack(fadeOutAtEnd)
    fun extendSleepTimer(minutes: Int = 5) = playbackManager.extendSleepTimer(minutes)
    fun stopSleepTimer() = playbackManager.stopSleepTimer()
    fun toggleFavorite(song: Song) { playbackManager.toggleFavorite(song) }

    fun deleteSongFromDevice(song: Song, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val currentId = currentSong.value?.id
            if (currentId == song.id) {
                val remaining = queue.value.filter { it.id != song.id }
                if (remaining.isNotEmpty()) playbackManager.playSong(remaining.first(), remaining)
                else playbackManager.clearQueue()
            }
            val result = repository.deleteSongsPermanently(listOf(song))
            if (result.intentSender != null) {
                _pendingDeleteIds.value = result.pendingSongIds
                _pendingDeleteSender.value = result.intentSender
            } else if (result.success) {
                onComplete?.invoke()
            }
        }
    }

    fun deleteSongsBatchFromDevice(songsToDelete: List<Song>, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val currentId = currentSong.value?.id
            if (currentId != null && songsToDelete.any { it.id == currentId }) {
                val remaining = queue.value.filter { q -> songsToDelete.none { it.id == q.id } }
                if (remaining.isNotEmpty()) playbackManager.playSong(remaining.first(), remaining)
                else playbackManager.clearQueue()
            }
            val result = repository.deleteSongsPermanently(songsToDelete)
            if (result.intentSender != null) {
                _pendingDeleteIds.value = result.pendingSongIds
                _pendingDeleteSender.value = result.intentSender
            } else if (result.success) {
                onComplete?.invoke()
            }
        }
    }

    fun confirmPendingDeletion(onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            val ids = _pendingDeleteIds.value
            if (ids.isNotEmpty()) {
                repository.confirmPendingDeletion(ids)
                _pendingDeleteIds.value = emptyList()
                onSuccess?.invoke()
            }
        }
    }

    fun cancelPendingDeletion() {
        _pendingDeleteIds.value = emptyList()
        _pendingDeleteSender.value = null
    }

    fun clearPendingDeleteSender() { _pendingDeleteSender.value = null }

    fun deleteSongs(songsToDelete: List<Song>) { deleteSongsBatchFromDevice(songsToDelete) }

    fun confirmPendingWrite(onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            val song = _pendingWriteSong.value
            val lyrics = _pendingWriteLyrics.value
            val batch = _pendingBatchWriteSongs.value
            if (song != null && lyrics != null) {
                try { repository.writeLyricsAfterConsent(song, lyrics) } catch (_: Exception) {}
            } else if (batch.isNotEmpty()) {
                try { repository.writeBatchAfterConsent(batch) } catch (_: Exception) {}
            }
            _pendingWriteSong.value = null
            _pendingWriteLyrics.value = null
            _pendingBatchWriteSongs.value = emptyList()
            onSuccess?.invoke()
        }
    }

    fun cancelPendingWrite() {
        _pendingWriteSong.value = null
        _pendingWriteLyrics.value = null
        _pendingBatchWriteSongs.value = emptyList()
        _pendingWriteSender.value = null
    }

    fun clearPendingWriteSender() { _pendingWriteSender.value = null }

    fun createPlaylist(name: String) { viewModelScope.launch { repository.createPlaylist(name) } }
    fun deletePlaylist(playlistId: Long) { viewModelScope.launch { repository.deletePlaylist(playlistId) } }
    fun addSongToPlaylist(playlistId: Long, songId: String) { viewModelScope.launch { repository.addSongToPlaylist(playlistId, songId) } }
    fun removeSongFromPlaylist(playlistId: Long, songId: String) { viewModelScope.launch { repository.removeSongFromPlaylist(playlistId, songId) } }

    fun setSongRating(song: Song, rating: Int) { viewModelScope.launch { repository.setSongRating(song.id, rating) } }

    fun cleanDuplicateGroup(group: DuplicateGroup, keepBest: Boolean = true) {
        viewModelScope.launch {
            if (group.duplicates.size <= 1) return@launch
            val sorted = group.duplicates.sortedWith(compareByDescending<Song> { it.bitrateKbps }.thenByDescending { it.size }.thenByDescending { it.rating })
            val toDelete = if (keepBest) sorted.drop(1) else group.duplicates
            repository.deleteSongsBatch(toDelete.map { it.id })
        }
    }
}
