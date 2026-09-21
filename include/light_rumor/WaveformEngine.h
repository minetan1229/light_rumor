#pragma once

#include "light_rumor/Common.h"
#include <vector>
#include <memory>
#include <cstdint>

namespace lightrumor {

/**
 * High-performance real-time RGB Waveform Monitor & Parade Engine.
 * Supports GPU Vulkan compute acceleration with sub-3ms CPU SIMD multithreaded fallback.
 * Generates visual cinema scopes: RGB Additive Overlay, RGB Parade, and Luma.
 */
class WaveformEngine {
public:
    WaveformEngine();
    ~WaveformEngine();

    // Initialize compute pipeline (and Vulkan if present)
    bool init();

    // Check if Vulkan acceleration is active
    bool isVulkanAccelerated() const { return m_isVulkanAccelerated; }

    /**
     * Compute visual waveform texture from 8-bit RGBA source pixels (e.g. preview bitmap).
     * @param rgbaPixels Input image 32-bit RGBA (width * height * 4 bytes)
     * @param width Image width
     * @param height Image height
     * @param mode Waveform mode (RgbOverlay, RgbParade, Histogram, Luma)
     * @param waveW Target waveform output width (default 512)
     * @param waveH Target waveform output height (default 256)
     * @param outWaveform Output waveform buffer containing visual RGBA pixels
     * @return Execution elapsed time in milliseconds
     */
    double computeFromRGBA8(const uint8_t* rgbaPixels,
                            int32_t width, int32_t height,
                            WaveformMode mode,
                            int32_t waveW, int32_t waveH,
                            WaveformData& outWaveform);

    /**
     * Compute visual waveform from 32-bit float RGBA linear buffer.
     */
    double computeFromFloatRGBA(const FloatRGBA* floatPixels,
                                int32_t width, int32_t height,
                                WaveformMode mode,
                                int32_t waveW, int32_t waveH,
                                WaveformData& outWaveform);

    /**
     * Overlay IRE graticule grid lines (0%, 18% Gray, 50%, 70% Skin, 100% Clip) onto waveform buffer.
     */
    static void drawGraticuleLines(WaveformData& waveform, bool isDarkTheme = true);

private:
    struct Impl;
    std::unique_ptr<Impl> m_impl;
    bool m_isVulkanAccelerated = false;
};

} // namespace lightrumor
