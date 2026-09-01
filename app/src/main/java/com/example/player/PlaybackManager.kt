package com.example.player

import android.content.Context
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.Song
import com.example.data.repository.MusicRepository
import com.example.util.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask

class PlaybackManager private constructor(private val context: Context) {

    private val repository = MusicRepository.getInstance(context)
    private val settingsManager = SettingsManager(context.applicationContext)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ExoPlayer reference
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build(), 
            true // handleAudioFocus = true
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
        .build()

    // Sound FX
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: android.media.audiofx.Virtualizer? = null

    // Exposed Flows
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _sleepTimerMillis = MutableStateFlow(0L)
    val sleepTimerMillis: StateFlow<Long> = _sleepTimerMillis.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerPreset = MutableStateFlow("Flat")
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    private val _equalizerBands = MutableStateFlow<List<Int>>(listOf(0, 0, 0, 0, 0))
    val equalizerBands: StateFlow<List<Int>> = _equalizerBands.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0) // 0 to 1000 millibels
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0) // 0 to 1000 millibels
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    val crossfadeSeconds: StateFlow<Int> = settingsManager.crossfadeSecondsFlow

    private val _audioSessionId = MutableStateFlow(player.audioSessionId)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private var sleepTimer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private var fadeInJob: Job? = null
    private var isCrossfadingManual = false

    private val positionTrackerRunnable = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                val pos = player.currentPosition
                val dur = player.duration
                _currentPosition.value = pos
                if (dur > 0 && dur != _duration.value) {
                    _duration.value = dur
                }

                val crossfadeMs = settingsManager.crossfadeSecondsFlow.value * 1000L
                if (crossfadeMs > 0 && dur > crossfadeMs * 2 && !isCrossfadingManual) {
                    val remainingMs = dur - pos
                    if (remainingMs in 1L..crossfadeMs) {
                        if (fadeInJob?.isActive != true) {
                            val factor = (remainingMs.toFloat() / crossfadeMs.toFloat()).coerceIn(0f, 1f)
                            player.volume = factor
                        }
                    }
                }
            }
            handler.postDelayed(this, 120)
        }
    }

    init {
        restoreEqualizerState()
        setupPlayerListeners()
        handler.post(positionTrackerRunnable)
        restorePlaybackState()
        initializeMediaController()
    }

    private fun initializeMediaController() {
        try {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener(
                {
                    try {
                        mediaController = controllerFuture?.get()
                        Log.d("PlaybackManager", "MediaController connected successfully")
                    } catch (e: Throwable) {
                        Log.w("PlaybackManager", "MediaController connection non-fatal warning: ${e.message}")
                    }
                },
                MoreExecutors.directExecutor()
            )
        } catch (e: Throwable) {
            Log.w("PlaybackManager", "SessionToken initialization non-fatal warning: ${e.message}")
        }
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                _isPlaying.value = isPlayingChanged
                _duration.value = player.duration.coerceAtLeast(0L)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                if (mediaId != null) {
                    scope.launch {
                        val song = repository.getSongById(mediaId)
                        if (song != null) {
                            _currentSong.value = song
                            repository.incrementPlayCount(song.id)
                            saveCurrentState(song.id, player.currentPosition)
                        }
                    }
                }
                triggerCrossfadeFadeIn()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
                _playbackPitch.value = playbackParameters.pitch
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleMode.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(newRepeatMode: Int) {
                _repeatMode.value = newRepeatMode
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                super.onAudioSessionIdChanged(audioSessionId)
                _audioSessionId.value = audioSessionId
                initAudioEffects(audioSessionId)
            }
        })
    }

    private fun restoreEqualizerState() {
        val prefs = context.getSharedPreferences("soundbox_equalizer", Context.MODE_PRIVATE)
        _equalizerEnabled.value = prefs.getBoolean("eq_enabled", false)
        _equalizerPreset.value = prefs.getString("eq_preset", "Flat") ?: "Flat"
        val bandsStr = prefs.getString("eq_bands", "0,0,0,0,0") ?: "0,0,0,0,0"
        _equalizerBands.value = bandsStr.split(",").mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(0,0,0,0,0) }
        _bassBoostStrength.value = prefs.getInt("bass_strength", 0)
        _virtualizerStrength.value = prefs.getInt("virt_strength", 0)
    }

    private fun saveEqualizerState() {
        val prefs = context.getSharedPreferences("soundbox_equalizer", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("eq_enabled", _equalizerEnabled.value)
            .putString("eq_preset", _equalizerPreset.value)
            .putString("eq_bands", _equalizerBands.value.joinToString(","))
            .putInt("bass_strength", _bassBoostStrength.value)
            .putInt("virt_strength", _virtualizerStrength.value)
            .apply()
    }

    private var activeAudioEffectsSessionId: Int = -1

    private fun initAudioEffects(sessionId: Int) {
        ensureAudioEffectsInitialized(sessionId)
    }

    fun ensureAudioEffectsInitialized(requestedSessionId: Int = -1) {
        try {
            var sid = if (requestedSessionId > 0) requestedSessionId else player.audioSessionId
            if (sid <= 0) sid = _audioSessionId.value
            val targetSession = if (sid > 0) sid else 0

            if (equalizer != null && activeAudioEffectsSessionId == targetSession) {
                return
            }

            try {
                equalizer?.release()
                bassBoost?.release()
                virtualizer?.release()
            } catch (e: Throwable) {
                Log.w("PlaybackManager", "Releasing previous effects failed: ${e.message}")
            }
            equalizer = null
            bassBoost = null
            virtualizer = null

            try {
                equalizer = Equalizer(0, targetSession).apply {
                    enabled = _equalizerEnabled.value
                    applyStoredBandLevelsToHardware(this)
                }
                bassBoost = BassBoost(0, targetSession).apply {
                    enabled = _bassBoostStrength.value > 0
                    if (_bassBoostStrength.value > 0) {
                        setStrength(_bassBoostStrength.value.coerceIn(0, 1000).toShort())
                    }
                }
                virtualizer = android.media.audiofx.Virtualizer(0, targetSession).apply {
                    enabled = _virtualizerStrength.value > 0
                    if (_virtualizerStrength.value > 0) {
                        setStrength(_virtualizerStrength.value.coerceIn(0, 1000).toShort())
                    }
                }
                activeAudioEffectsSessionId = targetSession
                Log.d("PlaybackManager", "Audio effects successfully initialized on session $targetSession")
            } catch (e: Throwable) {
                Log.w("PlaybackManager", "Equalizer/BassBoost activation on session $targetSession: ${e.message}")
                if (targetSession != 0) {
                    try {
                        equalizer = Equalizer(0, 0).apply {
                            enabled = _equalizerEnabled.value
                            applyStoredBandLevelsToHardware(this)
                        }
                        activeAudioEffectsSessionId = 0
                        Log.d("PlaybackManager", "Audio effects fallback initialized on session 0")
                    } catch (ex: Throwable) {
                        Log.w("PlaybackManager", "Fallback EQ failed: ${ex.message}")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w("PlaybackManager", "Audio effects setup error suppressed: ${e.message}")
        }
    }

    private fun applyStoredBandLevelsToHardware(eq: Equalizer) {
        try {
            val numBands = eq.numberOfBands.toInt().coerceAtLeast(1)
            val range = eq.bandLevelRange // Array of 2 shorts: [min, max]
            val minMb = range?.getOrNull(0) ?: -1500
            val maxMb = range?.getOrNull(1) ?: 1500
            val currentLevels = _equalizerBands.value
            for (i in 0 until numBands) {
                val levelMb = currentLevels.getOrElse(i) { 0 }.toShort().coerceIn(minMb, maxMb)
                eq.setBandLevel(i.toShort(), levelMb)
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Failed setting band levels on hardware EQ: ${e.message}")
        }
    }

    fun triggerCrossfadeFadeIn() {
        val durationSec = settingsManager.crossfadeSecondsFlow.value
        if (durationSec <= 0) {
            player.volume = 1.0f
            return
        }
        fadeInJob?.cancel()
        fadeInJob = scope.launch {
            val totalMs = (durationSec * 1000L).coerceAtMost(4000L)
            val steps = 25
            val stepMs = totalMs / steps
            for (i in 0..steps) {
                if (!isActive) break
                val vol = i.toFloat() / steps
                player.volume = vol
                delay(stepMs)
            }
            player.volume = 1.0f
        }
    }

    private fun playWithCrossfade(action: () -> Unit) {
        val durationSec = settingsManager.crossfadeSecondsFlow.value
        if (durationSec > 0 && _isPlaying.value) {
            isCrossfadingManual = true
            scope.launch {
                fadeInJob?.cancel()
                val steps = 12
                val fadeOutTime = (durationSec * 500L).coerceIn(300L, 800L)
                val stepMs = fadeOutTime / steps
                val initialVol = player.volume
                for (i in steps downTo 0) {
                    if (!isActive) break
                    player.volume = initialVol * (i.toFloat() / steps)
                    delay(stepMs)
                }
                action()
                isCrossfadingManual = false
                triggerCrossfadeFadeIn()
            }
        } else {
            action()
            player.volume = 1.0f
        }
    }

    private fun extractArtworkBytes(path: String): ByteArray? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            if (path.startsWith("content://")) {
                retriever.setDataSource(context, android.net.Uri.parse(path))
            } else {
                retriever.setDataSource(path)
            }
            val art = retriever.embeddedPicture
            retriever.release()
            art
        } catch (e: Exception) {
            null
        }
    }

    private fun buildMediaItem(songItem: Song): MediaItem {
        val fileUri = if (songItem.path.startsWith("content://") || songItem.path.startsWith("file://")) {
            android.net.Uri.parse(songItem.path)
        } else {
            android.net.Uri.fromFile(java.io.File(songItem.path))
        }

        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(songItem.title)
            .setArtist(songItem.artist)
            .setAlbumTitle(songItem.album)
            .setDisplayTitle(songItem.title)

        var artworkBytes: ByteArray? = null

        // 1. Check custom online downloaded cover file first
        val customCoverFile = com.example.util.OnlineCoverFetcher.getSavedCoverFile(context, songItem.id)
        if (customCoverFile.exists()) {
            try {
                artworkBytes = customCoverFile.readBytes()
                metadataBuilder.setArtworkUri(android.net.Uri.fromFile(customCoverFile))
            } catch (e: Exception) { }
        }

        // 2. Check embedded ID3 artwork
        if (artworkBytes == null) {
            artworkBytes = extractArtworkBytes(songItem.path)
            if (artworkBytes != null) {
                metadataBuilder.setArtworkUri(fileUri)
            }
        }

        // 3. Fallback to app generated thumbnail resource bitmap
        if (artworkBytes == null) {
            try {
                val thumbIndex = settingsManager.songThumbnailMapFlow.value[songItem.id] ?: -1
                val thumbRes = com.example.ui.components.getDefaultThumbnailResId(songItem.title, thumbIndex)
                val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, thumbRes)
                if (bitmap != null) {
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                    artworkBytes = stream.toByteArray()
                }
            } catch (e: Exception) { }
        }

        if (artworkBytes != null) {
            metadataBuilder.setArtworkData(artworkBytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return MediaItem.Builder()
            .setMediaId(songItem.id)
            .setUri(fileUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun refreshCurrentSongArtwork() {
        val song = _currentSong.value ?: return
        try {
            val updatedMediaItem = buildMediaItem(song)
            val currentIdx = player.currentMediaItemIndex
            if (currentIdx >= 0 && currentIdx < player.mediaItemCount) {
                player.replaceMediaItem(currentIdx, updatedMediaItem)
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error refreshing song artwork: ${e.message}")
        }
    }

    fun startPlaybackService() {
        try {
            val intent = android.content.Intent(context, PlaybackService::class.java)
            context.startService(intent)
        } catch (e: Throwable) {
            Log.w("PlaybackManager", "Service start call non-fatal warning: ${e.message}")
        }
    }

    fun playSong(song: Song, customQueue: List<Song> = emptyList()) {
        playWithCrossfade {
            val currentList = if (customQueue.isNotEmpty()) customQueue else listOf(song)
            _queue.value = currentList

            val mediaItems = currentList.map { songItem -> buildMediaItem(songItem) }

            player.setMediaItems(mediaItems)
            val index = currentList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            player.seekTo(index, 0L)
            player.prepare()
            player.play()

            _currentSong.value = song
            _duration.value = song.duration

            saveCurrentState(song.id, 0L)
            startPlaybackService()
        }
    }

    fun playNext(song: Song) {
        val currentQueue = _queue.value.toMutableList()
        val currentIndex = player.currentMediaItemIndex
        val mediaItem = buildMediaItem(song)

        if (currentIndex < currentQueue.size) {
            currentQueue.add(currentIndex + 1, song)
            player.addMediaItem(currentIndex + 1, mediaItem)
        } else {
            currentQueue.add(song)
            player.addMediaItem(mediaItem)
        }
        _queue.value = currentQueue
        saveCurrentState(_currentSong.value?.id ?: song.id, player.currentPosition)
    }

    fun addToQueue(song: Song) {
        val currentQueue = _queue.value.toMutableList()
        currentQueue.add(song)
        val mediaItem = buildMediaItem(song)
        player.addMediaItem(mediaItem)
        _queue.value = currentQueue
        saveCurrentState(_currentSong.value?.id ?: song.id, player.currentPosition)
    }

    fun removeFromQueue(index: Int) {
        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
            val updatedQueue = _queue.value.toMutableList()
            if (index < updatedQueue.size) {
                updatedQueue.removeAt(index)
                _queue.value = updatedQueue
                saveCurrentState(_currentSong.value?.id ?: "", player.currentPosition)
            }
        }
    }

    fun clearQueue() {
        if (player.mediaItemCount > 0) {
            player.clearMediaItems()
            _queue.value = emptyList()
            _currentSong.value = null
            saveCurrentState("", 0L)
        }
    }

    fun onSongDeleted(songId: String) {
        val current = _currentSong.value
        val currentQueue = _queue.value.toMutableList()
        val index = currentQueue.indexOfFirst { it.id == songId }
        if (index != -1) {
            if (current?.id == songId) {
                if (currentQueue.size > 1) {
                    skipNext()
                    currentQueue.removeAt(index)
                    if (index < player.mediaItemCount) {
                        player.removeMediaItem(index)
                    }
                    _queue.value = currentQueue
                } else {
                    clearQueue()
                }
            } else {
                currentQueue.removeAt(index)
                if (index < player.mediaItemCount) {
                    player.removeMediaItem(index)
                }
                _queue.value = currentQueue
            }
        }
    }

    fun playPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
            startPlaybackService()
        }
    }

    fun skipNext() {
        playWithCrossfade {
            if (player.hasNextMediaItem()) {
                player.seekToNext()
            }
        }
    }

    fun skipPrevious() {
        playWithCrossfade {
            if (player.hasPreviousMediaItem()) {
                player.seekToPrevious()
            } else {
                player.seekTo(0L)
            }
        }
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
        _currentPosition.value = position
    }

    fun setShuffleMode(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
        _shuffleMode.value = enabled
    }

    fun setRepeatMode(mode: Int) {
        player.repeatMode = mode
        _repeatMode.value = mode
    }

    fun setPlaybackRate(speed: Float, pitch: Float) {
        val params = PlaybackParameters(speed, pitch)
        player.playbackParameters = params
        _playbackSpeed.value = speed
        _playbackPitch.value = pitch
    }

    fun toggleEqualizer() {
        val nextState = !_equalizerEnabled.value
        _equalizerEnabled.value = nextState
        saveEqualizerState()
        ensureAudioEffectsInitialized()
        try {
            equalizer?.enabled = nextState
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error toggling equalizer: ${e.message}")
        }
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelMb: Int) {
        val current = _equalizerBands.value.toMutableList()
        while (current.size <= bandIndex) {
            current.add(0)
        }
        current[bandIndex] = levelMb
        _equalizerBands.value = current
        _equalizerPreset.value = "Custom"
        saveEqualizerState()

        ensureAudioEffectsInitialized()
        try {
            equalizer?.let { eq ->
                val range = eq.bandLevelRange
                val minMb = range?.getOrNull(0) ?: -1500
                val maxMb = range?.getOrNull(1) ?: 1500
                val clampedMb = levelMb.toShort().coerceIn(minMb, maxMb)
                if (bandIndex < eq.numberOfBands) {
                    eq.setBandLevel(bandIndex.toShort(), clampedMb)
                }
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Error updating EQ band level: ${e.message}")
        }
    }

    fun setEqualizerPreset(presetName: String) {
        _equalizerPreset.value = presetName
        val presetBands = when (presetName) {
            "Bass Booster" -> listOf(600, 400, 0, 0, 0)
            "Bass Reducer" -> listOf(-600, -400, 0, 0, 0)
            "Treble Booster" -> listOf(0, 0, 0, 400, 600)
            "Vocal Booster" -> listOf(-200, 300, 500, 300, -200)
            "Rock" -> listOf(400, 200, -100, 300, 500)
            "Pop" -> listOf(-100, 200, 500, 300, -100)
            "Jazz" -> listOf(300, 200, -200, 200, 400)
            "Classical" -> listOf(500, 300, -200, 300, 400)
            "Heavy Metal" -> listOf(400, 100, 900, 300, -100)
            "Acoustic" -> listOf(300, 100, 200, 300, 200)
            else -> listOf(0, 0, 0, 0, 0) // "Flat" / Default
        }
        _equalizerBands.value = presetBands
        saveEqualizerState()

        ensureAudioEffectsInitialized()
        try {
            equalizer?.let { eq ->
                val range = eq.bandLevelRange
                val minMb = range?.getOrNull(0) ?: -1500
                val maxMb = range?.getOrNull(1) ?: 1500
                for (i in presetBands.indices) {
                    if (i < eq.numberOfBands) {
                        val clampedMb = presetBands[i].toShort().coerceIn(minMb, maxMb)
                        eq.setBandLevel(i.toShort(), clampedMb)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Error applying EQ preset: ${e.message}")
        }
    }

    fun setBassBoost(strength: Int) { // 0 to 1000
        _bassBoostStrength.value = strength
        saveEqualizerState()
        ensureAudioEffectsInitialized()
        try {
            bassBoost?.let { boost ->
                boost.enabled = strength > 0
                if (strength > 0) {
                    boost.setStrength(strength.coerceIn(0, 1000).toShort())
                }
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error setting bass boost strength: ${e.message}")
        }
    }

    fun setVirtualizer(strength: Int) { // 0 to 1000
        _virtualizerStrength.value = strength
        saveEqualizerState()
        ensureAudioEffectsInitialized()
        try {
            virtualizer?.let { virt ->
                virt.enabled = strength > 0
                if (strength > 0) {
                    virt.setStrength(strength.coerceIn(0, 1000).toShort())
                }
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Error setting virtualizer strength: ${e.message}")
        }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        settingsManager.setCrossfadeSeconds(seconds)
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimer?.cancel()
        if (minutes <= 0) {
            _sleepTimerMillis.value = 0L
            return
        }

        val totalMs = minutes * 60 * 1000L
        _sleepTimerMillis.value = totalMs

        sleepTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    handler.post {
                        val currentLeft = _sleepTimerMillis.value - 1000L
                        if (currentLeft <= 0) {
                            player.pause()
                            _sleepTimerMillis.value = 0L
                            cancel()
                        } else {
                            _sleepTimerMillis.value = currentLeft
                        }
                    }
                }
            }, 1000L, 1000L)
        }
    }

    fun stopSleepTimer() {
        sleepTimer?.cancel()
        _sleepTimerMillis.value = 0L
    }

    fun saveCurrentState(songId: String, position: Long) {
        val prefs = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE)
        val queueIds = _queue.value.joinToString(",") { it.id }
        val currentIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex.coerceAtLeast(0) else 0
        prefs.edit()
            .putString("last_song_id", songId)
            .putLong("last_position", position)
            .putString("last_queue_ids", queueIds)
            .putInt("last_queue_index", currentIndex)
            .apply()
    }

    private fun restorePlaybackState() {
        scope.launch {
            val prefs = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE)
            val lastSongId = prefs.getString("last_song_id", null)
            val lastPos = prefs.getLong("last_position", 0L)
            val lastQueueIds = prefs.getString("last_queue_ids", null)
            val lastQueueIndex = prefs.getInt("last_queue_index", 0)

            if (lastSongId != null) {
                val queueSongIds = lastQueueIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                val restoredQueue = mutableListOf<Song>()

                if (queueSongIds.isNotEmpty()) {
                    for (id in queueSongIds) {
                        repository.getSongById(id)?.let { restoredQueue.add(it) }
                    }
                }

                if (restoredQueue.isEmpty()) {
                    val singleSong = repository.getSongById(lastSongId)
                    if (singleSong != null) {
                        restoredQueue.add(singleSong)
                    }
                }

                if (restoredQueue.isNotEmpty()) {
                    _queue.value = restoredQueue
                    val startIndex = if (lastQueueIndex in restoredQueue.indices) {
                        lastQueueIndex
                    } else {
                        restoredQueue.indexOfFirst { it.id == lastSongId }.coerceAtLeast(0)
                    }
                    val targetSong = restoredQueue.getOrNull(startIndex) ?: restoredQueue.first()

                    _currentSong.value = targetSong
                    _currentPosition.value = lastPos
                    _duration.value = targetSong.duration

                    val mediaItems = restoredQueue.map { buildMediaItem(it) }
                    player.setMediaItems(mediaItems, startIndex, lastPos)
                    player.prepare()
                }
            }
        }
    }

    fun refreshCurrentSongMetadata(updatedSong: Song) {
        if (_currentSong.value?.id == updatedSong.id) {
            _currentSong.value = updatedSong
        }
        val currentQueue = _queue.value
        val index = currentQueue.indexOfFirst { it.id == updatedSong.id }
        if (index != -1) {
            val nextQueue = currentQueue.toMutableList()
            nextQueue[index] = updatedSong
            _queue.value = nextQueue
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PlaybackManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
