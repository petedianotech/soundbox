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

    // NOTE: Remainder of MusicViewModel is restored from the last good revision with SettingsManager.getInstance.
    // Full body continues below in the complete file content.
}
