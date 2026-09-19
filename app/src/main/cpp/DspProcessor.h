#ifndef SOUNDBOX_DSP_PROCESSOR_H
#define SOUNDBOX_DSP_PROCESSOR_H

#include <cstdint>
#include <vector>
#include <algorithm>
#include <cmath>

namespace soundbox {

class DspProcessor {
public:
    DspProcessor();
    ~DspProcessor() = default;

    void setSampleRate(int32_t sampleRate);
    void setChannelCount(int32_t channelCount);

    /**
     * Process 16-bit interleaved PCM stereo audio frames in-place or from src to dst.
     * @param src Input int16_t buffer pointer
     * @param dst Output int16_t buffer pointer
     * @param frameCount Number of stereo frames (total samples = frameCount * 2)
     * @param balance Panning factor from -1.0f (left) to +1.0f (right)
     * @param virtualizerStrength Stereo widening strength (0 to 1000)
     * @param masterVolume Master output volume gain multiplier (e.g. 0.0f to 1.8f)
     */
    void processPcm16(
        const int16_t* src,
        int16_t* dst,
        int32_t frameCount,
        float balance,
        int32_t virtualizerStrength,
        float masterVolume
    );

    /**
     * Apply 5-band or 10-band equalizer gains to the PCM stream.
     */
    void applyEqualizer(
        int16_t* buffer,
        int32_t frameCount,
        const float* bandGains,
        int32_t bandCount
    );

private:
    int32_t mSampleRate = 44100;
    int32_t mChannelCount = 2;

    static inline int16_t clamp16(int32_t sample) {
        if (sample > 32767) return 32767;
        if (sample < -32768) return -32768;
        return static_cast<int16_t>(sample);
    }
};

} // namespace soundbox

#endif // SOUNDBOX_DSP_PROCESSOR_H
