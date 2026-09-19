#include "DspProcessor.h"
#include <cstring>

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define USE_NEON 1
#else
#define USE_NEON 0
#endif

namespace soundbox {

DspProcessor::DspProcessor() {
}

void DspProcessor::setSampleRate(int32_t sampleRate) {
    mSampleRate = sampleRate;
}

void DspProcessor::setChannelCount(int32_t channelCount) {
    mChannelCount = channelCount;
}

void DspProcessor::processPcm16(
    const int16_t* src,
    int16_t* dst,
    int32_t frameCount,
    float balance,
    int32_t virtualizerStrength,
    float masterVolume
) {
    if (!src || !dst || frameCount <= 0) {
        return;
    }

    // Direct fast-path copy if no processing is needed and masterVolume is 1.0f
    if (balance == 0.0f && virtualizerStrength == 0 && std::abs(masterVolume - 1.0f) < 0.001f) {
        if (src != dst) {
            std::memcpy(dst, src, frameCount * mChannelCount * sizeof(int16_t));
        }
        return;
    }

    if (mChannelCount != 2) {
        // Mono or non-stereo: apply volume scaling only
        for (int32_t i = 0; i < frameCount * mChannelCount; ++i) {
            float sample = static_cast<float>(src[i]) * masterVolume;
            dst[i] = clamp16(static_cast<int32_t>(sample));
        }
        return;
    }

    // Stereo Processing
    float leftGain = (balance <= 0.0f) ? 1.0f : std::max(0.0f, 1.0f - balance);
    float rightGain = (balance >= 0.0f) ? 1.0f : std::max(0.0f, 1.0f + balance);
    leftGain *= masterVolume;
    rightGain *= masterVolume;

    float widen = (static_cast<float>(virtualizerStrength) / 1000.0f) * 0.85f;
    bool applyWiden = (widen > 0.001f);
    float sideMultiplier = 1.0f + widen;

    int32_t i = 0;

    // Scalar high-efficiency processing
    for (; i < frameCount; ++i) {
        float l = static_cast<float>(src[i * 2]);
        float r = static_cast<float>(src[i * 2 + 1]);

        if (applyWiden) {
            float mid = (l + r) * 0.5f;
            float side = (l - r) * 0.5f * sideMultiplier;
            l = mid + side;
            r = mid - side;
        }

        l *= leftGain;
        r *= rightGain;

        dst[i * 2] = clamp16(static_cast<int32_t>(l));
        dst[i * 2 + 1] = clamp16(static_cast<int32_t>(r));
    }
}

void DspProcessor::applyEqualizer(
    int16_t* buffer,
    int32_t frameCount,
    const float* bandGains,
    int32_t bandCount
) {
    if (!buffer || frameCount <= 0 || !bandGains || bandCount <= 0) {
        return;
    }

    // Calculate average gain across bands for smooth scaling
    float avgGain = 0.0f;
    for (int32_t b = 0; b < bandCount; ++b) {
        avgGain += bandGains[b];
    }
    avgGain /= static_cast<float>(bandCount);

    if (std::abs(avgGain - 1.0f) < 0.01f) {
        return;
    }

    int32_t totalSamples = frameCount * mChannelCount;
    for (int32_t i = 0; i < totalSamples; ++i) {
        float sample = static_cast<float>(buffer[i]) * avgGain;
        buffer[i] = clamp16(static_cast<int32_t>(sample));
    }
}

} // namespace soundbox
