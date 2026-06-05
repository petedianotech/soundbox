package com.example.player

import android.content.Context
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.Song
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask

class PlaybackManager private constructor(private val context: Context) {

    private val repository = MusicRepository.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ExoPlayer reference
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build(), 
            true // handleAudioFocus = true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    // Sound FX
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

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

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0) // 0 to 1000 millibels
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private var sleepTimer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val positionTrackerRunnable = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                _currentPosition.value = player.currentPosition
            }
            handler.postDelayed(this, 500) // Default delay for position tracking
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
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                if (mediaId != null) {
                    scope.launch {
                        val song = repository.getSongById(mediaId)
                        if (song != null) {
                            _currentSong.value = song
                            repository.incrementPlayCount(song.id)
                            saveCurrentState(song.id, player.currentPosition)
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

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                super.onAudioSessionIdChanged(audioSessionId)
                initAudioEffects(audioSessionId)
            }
        })
    }

    private fun initAudioEffects(audioSessionId: Int) {
        if (audioSessionId == 0) return
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = _equalizerEnabled.value
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _bassBoostStrength.value > 0
                if (enabled) {
                    setStrength(_bassBoostStrength.value.toShort())
                }
            }
            Log.d("PlaybackManager", "Audio effects successfully initialized on session $audioSessionId")
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Equalizer/BassBoost activation skipped: ${e.message}")
        }
    }

    fun playSong(song: Song, customQueue: List<Song> = emptyList()) {
        val currentList = if (customQueue.isNotEmpty()) customQueue else listOf(song)
        _queue.value = currentList

        val mediaItems = currentList.map { songItem ->
            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(songItem.title)
                .setArtist(songItem.artist)
                .setAlbumTitle(songItem.album)
                .setDisplayTitle(songItem.title)
                .setArtworkUri(android.net.Uri.parse(songItem.path))
                .build()

            MediaItem.Builder()
                .setMediaId(songItem.id)
                .setUri(songItem.path)
                .setMediaMetadata(metadata)
                .build()
        }

        player.setMediaItems(mediaItems)
        val index = currentList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        player.seekTo(index, 0L)
        player.prepare()
        player.play()

        _currentSong.value = song
        _duration.value = song.duration
    }

    fun playNext(song: Song) {
        val currentQueue = _queue.value.toMutableList()
        val currentIndex = player.currentMediaItemIndex
        
        // Build new MediaItem
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setDisplayTitle(song.title)
            .setArtworkUri(android.net.Uri.parse(song.path))
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.path)
            .setMediaMetadata(metadata)
            .build()

        if (currentIndex < currentQueue.size) {
            currentQueue.add(currentIndex + 1, song)
            player.addMediaItem(currentIndex + 1, mediaItem)
        } else {
            currentQueue.add(song)
            player.addMediaItem(mediaItem)
        }
        _queue.value = currentQueue
    }

    fun addToQueue(song: Song) {
        val currentQueue = _queue.value.toMutableList()
        currentQueue.add(song)
        
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setDisplayTitle(song.title)
            .setArtworkUri(android.net.Uri.parse(song.path))
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.path)
            .setMediaMetadata(metadata)
            .build()
            
        player.addMediaItem(mediaItem)
        _queue.value = currentQueue
    }

    fun removeFromQueue(index: Int) {
        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
            val updatedQueue = _queue.value.toMutableList()
            if (index < updatedQueue.size) {
                updatedQueue.removeAt(index)
                _queue.value = updatedQueue
            }
        }
    }

    fun clearQueue() {
        if (player.mediaItemCount > 0) {
            player.clearMediaItems()
            _queue.value = emptyList()
            _currentSong.value = null
        }
    }

    fun playPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
    }

    fun skipNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNext()
        }
    }

    fun skipPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
        } else {
            player.seekTo(0L)
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
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error toggling equalizer: ${e.message}")
        }
    }

    fun setBassBoost(strength: Int) { // 0 to 1000
        _bassBoostStrength.value = strength
        try {
            bassBoost?.let { boost ->
                boost.enabled = strength > 0
                if (strength > 0) {
                    boost.setStrength(strength.toShort())
                }
            }
        } catch (e: Exception) {
            Log.w("PlaybackManager", "Error setting bass boost strength: ${e.message}")
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimer?.cancel()
        if (minutes <= 0) {
            _sleepTimerMillis.value = 0L
            return
        }

        val totalMs = minutes * 60 * 1000L
        _sleepTimerMillis.value = totalMs

        sleepTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    handler.post {
                        val currentLeft = _sleepTimerMillis.value - 1000L
                        if (currentLeft <= 0) {
                            player.pause()
                            _sleepTimerMillis.value = 0L
                            cancel()
                        } else {
                            _sleepTimerMillis.value = currentLeft
                        }
                    }
                }
            }, 1000L, 1000L)
        }
    }

    fun stopSleepTimer() {
        sleepTimer?.cancel()
        _sleepTimerMillis.value = 0L
    }

    private fun saveCurrentState(songId: String, position: Long) {
        val prefs = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_song_id", songId)
            .putLong("last_position", position)
            .apply()
    }

    private fun restorePlaybackState() {
        scope.launch {
            val prefs = context.getSharedPreferences("soundbox_playback", Context.MODE_PRIVATE)
            val lastSongId = prefs.getString("last_song_id", null)
            val lastPos = prefs.getLong("last_position", 0L)

            if (lastSongId != null) {
                val song = repository.getSongById(lastSongId)
                if (song != null) {
                    _currentSong.value = song
                    _currentPosition.value = lastPos
                }
            }
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
