#include "AudioEngine.h"
#include <android/log.h>

#define TAG "SoundboxNativeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace soundbox {

AudioEngine& AudioEngine::getInstance() {
    static AudioEngine instance;
    return instance;
}

AudioEngine::AudioEngine() {
}

AudioEngine::~AudioEngine() {
    release();
}

bool AudioEngine::init(int32_t sampleRate, int32_t channelCount) {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
    mChannelCount = (channelCount > 0) ? channelCount : 2;

    mDspProcessor.setSampleRate(mSampleRate);
    mDspProcessor.setChannelCount(mChannelCount);

    mIsInitialized.store(true);
    LOGI("Soundbox Native Audio Engine initialized: %d Hz, %d channels", mSampleRate, mChannelCount);
    return true;
}

void AudioEngine::release() {
    std::lock_guard<std::mutex> lock(mEngineMutex);
    mIsInitialized.store(false);
    LOGI("Soundbox Native Audio Engine released");
}

} // namespace soundbox
