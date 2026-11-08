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
import kotlinx.coroutines.flow.firstOrNull
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
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _bassBoostStrength.value > 0
                if (strengthSupported) setStrength(_bassBoostStrength.value.toShort().coerceIn(0, 1000))
            }
        } catch (_: Exception) {}
        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = _virtualizerStrength.value > 0
                if (strengthSupported) setStrength(_virtualizerStrength.value.toShort().coerceIn(0, 1000))
            }
        } catch (_: Exception) {}
    }

    private fun applyHardwareEqualizerBands() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            if (numBands <= 0) return
            val currentGains = _eqBandLevels.value
            val preamp = _preampGain.value
            val minLevel = eq.bandLevelRange?.get(0)?.toInt() ?: -1500
            val maxLevel = eq.bandLevelRange?.get(1)?.toInt() ?: 1500
            for (hwBand in 0 until numBands) {
                val idx = (hwBand * currentGains.size / numBands).coerceIn(0, currentGains.lastIndex)
                val gain = ((currentGains.getOrElse(idx) { 0f } + preamp) * 100f).toInt().coerceIn(minLevel, maxLevel)
                eq.setBandLevel(hwBand.toShort(), gain.toShort())
            }
        } catch (_: Exception) {}
    }

    private fun buildMediaItem(songItem: Song): MediaItem {
        val artUri = try { com.example.util.AlbumArtHelper.getArtworkUri(context, songItem) } catch (_: Exception) { null }
        return MediaItem.Builder()
            .setUri(songItem.path)
            .setMediaId(songItem.id)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(songItem.title)
                    .setArtist(songItem.artist)
                    .setAlbumTitle(songItem.album)
                    .setDisplayTitle(songItem.title)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
    }

    private fun getTargetMasterVolume(): Float = 1.0f

    private fun updatePlayerVolume() {
        try {
            playerInstance.volume = (getTargetMasterVolume() * fadeVolumeMultiplier).coerceIn(0f, 1f)
        } catch (_: Exception) {}
    }

    private fun saveCurrentState(songId: String, position: Long) {
        try {
            context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE).edit()
                .putString("last_song_id", songId)
                .putLong("last_position", position)
                .apply()
        } catch (_: Exception) {}
    }

    private fun restorePlaybackState() {
        mainScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE)
                val lastId = prefs.getString("last_song_id", null) ?: return@launch
                val song = repository.getSongById(lastId) ?: return@launch
                val pos = prefs.getLong("last_position", 0L)
                mainScope.launch(Dispatchers.Main) {
                    _currentSong.value = song
                    _duration.value = song.duration
                    _currentPosition.value = pos
                }
            } catch (_: Exception) {}
        }
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "startPlaybackService: ${e.message}")
        }
    }

    private fun resolveNextSong(): Song? {
        val q = _queue.value
        if (q.isEmpty()) return null
        val cur = _currentSong.value
        val idx = q.indexOfFirst { it.id == cur?.id }
        return when {
            idx >= 0 && idx + 1 < q.size -> q[idx + 1]
            _repeatMode.value == Player.REPEAT_MODE_ALL -> q.firstOrNull()
            else -> null
        }
    }

    fun playSongDirect(song: Song, customQueue: List<Song> = emptyList()) {
        try { crossfadeController.cancel() } catch (_: Exception) {}
        try { standbyPlayerInstance.pause(); standbyPlayerInstance.stop(); standbyPlayerInstance.clearMediaItems() } catch (_: Exception) {}
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
            val p = playerInstance
            p.playWhenReady = true
            p.stop()
            p.clearMediaItems()
            p.setMediaItems(mediaItems, index, 0L)
            p.prepare()
            p.play()
        } catch (e: Exception) {
            Log.e("PlaybackManager", "playSongDirect failed: ${e.message}", e)
            mainScope.launch(Dispatchers.Main) {
                delay(350)
                try {
                    requestSystemAudioFocus()
                    val p = playerInstance
                    p.prepare(); p.playWhenReady = true; p.play()
                } catch (e2: Exception) {
                    Log.e("PlaybackManager", "playSongDirect retry failed: ${e2.message}", e2)
                }
            }
        }
        val sid = playerInstance.audioSessionId
        if (sid > 0) { _audioSessionId.value = sid; try { initAudioEffects(sid) } catch (_: Exception) {} }
        saveCurrentState(song.id, 0L)
        startPlaybackService()
        com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
        mainScope.launch(Dispatchers.Main) { ensurePlayingWithRetries(3, 400L) }
    }

    private suspend fun ensurePlayingWithRetries(maxAttempts: Int = 3, baseDelayMs: Long = 400L) {
        val p = playerInstance
        for (attempt in 0 until maxAttempts) {
            if (p.isPlaying) return
            try {
                requestSystemAudioFocus()
                when (p.playbackState) {
                    Player.STATE_IDLE, Player.STATE_ENDED -> { p.prepare(); p.playWhenReady = true; p.play() }
                    Player.STATE_BUFFERING, Player.STATE_READY -> { p.playWhenReady = true; if (!p.isPlaying) p.play() }
                    else -> { p.playWhenReady = true; p.play() }
                }
            } catch (e: Exception) {
                Log.w("PlaybackManager", "ensurePlaying attempt ${attempt + 1}: ${e.message}")
            }
            delay(baseDelayMs * (attempt + 1))
        }
    }

    private fun performPowerampCrossfade(nextSong: Song, queue: List<Song>) {
        if (crossfadeController.isCrossfading) return
        val outgoing = playerInstance
        val incoming = standbyPlayerInstance
        val master = (getTargetMasterVolume() * fadeVolumeMultiplier).coerceIn(0.05f, 1.0f)
        _queue.value = queue
        _currentSong.value = nextSong
        _duration.value = nextSong.duration
        currentSongPlayedMs = 0L
        hasCountedPlayForCurrentSong = false
        lastTickTimestamp = SystemClock.elapsedRealtime()
        requestSystemAudioFocus()
        startPlaybackService()
        crossfadeController.start(outgoing, incoming, nextSong, crossfadeDurationMs(), master) {
            activeIsA = !activeIsA
            try {
                val active = playerInstance
                active.volume = master
                active.playWhenReady = true
                if (!active.isPlaying) active.play()
                onActivePlayerChanged?.invoke(active)
                val sid = active.audioSessionId
                if (sid > 0) { _audioSessionId.value = sid; try { initAudioEffects(sid) } catch (_: Exception) {} }
            } catch (_: Exception) {}
            saveCurrentState(nextSong.id, 0L)
            _isPlaying.value = true
            com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
            mainScope.launch(Dispatchers.Main) {
                delay(200)
                if (!playerInstance.isPlaying) {
                    try { playerInstance.playWhenReady = true; playerInstance.play() } catch (_: Exception) {}
                }
            }
        }
    }

    fun play() {
        mainScope.launch(Dispatchers.Main) {
            requestSystemAudioFocus()
            updatePlayerVolume()
            val p = playerInstance
            try {
                if (p.mediaItemCount == 0) {
                    val lastId = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE).getString("last_song_id", null)
                    val song = _currentSong.value
                        ?: (if (lastId != null) repository.getSongById(lastId) else null)
                        ?: repository.allSongs.firstOrNull()?.firstOrNull()
                    if (song != null) {
                        playSongDirect(song, _queue.value.ifEmpty { listOf(song) })
                        return@launch
                    }
                }
                if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
                    p.prepare()
                }
                p.playWhenReady = true
                p.play()
                startPlaybackService()
                ensurePlayingWithRetries(3, 400L)
            } catch (e: Exception) {
                Log.e("PlaybackManager", "play() failed: ${e.message}", e)
            }
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
            _queue.value = _queue.value + song
            playerInstance.addMediaItem(buildMediaItem(song))
        }
    }

    fun removeFromQueue(index: Int) {
        mainScope.launch(Dispatchers.Main) {
            if (index in 0 until playerInstance.mediaItemCount) {
                playerInstance.removeMediaItem(index)
                val updated = _queue.value.toMutableList()
                if (index < updated.size) { updated.removeAt(index); _queue.value = updated }
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

    fun playPause() { mainScope.launch(Dispatchers.Main) { if (playerInstance.isPlaying) pause() else play() } }

    fun resumeOnHeadsetConnected() {
        mainScope.launch(Dispatchers.Main) {
            delay(900)
            if (_isPlaying.value || playerInstance.isPlaying) return@launch
            requestSystemAudioFocus()
            if (playerInstance.mediaItemCount > 0) { play(); ensurePlayingWithRetries(3, 500L); return@launch }
            val targetSong = _currentSong.value ?: run {
                val lastSongId = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE).getString("last_song_id", null)
                if (lastSongId != null) repository.getSongById(lastSongId) else null
            } ?: repository.allSongs.firstOrNull()?.firstOrNull()
            if (targetSong != null) playSong(targetSong)
        }
    }

    fun pauseOnHeadsetDisconnected() {
        mainScope.launch(Dispatchers.Main) { if (playerInstance.isPlaying || _isPlaying.value) pause() }
    }

    fun skipNext() {
        mainScope.launch(Dispatchers.Main) {
            if (crossfadeController.isCrossfading) crossfadeController.cancel()
            val nextSong = resolveNextSong()
            if (nextSong != null) {
                if (isCrossfadeOn() && (_isPlaying.value || playerInstance.isPlaying)) performPowerampCrossfade(nextSong, _queue.value)
                else playSongDirect(nextSong, _queue.value)
            } else if (playerInstance.hasNextMediaItem()) playerInstance.seekToNext()
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
            } else null
            if (prevSong != null) playSongDirect(prevSong, currentQueue)
            else if (playerInstance.hasPreviousMediaItem()) playerInstance.seekToPrevious()
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
            val pos = (playerInstance.currentPosition - ms).coerceAtLeast(0L)
            playerInstance.seekTo(pos)
            _currentPosition.value = pos
        }
    }

    fun seekForward(ms: Long = 10000L) {
        mainScope.launch(Dispatchers.Main) {
            val pos = (playerInstance.currentPosition + ms).coerceAtMost(playerInstance.duration.coerceAtLeast(0L))
            playerInstance.seekTo(pos)
            _currentPosition.value = pos
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

    fun setBassBoostStrength(strength: Int) = setBassBoost(strength)

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _bassBoostStrength.value = clamped
        settingsManager.setBassBoostStrength(clamped)
        try {
            bassBoost?.let {
                it.enabled = clamped > 0
                if (clamped > 0 && it.strengthSupported) it.setStrength(clamped.toShort())
            }
        } catch (_: Exception) {}
    }

    fun setTrebleGain(gain: Float) {
        _trebleGain.value = gain
        settingsManager.setTrebleGain(gain)
        applyHardwareEqualizerBands()
    }

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
        val n = !_equalizerEnabled.value
        setEqualizerEnabled(n)
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

    fun setSleepTimer(millis: Long, fadeOut: Boolean = true) {
        sleepTimer?.cancel()
        sleepTimerFadeOutEnabled = fadeOut
        _sleepTimerMillis.value = millis
        if (millis <= 0L) return
        sleepTimer = Timer().also { t ->
            t.schedule(object : TimerTask() {
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

    fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
        _sleepTimerMillis.value = 0L
        fadeVolumeMultiplier = 1f
        updatePlayerVolume()
    }

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
