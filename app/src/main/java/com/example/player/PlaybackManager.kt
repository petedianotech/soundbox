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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class PlaybackManager private constructor(private val context: Context) {

    private val repository = MusicRepository.getInstance(context)
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val settingsManager = SettingsManager.getInstance(context)

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )
        .build()

    private fun createExoPlayer(): ExoPlayer = ExoPlayer.Builder(context)
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

    private val playerA: ExoPlayer = createExoPlayer()
    private val playerB: ExoPlayer = createExoPlayer()
    @Volatile private var activeIsA: Boolean = true

    private val playerInstance: ExoPlayer get() = if (activeIsA) playerA else playerB
    private val standbyPlayerInstance: ExoPlayer get() = if (activeIsA) playerB else playerA

    val player: ExoPlayer get() = playerInstance
    val activePlayer: ExoPlayer get() = playerInstance
    val standbyPlayer: ExoPlayer get() = standbyPlayerInstance
    val fadePlayer: ExoPlayer get() = standbyPlayerInstance

    var onActivePlayerChanged: ((ExoPlayer) -> Unit)? = null

    private val crossfadeController = CrossfadeController(mainScope, ::buildMediaItem)
    val isCrossfading: Boolean get() = crossfadeController.isCrossfading
    val crossfadeActiveSongId: String? get() = crossfadeController.activeSongId

    private fun isCrossfadeOn(): Boolean = try {
        settingsManager.isCrossfadeEnabledNow()
    } catch (_: Exception) {
        settingsManager.crossfadeEnabled.value
    }

    private fun crossfadeDurationMs(): Long = try {
        settingsManager.getCrossfadeDurationMsNow().toLong()
    } catch (_: Exception) {
        settingsManager.crossfadeDurationMs.value.toLong()
    }

    private var headsetReceiverRegistered: Boolean = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var hasSystemAudioFocus = false
    private var audioFocusRequestHelper: Any? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        mainScope.launch(Dispatchers.Main) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> try { player.volume = 0.3f } catch (_: Exception) {}
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
                .setAcceptsDelayedFocusGain(true)
                .build()
            audioFocusRequestHelper = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasSystemAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasSystemAudioFocus
    }

    fun abandonSystemAudioFocus() {
        if (!hasSystemAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (audioFocusRequestHelper as? android.media.AudioFocusRequest)?.let { audioManager.abandonAudioFocusRequest(it) }
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
                _currentPosition.value = playerInstance.currentPosition.coerceAtLeast(0L)
            } else {
                lastTickTimestamp = now
            }
            handler.postDelayed(this, 250)
        }
    }

    init {
        try { NativeAudioEngine.init(44100, 2) } catch (e: Exception) { Log.w("PlaybackManager", "NativeAudioEngine init: ${e.message}") }
        setupPlayerListeners(playerA)
        setupPlayerListeners(playerB)
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
                if (target !== playerInstance && !crossfadeController.isCrossfading) return
                if (target === playerInstance || (crossfadeController.isCrossfading && target === standbyPlayerInstance)) {
                    _isPlaying.value = isPlayingChanged || (crossfadeController.isCrossfading && standbyPlayerInstance.isPlaying)
                    if (target === playerInstance) _duration.value = target.duration.coerceAtLeast(0L)
                    com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (target === playerInstance) {
                            val sid = target.audioSessionId
                            if (sid > 0) { _audioSessionId.value = sid; initAudioEffects(sid) }
                        }
                    }
                    Player.STATE_ENDED -> {
                        if (target !== playerInstance) return
                        if (crossfadeController.isCrossfading) return
                        val currentQueue = _queue.value
                        val next = resolveNextSong()
                        when {
                            next != null -> if (isCrossfadeOn()) performPowerampCrossfade(next, currentQueue) else playSongDirect(next, currentQueue)
                            _repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty() -> {
                                val first = currentQueue.first()
                                if (isCrossfadeOn()) performPowerampCrossfade(first, currentQueue) else playSongDirect(first, currentQueue)
                            }
                            _shuffleMode.value && currentQueue.size > 1 -> {
                                val randomNext = currentQueue.filter { it.id != _currentSong.value?.id }.randomOrNull() ?: currentQueue.first()
                                if (isCrossfadeOn()) performPowerampCrossfade(randomNext, currentQueue) else playSongDirect(randomNext, currentQueue)
                            }
                            else -> { _isPlaying.value = false; saveCurrentState(_currentSong.value?.id ?: "", 0L) }
                        }
                    }
                    else -> {}
                }
                com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackManager", "ExoPlayer error: ${error.errorCodeName}", error)
                _isPlaying.value = false
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId ?: return
                currentSongPlayedMs = 0L
                hasCountedPlayForCurrentSong = false
                lastTickTimestamp = SystemClock.elapsedRealtime()
                _queue.value.find { it.id == mediaId }?.let { _currentSong.value = it; _duration.value = it.duration }
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
                if (audioSessionId > 0 && target === playerInstance) {
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
                    context.applicationContext, HeadsetPlugReceiver(), headsetFilter,
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
        if (equalizer != null && lastAttachedSessionId == audioSessionId) { applyHardwareEqualizerBands(); return }
        releaseAudioEffects()
        lastAttachedSessionId = audioSessionId
        try {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = _equalizerEnabled.value }
            _equalizerHardwareBands.value = equalizer?.numberOfBands?.toInt() ?: 0
            _equalizerStatus.value = "Hardware EQ (${_equalizerHardwareBands.value} bands)"
            applyHardwareEqualizerBands()
        } catch (e: Exception) {
            Log.w("PlaybackManager", "EQ init: ${e.message}")
            _equalizerStatus.value = "Software DSP Active"
        }
        try {
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = _bassBoostStrength.value > 0; setStrength(_bassBoostStrength.value.toShort()) }
        } catch (_: Exception) {}
        try {
            virtualizer = Virtualizer(0, audioSessionId).apply { enabled = _virtualizerStrength.value > 0; setStrength(_virtualizerStrength.value.toShort()) }
        } catch (_: Exception) {}
    }

    private fun applyHardwareEqualizerBands() {
        val eq = equalizer ?: return
        val levels = _eqBandLevels.value
        try {
            val n = eq.numberOfBands.toInt()
            for (i in 0 until minOf(n, levels.size)) {
                val range = eq.getBandLevelRange()
                val level = (levels[i] * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                eq.setBandLevel(i.toShort(), level)
            }
        } catch (_: Exception) {}
    }

    private fun buildMediaItem(song: Song): MediaItem {
        return MediaItem.Builder().setUri(song.uri).setMediaId(song.id)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setDisplayTitle(song.title)
                    .build()
            ).build()
    }

    private fun resolveNextSong(): Song? {
        val q = _queue.value
        val cur = _currentSong.value ?: return q.firstOrNull()
        val idx = q.indexOfFirst { it.id == cur.id }
        return if (idx >= 0 && idx < q.size - 1) q[idx + 1] else null
    }

    private fun getTargetMasterVolume(): Float = fadeVolumeMultiplier.coerceIn(0f, 1f)
    private fun updatePlayerVolume() {
        try { playerInstance.volume = getTargetMasterVolume() } catch (_: Exception) {}
    }

    fun startPlaybackService() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (e: Exception) { Log.w("PlaybackManager", "startPlaybackService: ${e.message}") }
    }

    fun playSongDirect(song: Song, customQueue: List<Song> = emptyList()) {
        mainScope.launch(Dispatchers.Main) {
            try {
                crossfadeController.cancel()
                requestSystemAudioFocus()
                _currentSong.value = song
                if (customQueue.isNotEmpty()) _queue.value = customQueue
                val item = buildMediaItem(song)
                playerInstance.stop()
                playerInstance.clearMediaItems()
                playerInstance.setMediaItem(item)
                playerInstance.volume = getTargetMasterVolume()
                playerInstance.prepare()
                playerInstance.playWhenReady = true
                playerInstance.play()
                _isPlaying.value = true
                startPlaybackService()
                // Retry for Bluetooth / cold start
                delay(400)
                if (!playerInstance.isPlaying) {
                    playerInstance.prepare()
                    playerInstance.play()
                }
            } catch (e: Exception) {
                Log.e("PlaybackManager", "playSongDirect failed: ${e.message}", e)
            }
        }
    }

    private fun performPowerampCrossfade(nextSong: Song, queue: List<Song>) {
        if (crossfadeController.isCrossfading) return
        val duration = crossfadeDurationMs()
        val master = getTargetMasterVolume()
        val outgoing = playerInstance
        val incoming = standbyPlayerInstance
        _queue.value = queue
        _currentSong.value = nextSong
        crossfadeController.start(outgoing, incoming, nextSong, duration, master) {
            activeIsA = !activeIsA
            onActivePlayerChanged?.invoke(playerInstance)
            try {
                playerInstance.volume = master
                playerInstance.playWhenReady = true
                if (!playerInstance.isPlaying) playerInstance.play()
            } catch (_: Exception) {}
            startPlaybackService()
            com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
        }
    }

    fun play() {
        mainScope.launch(Dispatchers.Main) {
            requestSystemAudioFocus()
            playerInstance.playWhenReady = true
            playerInstance.play()
            _isPlaying.value = true
            startPlaybackService()
        }
    }

    fun pause() {
        mainScope.launch(Dispatchers.Main) {
            playerInstance.pause()
            _isPlaying.value = false
        }
    }

    fun playSong(song: Song, customQueue: List<Song> = emptyList()) = playSongDirect(song, customQueue)
    fun playPause() { if (playerInstance.isPlaying) pause() else play() }

    fun resumeOnHeadsetConnected() {
        mainScope.launch(Dispatchers.Main) {
            delay(900) // A2DP settle
            requestSystemAudioFocus()
            if (_currentSong.value != null) {
                playerInstance.playWhenReady = true
                playerInstance.play()
                _isPlaying.value = true
                delay(350)
                if (!playerInstance.isPlaying) {
                    playerInstance.prepare()
                    playerInstance.play()
                }
            }
        }
    }

    fun pauseOnHeadsetDisconnected() { pause() }

    fun skipNext() {
        val next = resolveNextSong()
        if (next != null) {
            if (isCrossfadeOn() && (_isPlaying.value || playerInstance.isPlaying)) {
                performPowerampCrossfade(next, _queue.value)
            } else {
                playSongDirect(next, _queue.value)
            }
        }
    }

    fun skipPrevious() {
        val q = _queue.value
        val cur = _currentSong.value
        val idx = q.indexOfFirst { it.id == cur?.id }
        val prev = if (idx > 0) q[idx - 1] else q.lastOrNull()
        if (prev != null) playSongDirect(prev, q)
    }

    fun seekTo(position: Long) { try { playerInstance.seekTo(position) } catch (_: Exception) {} }
    fun seekBackward() { seekTo((playerInstance.currentPosition - 10000).coerceAtLeast(0)) }
    fun seekForward() { seekTo(playerInstance.currentPosition + 10000) }

    fun setShuffleMode(enabled: Boolean) { _shuffleMode.value = enabled }
    fun setRepeatMode(mode: Int) { _repeatMode.value = mode; try { playerInstance.repeatMode = mode } catch (_: Exception) {} }
    fun setPlaybackSpeed(speed: Float) { _playbackSpeed.value = speed; try { playerInstance.playbackParameters = PlaybackParameters(speed, _playbackPitch.value) } catch (_: Exception) {} }
    fun setPlaybackPitch(pitch: Float) { _playbackPitch.value = pitch; try { playerInstance.playbackParameters = PlaybackParameters(_playbackSpeed.value, pitch) } catch (_: Exception) {} }
    fun setPlaybackRate(rate: Float) = setPlaybackSpeed(rate)

    fun setEqualizerEnabled(enabled: Boolean) { _equalizerEnabled.value = enabled; try { equalizer?.enabled = enabled } catch (_: Exception) {} }
    fun setEqBandLevels(levels: List<Float>) { _eqBandLevels.value = levels; applyHardwareEqualizerBands() }
    fun setAudioBalance(balance: Float) { _audioBalance.value = balance; updatePlayerVolume() }
    fun setEqualizerPresetName(name: String) { _currentPresetName.value = name }
    fun toggleEqualizer() { setEqualizerEnabled(!_equalizerEnabled.value) }
    fun setEqBandLevel(band: Int, level: Float) {
        val levels = _eqBandLevels.value.toMutableList()
        if (band in levels.indices) { levels[band] = level; setEqBandLevels(levels) }
    }
    fun resetEqualizerToFlat() { setEqBandLevels(List(10) { 0f }) }

    fun setSleepTimer(millis: Long) {
        sleepTimer?.cancel()
        if (millis <= 0) { _sleepTimerMillis.value = 0L; return }
        _sleepTimerMillis.value = millis
        sleepTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    mainScope.launch(Dispatchers.Main) {
                        if (sleepTimerFadeOutEnabled) {
                            for (i in 10 downTo 0) {
                                fadeVolumeMultiplier = i / 10f
                                updatePlayerVolume()
                                delay(300)
                            }
                        }
                        pause()
                        fadeVolumeMultiplier = 1f
                        updatePlayerVolume()
                        _sleepTimerMillis.value = 0L
                    }
                }
            }, millis)
        }
    }
    fun startSleepTimer(minutes: Int) = setSleepTimer(minutes * 60_000L)
    fun startSleepTimerSeconds(seconds: Int) = setSleepTimer(seconds * 1000L)
    fun cancelSleepTimer() { sleepTimer?.cancel(); sleepTimer = null; _sleepTimerMillis.value = 0L; fadeVolumeMultiplier = 1f; updatePlayerVolume() }
    fun stopSleepTimer() = cancelSleepTimer()
    fun startSleepTimerEndOfTrack() {}
    fun extendSleepTimer(extraMs: Long) { setSleepTimer(_sleepTimerMillis.value + extraMs) }

    fun toggleFavorite(song: Song) { mainScope.launch(Dispatchers.IO) { repository.toggleFavorite(song.id, song.isFavorite) } }
    fun addToQueue(song: Song) { _queue.value = _queue.value + song }
    fun removeFromQueue(song: Song) { _queue.value = _queue.value.filter { it.id != song.id } }
    fun clearQueue() { _queue.value = emptyList() }
    fun playNext(song: Song) {
        val q = _queue.value.toMutableList()
        val idx = q.indexOfFirst { it.id == _currentSong.value?.id }
        if (idx >= 0) q.add(idx + 1, song) else q.add(song)
        _queue.value = q
    }
    fun onSongTrimmed(updatedSong: Song) { if (_currentSong.value?.id == updatedSong.id) _currentSong.value = updatedSong }

    private fun restorePlaybackState() {}
    private fun saveCurrentState(id: String, pos: Long) {}

    fun release() {
        handler.removeCallbacks(positionTrackerRunnable)
        sleepTimer?.cancel()
        mainScope.launch(Dispatchers.Main) {
            try { playerA.release() } catch (_: Exception) {}
            try { playerB.release() } catch (_: Exception) {}
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
