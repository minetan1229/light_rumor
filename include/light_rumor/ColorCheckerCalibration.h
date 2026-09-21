#pragma once

#include "light_rumor/Common.h"
#include <vector>
#include <array>
#include <string>

namespace lightrumor {

struct ChartCorners {
    Point2D topLeft;
    Point2D topRight;
    Point2D bottomRight;
    Point2D bottomLeft;
};

/**
 * 24-ColorChecker Calibration & Camera Profile Generation Engine.
 * Samples ColorChecker Classic patches, estimates scene illuminant CCT,
 * solves regularized constrained least-squares color matrix, and evaluates Delta E.
 */
class ColorCheckerCalibration {
public:
    ColorCheckerCalibration();
    ~ColorCheckerCalibration();

    /**
     * Calibrate camera color response from a captured frame containing a 24-ColorChecker chart.
     * @param frame Input image FloatRGBA pixels
     * @param width Image width
     * @param height Image height
     * @param corners 4 corners of the chart in normalized coordinates (0.0 to 1.0)
     * @param outResult Calibration result containing 3x3 matrix, CCT, and per-patch Delta E
     * @return true if calibration successfully converged
     */
    static bool calibrate(const std::vector<FloatRGBA>& frame,
                          int32_t width, int32_t height,
                          const ChartCorners& corners,
                          CalibrationResult& outResult);

    /**
     * Sample average RGB for all 24 patches within specified chart corners.
     */
    static bool samplePatches(const std::vector<FloatRGBA>& frame,
                             int32_t width, int32_t height,
                             const ChartCorners& corners,
                             std::vector<ColorCheckerPatchData>& outPatches);

    /**
     * Compute CIEDE2000 color difference between two Lab colors.
     */
    static float calculateDeltaE00(const float lab1[3], const float lab2[3]);

    /**
     * Convert linear sRGB [0..1] to CIE L*a*b* (D65).
     */
    static void rgbToLab(float r, float g, float b, float outLab[3]);

    /**
     * Convert CIE L*a*b* (D65) to linear sRGB [0..1].
     */
    static void labToRgb(const float lab[3], float& outR, float& outG, float& outB);

    /**
     * Get reference standard Lab and sRGB values for patch 0..23.
     */
    static const ColorCheckerPatchData& getStandardPatch(int32_t patchIndex);
};

} // namespace lightrumor
