package com.example.player

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * DSP parameter container for balance and stereo expand settings.
 * Audio effects are applied directly via Android's hardware Virtualizer and ExoPlayer volume balance.
 */
class SoundboxDspAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var balance: Float = 0f // -1f (Full Left) to +1f (Full Right)

    @Volatile
    var virtualizerStrength: Int = 0 // 0 to 1000

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Return NOT_SET so ExoPlayer marks it inactive, routing through standard hardware audio path
        return AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val buffer = replaceOutputBuffer(remaining)
        buffer.put(inputBuffer)
        buffer.flip()
    }
}
