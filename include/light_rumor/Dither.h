#pragma once

#include <cstdint>
#include <vector>
#include <cmath>
#include <algorithm>

namespace lightrumor {

class Dither {
public:
    // Fast coordinate-based hash for thread-safe, deterministic, stateless pseudo-random numbers
    static inline float hashToFloat(uint32_t x, uint32_t y, uint32_t seed) {
        // High quality 32-bit integer hash (Murmur3 / PCG finalizer style)
        uint32_t h = x * 374761393u + y * 668265263u + seed * 3628273133u;
        h = (h ^ (h >> 13)) * 1274126177u;
        h = h ^ (h >> 16);
        return static_cast<float>(h) * (1.0f / 4294967296.0f); // [0.0, 1.0)
    }

    // TPDF (Triangular Probability Density Function) random dither in [-1.0, +1.0] LSB
    static inline float getTPDF(uint32_t x, uint32_t y, uint32_t channel) {
        float r1 = hashToFloat(x, y, channel * 2u + 101u);
        float r2 = hashToFloat(x, y, channel * 2u + 202u);
        return (r1 - r2); // Triangular distribution [-1.0, 1.0]
    }

    // Quantize 32-bit float [0.0, 1.0] to 8-bit integer [0, 255] with TPDF dither
    static inline uint8_t quantize8(float linearVal, uint32_t x, uint32_t y, uint32_t channel, bool applyDither) {
        float scaled = linearVal * 255.0f;
        if (applyDither) {
            scaled += getTPDF(x, y, channel);
        }
        int32_t val = static_cast<int32_t>(std::round(scaled));
        return static_cast<uint8_t>(std::clamp(val, 0, 255));
    }

    // Quantize 32-bit float [0.0, 1.0] to 16-bit integer [0, 65535] with TPDF dither
    static inline uint16_t quantize16(float linearVal, uint32_t x, uint32_t y, uint32_t channel, bool applyDither) {
        float scaled = linearVal * 65535.0f;
        if (applyDither) {
            scaled += getTPDF(x, y, channel);
        }
        int32_t val = static_cast<int32_t>(std::round(scaled));
        return static_cast<uint16_t>(std::clamp(val, 0, 65535));
    }
};

} // namespace lightrumor
