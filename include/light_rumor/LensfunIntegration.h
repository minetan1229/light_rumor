#pragma once

#include "light_rumor/Common.h"
#include <string>
#include <vector>
#include <memory>

namespace lightrumor {

struct LensProfile {
    std::string lensMake;
    std::string lensModel;
    std::string cameraMount;
    float focalLengthMin = 24.0f;
    float focalLengthMax = 70.0f;
    
    // Polynomial distortion coefficients: r_d = r * (1 + k1*r^2 + k2*r^4)
    float k1 = -0.025f;
    float k2 = 0.005f;

    // Vignetting polynomial coefficients: V(r) = 1 + v1*r^2 + v2*r^4
    float v1 = -0.35f;
    float v2 = 0.12f;

    // Transverse Chromatic Aberration (TCA): channel radial scales
    float tcaRed = 0.00035f;  // radial stretch for R
    float tcaBlue = -0.00045f;// radial stretch for B
};

class LensfunIntegration {
public:
    LensfunIntegration();
    ~LensfunIntegration();

    bool init();

    // Find best matching lens profile based on Exif metadata
    bool findProfile(const ExifMetadata& exif, LensProfile& outProfile) const;

    // Apply lens profile corrections to an image / tile buffer
    static void applyDistortionAndTCA(const FloatRGBA* src, FloatRGBA* dst,
                                      int32_t width, int32_t height,
                                      const LensProfile& profile,
                                      float distortionStrength,
                                      float tcaStrength);

    static void applyVignettingCorrection(const FloatRGBA* src, FloatRGBA* dst,
                                          int32_t width, int32_t height,
                                          const LensProfile& profile,
                                          float vignettingStrength);

    static void applyDefringe(const FloatRGBA* src, FloatRGBA* dst,
                              int32_t width, int32_t height,
                              float purpleStrength, float greenStrength);

private:
    struct Impl;
    std::unique_ptr<Impl> m_impl;
};

} // namespace lightrumor
