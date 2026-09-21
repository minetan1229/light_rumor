#pragma once

#include "light_rumor/Common.h"
#include <vector>
#include <cstdint>
#include <memory>

namespace lightrumor {

/**
 * Cinema Field Assistance Scopes Engine.
 * Generates False Color heatmaps, Zebra Stripes, Focus Peaking, Vectorscopes, and Waveforms.
 */
class FieldScopesEngine {
public:
    FieldScopesEngine();
    ~FieldScopesEngine();

    // Render False Color heatmap (maps IRE values to 10-zone color standard)
    static void generateFalseColor(const FloatRGBA* inPixels,
                                   int32_t width, int32_t height,
                                   std::vector<FloatRGBA>& outPixels);

    // Render Zebra Pattern (diagonal stripes overlay over threshold IRE)
    static void generateZebra(const FloatRGBA* inPixels,
                              int32_t width, int32_t height,
                              float thresholdIRE,
                              float animPhase,
                              std::vector<FloatRGBA>& outPixels);

    // Render Focus Peaking (highlights sharp in-focus edges with fluorescent color)
    static void generateFocusPeaking(const FloatRGBA* inPixels,
                                     int32_t width, int32_t height,
                                     PeakingColor color,
                                     float threshold,
                                     std::vector<FloatRGBA>& outPixels);

    // Render 256x256 circular Vectorscope texture with SMPTE 75% targets & skin line
    static void generateVectorscope(const FloatRGBA* inPixels,
                                    int32_t width, int32_t height,
                                    int32_t scopeDim,
                                    float gain,
                                    std::vector<uint32_t>& outScopeRgba);

    // Draw vectorscope graticule (circle, crosshairs, I-line / skin tone angle, 75% target boxes)
    static void drawVectorscopeGraticule(std::vector<uint32_t>& scopePixels, int32_t dim);

    // Evaluate IRE from RGB (0..100)
    static inline float calculateIRE(float r, float g, float b) {
        float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        return luma * 100.0f;
    }

    // Map IRE to False Color RGB
    static FloatRGBA mapIREToFalseColor(float ire);
};

} // namespace lightrumor
