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
import androidx.media3.common.PlaybackException
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

    // Memory-optimized LoadControl to keep background RAM footprint ultra-lean while ensuring skip-free playback
    private val lowMemoryLoadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15_000, // minBufferMs (15s is ample for music tracks)
            30_000, // maxBufferMs
            1_000,  // bufferForPlaybackMs
            2_000   // bufferForPlaybackAfterRebufferMs
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    // Studio DSP AudioProcessors for pristine Left/Right balance panning & spatial expansion
    val dspAudioProcessorA = SoundboxDspAudioProcessor()
    val dspAudioProcessorB = SoundboxDspAudioProcessor()

    // Backward-compatibility references for DSP processors
    val dspAudioProcessor get() = if (activePlayer == playerA) dspAudioProcessorA else dspAudioProcessorB
    val fadeDspAudioProcessor get() = if (activePlayer == playerA) dspAudioProcessorB else dspAudioProcessorA

    private fun createRenderersFactory(processor: SoundboxDspAudioProcessor): androidx.media3.exoplayer.DefaultRenderersFactory {
        return object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(processor))
                    .build()
            }
        }
    }

    // Two dedicated ExoPlayer instances for thread-safe crossfading
    private var playerA: ExoPlayer = ExoPlayer.Builder(context, createRenderersFactory(dspAudioProcessorA))
        .setLoadControl(lowMemoryLoadControl)
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

    private var playerB: ExoPlayer = ExoPlayer.Builder(context, createRenderersFactory(dspAudioProcessorB))
        .setLoadControl(lowMemoryLoadControl)
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

    var activePlayer: ExoPlayer = playerA
        private set
    var standbyPlayer: ExoPlayer = playerB
        private set

    // Primary player reference mapped directly to activePlayer for external consumers
    val player: ExoPlayer
        get() = activePlayer

    // Standby player reference mapped directly to standbyPlayer
    val fadePlayer: ExoPlayer
        get() = standbyPlayer

    var onActivePlayerChanged: ((ExoPlayer) -> Unit)? = null

    private var transitionJob: Job? = null
    // CoroutineScope explicitly bound to Main Thread for ExoPlayer thread safety
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var isCrossfading = false
    @Volatile
    private var crossfadeActiveSongId: String? = null

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

    private val settingsManager = SettingsManager(context)

    private val _sleepTimerMillis = MutableStateFlow(0L)
    val sleepTimerMillis: StateFlow<Long> = _sleepTimerMillis.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(settingsManager.isEqualizerEnabled())
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    // Poweramp 10-Band EQ Gains in dB (-15dB to +15dB)
    // Bands: 31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
    private val _eqBandLevels = MutableStateFlow(settingsManager.getEqualizerBandLevels())
    val eqBandLevels: StateFlow<List<Float>> = _eqBandLevels.asStateFlow()

    private val _preampGain = MutableStateFlow(settingsManager.getPreampGain()) // -15dB to +15dB
    val preampGain: StateFlow<Float> = _preampGain.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(settingsManager.getBassBoostStrength()) // 0 to 1000 millibels (30%)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _trebleGain = MutableStateFlow(settingsManager.getTrebleGain()) // -15dB to +15dB
    val trebleGain: StateFlow<Float> = _trebleGain.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(settingsManager.getVirtualizerStrength()) // 0 to 1000 millibels (Stereo Expansion)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    private val _audioBalance = MutableStateFlow(settingsManager.getAudioBalance()) // -1f (Left) to +1f (Right)
    val audioBalance: StateFlow<Float> = _audioBalance.asStateFlow()

    private val _reverbPreset = MutableStateFlow(settingsManager.getReverbPreset())
    val reverbPreset: StateFlow<Int> = _reverbPreset.asStateFlow()

    private val _currentPresetName = MutableStateFlow(settingsManager.getEqualizerPresetName())
    val currentPresetName: StateFlow<String> = _currentPresetName.asStateFlow()

    private val _audioSessionId = MutableStateFlow(player.audioSessionId)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val _equalizerHardwareBands = MutableStateFlow(0)
    val equalizerHardwareBands: StateFlow<Int> = _equalizerHardwareBands.asStateFlow()

    private val _equalizerStatus = MutableStateFlow("DSP Engine Standby")
    val equalizerStatus: StateFlow<String> = _equalizerStatus.asStateFlow()

    private var sleepTimer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())

    // Crossfade volume multiplier state (1.0f = full volume, smoothly ramping down at track end and up at track start)
    private var fadeVolumeMultiplier = 1.0f

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val positionTrackerRunnable = object : Runnable {
        override fun run() {
            if (_isPlaying.value || activePlayer.isPlaying || standbyPlayer.isPlaying) {
                if (isCrossfading) {
                    val curFadePos = standbyPlayer.currentPosition.coerceAtLeast(0L)
                    _currentPosition.value = curFadePos
                } else {
                    val curPos = activePlayer.currentPosition
                    val dur = activePlayer.duration
                    _currentPosition.value = curPos

                    // Automatic Auto-Crossfade detection at track ending
                    val isCrossfadeOn = settingsManager.crossfadeEnabled.value
                    val crossfadeSec = settingsManager.crossfadeSeconds.value
                    if (isCrossfadeOn && crossfadeSec > 0 && dur > 3000L && !isCrossfading && activePlayer.isPlaying) {
                        val remainingMs = dur - curPos
                        val maxFade = (dur / 2).coerceAtLeast(1000L)
                        val triggerWindow = (crossfadeSec * 1000L).coerceAtMost(maxFade).coerceIn(1000L, 15000L)
                        // If within the crossfade window before track ends
                        if (remainingMs in 100L..triggerWindow) {
                            val currentQueue = _queue.value
                            val currentIndex = activePlayer.currentMediaItemIndex
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

                            if (nextSong != null && nextSong.id != crossfadeActiveSongId) {
                                performPowerampCrossfade(nextSong)
                            }
                        }
                    }
                }
            }
            handler.postDelayed(this, 200) // Frequent check for precise auto-crossfade triggering
        }
    }

    init {
        try {
            NativeAudioEngine.init(44100, 2)
        } catch (e: Exception) {
            Log.w("PlaybackManager", "NativeAudioEngine init error: ${e.message}")
        }
        setupPlayerListeners()
        handler.post(positionTrackerRunnable)
        restorePlaybackState()
        initializeMediaController()

        // Observe crossfade setting - immediately cancel any active crossfade if disabled
        mainScope.launch {
            settingsManager.crossfadeEnabled.collect { enabled ->
                if (!enabled && isCrossfading) {
                    transitionJob?.cancel()
                    crossfadeActiveSongId = null
                    try {
                        standbyPlayer.stop()
                        standbyPlayer.clearMediaItems()
                        standbyPlayer.volume = 0f
                    } catch (e: Exception) {}
                    isCrossfading = false
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                }
            }
        }
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

    private fun attachPlayerListener(targetPlayer: ExoPlayer) {
        targetPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                if (targetPlayer != activePlayer) return
                if (isCrossfading) {
                    _isPlaying.value = true
                    return
                }
                _isPlaying.value = isPlayingChanged
                _duration.value = activePlayer.duration.coerceAtLeast(0L)
                com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (targetPlayer != activePlayer) return
                if (isCrossfading) return
                if (playbackState == Player.STATE_READY) {
                    val sid = activePlayer.audioSessionId
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
                if (targetPlayer != activePlayer) return
                if (isCrossfading) return
                val mediaId = mediaItem?.mediaId
                if (mediaId != null) {
                    val inMemorySong = _queue.value.find { it.id == mediaId }
                    if (inMemorySong != null) {
                        _currentSong.value = inMemorySong
                        _duration.value = inMemorySong.duration
                    }
                    mainScope.launch {
                        val song = repository.getSongById(mediaId)
                        if (song != null) {
                            _currentSong.value = song
                            _duration.value = song.duration
                            withContext(Dispatchers.IO) {
                                repository.incrementPlayCount(song.id)
                            }
                            saveCurrentState(song.id, activePlayer.currentPosition)
                            com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
                        }
                    }
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                if (targetPlayer == activePlayer) {
                    _playbackSpeed.value = playbackParameters.speed
                    _playbackPitch.value = playbackParameters.pitch
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (targetPlayer == activePlayer) {
                    _shuffleMode.value = shuffleModeEnabled
                }
            }

            override fun onRepeatModeChanged(newRepeatMode: Int) {
                if (targetPlayer == activePlayer) {
                    _repeatMode.value = newRepeatMode
                }
            }
        })

        // Media3 AnalyticsListener for reliable AudioSessionId tracking
        targetPlayer.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                Log.d("PlaybackManager", "Audio session ID received via AnalyticsListener: $audioSessionId")
                if (targetPlayer == activePlayer && audioSessionId > 0) {
                    _audioSessionId.value = audioSessionId
                    initAudioEffects(audioSessionId)
                }
            }
        })
    }

    private fun setupPlayerListeners() {
        attachPlayerListener(playerA)
        attachPlayerListener(playerB)

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
            val reverb = try {
                PresetReverb(0, 0)
            } catch (e: Exception) {
                PresetReverb(0, audioSessionId)
            }
            val currentPreset = _reverbPreset.value
            if (currentPreset != PresetReverb.PRESET_NONE.toInt()) {
                reverb.preset = currentPreset.toShort()
                reverb.enabled = true
                player.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(reverb.id, 1.0f))
                fadePlayer.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(reverb.id, 1.0f))
            } else {
                reverb.enabled = false
                player.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(androidx.media3.common.AuxEffectInfo.NO_AUX_EFFECT_ID, 0f))
                fadePlayer.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(androidx.media3.common.AuxEffectInfo.NO_AUX_EFFECT_ID, 0f))
            }
            presetReverb = reverb
            Log.d("PlaybackManager", "Hardware PresetReverb attached to ExoPlayer AuxEffect")
        } catch (e: Exception) {
            Log.w("PlaybackManager", "PresetReverb activation skipped: ${e.message}")
        }

        // Sync DSP AudioProcessors with persisted settings
        dspAudioProcessor.balance = _audioBalance.value
        dspAudioProcessor.virtualizerStrength = _virtualizerStrength.value
        fadeDspAudioProcessor.balance = _audioBalance.value
        fadeDspAudioProcessor.virtualizerStrength = _virtualizerStrength.value

        applyHardwareEqualizerBands()
    }

    private val userBandFrequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    fun getTargetMasterVolume(): Float {
        val preampDb = _preampGain.value
        // Real-time digital headroom/boost: 0dB = 1.0f, +6dB ~ 1.41f, -6dB ~ 0.5f
        return Math.pow(10.0, (preampDb / 20.0).toDouble()).toFloat().coerceIn(0.05f, 1.8f)
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
     * Studio-grade smooth acoustic crossfade transition using SafeAudioCrossfader double-player architecture.
     * The outgoing track on [activePlayer] continues playing seamlessly while fading its volume down,
     * while the incoming track starts playing on [standbyPlayer] from position 0 fading its volume up.
     * All ExoPlayer operations and volume ramping run strictly on Dispatchers.Main.
     */
    fun performPowerampCrossfade(nextSong: Song, customQueue: List<Song> = emptyList()) {
        val isCrossfadeOn = settingsManager.crossfadeEnabled.value && settingsManager.crossfadeSeconds.value > 0
        if (!isCrossfadeOn || !activePlayer.isPlaying || _currentSong.value == null || _currentSong.value?.id == nextSong.id) {
            playSongDirect(nextSong, customQueue)
            return
        }

        // Safely cancel any in-flight transition job
        transitionJob?.cancel()

        val currentList = if (customQueue.isNotEmpty()) customQueue else _queue.value.ifEmpty { listOf(nextSong) }
        _queue.value = currentList

        val userSec = settingsManager.crossfadeSeconds.value
        val fadeDurationMs = (userSec * 1000L).coerceAtMost(
            (nextSong.duration / 2).coerceAtLeast(1000L)
        ).coerceIn(1000L, 15000L)
        val stepIntervalMs = 40L

        isCrossfading = true
        crossfadeActiveSongId = nextSong.id
        _currentSong.value = nextSong
        _duration.value = nextSong.duration

        mainScope.launch(Dispatchers.IO) {
            repository.incrementPlayCount(nextSong.id)
        }

        val masterVol = getTargetMasterVolume()

        transitionJob = mainScope.launch(Dispatchers.Main) {
            try {
                // Prepare standby player strictly on Main thread
                standbyPlayer.stop()
                standbyPlayer.clearMediaItems()
                val mediaItems = currentList.map { songItem -> buildMediaItem(songItem) }
                val nextIndex = currentList.indexOfFirst { it.id == nextSong.id }.coerceAtLeast(0)
                standbyPlayer.setMediaItems(mediaItems, nextIndex, 0L)
                standbyPlayer.volume = 0f
                standbyPlayer.prepare()
                standbyPlayer.playWhenReady = true

                val totalSteps = (fadeDurationMs / stepIntervalMs).toInt().coerceAtLeast(1)

                // Volume ramp loops safely inside cancellable job on Dispatchers.Main
                for (step in 1..totalSteps) {
                    val progress = step.toFloat() / totalSteps.toFloat()

                    if (activePlayer.playbackState == Player.STATE_READY || activePlayer.isPlaying) {
                        activePlayer.volume = ((1f - progress) * masterVol).coerceIn(0f, 1.8f)
                    }
                    standbyPlayer.volume = (progress * masterVol).coerceIn(0f, 1.8f)

                    delay(stepIntervalMs)
                }

                // Complete transition: stop active player and finalize volumes
                activePlayer.stop()
                activePlayer.clearMediaItems()
                activePlayer.volume = 0f
                standbyPlayer.volume = masterVol

                // Swap active and standby roles
                val previousActive = activePlayer
                activePlayer = standbyPlayer
                standbyPlayer = previousActive

                isCrossfading = false
                crossfadeActiveSongId = null
                fadeVolumeMultiplier = 1.0f

                _isPlaying.value = activePlayer.isPlaying
                _duration.value = activePlayer.duration.coerceAtLeast(0L)
                _currentPosition.value = activePlayer.currentPosition.coerceAtLeast(0L)

                val newSessionId = activePlayer.audioSessionId
                if (newSessionId > 0 && newSessionId != _audioSessionId.value) {
                    _audioSessionId.value = newSessionId
                    initAudioEffects(newSessionId)
                }

                onActivePlayerChanged?.invoke(activePlayer)
                saveCurrentState(nextSong.id, activePlayer.currentPosition)
                startPlaybackService()
                com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
            } catch (c: CancellationException) {
                // Coroutine cancelled cleanly
                Log.d("PlaybackManager", "Crossfade transition cancelled")
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Error during crossfade transition: ${e.message}", e)
                isCrossfading = false
                crossfadeActiveSongId = null
                updatePlayerVolume()
            }
        }
    }

    private fun playSongDirect(song: Song, customQueue: List<Song> = emptyList()) {
        transitionJob?.cancel()
        isCrossfading = false
        crossfadeActiveSongId = null

        mainScope.launch(Dispatchers.Main) {
            try {
                standbyPlayer.stop()
                standbyPlayer.clearMediaItems()
                standbyPlayer.volume = 0f
            } catch (e: Exception) {
                Log.w("PlaybackManager", "Error resetting standby player: ${e.message}")
            }

            val currentList = if (customQueue.isNotEmpty()) customQueue else listOf(song)
            _queue.value = currentList

            _currentSong.value = song
            _duration.value = song.duration
            withContext(Dispatchers.IO) {
                repository.incrementPlayCount(song.id)
            }

            val mediaItems = currentList.map { songItem -> buildMediaItem(songItem) }
            val index = currentList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

            fadeVolumeMultiplier = 1.0f
            val masterVol = getTargetMasterVolume()
            activePlayer.stop()
            activePlayer.clearMediaItems()
            activePlayer.setMediaItems(mediaItems, index, 0L)
            activePlayer.volume = masterVol
            activePlayer.prepare()
            activePlayer.playWhenReady = true

            val sid = activePlayer.audioSessionId
            if (sid > 0) {
                _audioSessionId.value = sid
                initAudioEffects(sid)
            }

            saveCurrentState(song.id, 0L)
            startPlaybackService()
            com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
        }
    }

    fun playSong(song: Song, customQueue: List<Song> = emptyList()) {
        val currentList = if (customQueue.isNotEmpty()) customQueue else listOf(song)
        _queue.value = currentList

        val isCrossfadeOn = settingsManager.crossfadeEnabled.value && settingsManager.crossfadeSeconds.value > 0
        if (isCrossfadeOn && activePlayer.isPlaying && _currentSong.value != null && _currentSong.value?.id != song.id) {
            performPowerampCrossfade(song, currentList)
        } else {
            playSongDirect(song, currentList)
        }
    }

    fun playNext(song: Song) {
        mainScope.launch(Dispatchers.Main) {
            val currentQueue = _queue.value.toMutableList()
            val currentIndex = activePlayer.currentMediaItemIndex
            val mediaItem = buildMediaItem(song)

            if (currentIndex < currentQueue.size) {
                currentQueue.add(currentIndex + 1, song)
                activePlayer.addMediaItem(currentIndex + 1, mediaItem)
            } else {
                currentQueue.add(song)
                activePlayer.addMediaItem(mediaItem)
            }
            _queue.value = currentQueue
            saveCurrentState(_currentSong.value?.id ?: song.id, activePlayer.currentPosition)
        }
    }

    fun addToQueue(song: Song) {
        mainScope.launch(Dispatchers.Main) {
            val currentQueue = _queue.value.toMutableList()
            currentQueue.add(song)
            val mediaItem = buildMediaItem(song)
            activePlayer.addMediaItem(mediaItem)
            _queue.value = currentQueue
            saveCurrentState(_currentSong.value?.id ?: song.id, activePlayer.currentPosition)
        }
    }

    fun removeFromQueue(index: Int) {
        mainScope.launch(Dispatchers.Main) {
            if (index in 0 until activePlayer.mediaItemCount) {
                activePlayer.removeMediaItem(index)
                val updatedQueue = _queue.value.toMutableList()
                if (index < updatedQueue.size) {
                    updatedQueue.removeAt(index)
                    _queue.value = updatedQueue
                    saveCurrentState(_currentSong.value?.id ?: "", activePlayer.currentPosition)
                }
            }
        }
    }

    fun clearQueue() {
        mainScope.launch(Dispatchers.Main) {
            if (activePlayer.mediaItemCount > 0) {
                activePlayer.clearMediaItems()
                _queue.value = emptyList()
                _currentSong.value = null
                saveCurrentState("", 0L)
            }
        }
    }

    fun playPause() {
        mainScope.launch(Dispatchers.Main) {
            if (isCrossfading) {
                transitionJob?.cancel()
                crossfadeActiveSongId = null
                try {
                    standbyPlayer.stop()
                    standbyPlayer.clearMediaItems()
                    standbyPlayer.volume = 0f
                } catch (e: Exception) {}
                isCrossfading = false
                fadeVolumeMultiplier = 1.0f
                updatePlayerVolume()
            }
            if (activePlayer.isPlaying) {
                // Smooth acoustic ramp-down before pause (prevents pop/click)
                fadeVolumeMultiplier = 0.35f
                updatePlayerVolume()
                handler.postDelayed({
                    activePlayer.pause()
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                }, 100L)
            } else {
                if (activePlayer.playbackState == Player.STATE_IDLE) {
                    activePlayer.prepare()
                }
                fadeVolumeMultiplier = 0.25f
                updatePlayerVolume()
                activePlayer.playWhenReady = true
                activePlayer.play()
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
    }

    /**
     * Resumes playback automatically when wired headphones or Bluetooth audio connects.
     */
    fun resumeOnHeadsetConnected() {
        mainScope.launch(Dispatchers.Main) {
            if (_isPlaying.value || activePlayer.isPlaying) return@launch

            // If player already has media items loaded (e.g. was paused in background)
            if (activePlayer.mediaItemCount > 0) {
                if (activePlayer.playbackState == Player.STATE_IDLE) {
                    activePlayer.prepare()
                }
                activePlayer.playWhenReady = true
                fadeVolumeMultiplier = 0.3f
                updatePlayerVolume()
                activePlayer.play()
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
        mainScope.launch(Dispatchers.Main) {
            if (activePlayer.isPlaying || _isPlaying.value) {
                fadeVolumeMultiplier = 0.35f
                updatePlayerVolume()
                handler.postDelayed({
                    activePlayer.pause()
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                }, 80L)
            }
        }
    }

    fun skipNext() {
        mainScope.launch(Dispatchers.Main) {
            val isCrossfadeOn = settingsManager.crossfadeEnabled.value && settingsManager.crossfadeSeconds.value > 0
            val currentQueue = _queue.value
            val currentIndex = activePlayer.currentMediaItemIndex

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

            if (isCrossfadeOn && activePlayer.isPlaying && nextSong != null) {
                performPowerampCrossfade(nextSong)
            } else {
                if (isCrossfading) {
                    transitionJob?.cancel()
                    crossfadeActiveSongId = null
                    try {
                        standbyPlayer.stop()
                        standbyPlayer.clearMediaItems()
                        standbyPlayer.volume = 0f
                    } catch (e: Exception) {}
                    isCrossfading = false
                }
                if (nextSong != null) {
                    val nextIndex = currentQueue.indexOfFirst { it.id == nextSong.id }
                    if (nextIndex >= 0 && nextIndex < activePlayer.mediaItemCount) {
                        fadeVolumeMultiplier = 1.0f
                        updatePlayerVolume()
                        activePlayer.seekTo(nextIndex, 0L)
                        activePlayer.play()
                    } else {
                        playSongDirect(nextSong, currentQueue)
                    }
                } else if (activePlayer.hasNextMediaItem()) {
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                    activePlayer.seekToNext()
                } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) {
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                    activePlayer.seekTo(0, 0L)
                    activePlayer.play()
                }
            }
        }
    }

    fun skipPrevious() {
        mainScope.launch(Dispatchers.Main) {
            val isCrossfadeOn = settingsManager.crossfadeEnabled.value && settingsManager.crossfadeSeconds.value > 0
            val currentQueue = _queue.value
            val currentIndex = activePlayer.currentMediaItemIndex

            if (activePlayer.currentPosition > 3000L) {
                activePlayer.seekTo(0L)
                return@launch
            }

            val prevSong = if (currentIndex - 1 >= 0 && currentIndex - 1 < currentQueue.size) {
                currentQueue[currentIndex - 1]
            } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) {
                currentQueue.last()
            } else null

            if (isCrossfadeOn && activePlayer.isPlaying && prevSong != null) {
                performPowerampCrossfade(prevSong)
            } else {
                if (isCrossfading) {
                    transitionJob?.cancel()
                    crossfadeActiveSongId = null
                    try {
                        standbyPlayer.stop()
                        standbyPlayer.clearMediaItems()
                        standbyPlayer.volume = 0f
                    } catch (e: Exception) {}
                    isCrossfading = false
                }
                if (prevSong != null) {
                    val prevIndex = currentQueue.indexOfFirst { it.id == prevSong.id }
                    if (prevIndex >= 0 && prevIndex < activePlayer.mediaItemCount) {
                        fadeVolumeMultiplier = 1.0f
                        updatePlayerVolume()
                        activePlayer.seekTo(prevIndex, 0L)
                        activePlayer.play()
                    } else {
                        playSongDirect(prevSong, currentQueue)
                    }
                } else if (activePlayer.hasPreviousMediaItem()) {
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                    activePlayer.seekToPrevious()
                } else {
                    fadeVolumeMultiplier = 1.0f
                    updatePlayerVolume()
                    activePlayer.seekTo(0L)
                }
            }
        }
    }

    fun seekTo(position: Long) {
        mainScope.launch(Dispatchers.Main) {
            if (isCrossfading) {
                transitionJob?.cancel()
                crossfadeActiveSongId = null
                try {
                    standbyPlayer.stop()
                    standbyPlayer.clearMediaItems()
                    standbyPlayer.volume = 0f
                } catch (e: Exception) {}
                isCrossfading = false
                fadeVolumeMultiplier = 1.0f
                updatePlayerVolume()
            }
            activePlayer.seekTo(position)
            _currentPosition.value = position
        }
    }

    fun setShuffleMode(enabled: Boolean) {
        mainScope.launch(Dispatchers.Main) {
            activePlayer.shuffleModeEnabled = enabled
            _shuffleMode.value = enabled
        }
    }

    fun setRepeatMode(mode: Int) {
        mainScope.launch(Dispatchers.Main) {
            activePlayer.repeatMode = mode
            _repeatMode.value = mode
        }
    }

    fun setPlaybackRate(speed: Float, pitch: Float) {
        mainScope.launch(Dispatchers.Main) {
            val params = PlaybackParameters(speed, pitch)
            activePlayer.playbackParameters = params
            _playbackSpeed.value = speed
            _playbackPitch.value = pitch
        }
    }

    fun toggleEqualizer() {
        val nextState = !_equalizerEnabled.value
        _equalizerEnabled.value = nextState
        settingsManager.setEqualizerEnabled(nextState)
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
            settingsManager.setEqualizerBandLevels(current)
            settingsManager.setEqualizerPresetName("Custom")
            applyHardwareEqualizerBands()
        }
    }

    fun setPreampGain(gainDb: Float) {
        val clamped = gainDb.coerceIn(-15f, 15f)
        _preampGain.value = clamped
        settingsManager.setPreampGain(clamped)
        applyHardwareEqualizerBands()
    }

    fun setTrebleGain(gainDb: Float) {
        val clamped = gainDb.coerceIn(-15f, 15f)
        _trebleGain.value = clamped
        settingsManager.setTrebleGain(clamped)
        applyHardwareEqualizerBands()
    }

    fun setBassBoost(strength: Int) { // 0 to 1000
        val clamped = strength.coerceIn(0, 1000)
        _bassBoostStrength.value = clamped
        settingsManager.setBassBoostStrength(clamped)
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
        settingsManager.setVirtualizerStrength(clamped)
        dspAudioProcessor.virtualizerStrength = clamped
        fadeDspAudioProcessor.virtualizerStrength = clamped
        try {
            if (virtualizer == null && player.audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                virtualizer = Virtualizer(1000, player.audioSessionId)
            }
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
        settingsManager.setAudioBalance(clamped)
        dspAudioProcessor.balance = clamped
        fadeDspAudioProcessor.balance = clamped
        updatePlayerVolume()
    }

    fun setReverbPreset(presetId: Int) {
        _reverbPreset.value = presetId
        settingsManager.setReverbPreset(presetId)
        try {
            if (presetReverb == null) {
                presetReverb = try {
                    PresetReverb(0, 0)
                } catch (e: Exception) {
                    val session = player.audioSessionId
                    if (session != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                        PresetReverb(0, session)
                    } else null
                }
            }
            presetReverb?.let { reverb ->
                if (presetId != PresetReverb.PRESET_NONE.toInt()) {
                    reverb.preset = presetId.toShort()
                    reverb.enabled = true
                    player.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(reverb.id, 1.0f))
                    fadePlayer.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(reverb.id, 1.0f))
                    Log.d("PlaybackManager", "PresetReverb activated: preset=$presetId, id=${reverb.id}")
                } else {
                    reverb.enabled = false
                    player.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(androidx.media3.common.AuxEffectInfo.NO_AUX_EFFECT_ID, 0f))
                    fadePlayer.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(androidx.media3.common.AuxEffectInfo.NO_AUX_EFFECT_ID, 0f))
                    Log.d("PlaybackManager", "PresetReverb disabled")
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
        settingsManager.setEqualizerPresetName(presetName)
        settingsManager.setEqualizerBandLevels(bandGains)
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
        mainScope.launch {
            if (_currentSong.value != null || activePlayer.currentMediaItem != null) {
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

                withContext(Dispatchers.IO) {
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
                    activePlayer.setMediaItems(mediaItems, startIndex, lastPos)
                    activePlayer.prepare()
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
            mainScope.launch(Dispatchers.Main) {
                activePlayer.setMediaItems(mediaItems, index, 0L)
                activePlayer.prepare()
                if (isCurrentlyPlaying) {
                    activePlayer.play()
                }
            }
        }
    }

    fun release() {
        handler.removeCallbacks(positionTrackerRunnable)
        transitionJob?.cancel()
        isCrossfading = false
        crossfadeActiveSongId = null
        mainScope.launch(Dispatchers.Main) {
            try {
                playerA.stop()
                playerA.clearMediaItems()
                playerA.release()
            } catch (e: Exception) {
                Log.w("PlaybackManager", "Error releasing playerA: ${e.message}")
            }
            try {
                playerB.stop()
                playerB.clearMediaItems()
                playerB.release()
            } catch (e: Exception) {
                Log.w("PlaybackManager", "Error releasing playerB: ${e.message}")
            }
        }
        releaseAudioEffects()
        try {
            NativeAudioEngine.release()
        } catch (e: Exception) {}
        try {
            mediaController?.release()
        } catch (e: Exception) {}
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
