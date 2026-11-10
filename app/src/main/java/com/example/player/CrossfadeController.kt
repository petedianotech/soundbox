package com.example.player

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Poweramp-style equal-power dual-player crossfade controller.
 * Manages smooth volume curves and seamless track transitions between dual ExoPlayer instances.
 */
class CrossfadeController(
    private val scope: CoroutineScope,
    private val mediaItemBuilder: (Song) -> MediaItem
) {
    companion object {
        private const val TAG = "CrossfadeController"
    }

    @Volatile
    private var _isCrossfading: Boolean = false
    val isCrossfading: Boolean get() = _isCrossfading

    @Volatile
    private var _activeSongId: String? = null
    val activeSongId: String? get() = _activeSongId

    private var crossfadeJob: Job? = null
    private var currentOutgoing: ExoPlayer? = null
    private var currentIncoming: ExoPlayer? = null

    fun start(
        outgoing: ExoPlayer,
        incoming: ExoPlayer,
        nextSong: Song,
        durationMs: Long,
        masterVolume: Float,
        onComplete: () -> Unit
    ) {
        cancel()
        _isCrossfading = true
        _activeSongId = nextSong.id
        currentOutgoing = outgoing
        currentIncoming = incoming

        val fadeDuration = durationMs.coerceIn(1000L, 12000L)

        try {
            incoming.stop()
            incoming.clearMediaItems()
            incoming.setMediaItem(mediaItemBuilder(nextSong))
            incoming.volume = 0f
            incoming.prepare()
            incoming.playWhenReady = true
            incoming.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start incoming player for crossfade", e)
            _isCrossfading = false
            _activeSongId = null
            onComplete()
            return
        }

        crossfadeJob = scope.launch(Dispatchers.Main) {
            val startTime = SystemClock.elapsedRealtime()
            try {
                while (isActive) {
                    val elapsed = SystemClock.elapsedRealtime() - startTime
                    val progress = (elapsed.toFloat() / fadeDuration.toFloat()).coerceIn(0f, 1f)
                    val phase = (Math.PI / 2.0 * progress).toFloat()
                    val outVol = (masterVolume * cos(phase)).coerceIn(0f, 1f)
                    val inVol = (masterVolume * sin(phase)).coerceIn(0f, 1f)

                    try {
                        outgoing.volume = outVol
                        incoming.volume = inVol
                    } catch (_: Exception) {}

                    if (progress >= 1f) break
                    delay(16L)
                }
            } finally {
                try {
                    outgoing.pause()
                    outgoing.stop()
                    outgoing.clearMediaItems()
                } catch (_: Exception) {}
                try {
                    incoming.volume = masterVolume
                } catch (_: Exception) {}

                _isCrossfading = false
                _activeSongId = null
                currentOutgoing = null
                currentIncoming = null
                onComplete()
            }
        }
    }

    fun cancel() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        if (_isCrossfading) {
            _isCrossfading = false
            _activeSongId = null
            try {
                currentIncoming?.pause()
                currentIncoming?.stop()
                currentIncoming?.clearMediaItems()
            } catch (_: Exception) {}
            currentOutgoing = null
            currentIncoming = null
        }
    }
}
