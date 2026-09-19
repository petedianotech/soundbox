#ifndef SOUNDBOX_CROSSFADER_H
#define SOUNDBOX_CROSSFADER_H

#include <cstdint>
#include <cmath>
#include <algorithm>

namespace soundbox {

enum class CrossfadeCurve : int32_t {
    EQUAL_POWER = 0, // Constant energy curve (cos/sin) - prevents dip in mid-crossfade
    LINEAR = 1,      // Standard linear ramp
    S_CURVE = 2      // Smooth S-curve (smoothstep)
};

class Crossfader {
public:
    Crossfader() = default;
    ~Crossfader() = default;

    /**
     * Mix two 16-bit stereo PCM buffers during a crossfade transition.
     * @param srcA Outgoing stream buffer
     * @param srcB Incoming stream buffer
     * @param dst Mixed output buffer
     * @param frameCount Number of frames to mix
     * @param progress Transition progress between 0.0f (100% A) and 1.0f (100% B)
     * @param curve Curve blending algorithm
     */
    static void mix(
        const int16_t* srcA,
        const int16_t* srcB,
        int16_t* dst,
        int32_t frameCount,
        int32_t channelCount,
        float progress,
        CrossfadeCurve curve = CrossfadeCurve::EQUAL_POWER
    );

    /**
     * Calculate volume gains for stream A and stream B given the progress and curve.
     */
    static void calculateGains(
        float progress,
        CrossfadeCurve curve,
        float& outGainA,
        float& outGainB
    );

private:
    static inline int16_t clamp16(int32_t sample) {
        if (sample > 32767) return 32767;
        if (sample < -32768) return -32768;
        return static_cast<int16_t>(sample);
    }
};

} // namespace soundbox

#endif // SOUNDBOX_CROSSFADER_H
