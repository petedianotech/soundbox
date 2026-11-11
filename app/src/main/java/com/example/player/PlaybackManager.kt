package com.example.player

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.model.Song
import com.example.data.repository.MusicRepository
import com.example.util.HeadsetPlugReceiver
import com.example.util.SettingsManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

/**
 * Production-ready PlaybackManager with rock-solid ExoPlayer stability,
 * robust MediaStore URI resolution, protected AudioEffects, and MediaSession integration.
 */
class PlaybackManager private constructor(private val context: Context) {

    private val repository = MusicRepository.getInstance(context)
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val settingsManager = SettingsManager.getInstance(context)

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15_000, // 15s min buffer
            30_000, // 30s max buffer
            500,    // 500ms initial buffer for instant playback start!
            1_000   // 1s buffer after rebuffer
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    private val playerInstance: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            false
        )
        .setHandleAudioBecomingNoisy(false)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    val player: ExoPlayer get() = playerInstance
    val activePlayer: ExoPlayer get() = playerInstance
    val standbyPlayer: ExoPlayer get() = playerInstance
    val fadePlayer: ExoPlayer get() = playerInstance

    var onActivePlayerChanged: ((ExoPlayer) -> Unit)? = null

    private var headsetReceiverRegistered: Boolean = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var hasSystemAudioFocus = false
    private var audioFocusRequestHelper: Any? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        mainScope.launch(Dispatchers.Main) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    try { player.volume = 0.3f } catch (_: Exception) {}
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    updatePlayerVolume()
                }
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

    private var currentSongPlayedMs = 0L
    private var hasCountedPlayForCurrentSong = false
    private var lastTickTimestamp = SystemClock.elapsedRealtime()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

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

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val _equalizerHardwareBands = MutableStateFlow(0)
    val equalizerHardwareBands: StateFlow<Int> = _equalizerHardwareBands.asStateFlow()

    private val _equalizerStatus = MutableStateFlow("DSP Engine Active")
    val equalizerStatus: StateFlow<String> = _equalizerStatus.asStateFlow()

    private var sleepTimer: Timer? = null
    private var sleepTimerFadeOutEnabled: Boolean = true
    private val handler = Handler(Looper.getMainLooper())
    private var fadeVolumeMultiplier = 1.0f

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private var lastAttachedSessionId: Int = -1
    private val userBandFrequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    private val positionTrackerRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            if (_isPlaying.value || playerInstance.isPlaying) {
                val delta = (now - lastTickTimestamp).coerceAtLeast(0L)
                if (delta in 50L..2000L && playerInstance.isPlaying) {
                    currentSongPlayedMs += delta
                    settingsManager.addListeningTimeMs(delta)
                    val current = _currentSong.value
                    if (current != null && !hasCountedPlayForCurrentSong) {
                        val threshold = if (current.duration in 1..60000L) (current.duration / 2) else 30_000L
                        if (currentSongPlayedMs >= threshold) {
                            hasCountedPlayForCurrentSong = true
                            mainScope.launch(Dispatchers.IO) { repository.incrementPlayCount(current.id) }
                        }
                    }
                }
                lastTickTimestamp = now
                val curPos = playerInstance.currentPosition.coerceAtLeast(0L)
                _currentPosition.value = curPos
            } else {
                lastTickTimestamp = now
            }
            handler.postDelayed(this, 250)
        }
    }

    init {
        try { NativeAudioEngine.init(44100, 2) } catch (e: Exception) { Log.w("PlaybackManager", "NativeAudioEngine init: ${e.message}") }
        setupPlayerListeners(playerInstance)
        handler.post(positionTrackerRunnable)
        restorePlaybackState()
        initializeMediaController()
    }

    private fun initializeMediaController() {
        try {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try { mediaController = controllerFuture?.get() } catch (e: Exception) { Log.e("PlaybackManager", "MediaController fail", e) }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.w("PlaybackManager", "initializeMediaController error: ${e.message}")
        }
    }

    private fun setupPlayerListeners(target: ExoPlayer) {
        target.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                _isPlaying.value = isPlayingChanged
                _duration.value = target.duration.coerceAtLeast(0L)
                com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        val sid = target.audioSessionId
                        if (sid > 0) {
                            _audioSessionId.value = sid
                            initAudioEffects(sid)
                        }
                    }
                    Player.STATE_ENDED -> {
                        val currentQueue = _queue.value
                        val next = resolveNextSong()
                        when {
                            next != null -> playSongDirect(next, currentQueue)
                            _repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty() -> playSongDirect(currentQueue.first(), currentQueue)
                            _shuffleMode.value && currentQueue.size > 1 -> {
                                val randomNext = currentQueue.filter { it.id != _currentSong.value?.id }.randomOrNull() ?: currentQueue.first()
                                playSongDirect(randomNext, currentQueue)
                            }
                            else -> {
                                _isPlaying.value = false
                                saveCurrentState(_currentSong.value?.id ?: "", 0L)
                            }
                        }
                    }
                    Player.STATE_IDLE -> {
                        // Player stopped or error
                    }
                    Player.STATE_BUFFERING -> {}
                }
                com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackManager", "ExoPlayer playback error: ${error.errorCodeName} (${error.errorCode})", error)
                _isPlaying.value = false
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId ?: return
                currentSongPlayedMs = 0L
                hasCountedPlayForCurrentSong = false
                lastTickTimestamp = SystemClock.elapsedRealtime()
                _queue.value.find { it.id == mediaId }?.let {
                    _currentSong.value = it
                    _duration.value = it.duration
                }
                mainScope.launch {
                    repository.getSongById(mediaId)?.let { song ->
                        _currentSong.value = song
                        _duration.value = song.duration
                        saveCurrentState(song.id, target.currentPosition.coerceAtLeast(0L))
                        com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
                    }
                }
            }
        })

        target.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                if (audioSessionId > 0) {
                    _audioSessionId.value = audioSessionId
                    initAudioEffects(audioSessionId)
                }
            }
        })

        if (!headsetReceiverRegistered) {
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
                headsetReceiverRegistered = true
            } catch (e: Exception) {
                Log.w("PlaybackManager", "HeadsetPlugReceiver: ${e.message}")
            }
        }
    }

    private fun releaseAudioEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        equalizer = null
        try { bassBoost?.release() } catch (_: Exception) {}
        bassBoost = null
        try { virtualizer?.release() } catch (_: Exception) {}
        virtualizer = null
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
            context.sendBroadcast(
                Intent(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                }
            )
        } catch (_: Exception) {}

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = _equalizerEnabled.value
            }
            _equalizerHardwareBands.value = equalizer?.numberOfBands?.toInt() ?: 0
            _equalizerStatus.value = "Hardware EQ (${_equalizerHardwareBands.value} bands)"
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Hardware Equalizer init skipped: ${e.message}")
            _equalizerStatus.value = "Software DSP Active"
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _bassBoostStrength.value > 0
                if (strengthSupported) {
                    setStrength(_bassBoostStrength.value.toShort().coerceIn(0, 1000))
                }
            }
        } catch (_: Exception) {}

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = _virtualizerStrength.value > 0
                if (strengthSupported) {
                    setStrength(_virtualizerStrength.value.toShort().coerceIn(0, 1000))
                }
            }
        } catch (_: Exception) {}

        applyHardwareEqualizerBands()
    }

    fun getTargetMasterVolume(): Float =
        Math.pow(10.0, (_preampGain.value / 20.0).toDouble()).toFloat().coerceIn(0.05f, 1.0f)

    private fun updatePlayerVolume() {
        try {
            playerInstance.volume = (getTargetMasterVolume() * fadeVolumeMultiplier).coerceIn(0.05f, 1.0f)
        } catch (_: Exception) {}
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
                } catch (_: Exception) {
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
                if (hwFreqHz >= 3000) targetGainDb += treble * ((hwFreqHz - 3000f) / 13000f).coerceIn(0.2f, 1f)
                if (hwFreqHz <= 250) targetGainDb += (bassBoostFactor * 6f) * (1f - (hwFreqHz / 250f)).coerceIn(0.2f, 1f)
                eq.setBandLevel(hwBand.toShort(), (targetGainDb * 100f).toInt().coerceIn(minLevel, maxLevel).toShort())
            }
        } catch (_: Exception) {}
    }

    private fun buildMediaItem(songItem: Song): MediaItem {
        val fileUri = repository.getSongUri(songItem) ?: if (songItem.path.startsWith("content://") || songItem.path.startsWith("file://")) {
            android.net.Uri.parse(songItem.path)
        } else {
            android.net.Uri.fromFile(java.io.File(songItem.path))
        }
        val artUri = com.example.util.AlbumArtHelper.getArtworkUri(context, songItem)
        val metaBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(songItem.title)
            .setArtist(songItem.artist)
            .setAlbumTitle(songItem.album)
            .setDisplayTitle(songItem.title)
            .setArtworkUri(artUri)
        return MediaItem.Builder()
            .setMediaId(songItem.id)
            .setUri(fileUri)
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    fun startPlaybackService() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e("PlaybackManager", "startPlaybackService: ${e.message}")
        }
    }

    private fun resolveNextSong(): Song? {
        val currentQueue = _queue.value
        val currentIndex = currentQueue.indexOfFirst { it.id == _currentSong.value?.id }
        return when {
            _shuffleMode.value && currentQueue.size > 1 -> currentQueue.filter { it.id != _currentSong.value?.id }.randomOrNull()
            currentIndex >= 0 && currentIndex + 1 < currentQueue.size -> currentQueue[currentIndex + 1]
            _repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty() -> currentQueue.first()
            else -> null
        }
    }

    fun playSongDirect(song: Song, customQueue: List<Song> = emptyList()) {
        val currentList = if (customQueue.isNotEmpty()) customQueue else listOf(song)
        _queue.value = currentList
        _currentSong.value = song
        _duration.value = song.duration
        currentSongPlayedMs = 0L
        hasCountedPlayForCurrentSong = false
        lastTickTimestamp = SystemClock.elapsedRealtime()

        val mediaItems = currentList.map { buildMediaItem(it) }
        val index = currentList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        fadeVolumeMultiplier = 1.0f
        requestSystemAudioFocus()
        updatePlayerVolume()

        try {
            playerInstance.stop()
            playerInstance.clearMediaItems()
            playerInstance.setMediaItems(mediaItems, index, 0L)
            playerInstance.playWhenReady = true
            playerInstance.prepare()
            playerInstance.play()
        } catch (e: Exception) {
            Log.e("PlaybackManager", "playSongDirect failed: ${e.message}", e)
        }

        val sid = playerInstance.audioSessionId
        if (sid > 0) {
            _audioSessionId.value = sid
            try { initAudioEffects(sid) } catch (_: Exception) {}
        }
        saveCurrentState(song.id, 0L)
        startPlaybackService()
        com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
    }

    fun play() {
        mainScope.launch(Dispatchers.Main) {
            requestSystemAudioFocus()
            updatePlayerVolume()
            if (playerInstance.playbackState == Player.STATE_IDLE) {
                playerInstance.prepare()
            }
            playerInstance.playWhenReady = true
            playerInstance.play()
            startPlaybackService()
        }
    }

    fun pause() {
        mainScope.launch(Dispatchers.Main) {
            playerInstance.pause()
            abandonSystemAudioFocus()
        }
    }

    fun playSong(song: Song, customQueue: List<Song> = emptyList()) = playSongDirect(song, customQueue)

    fun playNext(song: Song) {
        mainScope.launch(Dispatchers.Main) {
            val currentQueue = _queue.value.toMutableList()
            val currentIndex = playerInstance.currentMediaItemIndex
            val mediaItem = buildMediaItem(song)
            if (currentIndex < currentQueue.size) {
                currentQueue.add(currentIndex + 1, song)
                playerInstance.addMediaItem(currentIndex + 1, mediaItem)
            } else {
                currentQueue.add(song)
                playerInstance.addMediaItem(mediaItem)
            }
            _queue.value = currentQueue
            saveCurrentState(_currentSong.value?.id ?: song.id, playerInstance.currentPosition.coerceAtLeast(0L))
        }
    }

    fun addToQueue(song: Song) {
        mainScope.launch(Dispatchers.Main) {
            val currentQueue = _queue.value.toMutableList()
            currentQueue.add(song)
            playerInstance.addMediaItem(buildMediaItem(song))
            _queue.value = currentQueue
            saveCurrentState(_currentSong.value?.id ?: song.id, playerInstance.currentPosition.coerceAtLeast(0L))
        }
    }

    fun removeFromQueue(index: Int) {
        mainScope.launch(Dispatchers.Main) {
            if (index in 0 until playerInstance.mediaItemCount) {
                playerInstance.removeMediaItem(index)
                val updatedQueue = _queue.value.toMutableList()
                if (index < updatedQueue.size) {
                    updatedQueue.removeAt(index)
                    _queue.value = updatedQueue
                    saveCurrentState(_currentSong.value?.id ?: "", playerInstance.currentPosition.coerceAtLeast(0L))
                }
            }
        }
    }

    fun clearQueue() {
        mainScope.launch(Dispatchers.Main) {
            playerInstance.clearMediaItems()
            _queue.value = emptyList()
            _currentSong.value = null
            saveCurrentState("", 0L)
        }
    }

    fun playPause() {
        mainScope.launch(Dispatchers.Main) {
            if (playerInstance.isPlaying) pause() else play()
        }
    }

    fun resumeOnHeadsetConnected() {
        mainScope.launch(Dispatchers.Main) {
            if (_isPlaying.value || playerInstance.isPlaying) return@launch
            if (playerInstance.mediaItemCount > 0) {
                play()
                return@launch
            }
            val targetSong = _currentSong.value ?: run {
                val lastSongId = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE)
                    .getString("last_song_id", null)
                if (lastSongId != null) repository.getSongById(lastSongId) else null
            } ?: repository.allSongs.firstOrNull()?.firstOrNull()
            if (targetSong != null) playSong(targetSong)
        }
    }

    fun pauseOnHeadsetDisconnected() {
        mainScope.launch(Dispatchers.Main) {
            if (playerInstance.isPlaying || _isPlaying.value) pause()
        }
    }

    fun skipNext() {
        mainScope.launch(Dispatchers.Main) {
            val nextSong = resolveNextSong()
            if (nextSong != null) {
                playSongDirect(nextSong, _queue.value)
            } else if (playerInstance.hasNextMediaItem()) {
                playerInstance.seekToNext()
            }
        }
    }

    fun skipPrevious() {
        mainScope.launch(Dispatchers.Main) {
            val currentQueue = _queue.value
            val currentIndex = playerInstance.currentMediaItemIndex
            if (playerInstance.currentPosition > 3000L) {
                playerInstance.seekTo(0L)
                return@launch
            }
            val prevSong = if (currentIndex - 1 >= 0 && currentIndex - 1 < currentQueue.size) {
                currentQueue[currentIndex - 1]
            } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) {
                currentQueue.last()
            } else {
                null
            }
            if (prevSong != null) {
                playSongDirect(prevSong, currentQueue)
            } else if (playerInstance.hasPreviousMediaItem()) {
                playerInstance.seekToPrevious()
            } else {
                playerInstance.seekTo(0L)
            }
        }
    }

    fun seekTo(position: Long) {
        mainScope.launch(Dispatchers.Main) {
            playerInstance.seekTo(position)
            _currentPosition.value = position
        }
    }

    fun seekBackward(ms: Long = 10000L) {
        mainScope.launch(Dispatchers.Main) {
            val p = (playerInstance.currentPosition - ms).coerceAtLeast(0L)
            playerInstance.seekTo(p)
            _currentPosition.value = p
        }
    }

    fun seekForward(ms: Long = 10000L) {
        mainScope.launch(Dispatchers.Main) {
            val d = if (playerInstance.duration > 0) playerInstance.duration else Long.MAX_VALUE
            val p = (playerInstance.currentPosition + ms).coerceAtMost(d)
            playerInstance.seekTo(p)
            _currentPosition.value = p
        }
    }

    fun setShuffleMode(enabled: Boolean) {
        mainScope.launch(Dispatchers.Main) {
            _shuffleMode.value = enabled
            playerInstance.shuffleModeEnabled = enabled
        }
    }

    fun setRepeatMode(mode: Int) {
        mainScope.launch(Dispatchers.Main) {
            _repeatMode.value = mode
            playerInstance.repeatMode = mode
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        mainScope.launch(Dispatchers.Main) {
            _playbackSpeed.value = speed
            playerInstance.playbackParameters = PlaybackParameters(speed, _playbackPitch.value)
        }
    }

    fun setPlaybackPitch(pitch: Float) {
        mainScope.launch(Dispatchers.Main) {
            _playbackPitch.value = pitch
            playerInstance.playbackParameters = PlaybackParameters(_playbackSpeed.value, pitch)
        }
    }

    fun setPlaybackRate(speed: Float, pitch: Float) {
        mainScope.launch(Dispatchers.Main) {
            playerInstance.playbackParameters = PlaybackParameters(speed, pitch)
            _playbackSpeed.value = speed
            _playbackPitch.value = pitch
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerEnabled.value = enabled
        settingsManager.setEqualizerEnabled(enabled)
        try { equalizer?.enabled = enabled } catch (_: Exception) {}
        applyHardwareEqualizerBands()
    }

    fun setEqBandLevels(levels: List<Float>) {
        _eqBandLevels.value = levels
        settingsManager.setEqualizerBandLevels(levels)
        applyHardwareEqualizerBands()
    }

    fun setPreampGain(gain: Float) {
        _preampGain.value = gain
        settingsManager.setPreampGain(gain)
        applyHardwareEqualizerBands()
    }

    fun setBassBoostStrength(strength: Int) { setBassBoost(strength) }
    fun setTrebleGain(gain: Float) { _trebleGain.value = gain; settingsManager.setTrebleGain(gain); applyHardwareEqualizerBands() }
    fun setVirtualizerStrength(strength: Int) {
        _virtualizerStrength.value = strength
        settingsManager.setVirtualizerStrength(strength)
        try { virtualizer?.setStrength(strength.toShort().coerceIn(0, 1000)) } catch (_: Exception) {}
    }
    fun setAudioBalance(balance: Float) {
        _audioBalance.value = balance
        settingsManager.setAudioBalance(balance)
    }
    fun setReverbPreset(preset: Int) {
        _reverbPreset.value = preset
        settingsManager.setReverbPreset(preset)
    }
    fun setEqualizerPresetName(name: String) {
        _currentPresetName.value = name
        settingsManager.setEqualizerPresetName(name)
    }

    fun toggleEqualizer() {
        val nextState = !_equalizerEnabled.value
        _equalizerEnabled.value = nextState
        settingsManager.setEqualizerEnabled(nextState)
        try {
            equalizer?.enabled = nextState
            if (nextState) applyHardwareEqualizerBands()
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

    fun applyPowerampPreset(
        presetName: String,
        bandGains: List<Float>,
        bassBoost: Int = 300,
        treble: Float = 0f,
        virtualizer: Int = 0,
        reverb: Int = 0
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
            reverb = 0
        )
    }

    fun setSleepTimer(millis: Long) {
        sleepTimer?.cancel()
        _sleepTimerMillis.value = millis
        if (millis <= 0L) return
        sleepTimer = Timer()
        val start = SystemClock.elapsedRealtime()
        sleepTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val left = (millis - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0L)
                _sleepTimerMillis.value = left
                if (left <= 0L) {
                    mainScope.launch(Dispatchers.Main) { pause() }
                    cancel()
                }
            }
        }, 0L, 1000L)
    }

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
                            playerInstance.pause()
                            _sleepTimerMillis.value = 0L
                            cancel()
                        } else {
                            _sleepTimerMillis.value = currentLeft
                            if (sleepTimerFadeOutEnabled && currentLeft in 1L..15000L) {
                                fadeVolumeMultiplier = (currentLeft / 15000f).coerceIn(0.05f, 1.0f)
                                updatePlayerVolume()
                            }
                        }
                    }
                }
            }, 1000L, 1000L)
        }
    }

    fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
        _sleepTimerMillis.value = 0L
        fadeVolumeMultiplier = 1.0f
        updatePlayerVolume()
    }

    fun stopSleepTimer() {
        cancelSleepTimer()
    }

    fun startSleepTimerEndOfTrack(fadeOutAtEnd: Boolean = true) {
        val remainingMs = (_duration.value - _currentPosition.value).coerceAtLeast(1000L)
        startSleepTimerSeconds((remainingMs / 1000L).toInt(), fadeOutAtEnd)
    }

    fun extendSleepTimer(minutes: Int = 5) {
        val currentMs = _sleepTimerMillis.value
        val newTotalSeconds = ((currentMs + minutes * 60_000L) / 1000L).toInt()
        startSleepTimerSeconds(newTotalSeconds, sleepTimerFadeOutEnabled)
    }

    fun toggleFavorite(song: Song) {
        val targetFav = !song.isFavorite
        if (_currentSong.value?.id == song.id) {
            _currentSong.value = _currentSong.value?.copy(isFavorite = targetFav)
        }
        _queue.value = _queue.value.map {
            if (it.id == song.id) it.copy(isFavorite = targetFav) else it
        }
        mainScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(song.id, song.isFavorite)
        }
    }

    private fun saveCurrentState(songId: String, position: Long) {
        try {
            context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE)
                .edit()
                .putString("last_song_id", songId)
                .putLong("last_position", position)
                .apply()
        } catch (_: Exception) {}
    }

    private fun restorePlaybackState() {
        mainScope.launch {
            try {
                val lastSongId = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE)
                    .getString("last_song_id", null) ?: return@launch
                repository.getSongById(lastSongId)?.let {
                    _currentSong.value = it
                    _duration.value = it.duration
                }
            } catch (_: Exception) {}
        }
    }

    fun refreshCurrentSongMetadata(updatedSong: Song) {
        if (_currentSong.value?.id == updatedSong.id) _currentSong.value = updatedSong
        val nextQueue = _queue.value.toMutableList()
        val index = nextQueue.indexOfFirst { it.id == updatedSong.id }
        if (index >= 0) {
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
            val mediaItems = currentList.map { buildMediaItem(it) }
            val index = currentList.indexOfFirst { it.id == updatedSong.id }.coerceAtLeast(0)
            mainScope.launch(Dispatchers.Main) {
                playerInstance.setMediaItems(mediaItems, index, 0L)
                playerInstance.prepare()
                if (isCurrentlyPlaying) playerInstance.play()
            }
        }
    }

    fun release() {
        abandonSystemAudioFocus()
        handler.removeCallbacks(positionTrackerRunnable)
        sleepTimer?.cancel()
        mainScope.launch(Dispatchers.Main) {
            try { playerInstance.release() } catch (_: Exception) {}
        }
        releaseAudioEffects()
        try { NativeAudioEngine.release() } catch (_: Exception) {}
        try { mediaController?.release() } catch (_: Exception) {}
    }

    companion object {
        @Volatile private var INSTANCE: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PlaybackManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
