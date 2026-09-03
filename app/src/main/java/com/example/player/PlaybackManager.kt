package com.example.player

import android.content.Context
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
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
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null

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

    private val _equalizerEnabled = MutableStateFlow(true)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    // Poweramp 10-Band EQ Gains in dB (-15dB to +15dB)
    // Bands: 31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
    private val _eqBandLevels = MutableStateFlow(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
    val eqBandLevels: StateFlow<List<Float>> = _eqBandLevels.asStateFlow()

    private val _preampGain = MutableStateFlow(0f) // -15dB to +15dB
    val preampGain: StateFlow<Float> = _preampGain.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(300) // 0 to 1000 millibels (30%)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _trebleGain = MutableStateFlow(0f) // -15dB to +15dB
    val trebleGain: StateFlow<Float> = _trebleGain.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0) // 0 to 1000 millibels (Stereo Expansion)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    private val _audioBalance = MutableStateFlow(0f) // -1f (Left) to +1f (Right)
    val audioBalance: StateFlow<Float> = _audioBalance.asStateFlow()

    private val _reverbPreset = MutableStateFlow(PresetReverb.PRESET_NONE.toInt())
    val reverbPreset: StateFlow<Int> = _reverbPreset.asStateFlow()

    private val _currentPresetName = MutableStateFlow("Flat")
    val currentPresetName: StateFlow<String> = _currentPresetName.asStateFlow()

    private val _audioSessionId = MutableStateFlow(player.audioSessionId)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val _equalizerHardwareBands = MutableStateFlow(0)
    val equalizerHardwareBands: StateFlow<Int> = _equalizerHardwareBands.asStateFlow()

    private val _equalizerStatus = MutableStateFlow("DSP Engine Standby")
    val equalizerStatus: StateFlow<String> = _equalizerStatus.asStateFlow()

    private var sleepTimer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val settingsManager = SettingsManager(context)

    // Crossfade volume multiplier state (1.0f = full volume, fading down to 0.0f at track end, fading up from 0.0f at track start)
    private var fadeVolumeMultiplier = 1.0f
    private var isCrossfadingNext = false

    private val crossfadeStepRunnable = object : Runnable {
        override fun run() {
            val crossfadeSec = settingsManager.crossfadeSeconds.value
            if (crossfadeSec > 0 && player.isPlaying && player.duration > 0) {
                val currentPos = player.currentPosition
                val trackDuration = player.duration
                val crossfadeMs = crossfadeSec * 1000L
                val remainingMs = (trackDuration - currentPos).coerceAtLeast(0L)

                // 1. Fade-in near track beginning (< crossfadeMs)
                if (currentPos < crossfadeMs) {
                    val fadeInRatio = (currentPos.toFloat() / crossfadeMs.toFloat()).coerceIn(0.05f, 1.0f)
                    fadeVolumeMultiplier = fadeInRatio
                    updatePlayerVolume()
                }
                // 2. Fade-out near track ending (remainingMs < crossfadeMs)
                else if (remainingMs < crossfadeMs) {
                    val fadeOutRatio = (remainingMs.toFloat() / crossfadeMs.toFloat()).coerceIn(0.0f, 1.0f)
                    fadeVolumeMultiplier = fadeOutRatio
                    updatePlayerVolume()

                    // If faded out and player has next track, trigger seamless next
                    if (remainingMs <= 300L && !isCrossfadingNext && player.hasNextMediaItem()) {
                        isCrossfadingNext = true
                        player.seekToNext()
                        fadeVolumeMultiplier = 0.05f
                        updatePlayerVolume()
                        handler.postDelayed({ isCrossfadingNext = false }, 1500L)
                    }
                } else {
                    if (fadeVolumeMultiplier != 1.0f) {
                        fadeVolumeMultiplier = 1.0f
                        updatePlayerVolume()
                    }
                }
            } else {
                if (fadeVolumeMultiplier != 1.0f) {
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                }
            }
            handler.postDelayed(this, 100) // Smooth 100ms fade steps
        }
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val positionTrackerRunnable = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                _currentPosition.value = player.currentPosition
            }
            handler.postDelayed(this, 500) // Default delay for position tracking
        }
    }

    init {
        setupPlayerListeners()
        handler.post(positionTrackerRunnable)
        handler.post(crossfadeStepRunnable)
        restorePlaybackState()
        initializeMediaController()
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    mediaController = controllerFuture?.get()
                    Log.d("PlaybackManager", "MediaController connected successfully")
                } catch (e: Exception) {
                    Log.e("PlaybackManager", "Failed to connect MediaController", e)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                _isPlaying.value = isPlayingChanged
                _duration.value = player.duration.coerceAtLeast(0L)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val sid = player.audioSessionId
                    if (sid > 0) {
                        _audioSessionId.value = sid
                        initAudioEffects(sid)
                    }
                }
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
        })

        // Media3 AnalyticsListener for reliable AudioSessionId tracking
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                Log.d("PlaybackManager", "Audio session ID received via AnalyticsListener: $audioSessionId")
                if (audioSessionId > 0) {
                    _audioSessionId.value = audioSessionId
                    initAudioEffects(audioSessionId)
                }
            }
        })
    }

    private var lastAttachedSessionId: Int = -1

    private fun releaseAudioEffects() {
        try {
            equalizer?.release()
            equalizer = null
            bassBoost?.release()
            bassBoost = null
            virtualizer?.release()
            virtualizer = null
            presetReverb?.release()
            presetReverb = null
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error releasing audio effects: ${e.message}")
        }
    }

    private fun initAudioEffects(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (equalizer != null && lastAttachedSessionId == audioSessionId) {
            applyHardwareEqualizerBands()
            return
        }

        releaseAudioEffects()
        lastAttachedSessionId = audioSessionId

        try {
            val openIntent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
            }
            context.sendBroadcast(openIntent)
        } catch (e: Exception) {
            Log.w("PlaybackManager", "AudioEffect session broadcast error: ${e.message}")
        }

        try {
            val eq = Equalizer(1000, audioSessionId).apply {
                enabled = _equalizerEnabled.value
            }
            equalizer = eq
            val bandsCount = eq.numberOfBands.toInt()
            _equalizerHardwareBands.value = bandsCount
            _equalizerStatus.value = "Hardware DSP Active ($bandsCount HW Bands • Session #$audioSessionId)"
            Log.d("PlaybackManager", "Hardware Equalizer initialized: $bandsCount bands on session $audioSessionId")
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Hardware Equalizer activation error: ${e.message}")
            _equalizerStatus.value = "DSP Software Emulation Mode"
        }

        try {
            val boost = BassBoost(1000, audioSessionId).apply {
                enabled = _bassBoostStrength.value > 0
                if (strengthSupported) {
                    setStrength(_bassBoostStrength.value.toShort())
                }
            }
            bassBoost = boost
            Log.d("PlaybackManager", "Hardware BassBoost initialized (supported=${boost.strengthSupported})")
        } catch (e: Exception) {
            Log.w("PlaybackManager", "BassBoost activation skipped: ${e.message}")
        }

        try {
            val virt = Virtualizer(1000, audioSessionId).apply {
                enabled = _virtualizerStrength.value > 0
                if (strengthSupported) {
                    setStrength(_virtualizerStrength.value.toShort())
                }
            }
            virtualizer = virt
            Log.d("PlaybackManager", "Hardware Virtualizer initialized (supported=${virt.strengthSupported})")
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Virtualizer activation skipped: ${e.message}")
        }

        try {
            val reverb = PresetReverb(1000, audioSessionId).apply {
                enabled = _reverbPreset.value != PresetReverb.PRESET_NONE.toInt()
                if (enabled) {
                    preset = _reverbPreset.value.toShort()
                }
            }
            presetReverb = reverb
            player.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(reverb.id, 1.0f))
            Log.d("PlaybackManager", "Hardware PresetReverb attached to ExoPlayer AuxEffect")
        } catch (e: Exception) {
            Log.w("PlaybackManager", "PresetReverb activation skipped: ${e.message}")
        }

        applyHardwareEqualizerBands()
    }

    private val userBandFrequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    private fun updatePlayerVolume() {
        val preampDb = _preampGain.value
        // Real-time digital headroom/boost: 0dB = 1.0f, +6dB ~ 1.41f, -6dB ~ 0.5f
        val preampFactor = Math.pow(10.0, (preampDb / 20.0).toDouble()).toFloat().coerceIn(0.05f, 1.8f)
        val balance = _audioBalance.value
        val balFactor = if (Math.abs(balance) > 0.05f) 1f - (Math.abs(balance) * 0.15f) else 1f
        val finalVolume = (preampFactor * balFactor * fadeVolumeMultiplier).coerceIn(0f, 1.8f)
        player.volume = finalVolume
    }

    private fun applyHardwareEqualizerBands() {
        updatePlayerVolume()

        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val currentGains = _eqBandLevels.value
            val preamp = _preampGain.value
            val treble = _trebleGain.value
            val bassBoostFactor = _bassBoostStrength.value / 1000f

            val minLevel = eq.bandLevelRange?.get(0)?.toInt() ?: -1500
            val maxLevel = eq.bandLevelRange?.get(1)?.toInt() ?: 1500

            for (hwBand in 0 until numBands) {
                val hwFreqHz = try {
                    (eq.getCenterFreq(hwBand.toShort()) / 1000).coerceAtLeast(20)
                } catch (e: Exception) {
                    val ratio = hwBand.toFloat() / (numBands - 1).coerceAtLeast(1)
                    (31 * Math.pow(16000.0 / 31.0, ratio.toDouble())).toInt()
                }

                // Match with closest user band on logarithmic scale
                var closestIdx = 0
                var minDiff = Float.MAX_VALUE
                for (i in userBandFrequencies.indices) {
                    val diff = Math.abs(Math.log(hwFreqHz.toDouble()) - Math.log(userBandFrequencies[i].toDouble())).toFloat()
                    if (diff < minDiff) {
                        minDiff = diff
                        closestIdx = i
                    }
                }

                var targetGainDb = currentGains.getOrElse(closestIdx) { 0f } + preamp

                // Treble boost on high frequencies (> 3000 Hz)
                if (hwFreqHz >= 3000) {
                    val trebleRatio = ((hwFreqHz - 3000f) / 13000f).coerceIn(0.2f, 1f)
                    targetGainDb += treble * trebleRatio
                }

                // Bass boost reinforcement on low frequencies (<= 250 Hz)
                if (hwFreqHz <= 250) {
                    val bassRatio = (1f - (hwFreqHz / 250f)).coerceIn(0.2f, 1f)
                    targetGainDb += (bassBoostFactor * 6f) * bassRatio
                }

                val millibels = (targetGainDb * 100f).toInt().coerceIn(minLevel, maxLevel)
                eq.setBandLevel(hwBand.toShort(), millibels.toShort())
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error applying EQ band gains: ${e.message}")
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
            .setArtworkUri(fileUri)

        val artworkBytes = extractArtworkBytes(songItem.path)
        if (artworkBytes != null) {
            metadataBuilder.setArtworkData(artworkBytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return MediaItem.Builder()
            .setMediaId(songItem.id)
            .setUri(fileUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun startPlaybackService() {
        try {
            val intent = android.content.Intent(context, PlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Error starting PlaybackService: ${e.message}")
        }
    }

    fun playSong(song: Song, customQueue: List<Song> = emptyList()) {
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

    fun playPause() {
        val crossfadeSec = settingsManager.crossfadeSeconds.value
        if (player.isPlaying) {
            if (crossfadeSec > 0) {
                // Smooth fade-out before pause
                fadeVolumeMultiplier = 0.4f
                updatePlayerVolume()
                handler.postDelayed({
                    player.pause()
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                }, 90L)
            } else {
                player.pause()
            }
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            if (crossfadeSec > 0) {
                fadeVolumeMultiplier = 0.2f
                updatePlayerVolume()
                player.play()
                startPlaybackService()
                handler.postDelayed({
                    fadeVolumeMultiplier = 0.6f
                    updatePlayerVolume()
                    handler.postDelayed({
                        fadeVolumeMultiplier = 1.0f
                        updatePlayerVolume()
                    }, 80L)
                }, 70L)
            } else {
                player.play()
                startPlaybackService()
            }
        }
    }

    fun skipNext() {
        val crossfadeSec = settingsManager.crossfadeSeconds.value
        if (crossfadeSec > 0 && player.isPlaying) {
            fadeVolumeMultiplier = 0.2f
            updatePlayerVolume()
            handler.postDelayed({
                if (player.hasNextMediaItem()) {
                    player.seekToNext()
                }
                handler.postDelayed({
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                }, 100L)
            }, 80L)
        } else {
            if (player.hasNextMediaItem()) {
                player.seekToNext()
            }
        }
    }

    fun skipPrevious() {
        val crossfadeSec = settingsManager.crossfadeSeconds.value
        if (crossfadeSec > 0 && player.isPlaying) {
            fadeVolumeMultiplier = 0.2f
            updatePlayerVolume()
            handler.postDelayed({
                if (player.hasPreviousMediaItem()) {
                    player.seekToPrevious()
                } else {
                    player.seekTo(0L)
                }
                handler.postDelayed({
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                }, 100L)
            }, 80L)
        } else {
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
        try {
            equalizer?.enabled = nextState
            if (nextState) {
                applyHardwareEqualizerBands()
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error toggling equalizer: ${e.message}")
        }
    }

    fun setEqBandLevel(bandIndex: Int, levelDb: Float) {
        val current = _eqBandLevels.value.toMutableList()
        if (bandIndex in current.indices) {
            current[bandIndex] = levelDb.coerceIn(-15f, 15f)
            _eqBandLevels.value = current
            _currentPresetName.value = "Custom"
            applyHardwareEqualizerBands()
        }
    }

    fun setPreampGain(gainDb: Float) {
        _preampGain.value = gainDb.coerceIn(-15f, 15f)
        applyHardwareEqualizerBands()
    }

    fun setTrebleGain(gainDb: Float) {
        _trebleGain.value = gainDb.coerceIn(-15f, 15f)
        applyHardwareEqualizerBands()
    }

    fun setBassBoost(strength: Int) { // 0 to 1000
        val clamped = strength.coerceIn(0, 1000)
        _bassBoostStrength.value = clamped
        try {
            bassBoost?.let { boost ->
                boost.enabled = clamped > 0
                if (clamped > 0 && boost.strengthSupported) {
                    boost.setStrength(clamped.toShort())
                }
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error setting bass boost strength: ${e.message}")
        }
        applyHardwareEqualizerBands()
    }

    fun setVirtualizerStrength(strength: Int) { // 0 to 1000
        val clamped = strength.coerceIn(0, 1000)
        _virtualizerStrength.value = clamped
        try {
            virtualizer?.let { virt ->
                virt.enabled = clamped > 0
                if (clamped > 0 && virt.strengthSupported) {
                    virt.setStrength(clamped.toShort())
                }
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error setting virtualizer strength: ${e.message}")
        }
    }

    fun setAudioBalance(balance: Float) { // -1.0f (Full Left) to +1.0f (Full Right)
        val clamped = balance.coerceIn(-1f, 1f)
        _audioBalance.value = clamped
        updatePlayerVolume()
    }

    fun setReverbPreset(presetId: Int) {
        _reverbPreset.value = presetId
        try {
            presetReverb?.let { reverb ->
                reverb.enabled = presetId != PresetReverb.PRESET_NONE.toInt()
                if (reverb.enabled) {
                    reverb.preset = presetId.toShort()
                }
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error setting reverb preset: ${e.message}")
        }
    }

    fun applyPowerampPreset(
        presetName: String,
        bandGains: List<Float>,
        bassBoost: Int = 300,
        treble: Float = 0f,
        virtualizer: Int = 0,
        reverb: Int = PresetReverb.PRESET_NONE.toInt()
    ) {
        _currentPresetName.value = presetName
        _eqBandLevels.value = bandGains
        setBassBoost(bassBoost)
        setTrebleGain(treble)
        setVirtualizerStrength(virtualizer)
        setReverbPreset(reverb)
        applyHardwareEqualizerBands()
    }

    fun resetEqualizerToFlat() {
        applyPowerampPreset(
            presetName = "Flat",
            bandGains = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            bassBoost = 0,
            treble = 0f,
            virtualizer = 0,
            reverb = PresetReverb.PRESET_NONE.toInt()
        )
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
