package com.example.player

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
 * Poweramp / Musicolet style dual-player overlap.
 * Equal-power curve: outgoing = master * cos(π/2 · t), incoming = master * sin(π/2 · t).
 * Both players must use handleAudioFocus=false; focus is owned by PlaybackManager.
 *
 * Robust ready-wait + hard-cut fallback so Bluetooth / slow storage never produces
 * a silent or 0.5s-then-stop crossfade.
 */
class CrossfadeController(
    private val scope: CoroutineScope,
    private val buildMediaItem: (Song) -> MediaItem
) {
    @Volatile var isCrossfading: Boolean = false
        private set

    @Volatile var activeSongId: String? = null
        private set

    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
        isCrossfading = false
        activeSongId = null
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
        activeSongId = nextSong.id
        val fadeMs = durationMs.coerceIn(1000L, 12000L)
        val master = masterVolume.coerceIn(0.05f, 1.0f)

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
            activeSongId = null
            onFinished()
            return
        }

        job = scope.launch {
            val readyDeadline = SystemClock.elapsedRealtime() + 3500L
            var becameReady = false
            while (isActive && SystemClock.elapsedRealtime() < readyDeadline) {
                val st = try { incoming.playbackState } catch (_: Exception) { Player.STATE_IDLE }
                if (st == Player.STATE_READY) {
                    becameReady = true
                    break
                }
                if (st == Player.STATE_BUFFERING || st == Player.STATE_IDLE) {
                    try {
                        incoming.playWhenReady = true
                        if (!incoming.isPlaying) incoming.play()
                    } catch (_: Exception) {}
                }
                delay(32L)
            }

            if (!becameReady) {
                Log.w("CrossfadeController", "Incoming never READY — hard cut instead of silent fade")
                hardSwitch(outgoing, incoming, master, onFinished)
                return@launch
            }

            try {
                incoming.volume = 0f
                incoming.playWhenReady = true
                if (!incoming.isPlaying) incoming.play()
            } catch (_: Exception) {}

            val started = SystemClock.elapsedRealtime()
            try {
                while (isActive) {
                    val t = ((SystemClock.elapsedRealtime() - started).toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
                    try {
                        outgoing.volume = (master * cos((Math.PI / 2.0) * t)).toFloat().coerceIn(0f, 1f)
                        incoming.volume = (master * sin((Math.PI / 2.0) * t)).toFloat().coerceIn(0f, 1f)
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
                try {
                    incoming.volume = master
                    incoming.playWhenReady = true
                    if (!incoming.isPlaying) incoming.play()
                } catch (_: Exception) {}
                isCrossfading = false
                activeSongId = null
                onFinished()
            }
        }
    }

    private fun hardSwitch(
        outgoing: ExoPlayer,
        incoming: ExoPlayer,
        master: Float,
        onFinished: () -> Unit
    ) {
        try {
            outgoing.pause()
            outgoing.stop()
            outgoing.clearMediaItems()
            outgoing.volume = master
        } catch (_: Exception) {}
        try {
            incoming.volume = master
            incoming.playWhenReady = true
            incoming.play()
        } catch (_: Exception) {}
        isCrossfading = false
        activeSongId = null
        onFinished()
    }
}
