#pragma once

#include "light_rumor/Common.h"
#include <vector>
#include <memory>

namespace lightrumor {

class DenoiseEngine {
public:
    DenoiseEngine();
    ~DenoiseEngine();

    bool init();

    // Process a padded tile applying dual-domain noise reduction and edge-masked unsharp masking
    bool processTile(const std::vector<FloatRGBA>& inPaddedTile,
                     int32_t paddedW, int32_t paddedH,
                     int32_t validW, int32_t validH,
                     int32_t padding,
                     const DevelopmentParams& params,
                     std::vector<FloatRGBA>& outValidTile,
                     int32_t padLeft = -1,
                     int32_t padTop = -1);

    // Standalone algorithms for testing and granular GPU/CPU dispatch
    static void applyLuminanceNR(const FloatRGBA* src, FloatRGBA* dst,
                                 int32_t width, int32_t height,
                                 float strength, float detail, float contrast);

    static void applyChromaNR(const FloatRGBA* src, FloatRGBA* dst,
                              int32_t width, int32_t height,
                              float strength, float detail, float smoothness);

    static void applySharpening(const FloatRGBA* src, FloatRGBA* dst,
                                int32_t width, int32_t height,
                                float amount, float radius, float detail,
                                float masking, bool previewMask);

private:
    struct Impl;
    std::unique_ptr<Impl> m_impl;
};

} // namespace lightrumor
