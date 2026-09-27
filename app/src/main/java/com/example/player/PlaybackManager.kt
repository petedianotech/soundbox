package com.example.player

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.util.HeadsetPlugReceiver
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Single Studio DSP AudioProcessor
    val dspAudioProcessor = SoundboxDspAudioProcessor()
    val dspAudioProcessorA: SoundboxDspAudioProcessor get() = dspAudioProcessor
    val dspAudioProcessorB: SoundboxDspAudioProcessor get() = dspAudioProcessor
    val fadeDspAudioProcessor: SoundboxDspAudioProcessor get() = dspAudioProcessor

    private val lowMemoryLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15_000, // minBufferMs
            30_000, // maxBufferMs
            1_000,  // bufferForPlaybackMs
            2_000   // bufferForPlaybackAfterRebufferMs
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    private fun createRenderersFactory(): DefaultRenderersFactory {
        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(dspAudioProcessor))
                    .build()
            }
        }
    }

    // Direct Single ExoPlayer Instance
    val player: ExoPlayer = ExoPlayer.Builder(context, createRenderersFactory())
        .setLoadControl(lowMemoryLoadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    val activePlayer: ExoPlayer get() = player
    val standbyPlayer: ExoPlayer get() = player
    val fadePlayer: ExoPlayer get() = player

    var onActivePlayerChanged: ((ExoPlayer) -> Unit)? = null

    val isCrossfading: Boolean = false
    val crossfadeActiveSongId: String? = null

    // Centralized Android OS AudioFocus Management
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var hasSystemAudioFocus = false
    private var audioFocusRequestHelper: Any? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        mainScope.launch(Dispatchers.Main) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    try {
                        player.volume = 0.3f
                    } catch (e: Exception) {}
                }
                AudioManager.AUDIOFOCUS_GAIN -> updatePlayerVolume()
            }
        }
    }

    fun requestSystemAudioFocus(): Boolean {
        if (hasSystemAudioFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener, Handler(Looper.getMainLooper()))
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioFocusRequestHelper = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasSystemAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasSystemAudioFocus
    }

    fun abandonSystemAudioFocus() {
        if (!hasSystemAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (audioFocusRequestHelper as? android.media.AudioFocusRequest)?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasSystemAudioFocus = false
    }

    // Metrics tracking
    private var currentSongPlayedMs = 0L
    private var hasCountedPlayForCurrentSong = false
    private var lastTickTimestamp = SystemClock.elapsedRealtime()

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

    private val _eqBandLevels = MutableStateFlow(settingsManager.getEqualizerBandLevels())
    val eqBandLevels: StateFlow<List<Float>> = _eqBandLevels.asStateFlow()

    private val _preampGain = MutableStateFlow(settingsManager.getPreampGain())
    val preampGain: StateFlow<Float> = _preampGain.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(settingsManager.getBassBoostStrength())
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _trebleGain = MutableStateFlow(settingsManager.getTrebleGain())
    val trebleGain: StateFlow<Float> = _trebleGain.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(settingsManager.getVirtualizerStrength())
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    private val _audioBalance = MutableStateFlow(settingsManager.getAudioBalance())
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
    private var fadeVolumeMultiplier = 1.0f

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val positionTrackerRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            if (_isPlaying.value || player.isPlaying) {
                val delta = (now - lastTickTimestamp).coerceAtLeast(0L)
                if (delta in 50L..2000L && player.isPlaying) {
                    currentSongPlayedMs += delta
                    settingsManager.addListeningTimeMs(delta)

                    val current = _currentSong.value
                    if (current != null && !hasCountedPlayForCurrentSong) {
                        val threshold = if (current.duration in 1..60000L) (current.duration / 2) else 30_000L
                        if (currentSongPlayedMs >= threshold) {
                            hasCountedPlayForCurrentSong = true
                            mainScope.launch(Dispatchers.IO) {
                                repository.incrementPlayCount(current.id)
                            }
                        }
                    }
                }
                lastTickTimestamp = now

                val curPos = player.currentPosition.coerceAtLeast(0L)
                _currentPosition.value = curPos
            } else {
                lastTickTimestamp = now
            }
            handler.postDelayed(this, 250)
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
                val mediaId = mediaItem?.mediaId
                if (mediaId != null) {
                    currentSongPlayedMs = 0L
                    hasCountedPlayForCurrentSong = false
                    lastTickTimestamp = SystemClock.elapsedRealtime()

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
                            saveCurrentState(song.id, player.currentPosition.coerceAtLeast(0L))
                            com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
                        }
                    }
                }
            }
        })

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                if (audioSessionId > 0) {
                    _audioSessionId.value = audioSessionId
                    initAudioEffects(audioSessionId)
                }
            }
        })

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
            } else {
                reverb.enabled = false
                player.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(androidx.media3.common.AuxEffectInfo.NO_AUX_EFFECT_ID, 0f))
            }
            presetReverb = reverb
        } catch (e: Exception) {
            Log.w("PlaybackManager", "PresetReverb activation skipped: ${e.message}")
        }

        dspAudioProcessor.balance = _audioBalance.value
        dspAudioProcessor.virtualizerStrength = _virtualizerStrength.value

        applyHardwareEqualizerBands()
    }

    private val userBandFrequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    fun getTargetMasterVolume(): Float {
        val preampDb = _preampGain.value
        return Math.pow(10.0, (preampDb / 20.0).toDouble()).toFloat().coerceIn(0.05f, 1.8f)
    }

    private fun updatePlayerVolume() {
        val target = (getTargetMasterVolume() * fadeVolumeMultiplier).coerceIn(0.05f, 1.8f)
        try {
            player.volume = target
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error updating volume: ${e.message}")
        }
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

                if (hwFreqHz >= 3000) {
                    val trebleRatio = ((hwFreqHz - 3000f) / 13000f).coerceIn(0.2f, 1f)
                    targetGainDb += treble * trebleRatio
                }

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

    fun performPowerampCrossfade(nextSong: Song, customQueue: List<Song> = emptyList()) {
        playSongDirect(nextSong, customQueue)
    }

    fun playSongDirect(song: Song, customQueue: List<Song> = emptyList()) {
        val currentList = if (customQueue.isNotEmpty()) customQueue else listOf(song)
        _queue.value = currentList

        _currentSong.value = song
        _duration.value = song.duration

        currentSongPlayedMs = 0L
        hasCountedPlayForCurrentSong = false
        lastTickTimestamp = SystemClock.elapsedRealtime()

        val mediaItems = currentList.map { songItem -> buildMediaItem(songItem) }
        val index = currentList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        fadeVolumeMultiplier = 1.0f
        requestSystemAudioFocus()
        updatePlayerVolume()

        try {
            player.stop()
            player.clearMediaItems()
            player.setMediaItems(mediaItems, index, 0L)
            player.prepare()
            player.playWhenReady = true
            player.play()
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Error during playSongDirect: ${e.message}", e)
        }

        val sid = player.audioSessionId
        if (sid > 0) {
            _audioSessionId.value = sid
            try {
                initAudioEffects(sid)
            } catch (e: Exception) {
                Log.w("PlaybackManager", "initAudioEffects error: ${e.message}")
            }
        }

        saveCurrentState(song.id, 0L)
        startPlaybackService()
        com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
    }

    fun play() {
        android.util.Log.d("PlaybackManager", "play() called")
        mainScope.launch(Dispatchers.Main) {
            requestSystemAudioFocus()
            updatePlayerVolume()
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.playWhenReady = true
            player.play()
            startPlaybackService()
        }
    }

    fun pause() {
        android.util.Log.d("PlaybackManager", "pause() called")
        mainScope.launch(Dispatchers.Main) {
            player.pause()
            abandonSystemAudioFocus()
        }
    }

    fun playSong(song: Song, customQueue: List<Song> = emptyList()) {
        playSongDirect(song, customQueue)
    }

    fun playNext(song: Song) {
        mainScope.launch(Dispatchers.Main) {
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
            saveCurrentState(_currentSong.value?.id ?: song.id, player.currentPosition.coerceAtLeast(0L))
        }
    }

    fun addToQueue(song: Song) {
        mainScope.launch(Dispatchers.Main) {
            val currentQueue = _queue.value.toMutableList()
            currentQueue.add(song)
            val mediaItem = buildMediaItem(song)
            player.addMediaItem(mediaItem)
            _queue.value = currentQueue
            saveCurrentState(_currentSong.value?.id ?: song.id, player.currentPosition.coerceAtLeast(0L))
        }
    }

    fun removeFromQueue(index: Int) {
        mainScope.launch(Dispatchers.Main) {
            if (index in 0 until player.mediaItemCount) {
                player.removeMediaItem(index)
                val updatedQueue = _queue.value.toMutableList()
                if (index < updatedQueue.size) {
                    updatedQueue.removeAt(index)
                    _queue.value = updatedQueue
                    saveCurrentState(_currentSong.value?.id ?: "", player.currentPosition.coerceAtLeast(0L))
                }
            }
        }
    }

    fun clearQueue() {
        mainScope.launch(Dispatchers.Main) {
            player.clearMediaItems()
            _queue.value = emptyList()
            _currentSong.value = null
            saveCurrentState("", 0L)
        }
    }

    fun playPause() {
        mainScope.launch(Dispatchers.Main) {
            if (player.isPlaying) {
                pause()
            } else {
                play()
            }
        }
    }

    fun resumeOnHeadsetConnected() {
        mainScope.launch(Dispatchers.Main) {
            if (_isPlaying.value || player.isPlaying) return@launch

            if (player.mediaItemCount > 0) {
                play()
                return@launch
            }

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

    fun pauseOnHeadsetDisconnected() {
        mainScope.launch(Dispatchers.Main) {
            if (player.isPlaying || _isPlaying.value) {
                pause()
            }
        }
    }

    fun skipNext() {
        mainScope.launch(Dispatchers.Main) {
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
                val nextIndex = currentQueue.indexOfFirst { it.id == nextSong.id }
                if (nextIndex >= 0 && nextIndex < player.mediaItemCount) {
                    player.seekTo(nextIndex, 0L)
                    player.play()
                } else {
                    playSongDirect(nextSong, currentQueue)
                }
            } else if (player.hasNextMediaItem()) {
                player.seekToNext()
            } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) {
                player.seekTo(0, 0L)
                player.play()
            }
        }
    }

    fun skipPrevious() {
        mainScope.launch(Dispatchers.Main) {
            val currentQueue = _queue.value
            val currentIndex = player.currentMediaItemIndex

            if (player.currentPosition > 3000L) {
                player.seekTo(0L)
                return@launch
            }

            val prevSong = if (currentIndex - 1 >= 0 && currentIndex - 1 < currentQueue.size) {
                currentQueue[currentIndex - 1]
            } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) {
                currentQueue.last()
            } else null

            if (prevSong != null) {
                val prevIndex = currentQueue.indexOfFirst { it.id == prevSong.id }
                if (prevIndex >= 0 && prevIndex < player.mediaItemCount) {
                    player.seekTo(prevIndex, 0L)
                    player.play()
                } else {
                    playSongDirect(prevSong, currentQueue)
                }
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPrevious()
            } else {
                player.seekTo(0L)
            }
        }
    }

    fun seekTo(position: Long) {
        mainScope.launch(Dispatchers.Main) {
            player.seekTo(position)
            _currentPosition.value = position
        }
    }

    fun seekBackward(ms: Long = 10000L) {
        mainScope.launch(Dispatchers.Main) {
            val newPos = (player.currentPosition - ms).coerceAtLeast(0L)
            player.seekTo(newPos)
            _currentPosition.value = newPos
        }
    }

    fun seekForward(ms: Long = 10000L) {
        mainScope.launch(Dispatchers.Main) {
            val duration = if (player.duration > 0) player.duration else Long.MAX_VALUE
            val newPos = (player.currentPosition + ms).coerceAtMost(duration)
            player.seekTo(newPos)
            _currentPosition.value = newPos
        }
    }

    fun setShuffleMode(enabled: Boolean) {
        mainScope.launch(Dispatchers.Main) {
            player.shuffleModeEnabled = enabled
            _shuffleMode.value = enabled
        }
    }

    fun setRepeatMode(mode: Int) {
        mainScope.launch(Dispatchers.Main) {
            player.repeatMode = mode
            _repeatMode.value = mode
        }
    }

    fun setPlaybackRate(speed: Float, pitch: Float) {
        mainScope.launch(Dispatchers.Main) {
            val params = PlaybackParameters(speed, pitch)
            player.playbackParameters = params
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

    fun setBassBoost(strength: Int) {
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

    fun setVirtualizerStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _virtualizerStrength.value = clamped
        settingsManager.setVirtualizerStrength(clamped)
        dspAudioProcessor.virtualizerStrength = clamped
        try {
            if (virtualizer == null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
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

    fun setAudioBalance(balance: Float) {
        val clamped = balance.coerceIn(-1f, 1f)
        _audioBalance.value = clamped
        settingsManager.setAudioBalance(clamped)
        dspAudioProcessor.balance = clamped
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
                    if (session != C.AUDIO_SESSION_ID_UNSET) {
                        PresetReverb(0, session)
                    } else null
                }
            }
            presetReverb?.let { reverb ->
                if (presetId != PresetReverb.PRESET_NONE.toInt()) {
                    reverb.preset = presetId.toShort()
                    reverb.enabled = true
                    player.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(reverb.id, 1.0f))
                } else {
                    reverb.enabled = false
                    player.setAuxEffectInfo(androidx.media3.common.AuxEffectInfo(androidx.media3.common.AuxEffectInfo.NO_AUX_EFFECT_ID, 0f))
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
            if (_currentSong.value != null || player.currentMediaItem != null) {
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
        mainScope.launch(Dispatchers.IO) {
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
                player.setMediaItems(mediaItems, index, 0L)
                player.prepare()
                if (isCurrentlyPlaying) {
                    player.play()
                }
            }
        }
    }

    fun release() {
        abandonSystemAudioFocus()
        handler.removeCallbacks(positionTrackerRunnable)
        mainScope.launch(Dispatchers.Main) {
            try {
                player.release()
            } catch (e: Exception) {
                Log.w("PlaybackManager", "Error releasing player: ${e.message}")
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
