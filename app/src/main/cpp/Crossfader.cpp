#include "Crossfader.h"
#include <limits>

namespace soundbox {

constexpr float PI_2 = 1.57079632679489661923f; // PI / 2

void Crossfader::calculateGains(
    float progress,
    CrossfadeCurve curve,
    float& outGainA,
    float& outGainB
) {
    float p = std::clamp(progress, 0.0f, 1.0f);

    switch (curve) {
        case CrossfadeCurve::EQUAL_POWER: {
            // Equal-power crossfade (sin/cos curve maintains perceived constant loudness)
            outGainA = std::cos(p * PI_2);
            outGainB = std::sin(p * PI_2);
            break;
        }
        case CrossfadeCurve::S_CURVE: {
            // Smoothstep S-curve: 3p^2 - 2p^3
            float smooth = p * p * (3.0f - 2.0f * p);
            outGainA = 1.0f - smooth;
            outGainB = smooth;
            break;
        }
        case CrossfadeCurve::LINEAR:
        default: {
            outGainA = 1.0f - p;
            outGainB = p;
            break;
        }
    }
}

void Crossfader::mix(
    const int16_t* srcA,
    const int16_t* srcB,
    int16_t* dst,
    int32_t frameCount,
    int32_t channelCount,
    float progress,
    CrossfadeCurve curve
) {
    if (!dst || frameCount <= 0 || channelCount <= 0 ||
        static_cast<int64_t>(frameCount) >
            (std::numeric_limits<int32_t>::max() / channelCount)) {
        return;
    }

    float gainA = 1.0f;
    float gainB = 0.0f;
    calculateGains(progress, curve, gainA, gainB);

    int32_t totalSamples = frameCount * channelCount;

    if (!srcA && !srcB) {
        for (int32_t i = 0; i < totalSamples; ++i) {
            dst[i] = 0;
        }
        return;
    }

    if (!srcA) {
        for (int32_t i = 0; i < totalSamples; ++i) {
            float sampleB = static_cast<float>(srcB[i]) * gainB;
            dst[i] = clamp16(static_cast<int32_t>(sampleB));
        }
        return;
    }

    if (!srcB) {
        for (int32_t i = 0; i < totalSamples; ++i) {
            float sampleA = static_cast<float>(srcA[i]) * gainA;
            dst[i] = clamp16(static_cast<int32_t>(sampleA));
        }
        return;
    }

    // Mix both streams with calculated equal-power or linear gains
    for (int32_t i = 0; i < totalSamples; ++i) {
        float sampleA = static_cast<float>(srcA[i]) * gainA;
        float sampleB = static_cast<float>(srcB[i]) * gainB;
        float mixed = sampleA + sampleB;
        dst[i] = clamp16(static_cast<int32_t>(mixed));
    }
}

} // namespace soundbox
