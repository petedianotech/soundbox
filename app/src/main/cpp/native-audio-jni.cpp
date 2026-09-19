#include <jni.h>
#include <android/log.h>
#include "AudioEngine.h"
#include "DspProcessor.h"
#include "Crossfader.h"

#define TAG "SoundboxJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_player_NativeAudioEngine_nativeInit(
    JNIEnv* env,
    jobject thiz,
    jint sampleRate,
    jint channelCount
) {
    bool result = soundbox::AudioEngine::getInstance().init(sampleRate, channelCount);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_player_NativeAudioEngine_nativeProcessPcm16Direct(
    JNIEnv* env,
    jobject thiz,
    jobject inDirectBuffer,
    jobject outDirectBuffer,
    jint frameCount,
    jfloat balance,
    jint virtualizerStrength,
    jfloat masterVolume
) {
    if (!inDirectBuffer || !outDirectBuffer || frameCount <= 0) {
        return;
    }

    auto* src = static_cast<const int16_t*>(env->GetDirectBufferAddress(inDirectBuffer));
    auto* dst = static_cast<int16_t*>(env->GetDirectBufferAddress(outDirectBuffer));

    if (!src || !dst) {
        return;
    }

    soundbox::AudioEngine::getInstance().getDspProcessor().processPcm16(
        src,
        dst,
        frameCount,
        balance,
        virtualizerStrength,
        masterVolume
    );
}

JNIEXPORT void JNICALL
Java_com_example_player_NativeAudioEngine_nativeMixCrossfadePcm16Direct(
    JNIEnv* env,
    jobject thiz,
    jobject bufferA,
    jobject bufferB,
    jobject outBuffer,
    jint frameCount,
    jint channelCount,
    jfloat progress,
    jint curveType
) {
    if (!outBuffer || frameCount <= 0 || channelCount <= 0) {
        return;
    }

    const int16_t* srcA = bufferA ? static_cast<const int16_t*>(env->GetDirectBufferAddress(bufferA)) : nullptr;
    const int16_t* srcB = bufferB ? static_cast<const int16_t*>(env->GetDirectBufferAddress(bufferB)) : nullptr;
    auto* dst = static_cast<int16_t*>(env->GetDirectBufferAddress(outBuffer));

    if (!dst) {
        return;
    }

    auto curve = static_cast<soundbox::CrossfadeCurve>(curveType);

    soundbox::Crossfader::mix(
        srcA,
        srcB,
        dst,
        frameCount,
        channelCount,
        progress,
        curve
    );
}

JNIEXPORT void JNICALL
Java_com_example_player_NativeAudioEngine_nativeApplyEqualizer(
    JNIEnv* env,
    jobject thiz,
    jobject directBuffer,
    jint frameCount,
    jfloatArray bandGains
) {
    if (!directBuffer || frameCount <= 0 || !bandGains) {
        return;
    }

    auto* buffer = static_cast<int16_t*>(env->GetDirectBufferAddress(directBuffer));
    if (!buffer) {
        return;
    }

    jsize bandCount = env->GetArrayLength(bandGains);
    if (bandCount <= 0) {
        return;
    }

    jfloat* gains = env->GetFloatArrayElements(bandGains, nullptr);
    if (gains) {
        soundbox::AudioEngine::getInstance().getDspProcessor().applyEqualizer(
            buffer,
            frameCount,
            gains,
            bandCount
        );
        env->ReleaseFloatArrayElements(bandGains, gains, JNI_ABORT);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_player_NativeAudioEngine_nativeIsNeonSupported(
    JNIEnv* env,
    jobject thiz
) {
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT void JNICALL
Java_com_example_player_NativeAudioEngine_nativeRelease(
    JNIEnv* env,
    jobject thiz
) {
    soundbox::AudioEngine::getInstance().release();
}

} // extern "C"
