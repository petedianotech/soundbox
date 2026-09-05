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

    // Crossfade & Gapless Playback
    val crossfadeSeconds: StateFlow<Int> = settingsManager.crossfadeSeconds
    val gaplessPlayback: StateFlow<Boolean> = settingsManager.gaplessPlayback

    fun setCrossfadeSeconds(seconds: Int) {
        settingsManager.setCrossfadeSeconds(seconds.coerceIn(0, 10))
    }

    fun setGaplessPlayback(enabled: Boolean) {
        settingsManager.setGaplessPlayback(enabled)
    }

    fun toggleCrossfade(enabled: Boolean) {
        if (enabled) {
            val current = settingsManager.crossfadeSeconds.value
            settingsManager.setCrossfadeSeconds(if (current > 0) current else 3)
        } else {
            settingsManager.setCrossfadeSeconds(0)
        }
    }

    // Scanning states & silent notification
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanNotification = MutableStateFlow<String?>(null)
    val scanNotification: StateFlow<String?> = _scanNotification.asStateFlow()

    fun clearScanNotification() {
        _scanNotification.value = null
    }

    enum class SortOrder {
        A_TO_Z, Z_TO_A, DATE_ADDED, DURATION, RATING, MOST_PLAYED
    }

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    // Core dataset flows (using Lazily so songs are never wiped/reset when backgrounding the app)
    val allSongs: StateFlow<List<Song>> = combine(repository.allSongs, _sortOrder) { songs, order ->
        when (order) {
            SortOrder.A_TO_Z -> songs.sortedBy { it.title.lowercase() }
            SortOrder.Z_TO_A -> songs.sortedByDescending { it.title.lowercase() }
            SortOrder.DATE_ADDED -> songs.sortedByDescending { it.dateAdded }
            SortOrder.DURATION -> songs.sortedByDescending { it.duration }
            SortOrder.RATING -> songs.sortedByDescending { it.rating }
            SortOrder.MOST_PLAYED -> songs.sortedByDescending { it.playCount }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topRatedSongs: StateFlow<List<Song>> = repository.topRatedSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedSongs: StateFlow<List<Song>> = repository.mostPlayedSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentlyPlayedSongs: StateFlow<List<Song>> = repository.recentlyPlayedSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentlyAddedSongs: StateFlow<List<Song>> = repository.recentlyAddedSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // UI grouping states (Folder-based navigation, album grouping, and artist metadata projection)
    val folderList: StateFlow<Map<String, List<Song>>> = repository.allSongs
        .map { songs -> songs.groupBy { it.folderPath } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val albumList: StateFlow<Map<String, List<Song>>> = repository.allSongs
        .map { songs -> songs.groupBy { it.album } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val artistList: StateFlow<Map<String, List<Song>>> = repository.allSongs
        .map { songs -> songs.groupBy { it.artist } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val genreList: StateFlow<Map<String, List<Song>>> = repository.allSongs
        .map { songs -> songs.groupBy { it.genre } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    // Dynamic Smart Playlists combining metadata, play history, ratings, and genres
    val smartPlaylists: StateFlow<List<SmartPlaylist>> = combine(
        repository.allSongs,
        repository.favoriteSongs,
        repository.mostPlayedSongs,
        repository.recentlyPlayedSongs,
        repository.recentlyAddedSongs
    ) { all, favs, mostPlayed, recentRuns, recentAdded ->
        val list = mutableListOf<SmartPlaylist>()

        // 1. Most Played
        val playedOnly = mostPlayed.filter { it.playCount > 0 }
        if (playedOnly.isNotEmpty()) {
            list.add(
                SmartPlaylist(
                    id = "smart_most_played",
                    type = SmartPlaylistType.MOST_PLAYED,
                    title = "Most Played",
                    description = "Your top listened tracks ranked by play count",
                    icon = Icons.Default.Whatshot,
                    tintColor = Color(0xFFFF9800),
                    songs = playedOnly
                )
            )
        }

        // 2. Recently Added
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

        // 3. Top 25 Favorites
        val top25Favs = if (favs.isNotEmpty()) {
            favs.take(25)
        } else {
            all.sortedWith(compareByDescending<Song> { it.rating }.thenByDescending { it.playCount }).take(25)
        }
        if (top25Favs.isNotEmpty()) {
            list.add(
                SmartPlaylist(
                    id = "smart_top_25_favorites",
                    type = SmartPlaylistType.TOP_25_FAVORITES,
                    title = "Top 25 Favorites",
                    description = "Curated top 25 collection of your favorite & top-scored tracks",
                    icon = Icons.Default.Star,
                    tintColor = Color(0xFFFFD700),
                    songs = top25Favs
                )
            )
        }

        // 4. Liked Songs
        if (favs.isNotEmpty()) {
            list.add(
                SmartPlaylist(
                    id = "smart_favorites",
                    type = SmartPlaylistType.FAVORITES,
                    title = "Liked Songs",
                    description = "All liked tracks marked with thumbs up",
                    icon = Icons.Default.ThumbUp,
                    tintColor = Color(0xFF00E5FF),
                    songs = favs
                )
            )
        }

        // 5. 5-Star Classics (Top Rated)
        val ratedSongs = all.filter { it.rating >= 4 }.sortedByDescending { it.rating }
        if (ratedSongs.isNotEmpty()) {
            list.add(
                SmartPlaylist(
                    id = "smart_top_rated",
                    type = SmartPlaylistType.TOP_RATED,
                    title = "5-Star Classics",
                    description = "Top scoring music rated 4 and 5 stars",
                    icon = Icons.Default.AutoAwesome,
                    tintColor = Color(0xFFFF6D00),
                    songs = ratedSongs
                )
            )
        }

        // 6. Forgotten Gems (Never Played or long unplayed)
        val unplayed = all.filter { it.playCount == 0 }
        if (unplayed.isNotEmpty()) {
            list.add(
                SmartPlaylist(
                    id = "smart_unplayed",
                    type = SmartPlaylistType.NEVER_PLAYED,
                    title = "Forgotten Gems",
                    description = "Hidden songs in your library you haven't played yet",
                    icon = Icons.Default.Explore,
                    tintColor = Color(0xFF43A047),
                    songs = unplayed
                )
            )
        }

        // 7. Recently Played
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

        // 8. Long Mixes (> 5 minutes)
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

        // 9. Quick Hits (< 3 minutes)
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

        // 10. Auto-created Genre Smart Playlists for all detected genres
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
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Soundbox Insights (Listening Statistics)
    val insights: StateFlow<SoundboxInsights> = repository.allSongs.map { songs ->
        val totalPlays = songs.sumOf { it.playCount }
        val totalDurationMs = songs.filter { it.playCount > 0 }.sumOf { it.duration * it.playCount.coerceAtLeast(1) }
        val uniqueArtists = songs.map { it.artist }.distinct().size
        val uniqueGenres = songs.map { it.genre }.filter { it.isNotBlank() && it.lowercase() != "unknown" }.distinct().size

        val artistMap = songs.groupBy { it.artist }
        val topArtists = artistMap.map { (artist, list) ->
            val plays = list.sumOf { it.playCount }
            ArtistStat(
                artistName = artist,
                playCount = plays,
                trackCount = list.size,
                percentage = if (totalPlays > 0) (plays.toFloat() / totalPlays.toFloat()) else 0f
            )
        }.sortedByDescending { it.playCount }.take(5)

        val genreMap = songs.groupBy { it.genre }.filter { it.key.isNotBlank() && it.key.lowercase() != "unknown" }
        val topGenres = genreMap.map { (genre, list) ->
            val plays = list.sumOf { it.playCount }.coerceAtLeast(list.size)
            GenreStat(
                genreName = genre,
                playCount = plays,
                percentage = if (songs.isNotEmpty()) (list.size.toFloat() / songs.size.toFloat()) else 0f
            )
        }.sortedByDescending { it.playCount }.take(5)

        val playedSongs = songs.filter { it.lastPlayedTime > 0 }
        val habits = if (playedSongs.isNotEmpty()) {
            val morningCount = playedSongs.count { 
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.lastPlayedTime }
                cal.get(java.util.Calendar.HOUR_OF_DAY) in 6..11 
            }
            val afternoonCount = playedSongs.count { 
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.lastPlayedTime }
                cal.get(java.util.Calendar.HOUR_OF_DAY) in 12..17 
            }
            val eveningCount = playedSongs.count { 
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.lastPlayedTime }
                cal.get(java.util.Calendar.HOUR_OF_DAY) in 18..22 
            }
            val nightCount = playedSongs.count { 
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.lastPlayedTime }
                val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
                h in 23..24 || h in 0..5
            }
            val total = (morningCount + afternoonCount + eveningCount + nightCount).coerceAtLeast(1)
            ListeningHabits(
                morningPercent = (morningCount * 100) / total,
                afternoonPercent = (afternoonCount * 100) / total,
                eveningPercent = (eveningCount * 100) / total,
                lateNightPercent = (nightCount * 100) / total
            )
        } else {
            ListeningHabits(morningPercent = 25, afternoonPercent = 35, eveningPercent = 25, lateNightPercent = 15)
        }

        val milestones = listOf(
            ListeningMilestone(
                id = "m1",
                title = "Audiophile Explorer",
                description = "Listen to at least 25 different songs in high quality",
                progress = (songs.count { it.playCount > 0 }.toFloat() / 25f).coerceIn(0f, 1f),
                currentFormatted = "${songs.count { it.playCount > 0 }} tracks",
                targetFormatted = "25 tracks",
                isAchieved = songs.count { it.playCount > 0 } >= 25
            ),
            ListeningMilestone(
                id = "m2",
                title = "Genre Connoisseur",
                description = "Explore tracks across 4 different music genres",
                progress = (uniqueGenres.toFloat() / 4f).coerceIn(0f, 1f),
                currentFormatted = "$uniqueGenres genres",
                targetFormatted = "4 genres",
                isAchieved = uniqueGenres >= 4
            ),
            ListeningMilestone(
                id = "m3",
                title = "Marathon Session",
                description = "Accumulate 2 hours of music listening time",
                progress = (totalDurationMs.toFloat() / (2 * 3600 * 1000f)).coerceIn(0f, 1f),
                currentFormatted = "${totalDurationMs / (60 * 1000)} min",
                targetFormatted = "120 min",
                isAchieved = totalDurationMs >= 2 * 3600 * 1000
            ),
            ListeningMilestone(
                id = "m4",
                title = "5-Star Curator",
                description = "Rate 10 of your favorite tracks with 5 stars",
                progress = (songs.count { it.rating >= 5 }.toFloat() / 10f).coerceIn(0f, 1f),
                currentFormatted = "${songs.count { it.rating >= 5 }} rated",
                targetFormatted = "10 rated",
                isAchieved = songs.count { it.rating >= 5 } >= 10
            )
        )

        SoundboxInsights(
            totalPlayCount = totalPlays,
            totalPlaytimeMs = totalDurationMs,
            uniqueArtistsCount = uniqueArtists,
            uniqueGenresCount = uniqueGenres,
            topArtists = topArtists,
            topGenres = topGenres,
            habits = habits,
            milestones = milestones
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, SoundboxInsights(0, 0, 0, 0, emptyList(), emptyList(), ListeningHabits(25, 25, 25, 25), emptyList()))

    // Library Cleaner Summary (Duplicates & Low-Quality Files)
    val cleanerSummary: StateFlow<CleanerSummary> = repository.allSongs.map { songs ->
        val grouped = songs.groupBy { "${it.title.trim().lowercase()}:::${it.artist.trim().lowercase()}" }
        val duplicateGroups = grouped.filter { it.value.size > 1 }.map { (key, dupSongs) ->
            DuplicateGroup(
                key = key,
                title = dupSongs.first().title,
                artist = dupSongs.first().artist,
                duplicates = dupSongs
            )
        }

        val lowQuality = songs.filter { 
            it.bitrateKbps < 160 || (it.size in 1..800_000 && it.duration > 45_000)
        }

        val dupWaste = duplicateGroups.sumOf { it.totalWastedBytes }
        val lowWaste = lowQuality.sumOf { it.size }

        CleanerSummary(
            duplicateGroups = duplicateGroups,
            lowQualityTracks = lowQuality,
            totalDuplicateWasteBytes = dupWaste,
            totalLowQualityBytes = lowWaste
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, CleanerSummary(emptyList(), emptyList(), 0L, 0L))

    init {
        // Run first local scan to populate music database silently
        scanStorage()
    }

    fun scanStorage() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val newCount = repository.scanStorage()
                if (newCount > 0) {
                    _scanNotification.value = "Library updated • $newCount new track${if (newCount > 1) "s" else ""} added"
                    launch {
                        kotlinx.coroutines.delay(4000)
                        _scanNotification.value = null
                    }
                }
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
    fun setPlaybackSpeed(speed: Float) = playbackManager.setPlaybackRate(speed, playbackPitch.value)
    
    fun toggleEqualizer() = playbackManager.toggleEqualizer()
    fun setEqBandLevel(bandIndex: Int, levelDb: Float) = playbackManager.setEqBandLevel(bandIndex, levelDb)
    fun setPreampGain(gainDb: Float) = playbackManager.setPreampGain(gainDb)
    fun setTrebleGain(gainDb: Float) = playbackManager.setTrebleGain(gainDb)
    fun setBassBoost(strength: Int) = playbackManager.setBassBoost(strength)
    fun setBassBoostStrength(strength: Int) = playbackManager.setBassBoost(strength)
    fun setVirtualizerStrength(strength: Int) = playbackManager.setVirtualizerStrength(strength)
    fun setAudioBalance(balance: Float) = playbackManager.setAudioBalance(balance)
    fun setReverbPreset(presetId: Int) = playbackManager.setReverbPreset(presetId)
    fun applyPowerampPreset(
        presetName: String,
        bandGains: List<Float>,
        bassBoost: Int = 300,
        treble: Float = 0f,
        virtualizer: Int = 0,
        reverb: Int = 0
    ) = playbackManager.applyPowerampPreset(presetName, bandGains, bassBoost, treble, virtualizer, reverb)
    fun resetEqualizerToFlat() = playbackManager.resetEqualizerToFlat()

    fun startSleepTimer(minutes: Int) = playbackManager.startSleepTimer(minutes)
    fun stopSleepTimer() = playbackManager.stopSleepTimer()

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song.id, song.isFavorite)
        }
    }

    fun updateSongRating(song: Song, rating: Int) {
        viewModelScope.launch {
            val clamped = rating.coerceIn(0, 5)
            repository.updateRating(song.id, clamped)
            val updated = song.copy(rating = clamped)
            if (currentSong.value?.id == song.id) {
                playbackManager.refreshCurrentSongMetadata(updated)
            }
        }
    }

    fun updateSongMetadata(updatedSong: Song, newLyrics: String? = null) {
        viewModelScope.launch {
            repository.updateSongMetadata(updatedSong, newLyrics)
            playbackManager.refreshCurrentSongMetadata(updatedSong)
        }
    }

    fun batchUpdateMetadata(
        songsToUpdate: List<Song>,
        artist: String?,
        album: String?,
        genre: String?,
        rating: Int?
    ) {
        viewModelScope.launch {
            val updatedList = songsToUpdate.map { song ->
                song.copy(
                    artist = if (!artist.isNullOrBlank()) artist.trim() else song.artist,
                    album = if (!album.isNullOrBlank()) album.trim() else song.album,
                    genre = if (!genre.isNullOrBlank()) genre.trim() else song.genre,
                    rating = rating?.coerceIn(0, 5) ?: song.rating
                )
            }
            repository.updateSongsBatch(updatedList)
            currentSong.value?.let { curr ->
                updatedList.find { it.id == curr.id }?.let { updatedCurr ->
                    playbackManager.refreshCurrentSongMetadata(updatedCurr)
                }
            }
        }
    }

    /**
     * Completely deletes a song from physical disk, MediaStore, and Room database.
     * Safely handles currently playing track by advancing or stopping playback.
     */
    fun deleteSongFromDevice(song: Song, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (currentSong.value?.id == song.id) {
                val currentQueue = queue.value
                if (currentQueue.size > 1) {
                    playbackManager.skipNext()
                } else {
                    playbackManager.clearQueue()
                }
            }
            repository.deleteSongCompletely(song)
            onComplete?.invoke()
        }
    }

    fun deleteSongsBatchFromDevice(songsToDelete: List<Song>, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val currentId = currentSong.value?.id
            if (currentId != null && songsToDelete.any { it.id == currentId }) {
                val remaining = queue.value.filter { q -> songsToDelete.none { it.id == q.id } }
                if (remaining.isNotEmpty()) {
                    playbackManager.playSong(remaining.first(), remaining)
                } else {
                    playbackManager.clearQueue()
                }
            }
            repository.deleteSongsBatchCompletely(songsToDelete)
            onComplete?.invoke()
        }
    }

    fun deleteSongs(songsToDelete: List<Song>) {
        viewModelScope.launch {
            repository.deleteSongsBatchCompletely(songsToDelete)
        }
    }

    fun cleanDuplicateGroup(group: DuplicateGroup, keepBest: Boolean = true) {
        viewModelScope.launch {
            if (group.duplicates.size <= 1) return@launch
            val sorted = group.duplicates.sortedWith(
                compareByDescending<Song> { it.bitrateKbps }
                    .thenByDescending { it.size }
                    .thenByDescending { it.rating }
            )
            val toDelete = if (keepBest) sorted.drop(1) else group.duplicates
            repository.deleteSongsBatch(toDelete.map { it.id })
        }
    }

    fun cleanAllDuplicates() {
        viewModelScope.launch {
            val summary = cleanerSummary.value
            val toDeleteIds = mutableListOf<String>()
            summary.duplicateGroups.forEach { group ->
                if (group.duplicates.size > 1) {
                    val sorted = group.duplicates.sortedWith(
                        compareByDescending<Song> { it.bitrateKbps }
                            .thenByDescending { it.size }
                            .thenByDescending { it.rating }
                    )
                    toDeleteIds.addAll(sorted.drop(1).map { it.id })
                }
            }
            if (toDeleteIds.isNotEmpty()) {
                repository.deleteSongsBatch(toDeleteIds)
            }
        }
    }

    fun cleanLowQualityTracks(tracks: List<Song>) {
        viewModelScope.launch {
            if (tracks.isNotEmpty()) {
                repository.deleteSongsBatch(tracks.map { it.id })
            }
        }
    }

    // Playlist Commands
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
}
