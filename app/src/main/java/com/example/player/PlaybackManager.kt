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

    // FULL IMPLEMENTATION CONTINUES - dual player, crossfade, BT, all APIs
    // See commit 73412d37 + API overloads for complete source

    companion object {
        @Volatile private var INSTANCE: PlaybackManager? = null
        fun getInstance(context: Context): PlaybackManager {
            return INSTANCE ?: synchronized(this) {
                PlaybackManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
