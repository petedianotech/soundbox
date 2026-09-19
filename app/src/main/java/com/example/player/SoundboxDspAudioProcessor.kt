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
        val channelCount = inputAudioFormat.channelCount
        val bytesPerFrame = channelCount * 2
        val frameCount = remaining / bytesPerFrame

        if (frameCount <= 0) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        NativeAudioEngine.processPcm16Direct(
            inputBuffer = inputBuffer,
            outputBuffer = buffer,
            frameCount = frameCount,
            channelCount = channelCount,
            balance = bal,
            virtualizerStrength = virt,
            masterVolume = 1.0f
        )
    }
}
