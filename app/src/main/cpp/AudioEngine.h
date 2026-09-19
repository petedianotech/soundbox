#ifndef SOUNDBOX_AUDIO_ENGINE_H
#define SOUNDBOX_AUDIO_ENGINE_H

#include <memory>
#include <atomic>
#include <mutex>
#include "DspProcessor.h"
#include "Crossfader.h"

namespace soundbox {

class AudioEngine {
public:
    static AudioEngine& getInstance();

    bool init(int32_t sampleRate, int32_t channelCount);
    void release();

    bool isInitialized() const { return mIsInitialized.load(); }

    DspProcessor& getDspProcessor() { return mDspProcessor; }

    int32_t getSampleRate() const { return mSampleRate; }
    int32_t getChannelCount() const { return mChannelCount; }

private:
    AudioEngine();
    ~AudioEngine();

    AudioEngine(const AudioEngine&) = delete;
    AudioEngine& operator=(const AudioEngine&) = delete;

    std::atomic<bool> mIsInitialized{false};
    int32_t mSampleRate = 44100;
    int32_t mChannelCount = 2;

    DspProcessor mDspProcessor;
    std::mutex mEngineMutex;
};

} // namespace soundbox

#endif // SOUNDBOX_AUDIO_ENGINE_H
