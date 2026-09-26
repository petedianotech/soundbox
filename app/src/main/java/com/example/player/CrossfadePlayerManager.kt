package com.example.player

import android.content.Context
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
import kotlinx.coroutines.*
import kotlin.math.max

/**
 * Production-grade dual-player crossfade engine using Media3 (ExoPlayer).
 * Uses two ExoPlayer instances with equal-power volume ramping on Dispatchers.Main.immediate.
 * Prevents crashes and delivers clean, gapless crossfade transitions between tracks.
 */
class CrossfadePlayerManager(
    private val context: Context,
    var crossfadeDurationMs: Long = 5000L,
    private val stepMs: Long = 50L,
    val dspAudioProcessorA: SoundboxDspAudioProcessor = SoundboxDspAudioProcessor(),
    val dspAudioProcessorB: SoundboxDspAudioProcessor = SoundboxDspAudioProcessor()
) {
    companion object {
        private const val TAG = "CrossfadePlayerManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val lowMemoryLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15_000, // minBufferMs
            30_000, // maxBufferMs
            1_000,  // bufferForPlaybackMs
            2_000   // bufferForPlaybackAfterRebufferMs
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    private fun createPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setLoadControl(lowMemoryLoadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false // Allow dual output during crossfade without AudioFocus conflict
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                volume = 1f
                playWhenReady = false
            }
    }

    var playerA: ExoPlayer = createPlayer()
        private set
    var playerB: ExoPlayer = createPlayer()
        private set

    var currentPlayer: ExoPlayer = playerA
        private set
    var nextPlayer: ExoPlayer = playerB
        private set

    // Primary player reference for external consumers
    val player: ExoPlayer
        get() = currentPlayer

    // Standby player reference
    val standbyPlayer: ExoPlayer
        get() = nextPlayer

    // Active DSP processor matching currentPlayer
    val currentDspProcessor: SoundboxDspAudioProcessor
        get() = if (currentPlayer == playerA) dspAudioProcessorA else dspAudioProcessorB

    val nextDspProcessor: SoundboxDspAudioProcessor
        get() = if (currentPlayer == playerA) dspAudioProcessorB else dspAudioProcessorA

    private var crossfadeJob: Job? = null

    @Volatile
    var isCrossfading: Boolean = false
        private set

    @Volatile
    var crossfadeTargetMediaId: String? = null
        private set

    var masterVolume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.05f, 1.0f)
            if (!isCrossfading) {
                try {
                    currentPlayer.volume = field
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating player volume: ${e.message}")
                }
            }
        }

    // Callbacks for outer manager integration
    var onPlayerSwapped: ((ExoPlayer) -> Unit)? = null
    var onPlaybackStateChanged: ((ExoPlayer, Int) -> Unit)? = null
    var onIsPlayingChanged: ((ExoPlayer, Boolean) -> Unit)? = null
    var onMediaItemTransition: ((ExoPlayer, MediaItem?, Int) -> Unit)? = null
    var onAudioSessionIdChanged: ((ExoPlayer, Int) -> Unit)? = null

    init {
        attachListeners(playerA)
        attachListeners(playerB)
    }

    private fun attachListeners(targetPlayer: ExoPlayer) {
        targetPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onIsPlayingChanged?.invoke(targetPlayer, isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                onPlaybackStateChanged?.invoke(targetPlayer, playbackState)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                onMediaItemTransition?.invoke(targetPlayer, mediaItem, reason)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error on ${if (targetPlayer == currentPlayer) "currentPlayer" else "nextPlayer"}: ${error.message}", error)
                if (targetPlayer == nextPlayer && isCrossfading) {
                    cancelCrossfadeCleanup()
                }
            }
        })

        targetPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                if (audioSessionId > 0) {
                    onAudioSessionIdChanged?.invoke(targetPlayer, audioSessionId)
                }
            }
        })
    }

    /**
     * Starts playing a new track with smooth equal-power volume crossfade.
     * If nothing is currently playing or crossfade is disabled, starts immediately.
     */
    fun playWithCrossfade(
        mediaItem: MediaItem,
        queueItems: List<MediaItem> = emptyList(),
        targetIndex: Int = 0,
        customDurationMs: Long = crossfadeDurationMs
    ) {
        if (!currentPlayer.isPlaying && currentPlayer.playbackState == Player.STATE_IDLE) {
            // First track – start normally
            playDirect(if (queueItems.isNotEmpty()) queueItems else listOf(mediaItem), targetIndex)
            return
        }

        crossfadeJob?.cancel()

        val fadeDuration = customDurationMs.coerceIn(500L, 15000L)
        val targetId = mediaItem.mediaId
        crossfadeTargetMediaId = targetId
        isCrossfading = true

        val itemsToSet = if (queueItems.isNotEmpty()) queueItems else listOf(mediaItem)
        val indexToPlay = if (targetIndex in itemsToSet.indices) targetIndex else 0

        // Prepare next player at volume 0
        try {
            nextPlayer.stop()
            nextPlayer.clearMediaItems()
            nextPlayer.setMediaItems(itemsToSet, indexToPlay, 0L)
            nextPlayer.volume = 0f
            nextPlayer.prepare()
            nextPlayer.playWhenReady = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed preparing nextPlayer for crossfade: ${e.message}", e)
            playDirect(itemsToSet, indexToPlay)
            return
        }

        crossfadeJob = scope.launch {
            try {
                // Wait briefly for nextPlayer to buffer audio before starting the fade
                var waitedMs = 0L
                while (isActive && nextPlayer.playbackState == Player.STATE_BUFFERING && waitedMs < 1500L) {
                    delay(30L)
                    waitedMs += 30L
                }

                if (!isActive) return@launch

                if (nextPlayer.playerError != null) {
                    Log.w(TAG, "Next player has error, aborting crossfade: ${nextPlayer.playerError?.message}")
                    cancelCrossfadeCleanup()
                    return@launch
                }

                nextPlayer.play()

                val totalSteps = max(1, (fadeDuration / stepMs).toInt())
                val targetMaster = masterVolume.coerceIn(0.05f, 1.0f)

                for (i in 1..totalSteps) {
                    if (!isActive) break

                    val p = (i.toFloat() / totalSteps).coerceIn(0f, 1f)
                    // Equal-Power crossfade curve: cos(p * pi/2) and sin(p * pi/2)
                    // Guarantees constant perceived acoustic energy (cos^2 + sin^2 = 1.0)
                    val outRatio = kotlin.math.cos(p * (Math.PI / 2.0)).toFloat()
                    val inRatio = kotlin.math.sin(p * (Math.PI / 2.0)).toFloat()

                    val outVol = (outRatio * targetMaster).coerceIn(0f, 1.0f)
                    val inVol = (inRatio * targetMaster).coerceIn(0f, 1.0f)

                    try {
                        currentPlayer.volume = outVol
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating currentPlayer volume: ${e.message}")
                    }
                    try {
                        nextPlayer.volume = inVol
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating nextPlayer volume: ${e.message}")
                    }

                    delay(stepMs)
                }

                // Crossfade finished cleanly: stop old player, bring new player to full master volume
                try {
                    currentPlayer.pause()
                    currentPlayer.stop()
                    currentPlayer.clearMediaItems()
                    currentPlayer.volume = 0f
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping currentPlayer after crossfade: ${e.message}")
                }

                try {
                    nextPlayer.volume = targetMaster
                } catch (e: Exception) {
                    Log.w(TAG, "Error finalizing nextPlayer volume: ${e.message}")
                }

                // Swap roles
                val temp = currentPlayer
                currentPlayer = nextPlayer
                nextPlayer = temp

                isCrossfading = false
                crossfadeTargetMediaId = null

                onPlayerSwapped?.invoke(currentPlayer)
                Log.d(TAG, "Crossfade completed successfully to track: $targetId")
            } catch (c: CancellationException) {
                Log.d(TAG, "Crossfade coroutine cancelled")
                cancelCrossfadeCleanup()
            } catch (e: Exception) {
                Log.e(TAG, "Error in crossfade transition: ${e.message}", e)
                cancelCrossfadeCleanup()
            }
        }
    }

    private fun cancelCrossfadeCleanup() {
        try {
            nextPlayer.stop()
            nextPlayer.clearMediaItems()
            nextPlayer.volume = 0f
            currentPlayer.volume = masterVolume.coerceIn(0.05f, 1.0f)
        } catch (e: Exception) {
            Log.w(TAG, "Error during crossfade cancel cleanup: ${e.message}")
        }
        isCrossfading = false
        crossfadeTargetMediaId = null
    }

    /**
     * Plays items directly on the current player without crossfade.
     */
    fun playDirect(mediaItems: List<MediaItem>, initialIndex: Int = 0) {
        crossfadeJob?.cancel()
        cancelCrossfadeCleanup()

        try {
            currentPlayer.stop()
            currentPlayer.clearMediaItems()
            val validIndex = if (initialIndex in mediaItems.indices) initialIndex else 0
            currentPlayer.setMediaItems(mediaItems, validIndex, 0L)
            currentPlayer.volume = masterVolume.coerceIn(0.05f, 1.0f)
            currentPlayer.prepare()
            currentPlayer.playWhenReady = true
            currentPlayer.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error during playDirect: ${e.message}", e)
        }
    }

    fun pause() {
        crossfadeJob?.cancel()
        cancelCrossfadeCleanup()
        try {
            currentPlayer.pause()
            nextPlayer.pause()
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing players: ${e.message}")
        }
    }

    fun resume() {
        try {
            if (currentPlayer.playbackState == Player.STATE_IDLE) {
                currentPlayer.prepare()
            }
            currentPlayer.playWhenReady = true
            currentPlayer.volume = masterVolume
            currentPlayer.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming currentPlayer: ${e.message}")
        }
    }

    fun seekTo(positionMs: Long) {
        if (isCrossfading) {
            crossfadeJob?.cancel()
            cancelCrossfadeCleanup()
        }
        try {
            currentPlayer.seekTo(positionMs)
        } catch (e: Exception) {
            Log.w(TAG, "Error seeking currentPlayer: ${e.message}")
        }
    }

    fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (isCrossfading) {
            crossfadeJob?.cancel()
            cancelCrossfadeCleanup()
        }
        try {
            currentPlayer.seekTo(mediaItemIndex, positionMs)
        } catch (e: Exception) {
            Log.w(TAG, "Error seeking currentPlayer index: ${e.message}")
        }
    }

    fun getCurrentPosition(): Long = try {
        if (isCrossfading) nextPlayer.currentPosition.coerceAtLeast(0L)
        else currentPlayer.currentPosition.coerceAtLeast(0L)
    } catch (e: Exception) {
        0L
    }

    fun getDuration(): Long = try {
        if (isCrossfading) nextPlayer.duration.coerceAtLeast(0L)
        else currentPlayer.duration.coerceAtLeast(0L)
    } catch (e: Exception) {
        0L
    }

    fun isPlaying(): Boolean = try {
        currentPlayer.isPlaying || (isCrossfading && nextPlayer.isPlaying)
    } catch (e: Exception) {
        false
    }

    fun getPlaybackState(): Int = try {
        if (isCrossfading) nextPlayer.playbackState else currentPlayer.playbackState
    } catch (e: Exception) {
        Player.STATE_IDLE
    }

    fun getAudioSessionId(): Int = try {
        currentPlayer.audioSessionId
    } catch (e: Exception) {
        0
    }

    fun setPlaybackParameters(params: PlaybackParameters) {
        try {
            currentPlayer.playbackParameters = params
            nextPlayer.playbackParameters = params
        } catch (e: Exception) {
            Log.w(TAG, "Error setting playback parameters: ${e.message}")
        }
    }

    fun setShuffleModeEnabled(enabled: Boolean) {
        try {
            currentPlayer.shuffleModeEnabled = enabled
            nextPlayer.shuffleModeEnabled = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Error setting shuffle mode: ${e.message}")
        }
    }

    fun setRepeatMode(repeatMode: Int) {
        try {
            currentPlayer.repeatMode = repeatMode
            nextPlayer.repeatMode = repeatMode
        } catch (e: Exception) {
            Log.w(TAG, "Error setting repeat mode: ${e.message}")
        }
    }

    fun addMediaItem(mediaItem: MediaItem) {
        try {
            currentPlayer.addMediaItem(mediaItem)
        } catch (e: Exception) {
            Log.w(TAG, "Error adding media item: ${e.message}")
        }
    }

    fun addMediaItem(index: Int, mediaItem: MediaItem) {
        try {
            currentPlayer.addMediaItem(index, mediaItem)
        } catch (e: Exception) {
            Log.w(TAG, "Error adding media item at index: ${e.message}")
        }
    }

    fun removeMediaItem(index: Int) {
        try {
            currentPlayer.removeMediaItem(index)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing media item: ${e.message}")
        }
    }

    fun clearMediaItems() {
        crossfadeJob?.cancel()
        cancelCrossfadeCleanup()
        try {
            currentPlayer.clearMediaItems()
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing media items: ${e.message}")
        }
    }

    fun release() {
        crossfadeJob?.cancel()
        scope.cancel()
        try {
            playerA.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing playerA: ${e.message}")
        }
        try {
            playerB.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing playerB: ${e.message}")
        }
    }
}
