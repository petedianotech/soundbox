package com.example.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Temporary restore stub so the project compiles.
 * Full PlaybackManager with crossfade fixes will replace this.
 */
class PlaybackManager private constructor(private val context: Context) {

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

    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _sleepTimerMillis = MutableStateFlow(0L)
    val sleepTimerMillis: StateFlow<Long> = _sleepTimerMillis.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _eqBandLevels = MutableStateFlow(List(10) { 0f })
    val eqBandLevels: StateFlow<List<Float>> = _eqBandLevels.asStateFlow()

    private val _preampGain = MutableStateFlow(0f)
    val preampGain: StateFlow<Float> = _preampGain.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _trebleGain = MutableStateFlow(0f)
    val trebleGain: StateFlow<Float> = _trebleGain.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    private val _audioBalance = MutableStateFlow(0f)
    val audioBalance: StateFlow<Float> = _audioBalance.asStateFlow()

    private val _reverbPreset = MutableStateFlow(0)
    val reverbPreset: StateFlow<Int> = _reverbPreset.asStateFlow()

    private val _currentPresetName = MutableStateFlow("Flat")
    val currentPresetName: StateFlow<String> = _currentPresetName.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val _equalizerHardwareBands = MutableStateFlow(0)
    val equalizerHardwareBands: StateFlow<Int> = _equalizerHardwareBands.asStateFlow()

    private val _equalizerStatus = MutableStateFlow("Standby")
    val equalizerStatus: StateFlow<String> = _equalizerStatus.asStateFlow()

    // Minimal ExoPlayer so other code that touches player doesn't NPE hard
    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build()
    }

    fun playPause() {}
    fun skipNext() {}
    fun skipPrevious() {}
    fun playSong(song: Song, customQueue: List<Song> = emptyList()) {
        _currentSong.value = song
        _isPlaying.value = true
    }
    fun playSongDirect(song: Song, customQueue: List<Song> = emptyList()) = playSong(song, customQueue)
    fun performPowerampCrossfade(nextSong: Song, customQueue: List<Song> = emptyList()) = playSong(nextSong, customQueue)
    fun clearQueue() { _queue.value = emptyList(); _currentSong.value = null; _isPlaying.value = false }
    fun playNext(song: Song) {}
    fun addToQueue(song: Song) {}
    fun removeFromQueue(index: Int) {}
    fun setShuffleMode(enabled: Boolean) { _shuffleMode.value = enabled }
    fun setRepeatMode(mode: Int) { _repeatMode.value = mode }
    fun seekTo(position: Long) {}
    fun setPlaybackSpeed(speed: Float) { _playbackSpeed.value = speed }
    fun setPlaybackPitch(pitch: Float) { _playbackPitch.value = pitch }
    fun toggleEqualizer(enabled: Boolean) { _equalizerEnabled.value = enabled }
    fun setEqBandLevel(bandIndex: Int, levelDb: Float) {}
    fun setPreampGain(gainDb: Float) { _preampGain.value = gainDb }
    fun setTrebleGain(gainDb: Float) { _trebleGain.value = gainDb }
    fun setBassBoost(strength: Int) { _bassBoostStrength.value = strength }
    fun setVirtualizerStrength(strength: Int) { _virtualizerStrength.value = strength }
    fun setAudioBalance(balance: Float) { _audioBalance.value = balance }
    fun setReverbPreset(preset: Int) { _reverbPreset.value = preset }
    fun setEqualizerPreset(name: String, levels: List<Float>) {}
    fun startSleepTimer(millis: Long) { _sleepTimerMillis.value = millis }
    fun cancelSleepTimer() { _sleepTimerMillis.value = 0L }
    fun refreshCurrentSongMetadata(song: Song) { _currentSong.value = song }

    companion object {
        @Volatile private var instance: PlaybackManager? = null
        fun getInstance(context: Context): PlaybackManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
