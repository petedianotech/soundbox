package com.example.player

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.media.AudioManager
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.util.HeadsetPlugReceiver
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
import kotlinx.coroutines.flow.firstOrNull
import java.util.Timer
import java.util.TimerTask

class PlaybackManager private constructor(private val context: Context) {

    private val repository = MusicRepository.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Primary ExoPlayer reference
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

    // Secondary Auxiliary ExoPlayer for true Poweramp-style dual-engine overlapping crossfade
    val fadePlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build(),
            false // Do not steal primary audio focus
        )
        .setHandleAudioBecomingNoisy(false)
        .build()

    private var crossfadeJob: Job? = null
    @Volatile
    private var isCrossfading = false

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

    // Crossfade volume multiplier state (1.0f = full volume, smoothly ramping down at track end and up at track start)
    private var fadeVolumeMultiplier = 1.0f

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val positionTrackerRunnable = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                val curPos = player.currentPosition
                val dur = player.duration
                _currentPosition.value = curPos

                // Automatic Poweramp Auto-Crossfade detection at track ending
                val crossfadeSec = settingsManager.crossfadeSeconds.value
                if (crossfadeSec > 0 && dur > 4000L && !isCrossfading) {
                    val remainingMs = dur - curPos
                    val triggerWindow = (crossfadeSec * 1000L).coerceIn(1000L, 8000L)
                    // If within the crossfade window before track ends
                    if (remainingMs in 200L..triggerWindow) {
                        val currentQueue = _queue.value
                        val currentIndex = player.currentMediaItemIndex
                        val nextSong = when {
                            _shuffleMode.value && currentQueue.size > 1 -> {
                                currentQueue.filter { it.id != _currentSong.value?.id }.randomOrNull()
                            }
                            currentIndex + 1 < currentQueue.size -> {
                                currentQueue[currentIndex + 1]
                            }
                            _repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty() -> {
                                currentQueue.first()
                            }
                            else -> null
                        }

                        if (nextSong != null) {
                            performPowerampCrossfade(nextSong)
                        }
                    }
                }
            }
            handler.postDelayed(this, 250) // Frequent check for precise auto-crossfade triggering
        }
    }

    init {
        setupPlayerListeners()
        handler.post(positionTrackerRunnable)
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
                com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val sid = player.audioSessionId
                    if (sid > 0) {
                        _audioSessionId.value = sid
                        initAudioEffects(sid)
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    val currentQueue = _queue.value
                    if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) {
                        val firstSong = currentQueue.first()
                        playSongDirect(firstSong, currentQueue)
                    } else if (_shuffleMode.value && currentQueue.size > 1) {
                        val nextSong = currentQueue.filter { it.id != _currentSong.value?.id }.randomOrNull() ?: currentQueue.first()
                        playSongDirect(nextSong, currentQueue)
                    } else {
                        _isPlaying.value = false
                        saveCurrentState(_currentSong.value?.id ?: "", 0L)
                    }
                }
                com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isCrossfading) return
                val mediaId = mediaItem?.mediaId
                if (mediaId != null) {
                    val inMemorySong = _queue.value.find { it.id == mediaId }
                    if (inMemorySong != null) {
                        _currentSong.value = inMemorySong
                        _duration.value = inMemorySong.duration
                    }
                    scope.launch {
                        val song = repository.getSongById(mediaId)
                        if (song != null) {
                            _currentSong.value = song
                            _duration.value = song.duration
                            repository.incrementPlayCount(song.id)
                            saveCurrentState(song.id, player.currentPosition)
                            com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
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

        // Dynamically register HeadsetPlugReceiver for reliable wired & bluetooth automation
        try {
            val headsetFilter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context.applicationContext,
                HeadsetPlugReceiver(),
                headsetFilter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
            Log.d("PlaybackManager", "Registered HeadsetPlugReceiver dynamically for automation")
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Could not register HeadsetPlugReceiver: ${e.message}")
        }
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
            fadePlayer.stop()
            fadePlayer.clearMediaItems()
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

    fun getTargetMasterVolume(): Float {
        val preampDb = _preampGain.value
        // Real-time digital headroom/boost: 0dB = 1.0f, +6dB ~ 1.41f, -6dB ~ 0.5f
        val preampFactor = Math.pow(10.0, (preampDb / 20.0).toDouble()).toFloat().coerceIn(0.05f, 1.8f)
        val balance = _audioBalance.value
        val balFactor = if (Math.abs(balance) > 0.05f) 1f - (Math.abs(balance) * 0.15f) else 1f
        return (preampFactor * balFactor).coerceIn(0.05f, 1.8f)
    }

    private fun updatePlayerVolume() {
        if (isCrossfading) return
        val finalVolume = (getTargetMasterVolume() * fadeVolumeMultiplier).coerceIn(0f, 1.8f)
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

    private fun buildMediaItem(songItem: Song): MediaItem {
        val fileUri = if (songItem.path.startsWith("content://") || songItem.path.startsWith("file://")) {
            android.net.Uri.parse(songItem.path)
        } else {
            android.net.Uri.fromFile(java.io.File(songItem.path))
        }

        val artUri = com.example.util.AlbumArtHelper.getArtworkUri(context, songItem)

        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(songItem.title)
            .setArtist(songItem.artist)
            .setAlbumTitle(songItem.album)
            .setDisplayTitle(songItem.title)
            .setArtworkUri(artUri)

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

    /**
     * Poweramp-grade smooth acoustic crossfade transition.
     * The outgoing track cleanly lowers its volume without any volume spikes or surges,
     * while the incoming track smoothly rises from silence.
     */
    fun performPowerampCrossfade(nextSong: Song) {
        val crossfadeSec = settingsManager.crossfadeSeconds.value
        val current = _currentSong.value

        if (crossfadeSec <= 0 || current == null || !player.isPlaying) {
            playSongDirect(nextSong, _queue.value)
            return
        }

        if (isCrossfading) {
            crossfadeJob?.cancel()
            try {
                fadePlayer.stop()
                fadePlayer.clearMediaItems()
                fadePlayer.volume = 0f
            } catch (e: Exception) {
                // Ignore
            }
            isCrossfading = false
        }

        crossfadeJob = scope.launch(Dispatchers.Main) {
            isCrossfading = true
            try {
                val crossfadeDurationMs = (crossfadeSec * 1000L).coerceIn(1000L, 8000L)
                val masterVol = getTargetMasterVolume()

                // 1. Prepare incoming track on auxiliary fadePlayer starting at zero volume
                val incomingMediaItem = buildMediaItem(nextSong)
                fadePlayer.setMediaItem(incomingMediaItem)
                fadePlayer.volume = 0f
                fadePlayer.prepare()
                fadePlayer.play()

                val currentQueue = _queue.value
                val nextIndex = currentQueue.indexOfFirst { it.id == nextSong.id }.coerceAtLeast(0)
                val mediaItems = if (currentQueue.isNotEmpty()) {
                    currentQueue.map { buildMediaItem(it) }
                } else {
                    listOf(incomingMediaItem)
                }

                // Update UI state immediately so NowPlaying, notification, and widgets reflect next song
                _currentSong.value = nextSong
                _duration.value = nextSong.duration
                repository.incrementPlayCount(nextSong.id)
                saveCurrentState(nextSong.id, 0L)
                com.example.widget.SoundboxAppWidget.updateAllWidgets(context)

                // 2. Smooth volume progression:
                // Outgoing track (player) strictly LOWERS its volume down to 0.
                // Incoming track (fadePlayer) smoothly RISES from 0 to masterVol.
                // Total combined volume is capped <= masterVol so volume NEVER rises/spikes.
                val startTime = System.currentTimeMillis()
                while (isActive) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)

                    // Acoustic dip curve: gently softens the mix center, strictly preventing any volume surge
                    val outFactor = Math.pow((1f - progress).toDouble(), 1.25).toFloat()
                    val inFactor = Math.pow(progress.toDouble(), 1.25).toFloat()

                    player.volume = (outFactor * masterVol).coerceIn(0f, masterVol)
                    fadePlayer.volume = (inFactor * masterVol).coerceIn(0f, masterVol)

                    if (progress >= 1f) break
                    delay(25)
                }

                // 3. Seamless handoff: Primary player takes over incoming track from fadePlayer position
                val handoffPosMs = fadePlayer.currentPosition.coerceAtLeast(0L)
                player.setMediaItems(mediaItems, nextIndex, handoffPosMs)
                player.volume = masterVol
                player.prepare()
                player.play()

                fadePlayer.stop()
                fadePlayer.clearMediaItems()
                fadePlayer.volume = 0f
            } catch (e: Exception) {
                Log.w("PlaybackManager", "Crossfade transition interrupted: ${e.message}")
            } finally {
                try {
                    fadePlayer.stop()
                    fadePlayer.clearMediaItems()
                    fadePlayer.volume = 0f
                } catch (e: Exception) {
                    // Safe cleanup
                }
                isCrossfading = false
                fadeVolumeMultiplier = 1.0f
                updatePlayerVolume()
            }
        }
    }

    private fun playSongDirect(song: Song, customQueue: List<Song> = emptyList()) {
        try {
            crossfadeJob?.cancel()
            fadePlayer.stop()
            fadePlayer.clearMediaItems()
            fadePlayer.volume = 0f
            isCrossfading = false
        } catch (e: Exception) {
            // Ignore
        }

        val currentList = if (customQueue.isNotEmpty()) customQueue else listOf(song)
        _queue.value = currentList

        _currentSong.value = song
        _duration.value = song.duration
        scope.launch {
            repository.incrementPlayCount(song.id)
        }

        val mediaItems = currentList.map { songItem -> buildMediaItem(songItem) }
        val index = currentList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        fadeVolumeMultiplier = 1.0f
        player.setMediaItems(mediaItems, index, 0L)
        updatePlayerVolume()
        player.prepare()
        player.play()

        saveCurrentState(song.id, 0L)
        startPlaybackService()
        com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
    }

    fun playSong(song: Song, customQueue: List<Song> = emptyList()) {
        val currentList = if (customQueue.isNotEmpty()) customQueue else listOf(song)
        _queue.value = currentList

        val crossfadeSec = settingsManager.crossfadeSeconds.value
        if (crossfadeSec > 0 && player.isPlaying && _currentSong.value != null && _currentSong.value?.id != song.id) {
            performPowerampCrossfade(song)
        } else {
            playSongDirect(song, currentList)
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

    fun playPause() {
        if (player.isPlaying) {
            // Poweramp smooth acoustic ramp-down before pause (prevents pop/click)
            fadeVolumeMultiplier = 0.35f
            updatePlayerVolume()
            handler.postDelayed({
                player.pause()
                fadeVolumeMultiplier = 1.0f
                updatePlayerVolume()
            }, 100L)
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            fadeVolumeMultiplier = 0.25f
            updatePlayerVolume()
            player.play()
            startPlaybackService()
            handler.postDelayed({
                fadeVolumeMultiplier = 0.7f
                updatePlayerVolume()
                handler.postDelayed({
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                }, 80L)
            }, 70L)
        }
    }

    /**
     * Resumes playback automatically when wired headphones or Bluetooth audio connects.
     * Works reliably even when the app was in the background and paused.
     */
    fun resumeOnHeadsetConnected() {
        scope.launch {
            if (_isPlaying.value || player.isPlaying) return@launch

            // If player already has media items loaded (e.g. was paused in background)
            if (player.mediaItemCount > 0) {
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                player.playWhenReady = true
                fadeVolumeMultiplier = 0.3f
                updatePlayerVolume()
                player.play()
                startPlaybackService()
                handler.postDelayed({
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                }, 120L)
                return@launch
            }

            // If player queue is empty, restore last saved song or pick library first
            val targetSong = _currentSong.value ?: run {
                val prefs = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE)
                val lastSongId = prefs.getString("last_song_id", null)
                if (lastSongId != null) repository.getSongById(lastSongId) else null
            } ?: repository.allSongs.firstOrNull()?.firstOrNull()

            if (targetSong != null) {
                playSong(targetSong)
            }
        }
    }

    /**
     * Pauses playback automatically when wired headphones or Bluetooth audio disconnects.
     */
    fun pauseOnHeadsetDisconnected() {
        if (player.isPlaying || _isPlaying.value) {
            fadeVolumeMultiplier = 0.35f
            updatePlayerVolume()
            handler.postDelayed({
                player.pause()
                fadeVolumeMultiplier = 1.0f
                updatePlayerVolume()
            }, 80L)
        }
    }

    fun skipNext() {
        val crossfadeSec = settingsManager.crossfadeSeconds.value
        val currentQueue = _queue.value
        val currentIndex = player.currentMediaItemIndex

        val nextSong = when {
            _shuffleMode.value && currentQueue.size > 1 -> {
                currentQueue.filter { it.id != _currentSong.value?.id }.randomOrNull()
            }
            currentIndex + 1 < currentQueue.size -> {
                currentQueue[currentIndex + 1]
            }
            _repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty() -> {
                currentQueue.first()
            }
            else -> null
        }

        if (crossfadeSec > 0 && player.isPlaying && nextSong != null) {
            performPowerampCrossfade(nextSong)
        } else {
            if (nextSong != null) {
                val nextIndex = currentQueue.indexOfFirst { it.id == nextSong.id }
                if (nextIndex >= 0 && nextIndex < player.mediaItemCount) {
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                    player.seekTo(nextIndex, 0L)
                    player.play()
                } else {
                    playSongDirect(nextSong, currentQueue)
                }
            } else if (player.hasNextMediaItem()) {
                fadeVolumeMultiplier = 1.0f
                updatePlayerVolume()
                player.seekToNext()
            } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) {
                fadeVolumeMultiplier = 1.0f
                updatePlayerVolume()
                player.seekTo(0, 0L)
                player.play()
            }
        }
    }

    fun skipPrevious() {
        val crossfadeSec = settingsManager.crossfadeSeconds.value
        val currentQueue = _queue.value
        val currentIndex = player.currentMediaItemIndex

        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
            return
        }

        val prevSong = if (currentIndex - 1 >= 0 && currentIndex - 1 < currentQueue.size) {
            currentQueue[currentIndex - 1]
        } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) {
            currentQueue.last()
        } else null

        if (crossfadeSec > 0 && player.isPlaying && prevSong != null) {
            performPowerampCrossfade(prevSong)
        } else {
            if (prevSong != null) {
                val prevIndex = currentQueue.indexOfFirst { it.id == prevSong.id }
                if (prevIndex >= 0 && prevIndex < player.mediaItemCount) {
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                    player.seekTo(prevIndex, 0L)
                    player.play()
                } else {
                    playSongDirect(prevSong, currentQueue)
                }
            } else if (player.hasPreviousMediaItem()) {
                fadeVolumeMultiplier = 1.0f
                updatePlayerVolume()
                player.seekToPrevious()
            } else {
                fadeVolumeMultiplier = 1.0f
                updatePlayerVolume()
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

    private var sleepTimerFadeOutEnabled: Boolean = true

    fun startSleepTimer(minutes: Int, fadeOutAtEnd: Boolean = true) {
        startSleepTimerSeconds(minutes * 60, fadeOutAtEnd)
    }

    fun startSleepTimerSeconds(seconds: Int, fadeOutAtEnd: Boolean = true) {
        sleepTimer?.cancel()
        sleepTimerFadeOutEnabled = fadeOutAtEnd
        if (seconds <= 0) {
            _sleepTimerMillis.value = 0L
            fadeVolumeMultiplier = 1.0f
            updatePlayerVolume()
            return
        }

        val totalMs = seconds * 1000L
        _sleepTimerMillis.value = totalMs

        sleepTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    handler.post {
                        val currentLeft = _sleepTimerMillis.value - 1000L
                        if (currentLeft <= 0) {
                            fadeVolumeMultiplier = 1.0f
                            updatePlayerVolume()
                            player.pause()
                            _sleepTimerMillis.value = 0L
                            cancel()
                        } else {
                            _sleepTimerMillis.value = currentLeft
                            // Smooth acoustic fade-out during the final 15 seconds if enabled
                            if (sleepTimerFadeOutEnabled && currentLeft in 1L..15000L) {
                                fadeVolumeMultiplier = (currentLeft / 15000f).coerceIn(0.05f, 1.0f)
                                updatePlayerVolume()
                            } else if (fadeVolumeMultiplier < 1.0f) {
                                fadeVolumeMultiplier = 1.0f
                                updatePlayerVolume()
                            }
                        }
                    }
                }
            }, 1000L, 1000L)
        }
    }

    fun startSleepTimerEndOfTrack(fadeOutAtEnd: Boolean = true) {
        val remainingMs = (player.duration - player.currentPosition).coerceAtLeast(1000L)
        val remainingSec = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(5)
        startSleepTimerSeconds(remainingSec, fadeOutAtEnd)
    }

    fun extendSleepTimer(minutes: Int = 5) {
        val current = _sleepTimerMillis.value
        val additionalMs = minutes * 60 * 1000L
        val newDurationMs = if (current > 0) current + additionalMs else additionalMs
        startSleepTimerSeconds((newDurationMs / 1000L).toInt(), sleepTimerFadeOutEnabled)
    }

    fun stopSleepTimer() {
        sleepTimer?.cancel()
        _sleepTimerMillis.value = 0L
        fadeVolumeMultiplier = 1.0f
        updatePlayerVolume()
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
            if (_currentSong.value != null || player.currentMediaItem != null) {
                // Audio or song state is already active in memory; do not reset!
                return@launch
            }
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

    fun toggleFavorite(song: Song) {
        val targetFav = !song.isFavorite
        if (_currentSong.value?.id == song.id) {
            _currentSong.value = _currentSong.value?.copy(isFavorite = targetFav)
        }
        val currentQueue = _queue.value
        if (currentQueue.any { it.id == song.id }) {
            _queue.value = currentQueue.map {
                if (it.id == song.id) it.copy(isFavorite = targetFav) else it
            }
        }
        scope.launch(Dispatchers.IO) {
            repository.toggleFavorite(song.id, song.isFavorite)
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

    fun onSongTrimmed(updatedSong: Song) {
        refreshCurrentSongMetadata(updatedSong)
        if (_currentSong.value?.id == updatedSong.id) {
            _duration.value = updatedSong.duration
            _currentPosition.value = 0L
            val isCurrentlyPlaying = _isPlaying.value
            val currentList = _queue.value
            val mediaItems = currentList.map { songItem -> buildMediaItem(songItem) }
            val index = currentList.indexOfFirst { it.id == updatedSong.id }.coerceAtLeast(0)
            player.setMediaItems(mediaItems, index, 0L)
            player.prepare()
            if (isCurrentlyPlaying) {
                player.play()
            }
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
