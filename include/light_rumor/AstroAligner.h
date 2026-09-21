#pragma once

#include "light_rumor/Common.h"
#include <vector>
#include <memory>
#include <array>

namespace apex {

struct StarPoint {
    float x = 0.0f;           // Sub-pixel centroid X
    float y = 0.0f;           // Sub-pixel centroid Y
    float flux = 0.0f;        // Integrated star intensity above sky background
    float snr = 0.0f;         // Signal-to-noise ratio
    float fwhm = 2.0f;        // Estimated Full Width at Half Maximum
};

struct AffineTransform2D {
    // [x'] = [m00 m01] [x] + [tx]
    // [y']   [m10 m11] [y]   [ty]
    float m00 = 1.0f; float m01 = 0.0f; float tx = 0.0f;
    float m10 = 0.0f; float m11 = 1.0f; float ty = 0.0f;

    Point2D transform(float x, float y) const {
        return Point2D(m00 * x + m01 * y + tx, m10 * x + m11 * y + ty);
    }

    AffineTransform2D inverse() const {
        float det = m00 * m11 - m01 * m10;
        if (std::abs(det) < 1e-7f) return AffineTransform2D();
        float invDet = 1.0f / det;
        AffineTransform2D inv;
        inv.m00 =  m11 * invDet;
        inv.m01 = -m01 * invDet;
        inv.m10 = -m10 * invDet;
        inv.m11 =  m00 * invDet;
        inv.tx  = (m01 * ty - m11 * tx) * invDet;
        inv.ty  = (m10 * tx - m00 * ty) * invDet;
        return inv;
    }
};

/**
 * Astrophotography Geometric Alignment & Mathematical Stacking Engine.
 * Detects star centroids, aligns frames through asterism triangle matching and RANSAC,
 * and performs Kappa-Sigma clipping stacking.
 */
class AstroAligner {
public:
    AstroAligner();
    ~AstroAligner();

    /**
     * Detect star candidates with sub-pixel centroid accuracy.
     */
    static void detectStars(const std::vector<FloatRGBA>& frame,
                            int32_t width, int32_t height,
                            float minSNR,
                            int32_t maxCandidates,
                            std::vector<StarPoint>& outStars);

    /**
     * Estimate Affine transform from target frame to reference frame using star matching.
     */
    static bool estimateTransform(const std::vector<StarPoint>& refStars,
                                  const std::vector<StarPoint>& targetStars,
                                  AffineTransform2D& outTransform);

    /**
     * Warp a target frame to reference frame coordinate space using bilinear interpolation.
     */
    static void warpFrame(const std::vector<FloatRGBA>& inFrame,
                          int32_t width, int32_t height,
                          const AffineTransform2D& transform,
                          std::vector<FloatRGBA>& outWarped);

    /**
     * Stack multiple aligned astrophotography frames using Kappa-Sigma clipping.
     * Eliminates satellite trails, aircraft streaks, and sensor hot pixels while improving SNR by sqrt(N).
     */
    static bool stackKappaSigma(const std::vector<std::vector<FloatRGBA>>& frames,
                                int32_t width, int32_t height,
                                const AstroStackParams& params,
                                std::vector<FloatRGBA>& outStacked);
};

} // namespace apex
