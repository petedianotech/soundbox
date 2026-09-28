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
import kotlin.math.cos
import kotlin.math.sin

/** Dual-ExoPlayer with Poweramp/Musicolet equal-power crossfade. */
class PlaybackManager private constructor(private val context: Context) {

    private val repository = MusicRepository.getInstance(context)
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val dspAudioProcessorA = SoundboxDspAudioProcessor()
    val dspAudioProcessorB = SoundboxDspAudioProcessor()
    val dspAudioProcessor: SoundboxDspAudioProcessor get() = if (activeIsA) dspAudioProcessorA else dspAudioProcessorB
    val fadeDspAudioProcessor: SoundboxDspAudioProcessor get() = if (activeIsA) dspAudioProcessorB else dspAudioProcessorA

    private val lowMemoryLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 30_000, 1_000, 2_000)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    private fun createRenderersFactory(processor: SoundboxDspAudioProcessor): DefaultRenderersFactory {
        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
                return DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(processor)).build()
            }
        }
    }

    private fun buildPlayer(processor: SoundboxDspAudioProcessor): ExoPlayer {
        return ExoPlayer.Builder(context, createRenderersFactory(processor))
            .setLoadControl(lowMemoryLoadControl)
            // handleAudioFocus=false: we manage focus via requestSystemAudioFocus() so dual players do not fight.
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), false)
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    private val playerA: ExoPlayer = buildPlayer(dspAudioProcessorA)
    private val playerB: ExoPlayer = buildPlayer(dspAudioProcessorB)
    private var activeIsA: Boolean = true

    val player: ExoPlayer get() = if (activeIsA) playerA else playerB
    val activePlayer: ExoPlayer get() = player
    val standbyPlayer: ExoPlayer get() = if (activeIsA) playerB else playerA
    val fadePlayer: ExoPlayer get() = standbyPlayer

    var onActivePlayerChanged: ((ExoPlayer) -> Unit)? = null
    @Volatile private var _isCrossfading: Boolean = false
    val isCrossfading: Boolean get() = _isCrossfading
    @Volatile var crossfadeActiveSongId: String? = null
        private set
    private var crossfadeJob: Job? = null
    private var autoCrossfadeArmedForSongId: String? = null
    private var headsetReceiverRegistered: Boolean = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var hasSystemAudioFocus = false
    private var audioFocusRequestHelper: Any? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        mainScope.launch(Dispatchers.Main) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { try { player.volume = 0.3f } catch (_: Exception) {} }
                AudioManager.AUDIOFOCUS_GAIN -> updatePlayerVolume()
            }
        }
    }

    fun requestSystemAudioFocus(): Boolean {
        if (hasSystemAudioFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(audioFocusChangeListener, Handler(Looper.getMainLooper()))
                .setAcceptsDelayedFocusGain(false).build()
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
    private var presetReverb: PresetReverb? = null

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

    private val settingsManager = SettingsManager.getInstance(context)
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
    private val _equalizerStatus = MutableStateFlow("DSP Engine Standby")
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
                            mainScope.launch(Dispatchers.IO) { repository.incrementPlayCount(current.id) }
                        }
                    }
                }
                lastTickTimestamp = now
                val curPos = player.currentPosition.coerceAtLeast(0L)
                _currentPosition.value = curPos
                maybeStartAutoCrossfade(curPos)
            } else lastTickTimestamp = now
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
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try { mediaController = controllerFuture?.get() } catch (e: Exception) { Log.e("PlaybackManager", "MediaController fail", e) }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListeners(target: ExoPlayer) {
        target.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                if (target !== player) return
                _isPlaying.value = isPlayingChanged || _isCrossfading
                _duration.value = target.duration.coerceAtLeast(0L)
                com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (target !== player) return
                if (playbackState == Player.STATE_READY) {
                    val sid = target.audioSessionId
                    if (sid > 0) { _audioSessionId.value = sid; initAudioEffects(sid) }
                } else if (playbackState == Player.STATE_ENDED && !_isCrossfading) {
                    val currentQueue = _queue.value
                    val next = resolveNextSong()
                    when {
                        next != null && settingsManager.crossfadeEnabled.value -> performPowerampCrossfade(next, currentQueue)
                        _repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty() -> playSongDirect(currentQueue.first(), currentQueue)
                        _shuffleMode.value && currentQueue.size > 1 -> playSongDirect(currentQueue.filter { it.id != _currentSong.value?.id }.randomOrNull() ?: currentQueue.first(), currentQueue)
                        else -> { _isPlaying.value = false; saveCurrentState(_currentSong.value?.id ?: "", 0L) }
                    }
                }
                com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (target !== player) return
                val mediaId = mediaItem?.mediaId ?: return
                currentSongPlayedMs = 0L; hasCountedPlayForCurrentSong = false; lastTickTimestamp = SystemClock.elapsedRealtime(); autoCrossfadeArmedForSongId = null
                _queue.value.find { it.id == mediaId }?.let { _currentSong.value = it; _duration.value = it.duration }
                mainScope.launch {
                    repository.getSongById(mediaId)?.let { song ->
                        if (target === player) {
                            _currentSong.value = song; _duration.value = song.duration
                            saveCurrentState(song.id, target.currentPosition.coerceAtLeast(0L))
                            com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
                        }
                    }
                }
            }
        })
        target.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                if (target !== player) return
                if (audioSessionId > 0) { _audioSessionId.value = audioSessionId; initAudioEffects(audioSessionId) }
            }
        })
        if (!headsetReceiverRegistered) {
            try {
                val headsetFilter = IntentFilter().apply {
                    addAction(Intent.ACTION_HEADSET_PLUG); addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                }
                androidx.core.content.ContextCompat.registerReceiver(context.applicationContext, HeadsetPlugReceiver(), headsetFilter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
                headsetReceiverRegistered = true
            } catch (e: Exception) { Log.w("PlaybackManager", "HeadsetPlugReceiver: ${e.message}") }
        }
    }

    private fun releaseAudioEffects() {
        try { equalizer?.release(); equalizer = null; bassBoost?.release(); bassBoost = null; virtualizer?.release(); virtualizer = null; presetReverb?.release(); presetReverb = null } catch (_: Exception) {}
    }

    private fun initAudioEffects(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (equalizer != null && lastAttachedSessionId == audioSessionId) { applyHardwareEqualizerBands(); return }
        releaseAudioEffects()
        lastAttachedSessionId = audioSessionId
        try {
            context.sendBroadcast(android.content.Intent(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
            })
        } catch (_: Exception) {}
        try {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = _equalizerEnabled.value }
            _equalizerHardwareBands.value = equalizer?.numberOfBands?.toInt() ?: 0
            _equalizerStatus.value = "Hardware EQ (${_equalizerHardwareBands.value} bands)"
        } catch (e: Exception) { _equalizerStatus.value = "EQ unavailable" }
        try { bassBoost = BassBoost(0, audioSessionId).apply { enabled = _bassBoostStrength.value > 0; setStrength(_bassBoostStrength.value.toShort().coerceIn(0, 1000)) } } catch (_: Exception) {}
        try { virtualizer = Virtualizer(0, audioSessionId).apply { enabled = _virtualizerStrength.value > 0; setStrength(_virtualizerStrength.value.toShort().coerceIn(0, 1000)) } } catch (_: Exception) {}
        try { presetReverb = PresetReverb(0, audioSessionId).apply { preset = _reverbPreset.value.toShort(); enabled = _reverbPreset.value > 0 } } catch (_: Exception) {}
        dspAudioProcessor.balance = _audioBalance.value
        dspAudioProcessor.virtualizerStrength = _virtualizerStrength.value
        applyHardwareEqualizerBands()
    }

    fun getTargetMasterVolume(): Float = Math.pow(10.0, (_preampGain.value / 20.0).toDouble()).toFloat().coerceIn(0.05f, 1.0f)

    private fun updatePlayerVolume() {
        try { player.volume = (getTargetMasterVolume() * fadeVolumeMultiplier).coerceIn(0.05f, 1.0f) } catch (_: Exception) {}
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
                val hwFreqHz = try { (eq.getCenterFreq(hwBand.toShort()) / 1000).coerceAtLeast(20) } catch (_: Exception) {
                    val ratio = hwBand.toFloat() / (numBands - 1).coerceAtLeast(1)
                    (31 * Math.pow(16000.0 / 31.0, ratio.toDouble())).toInt()
                }
                var closestIdx = 0; var minDiff = Float.MAX_VALUE
                for (i in userBandFrequencies.indices) {
                    val diff = Math.abs(Math.log(hwFreqHz.toDouble()) - Math.log(userBandFrequencies[i].toDouble())).toFloat()
                    if (diff < minDiff) { minDiff = diff; closestIdx = i }
                }
                var targetGainDb = currentGains.getOrElse(closestIdx) { 0f } + preamp
                if (hwFreqHz >= 3000) targetGainDb += treble * ((hwFreqHz - 3000f) / 13000f).coerceIn(0.2f, 1f)
                if (hwFreqHz <= 250) targetGainDb += (bassBoostFactor * 6f) * (1f - (hwFreqHz / 250f)).coerceIn(0.2f, 1f)
                eq.setBandLevel(hwBand.toShort(), (targetGainDb * 100f).toInt().coerceIn(minLevel, maxLevel).toShort())
            }
        } catch (_: Exception) {}
    }

    private fun buildMediaItem(songItem: Song): MediaItem {
        val fileUri = if (songItem.path.startsWith("content://") || songItem.path.startsWith("file://")) android.net.Uri.parse(songItem.path) else android.net.Uri.fromFile(java.io.File(songItem.path))
        val artUri = com.example.util.AlbumArtHelper.getArtworkUri(context, songItem)
        return MediaItem.Builder().setMediaId(songItem.id).setUri(fileUri)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(songItem.title).setArtist(songItem.artist).setAlbumTitle(songItem.album).setDisplayTitle(songItem.title).setArtworkUri(artUri).build())
            .build()
    }

    fun startPlaybackService() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        } catch (e: Exception) { Log.e("PlaybackManager", "startPlaybackService: ${e.message}") }
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

    private fun isCrossfadeEnabledNow(): Boolean {
        // Always read latest from SharedPreferences so settings apply without restart
        return try {
            context.getSharedPreferences("soundbox_settings", Context.MODE_PRIVATE)
                .getBoolean("crossfade_enabled", settingsManager.crossfadeEnabled.value)
        } catch (_: Exception) {
            settingsManager.crossfadeEnabled.value
        }
    }

    private fun crossfadeDurationMsNow(): Int {
        return try {
            context.getSharedPreferences("soundbox_settings", Context.MODE_PRIVATE)
                .getInt("crossfade_duration_ms", settingsManager.crossfadeDurationMs.value)
                .coerceIn(1000, 12000)
        } catch (_: Exception) {
            settingsManager.crossfadeDurationMs.value.coerceIn(1000, 12000)
        }
    }

    private fun maybeStartAutoCrossfade(curPos: Long) {
        if (_isCrossfading || !isCrossfadeEnabledNow()) return
        val song = _currentSong.value ?: return
        if (autoCrossfadeArmedForSongId == song.id) return
        val dur = player.duration.takeIf { it > 0 } ?: song.duration
        if (dur <= 0L) return
        val fadeMs = crossfadeDurationMsNow().toLong()
        if (dur <= fadeMs + 1500L) return
        if ((dur - curPos) in 1L..fadeMs) {
            val next = resolveNextSong() ?: return
            if (next.id == song.id) return
            autoCrossfadeArmedForSongId = song.id
            performPowerampCrossfade(next, _queue.value)
        }
    }

    fun performPowerampCrossfade(nextSong: Song, customQueue: List<Song> = emptyList()) {
        val outgoing = player
        val incoming = standbyPlayer
        if (!outgoing.isPlaying && outgoing.playbackState != Player.STATE_READY) { playSongDirect(nextSong, customQueue); return }
        val currentList = if (customQueue.isNotEmpty()) customQueue else _queue.value.ifEmpty { listOf(nextSong) }
        _queue.value = currentList
        crossfadeJob?.cancel(); _isCrossfading = true; crossfadeActiveSongId = nextSong.id; requestSystemAudioFocus()
        val fadeMs = crossfadeDurationMsNow().toLong()
        val master = getTargetMasterVolume()
        try {
            incoming.stop(); incoming.clearMediaItems(); incoming.setMediaItem(buildMediaItem(nextSong))
            incoming.volume = 0f; incoming.prepare(); incoming.playWhenReady = true; incoming.play()
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Incoming prepare failed", e); _isCrossfading = false; playSongDirect(nextSong, currentList); return
        }
        _currentSong.value = nextSong; _duration.value = nextSong.duration
        currentSongPlayedMs = 0L; hasCountedPlayForCurrentSong = false; lastTickTimestamp = SystemClock.elapsedRealtime()
        saveCurrentState(nextSong.id, 0L); startPlaybackService(); com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
        crossfadeJob = mainScope.launch(Dispatchers.Main) {
            val started = SystemClock.elapsedRealtime()
            try {
                while (isActive) {
                    val t = ((SystemClock.elapsedRealtime() - started).toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
                    try {
                        outgoing.volume = (master * cos((Math.PI / 2.0) * t).toFloat()).coerceIn(0f, 1.0f)
                        incoming.volume = (master * sin((Math.PI / 2.0) * t).toFloat()).coerceIn(0f, 1.0f)
                    } catch (_: Exception) {}
                    if (t >= 1f) break
                    delay(16L)
                }
            } finally {
                try { outgoing.pause(); outgoing.stop(); outgoing.clearMediaItems(); outgoing.volume = master } catch (_: Exception) {}
                try { incoming.volume = master } catch (_: Exception) {}
                activeIsA = !activeIsA; fadeVolumeMultiplier = 1.0f; _isCrossfading = false; crossfadeActiveSongId = null
                onActivePlayerChanged?.invoke(player)
                val sid = player.audioSessionId
                if (sid > 0) { _audioSessionId.value = sid; initAudioEffects(sid) }
                _isPlaying.value = player.isPlaying; updatePlayerVolume()
            }
        }
    }

    fun playSongDirect(song: Song, customQueue: List<Song> = emptyList()) {
        cancelCrossfadeHard()
        val currentList = if (customQueue.isNotEmpty()) customQueue else listOf(song)
        _queue.value = currentList; _currentSong.value = song; _duration.value = song.duration
        currentSongPlayedMs = 0L; hasCountedPlayForCurrentSong = false; lastTickTimestamp = SystemClock.elapsedRealtime(); autoCrossfadeArmedForSongId = null
        val mediaItems = currentList.map { buildMediaItem(it) }
        val index = currentList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        fadeVolumeMultiplier = 1.0f; requestSystemAudioFocus(); updatePlayerVolume()
        try {
            player.stop(); player.clearMediaItems(); player.setMediaItems(mediaItems, index, 0L)
            player.prepare(); player.playWhenReady = true; player.play()
        } catch (e: Exception) { Log.e("PlaybackManager", "playSongDirect: ${e.message}", e) }
        val sid = player.audioSessionId
        if (sid > 0) { _audioSessionId.value = sid; try { initAudioEffects(sid) } catch (_: Exception) {} }
        saveCurrentState(song.id, 0L); startPlaybackService(); com.example.widget.SoundboxAppWidget.updateAllWidgets(context)
    }

    fun play() {
        mainScope.launch(Dispatchers.Main) {
            requestSystemAudioFocus(); updatePlayerVolume()
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.playWhenReady = true; player.play(); startPlaybackService()
        }
    }

    fun pause() { mainScope.launch(Dispatchers.Main) { player.pause(); abandonSystemAudioFocus() } }
    fun playSong(song: Song, customQueue: List<Song> = emptyList()) = playSongDirect(song, customQueue)

    fun playNext(song: Song) {
        mainScope.launch(Dispatchers.Main) {
            val currentQueue = _queue.value.toMutableList()
            val currentIndex = player.currentMediaItemIndex
            val mediaItem = buildMediaItem(song)
            if (currentIndex < currentQueue.size) { currentQueue.add(currentIndex + 1, song); player.addMediaItem(currentIndex + 1, mediaItem) }
            else { currentQueue.add(song); player.addMediaItem(mediaItem) }
            _queue.value = currentQueue
            saveCurrentState(_currentSong.value?.id ?: song.id, player.currentPosition.coerceAtLeast(0L))
        }
    }

    fun addToQueue(song: Song) {
        mainScope.launch(Dispatchers.Main) {
            val currentQueue = _queue.value.toMutableList()
            currentQueue.add(song); player.addMediaItem(buildMediaItem(song)); _queue.value = currentQueue
            saveCurrentState(_currentSong.value?.id ?: song.id, player.currentPosition.coerceAtLeast(0L))
        }
    }

    fun removeFromQueue(index: Int) {
        mainScope.launch(Dispatchers.Main) {
            if (index in 0 until player.mediaItemCount) {
                player.removeMediaItem(index)
                val updatedQueue = _queue.value.toMutableList()
                if (index < updatedQueue.size) {
                    updatedQueue.removeAt(index); _queue.value = updatedQueue
                    saveCurrentState(_currentSong.value?.id ?: "", player.currentPosition.coerceAtLeast(0L))
                }
            }
        }
    }

    fun clearQueue() {
        mainScope.launch(Dispatchers.Main) {
            player.clearMediaItems(); _queue.value = emptyList(); _currentSong.value = null; saveCurrentState("", 0L)
        }
    }

    fun playPause() { mainScope.launch(Dispatchers.Main) { if (player.isPlaying) pause() else play() } }

    fun resumeOnHeadsetConnected() {
        mainScope.launch(Dispatchers.Main) {
            if (_isPlaying.value || player.isPlaying) return@launch
            if (player.mediaItemCount > 0) { play(); return@launch }
            val targetSong = _currentSong.value ?: run {
                val lastSongId = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE).getString("last_song_id", null)
                if (lastSongId != null) repository.getSongById(lastSongId) else null
            } ?: repository.allSongs.firstOrNull()?.firstOrNull()
            if (targetSong != null) playSong(targetSong)
        }
    }

    fun pauseOnHeadsetDisconnected() { mainScope.launch(Dispatchers.Main) { if (player.isPlaying || _isPlaying.value) pause() } }

    fun skipNext() {
        mainScope.launch(Dispatchers.Main) {
            val nextSong = resolveNextSong()
            if (nextSong != null) {
                if (isCrossfadeEnabledNow() && player.isPlaying) performPowerampCrossfade(nextSong, _queue.value)
                else playSongDirect(nextSong, _queue.value)
            } else if (player.hasNextMediaItem()) player.seekToNext()
        }
    }

    fun skipPrevious() {
        mainScope.launch(Dispatchers.Main) {
            val currentQueue = _queue.value
            val currentIndex = player.currentMediaItemIndex
            if (player.currentPosition > 3000L) { player.seekTo(0L); return@launch }
            val prevSong = if (currentIndex - 1 >= 0 && currentIndex - 1 < currentQueue.size) currentQueue[currentIndex - 1]
            else if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) currentQueue.last() else null
            if (prevSong != null) playSongDirect(prevSong, currentQueue)
            else if (player.hasPreviousMediaItem()) player.seekToPrevious()
            else player.seekTo(0L)
        }
    }

    fun seekTo(position: Long) { mainScope.launch(Dispatchers.Main) { player.seekTo(position); _currentPosition.value = position } }
    fun seekBackward(ms: Long = 10000L) { mainScope.launch(Dispatchers.Main) { val p = (player.currentPosition - ms).coerceAtLeast(0L); player.seekTo(p); _currentPosition.value = p } }
    fun seekForward(ms: Long = 10000L) { mainScope.launch(Dispatchers.Main) { val d = if (player.duration > 0) player.duration else Long.MAX_VALUE; val p = (player.currentPosition + ms).coerceAtMost(d); player.seekTo(p); _currentPosition.value = p } }
    fun setShuffleMode(enabled: Boolean) { mainScope.launch(Dispatchers.Main) { _shuffleMode.value = enabled; player.shuffleModeEnabled = enabled } }
    fun setRepeatMode(mode: Int) { mainScope.launch(Dispatchers.Main) { _repeatMode.value = mode; player.repeatMode = mode } }
    fun setPlaybackSpeed(speed: Float) { mainScope.launch(Dispatchers.Main) { _playbackSpeed.value = speed; player.playbackParameters = PlaybackParameters(speed, _playbackPitch.value) } }
    fun setPlaybackPitch(pitch: Float) { mainScope.launch(Dispatchers.Main) { _playbackPitch.value = pitch; player.playbackParameters = PlaybackParameters(_playbackSpeed.value, pitch) } }

    fun setPlaybackRate(speed: Float, pitch: Float) {
        mainScope.launch(Dispatchers.Main) {
            player.playbackParameters = PlaybackParameters(speed, pitch)
            _playbackSpeed.value = speed
            _playbackPitch.value = pitch
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) { _equalizerEnabled.value = enabled; settingsManager.setEqualizerEnabled(enabled); try { equalizer?.enabled = enabled } catch (_: Exception) {}; applyHardwareEqualizerBands() }
    fun setEqBandLevels(levels: List<Float>) { _eqBandLevels.value = levels; settingsManager.setEqualizerBandLevels(levels); applyHardwareEqualizerBands() }
    fun setPreampGain(gain: Float) { _preampGain.value = gain; settingsManager.setPreampGain(gain); applyHardwareEqualizerBands() }
    fun setBassBoostStrength(strength: Int) { setBassBoost(strength) }
    fun setTrebleGain(gain: Float) { _trebleGain.value = gain; settingsManager.setTrebleGain(gain); applyHardwareEqualizerBands() }
    fun setVirtualizerStrength(strength: Int) { _virtualizerStrength.value = strength; settingsManager.setVirtualizerStrength(strength); try { virtualizer?.setStrength(strength.toShort().coerceIn(0, 1000)); virtualizer?.enabled = strength > 0 } catch (_: Exception) {}; dspAudioProcessor.virtualizerStrength = strength }
    fun setAudioBalance(balance: Float) { _audioBalance.value = balance; settingsManager.setAudioBalance(balance); dspAudioProcessor.balance = balance }
    fun setReverbPreset(preset: Int) { _reverbPreset.value = preset; settingsManager.setReverbPreset(preset); try { presetReverb?.preset = preset.toShort(); presetReverb?.enabled = preset > 0 } catch (_: Exception) {} }
    fun setEqualizerPresetName(name: String) { _currentPresetName.value = name; settingsManager.setEqualizerPresetName(name) }

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

    fun setSleepTimer(millis: Long) {
        sleepTimer?.cancel(); _sleepTimerMillis.value = millis
        if (millis <= 0L) return
        sleepTimer = Timer()
        val start = SystemClock.elapsedRealtime()
        sleepTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val left = (millis - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0L)
                _sleepTimerMillis.value = left
                if (left <= 0L) { mainScope.launch(Dispatchers.Main) { pause() }; cancel() }
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

    private fun saveCurrentState(songId: String, position: Long) {
        try { context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE).edit().putString("last_song_id", songId).putLong("last_position", position).apply() } catch (_: Exception) {}
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
            _duration.value = updatedSong.duration; _currentPosition.value = 0L
            val isCurrentlyPlaying = _isPlaying.value
            val currentList = _queue.value
            val mediaItems = currentList.map { buildMediaItem(it) }
            val index = currentList.indexOfFirst { it.id == updatedSong.id }.coerceAtLeast(0)
            mainScope.launch(Dispatchers.Main) {
                player.setMediaItems(mediaItems, index, 0L); player.prepare()
                if (isCurrentlyPlaying) player.play()
            }
        }
    }

    private fun cancelCrossfadeHard() {
        crossfadeJob?.cancel(); crossfadeJob = null; _isCrossfading = false; crossfadeActiveSongId = null
        try { playerA.volume = getTargetMasterVolume() } catch (_: Exception) {}
        try { playerB.volume = getTargetMasterVolume() } catch (_: Exception) {}
        try {
            val idle = standbyPlayer
            if (idle.playbackState != Player.STATE_IDLE) { idle.pause(); idle.stop(); idle.clearMediaItems() }
        } catch (_: Exception) {}
    }

    fun release() {
        cancelCrossfadeHard(); abandonSystemAudioFocus(); handler.removeCallbacks(positionTrackerRunnable); sleepTimer?.cancel()
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
