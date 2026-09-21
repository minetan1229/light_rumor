#pragma once

#include "light_rumor/Common.h"
#include <vector>
#include <memory>

namespace lightrumor {

/**
 * Computational Mathematical Stacking Engine.
 * Implements Focus Stacking (Laplacian pyramid / SML depth fusion),
 * Long Exposure Simulation (Median/Mean crowd & wave removal),
 * Multiple Exposure (5 blending modes), and 4-Shot Pixel Shift Super Resolution.
 */
class FocusStacker {
public:
    FocusStacker();
    ~FocusStacker();

    /**
     * Focus Stacking (Depth Synthesis / 深度合成)
     * Combines multiple RAW/Float frames taken at different focal distances into a single pan-focused image.
     * @param frames Vector of input frames (each has width * height FloatRGBA pixels)
     * @param width Image width
     * @param height Image height
     * @param params Focus stacking parameters (pyramid levels, sharpness exponent, feather)
     * @param outPanFocus Output pan-focus composite image
     * @return true on success
     */
    static bool stackFocus(const std::vector<std::vector<FloatRGBA>>& frames,
                           int32_t width, int32_t height,
                           const FocusStackParams& params,
                           std::vector<FloatRGBA>& outPanFocus);

    /**
     * Long Exposure Simulation: Median Stacking
     * Eliminates moving pedestrians, vehicles, and choppy wave textures without physical ND filter.
     */
    static bool stackMedian(const std::vector<std::vector<FloatRGBA>>& frames,
                            int32_t width, int32_t height,
                            std::vector<FloatRGBA>& outComposite);

    /**
     * Long Exposure Simulation: Mean Stacking
     * Produces silky smooth water/clouds and reduces random sensor noise by factor of sqrt(N).
     */
    static bool stackMean(const std::vector<std::vector<FloatRGBA>>& frames,
                          int32_t width, int32_t height,
                          std::vector<FloatRGBA>& outComposite);

    /**
     * Multiple Exposure Layer Blending
     * Modes: Additive, Average, Screen, Lighten, Darken.
     */
    static bool stackMultipleExposure(const std::vector<std::vector<FloatRGBA>>& frames,
                                      int32_t width, int32_t height,
                                      MultiExposureBlendMode mode,
                                      const std::vector<float>& opacities,
                                      std::vector<FloatRGBA>& outComposite);

    /**
     * 4-Shot Pixel Shift Super Resolution
     * Merges 4 sensor-shift RAW exposures (0,0 / 1,0 / 1,1 / 0,1) into demosaic-free full RGB.
     */
    static bool stackPixelShift4Shot(const std::vector<std::vector<FloatRGBA>>& fourShots,
                                     int32_t width, int32_t height,
                                     std::vector<FloatRGBA>& outSuperRes);

    /**
     * Compute Sum Modified Laplacian (SML) Sharpness map for an individual frame.
     */
    static void computeSharpnessMap(const std::vector<FloatRGBA>& frame,
                                   int32_t width, int32_t height,
                                   int32_t windowRadius,
                                   std::vector<float>& outSharpness);
};

} // namespace lightrumor
