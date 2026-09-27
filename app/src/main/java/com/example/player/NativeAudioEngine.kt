package com.example.player

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Native C/C++ (NDK) Audio Engine JNI Bridge.
 *
 * Provides:
 * 1. SIMD / ARM NEON-accelerated 16-bit PCM buffer DSP processing (balance panning, M/S spatial widening).
 * 2. Fallback Kotlin engine for devices/architectures where native library is unavailable.
 */
object NativeAudioEngine {

    private const val TAG = "NativeAudioEngine"
    private const val LIB_NAME = "soundbox_audio"

    @Volatile
    var isNativeAvailable: Boolean = false
        private set

    @Volatile
    var isNeonSupported: Boolean = false
        private set

    init {
        try {
            System.loadLibrary(LIB_NAME)
            isNativeAvailable = true
            isNeonSupported = try {
                nativeIsNeonSupported()
            } catch (e: Throwable) {
                false
            }
            Log.i(TAG, "Native audio engine loaded successfully. ARM NEON supported: $isNeonSupported")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library '$LIB_NAME' not found or unsupported ABI. Falling back to high-performance Kotlin DSP engine: ${e.message}")
            isNativeAvailable = false
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize native audio engine: ${t.message}. Using Kotlin fallback engine.")
            isNativeAvailable = false
        }
    }

    /**
     * Initializes the native audio engine with sample rate and channel count.
     */
    fun init(sampleRate: Int, channelCount: Int): Boolean {
        if (!isNativeAvailable) return false
        return try {
            nativeInit(sampleRate, channelCount)
        } catch (t: Throwable) {
            Log.w(TAG, "nativeInit failed: ${t.message}")
            false
        }
    }

    /**
     * High-performance DSP processing for 16-bit stereo PCM buffers.
     * Uses native SIMD C++ when available, or optimized fallback Kotlin code.
     */
    fun processPcm16Direct(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        frameCount: Int,
        channelCount: Int,
        balance: Float,
        virtualizerStrength: Int,
        masterVolume: Float = 1.0f
    ) {
        if (isNativeAvailable && inputBuffer.isDirect && outputBuffer.isDirect) {
            try {
                nativeProcessPcm16Direct(
                    inputBuffer,
                    outputBuffer,
                    frameCount,
                    balance,
                    virtualizerStrength,
                    masterVolume
                )
                outputBuffer.position(0)
                outputBuffer.limit(frameCount * channelCount * 2)
                return
            } catch (t: Throwable) {
                Log.w(TAG, "nativeProcessPcm16Direct failed, falling back to Kotlin: ${t.message}")
            }
        }

        // Optimized Fallback Engine in Kotlin
        processPcm16Fallback(
            inputBuffer,
            outputBuffer,
            frameCount,
            channelCount,
            balance,
            virtualizerStrength,
            masterVolume
        )
    }

    /**
     * Releases native audio engine resources.
     */
    fun release() {
        if (!isNativeAvailable) return
        try {
            nativeRelease()
        } catch (t: Throwable) {
            Log.w(TAG, "nativeRelease error: ${t.message}")
        }
    }

    // --- Kotlin Fallback Algorithms ---

    private fun processPcm16Fallback(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        frameCount: Int,
        channelCount: Int,
        balance: Float,
        virtualizerStrength: Int,
        masterVolume: Float
    ) {
        if (frameCount <= 0) return

        if (balance == 0f && virtualizerStrength == 0 && kotlin.math.abs(masterVolume - 1.0f) < 0.001f) {
            val bytes = frameCount * channelCount * 2
            val oldPos = inputBuffer.position()
            val slice = inputBuffer.slice()
            slice.limit(bytes)
            outputBuffer.put(slice)
            inputBuffer.position(oldPos + bytes)
            outputBuffer.flip()
            return
        }

        val leftGain = ((if (balance <= 0f) 1.0f else (1.0f - balance).coerceIn(0f, 1f)) * masterVolume)
        val rightGain = ((if (balance >= 0f) 1.0f else (1.0f + balance).coerceIn(0f, 1f)) * masterVolume)
        val widen = (virtualizerStrength / 1000f) * 0.85f
        val applyWiden = widen > 0.001f
        val sideMult = 1.0f + widen

        if (channelCount == 2) {
            for (i in 0 until frameCount) {
                var l = inputBuffer.short.toFloat()
                var r = inputBuffer.short.toFloat()

                if (applyWiden) {
                    val mid = (l + r) * 0.5f
                    val side = (l - r) * 0.5f * sideMult
                    l = mid + side
                    r = mid - side
                }

                l *= leftGain
                r *= rightGain

                outputBuffer.putShort(l.toInt().coerceIn(-32768, 32767).toShort())
                outputBuffer.putShort(r.toInt().coerceIn(-32768, 32767).toShort())
            }
        } else {
            for (i in 0 until frameCount * channelCount) {
                val sample = (inputBuffer.short.toFloat() * masterVolume).toInt().coerceIn(-32768, 32767).toShort()
                outputBuffer.putShort(sample)
            }
        }
        outputBuffer.flip()
    }

    // --- Native JNI External Methods ---

    private external fun nativeInit(sampleRate: Int, channelCount: Int): Boolean
    private external fun nativeProcessPcm16Direct(
        inDirectBuffer: ByteBuffer,
        outDirectBuffer: ByteBuffer,
        frameCount: Int,
        balance: Float,
        virtualizerStrength: Int,
        masterVolume: Float
    )
    private external fun nativeApplyEqualizer(
        buffer: ByteBuffer,
        frameCount: Int,
        bandGains: FloatArray
    )
    private external fun nativeIsNeonSupported(): Boolean
    private external fun nativeRelease()
}
