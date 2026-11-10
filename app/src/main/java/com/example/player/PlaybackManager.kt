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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
                .setAcceptsDelayedFocusGain(false)
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
                val curPos = playerInstance.currentPosition.coerceAtLeast(0L)
                _currentPosition.value = curPos
                // Clean playback position tracking without intrusive crossfade interruption
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
                            next != null -> playSongDirect(next, currentQueue)
                            _repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty() -> {
                                val first = currentQueue.first()
                                playSongDirect(first, currentQueue)
                            }
                            _shuffleMode.value && currentQueue.size > 1 -> {
                                val randomNext = currentQueue.filter { it.id != _currentSong.value?.id }.randomOrNull() ?: currentQueue.first()
                                playSongDirect(randomNext, currentQueue)
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
        } catch (e: Exception) {
            Log.w("PlaybackManager", "EQ init: ${e.message}")
            _equalizerStatus.value = "Software DSP Active"
        }
        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _bassBoostStrength.value > 0
                setStrength(_bassBoostStrength.value.toShort().coerceIn(0, 1000))
            }
        } catch (_: Exception) {}
        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = _virtualizerStrength.value > 0
                setStrength(_virtualizerStrength.value.toShort().coerceIn(0, 1000))
            }
        } catch (_: Exception) {}
        applyHardwareEqualizerBands()
    }

    private fun applyHardwareEqualizerBands() {
        val eq = equalizer ?: return
        try {
            val levels = _eqBandLevels.value
            val n = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]
            for (i in 0 until n) {
                val db = if (i < levels.size) levels[i] else 0f
                val milliBel = (db * 100).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                eq.setBandLevel(i.toShort(), milliBel)
            }
            eq.enabled = _equalizerEnabled.value
        } catch (_: Exception) {}
    }

    private fun updatePlayerVolume() {
        try {
            val vol = (getTargetMasterVolume() * fadeVolumeMultiplier).coerceIn(0.05f, 1.0f)
            if (!crossfadeController.isCrossfading) playerInstance.volume = vol
        } catch (_: Exception) {}
    }

    private fun getTargetMasterVolume(): Float = 1.0f

    private fun buildMediaItem(songItem: Song): MediaItem {
        val fileUri = repository.getSongUri(songItem) ?: if (songItem.path.startsWith("content://") || songItem.path.startsWith("file://")) {
            android.net.Uri.parse(songItem.path)
        } else {
            android.net.Uri.fromFile(java.io.File(songItem.path))
        }
        val artUri = com.example.util.AlbumArtHelper.getArtworkUri(context, songItem)
        return MediaItem.Builder()
            .setMediaId(songItem.id)
            .setUri(fileUri)
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

    private fun resolveNextSong(): Song? {
        val q = _queue.value
        if (q.isEmpty()) return null
        val cur = _currentSong.value
        val idx = q.indexOfFirst { it.id == cur?.id }
        return when {
            _shuffleMode.value && q.size > 1 -> q.filter { it.id != cur?.id }.randomOrNull()
            idx >= 0 && idx + 1 < q.size -> q[idx + 1]
            _repeatMode.value == Player.REPEAT_MODE_ALL -> q.firstOrNull()
            else -> null
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
                onActivePlayerChanged?.invoke(active)
                val sid = active.audioSessionId
                if (sid > 0) { _audioSessionId.value = sid; try { initAudioEffects(sid) } catch (_: Exception) {} }
            } catch (_: Exception) {}
            saveCurrentState(nextSong.id, 0L)
            _isPlaying.value = true
            com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
        }
    }

    private fun playSongDirect(song: Song, queue: List<Song>) {
        mainScope.launch(Dispatchers.Main) {
            try { crossfadeController.cancel() } catch (_: Exception) {}
            try {
                standbyPlayerInstance.pause()
                standbyPlayerInstance.stop()
                standbyPlayerInstance.clearMediaItems()
            } catch (_: Exception) {}
            _queue.value = queue
            _currentSong.value = song
            _duration.value = song.duration
            currentSongPlayedMs = 0L
            hasCountedPlayForCurrentSong = false
            lastTickTimestamp = SystemClock.elapsedRealtime()
            requestSystemAudioFocus()
            val p = playerInstance
            try {
                p.stop()
                p.clearMediaItems()
                p.setMediaItem(buildMediaItem(song))
                p.prepare()
                p.playWhenReady = true
                p.play()
                p.volume = getTargetMasterVolume() * fadeVolumeMultiplier
            } catch (e: Exception) {
                Log.e("PlaybackManager", "playSongDirect failed", e)
            }
            val sid = p.audioSessionId
            if (sid > 0) { _audioSessionId.value = sid; try { initAudioEffects(sid) } catch (_: Exception) {} }
            saveCurrentState(song.id, 0L)
            startPlaybackService()
            com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
        }
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        playSongDirect(song, queue.ifEmpty { listOf(song) })
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
                    if (song != null) { playSongDirect(song, _queue.value.ifEmpty { listOf(song) }); return@launch }
                }
                if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) p.prepare()
                p.playWhenReady = true
                p.play()
            } catch (e: Exception) {
                Log.e("PlaybackManager", "play failed", e)
            }
            startPlaybackService()
        }
    }

    fun pause() {
        mainScope.launch(Dispatchers.Main) {
            try {
                if (crossfadeController.isCrossfading) crossfadeController.cancel()
                playerInstance.pause()
                standbyPlayerInstance.pause()
            } catch (_: Exception) {}
            _isPlaying.value = false
            saveCurrentState(_currentSong.value?.id ?: "", playerInstance.currentPosition.coerceAtLeast(0L))
            com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
        }
    }

    fun playPause() { mainScope.launch(Dispatchers.Main) { if (playerInstance.isPlaying) pause() else play() } }

    fun playNext(song: Song) {
        mainScope.launch(Dispatchers.Main) {
            val currentQueue = _queue.value.toMutableList()
            val currentIndex = playerInstance.currentMediaItemIndex
            val mediaItem = buildMediaItem(song)
            if (currentIndex in 0 until currentQueue.size) {
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
            standbyPlayerInstance.clearMediaItems()
            _queue.value = emptyList()
            _currentSong.value = null
            saveCurrentState("", 0L)
        }
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
            if (crossfadeController.isCrossfading) crossfadeController.cancel()
            val currentQueue = _queue.value
            val currentIndex = currentQueue.indexOfFirst { it.id == _currentSong.value?.id }
            if (playerInstance.currentPosition > 3000L) { playerInstance.seekTo(0L); return@launch }
            val prevSong = if (currentIndex - 1 >= 0) currentQueue[currentIndex - 1]
            else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) currentQueue.last()
            else null
            if (prevSong != null) {
                if (isCrossfadeOn() && (_isPlaying.value || playerInstance.isPlaying)) performPowerampCrossfade(prevSong, currentQueue)
                else playSongDirect(prevSong, currentQueue)
            } else if (playerInstance.hasPreviousMediaItem()) playerInstance.seekToPrevious()
            else playerInstance.seekTo(0L)
        }
    }

    fun seekTo(position: Long) { mainScope.launch(Dispatchers.Main) { playerInstance.seekTo(position); _currentPosition.value = position } }
    fun seekBackward(ms: Long = 10000L) { mainScope.launch(Dispatchers.Main) { val p = (playerInstance.currentPosition - ms).coerceAtLeast(0L); playerInstance.seekTo(p); _currentPosition.value = p } }
    fun seekForward(ms: Long = 10000L) { mainScope.launch(Dispatchers.Main) { val d = if (playerInstance.duration > 0) playerInstance.duration else Long.MAX_VALUE; val p = (playerInstance.currentPosition + ms).coerceAtMost(d); playerInstance.seekTo(p); _currentPosition.value = p } }
    fun setShuffleMode(enabled: Boolean) { mainScope.launch(Dispatchers.Main) { _shuffleMode.value = enabled; playerInstance.shuffleModeEnabled = enabled } }
    fun setRepeatMode(mode: Int) { mainScope.launch(Dispatchers.Main) { _repeatMode.value = mode; playerInstance.repeatMode = mode } }
    fun setPlaybackSpeed(speed: Float) { mainScope.launch(Dispatchers.Main) { _playbackSpeed.value = speed; playerInstance.playbackParameters = PlaybackParameters(speed, _playbackPitch.value) } }
    fun setPlaybackPitch(pitch: Float) { mainScope.launch(Dispatchers.Main) { _playbackPitch.value = pitch; playerInstance.playbackParameters = PlaybackParameters(_playbackSpeed.value, pitch) } }
    fun setPlaybackRate(speed: Float, pitch: Float) { mainScope.launch(Dispatchers.Main) { playerInstance.playbackParameters = PlaybackParameters(speed, pitch); _playbackSpeed.value = speed; _playbackPitch.value = pitch } }

    fun setEqualizerEnabled(enabled: Boolean) { _equalizerEnabled.value = enabled; settingsManager.setEqualizerEnabled(enabled); try { equalizer?.enabled = enabled } catch (_: Exception) {}; applyHardwareEqualizerBands() }
    fun setEqBandLevels(levels: List<Float>) { _eqBandLevels.value = levels; settingsManager.setEqualizerBandLevels(levels); applyHardwareEqualizerBands() }
    fun setPreampGain(gain: Float) { _preampGain.value = gain; settingsManager.setPreampGain(gain); applyHardwareEqualizerBands() }
    fun setBassBoost(strength: Int) { _bassBoostStrength.value = strength; settingsManager.setBassBoostStrength(strength); try { bassBoost?.setStrength(strength.toShort().coerceIn(0, 1000)); bassBoost?.enabled = strength > 0 } catch (_: Exception) {} }
    fun setBassBoostStrength(strength: Int) { setBassBoost(strength) }
    fun setTrebleGain(gain: Float) { _trebleGain.value = gain; settingsManager.setTrebleGain(gain); applyHardwareEqualizerBands() }
    fun setVirtualizerStrength(strength: Int) { _virtualizerStrength.value = strength; settingsManager.setVirtualizerStrength(strength); try { virtualizer?.setStrength(strength.toShort().coerceIn(0, 1000)); virtualizer?.enabled = strength > 0 } catch (_: Exception) {} }
    fun setAudioBalance(balance: Float) { _audioBalance.value = balance; settingsManager.setAudioBalance(balance) }
    fun setReverbPreset(preset: Int) { _reverbPreset.value = preset; settingsManager.setReverbPreset(preset) }
    fun setEqualizerPresetName(name: String) { _currentPresetName.value = name; settingsManager.setEqualizerPresetName(name) }
    fun toggleEqualizer() { val n = !_equalizerEnabled.value; _equalizerEnabled.value = n; settingsManager.setEqualizerEnabled(n); try { equalizer?.enabled = n; if (n) applyHardwareEqualizerBands() } catch (_: Exception) {} }
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
        name: String,
        bands: List<Float>,
        bassBoost: Int = 300,
        treble: Float = 0f,
        virtualizer: Int = 0,
        reverb: Int = 0
    ) {
        _currentPresetName.value = name
        _eqBandLevels.value = bands
        settingsManager.setEqualizerPresetName(name)
        settingsManager.setEqualizerBandLevels(bands)
        setBassBoost(bassBoost)
        setTrebleGain(treble)
        setVirtualizerStrength(virtualizer)
        setReverbPreset(reverb)
        applyHardwareEqualizerBands()
    }

    fun resetEqualizerToFlat() {
        applyPowerampPreset(
            name = "Flat",
            bands = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            bassBoost = 0,
            treble = 0f,
            virtualizer = 0,
            reverb = 0
        )
    }

    fun toggleFavorite(song: Song) {
        val targetFav = !song.isFavorite
        if (_currentSong.value?.id == song.id) _currentSong.value = _currentSong.value?.copy(isFavorite = targetFav)
        _queue.value = _queue.value.map { if (it.id == song.id) it.copy(isFavorite = targetFav) else it }
        mainScope.launch(Dispatchers.IO) { repository.toggleFavorite(song.id, song.isFavorite) }
    }

    private fun saveCurrentState(songId: String, position: Long) {
        try {
            context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE).edit()
                .putString("last_song_id", songId).putLong("last_position", position).apply()
        } catch (_: Exception) {}
    }

    private fun restorePlaybackState() {
        mainScope.launch {
            try {
                val lastSongId = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE).getString("last_song_id", null) ?: return@launch
                repository.getSongById(lastSongId)?.let { _currentSong.value = it; _duration.value = it.duration }
            } catch (_: Exception) {}
        }
    }

    fun refreshCurrentSongMetadata(updatedSong: Song) {
        if (_currentSong.value?.id == updatedSong.id) _currentSong.value = updatedSong
        val nextQueue = _queue.value.toMutableList()
        val index = nextQueue.indexOfFirst { it.id == updatedSong.id }
        if (index >= 0) { nextQueue[index] = updatedSong; _queue.value = nextQueue }
    }

    fun onSongTrimmed(updatedSong: Song) {
        refreshCurrentSongMetadata(updatedSong)
        if (_currentSong.value?.id == updatedSong.id) {
            _duration.value = updatedSong.duration
            _currentPosition.value = 0L
        }
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            Log.w("PlaybackManager", "startPlaybackService: ${e.message}")
        }
    }

    fun resumeOnHeadsetConnected() {
        mainScope.launch(Dispatchers.Main) {
            delay(550)
            if (_isPlaying.value || playerInstance.isPlaying) return@launch
            requestSystemAudioFocus()
            if (playerInstance.mediaItemCount > 0) { play(); return@launch }
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

    fun stopSleepTimer() { cancelSleepTimer() }

    fun startSleepTimerEndOfTrack(fadeOutAtEnd: Boolean = true) {
        val remainingMs = (_duration.value - _currentPosition.value).coerceAtLeast(1000L)
        startSleepTimerSeconds((remainingMs / 1000L).toInt(), fadeOutAtEnd)
    }

    fun extendSleepTimer(minutes: Int = 5) {
        val currentMs = _sleepTimerMillis.value
        val newTotalSeconds = ((currentMs + minutes * 60_000L) / 1000L).toInt()
        startSleepTimerSeconds(newTotalSeconds, sleepTimerFadeOutEnabled)
    }

    fun release() {
        abandonSystemAudioFocus()
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
