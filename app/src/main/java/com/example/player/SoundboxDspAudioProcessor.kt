package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, low-latency DSP AudioProcessor running in the ExoPlayer audio pipeline.
 * Delivers:
 * 1. True Left/Right Stereo Panning (Balance)
 * 2. Studio Mid/Side (M/S) Acoustic Stereo Widening / Expansion (Virtualizer enhancer)
 */
class SoundboxDspAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var balance: Float = 0f // -1f (Full Left) to +1f (Full Right)

    @Volatile
    var virtualizerStrength: Int = 0 // 0 to 1000

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val bal = balance
        val virt = virtualizerStrength

        // If balance is centered and virtualizer is 0, pass-through directly with zero processing overhead
        if (bal == 0f && virt == 0) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)
        val channelCount = inputAudioFormat.channelCount

        if (channelCount == 2) {
            // Compute stereo balance panning gains
            val leftGain = if (bal <= 0f) 1.0f else (1.0f - bal).coerceIn(0f, 1f)
            val rightGain = if (bal >= 0f) 1.0f else (1.0f + bal).coerceIn(0f, 1f)

            // Compute Mid/Side stereo widening expansion: virt in 0..1000 -> widen in 0.0..0.85
            val widen = (virt / 1000f) * 0.85f

            while (inputBuffer.remaining() >= 4) {
                var l = inputBuffer.short.toFloat()
                var r = inputBuffer.short.toFloat()

                // Mid/Side spatial widening
                if (widen > 0.001f) {
                    val mid = (l + r) * 0.5f
                    val side = (l - r) * 0.5f * (1.0f + widen)
                    l = mid + side
                    r = mid - side
                }

                // Balance panning
                l *= leftGain
                r *= rightGain

                val outL = l.toInt().coerceIn(-32768, 32767).toShort()
                val outR = r.toInt().coerceIn(-32768, 32767).toShort()

                buffer.putShort(outL)
                buffer.putShort(outR)
            }
        } else {
            // Mono or other channel configurations: pass-through safely
            buffer.put(inputBuffer)
        }
        buffer.flip()
    }
}
