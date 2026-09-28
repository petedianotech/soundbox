package com.example.player

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Poweramp / Musicolet style overlap:
 * outgoing volume uses cos(pi/2 * t), incoming uses sin(pi/2 * t).
 * Both players play at once for [durationMs], then outgoing is stopped.
 */
class CrossfadeController(
    private val scope: CoroutineScope,
    private val buildMediaItem: (Song) -> MediaItem
) {
    @Volatile var isCrossfading: Boolean = false
        private set

    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
        isCrossfading = false
    }

    fun start(
        outgoing: ExoPlayer,
        incoming: ExoPlayer,
        nextSong: Song,
        durationMs: Long,
        masterVolume: Float,
        onFinished: () -> Unit
    ) {
        cancel()
        isCrossfading = true
        val fadeMs = durationMs.coerceIn(1000L, 12000L)
        val master = masterVolume.coerceIn(0.05f, 1.8f)

        try {
            incoming.stop()
            incoming.clearMediaItems()
            incoming.setMediaItem(buildMediaItem(nextSong))
            incoming.volume = 0f
            incoming.prepare()
            incoming.playWhenReady = true
            incoming.play()
        } catch (e: Exception) {
            Log.e("CrossfadeController", "Incoming prepare failed", e)
            isCrossfading = false
            onFinished()
            return
        }

        job = scope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                while (isActive) {
                    val t = ((SystemClock.elapsedRealtime() - started).toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
                    try {
                        outgoing.volume = (master * cos((Math.PI / 2.0) * t)).toFloat().coerceIn(0f, 1.8f)
                        incoming.volume = (master * sin((Math.PI / 2.0) * t)).toFloat().coerceIn(0f, 1.8f)
                    } catch (_: Exception) {}
                    if (t >= 1f) break
                    delay(16L)
                }
            } finally {
                try {
                    outgoing.pause()
                    outgoing.stop()
                    outgoing.clearMediaItems()
                    outgoing.volume = master
                } catch (_: Exception) {}
                try { incoming.volume = master } catch (_: Exception) {}
                isCrossfading = false
                onFinished()
            }
        }
    }
}
