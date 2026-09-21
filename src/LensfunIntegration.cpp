#include "light_rumor/LensfunIntegration.h"
#include <iostream>
#include <vector>
#include <cmath>
#include <algorithm>
#include <cstring>

namespace apex {

struct LensfunIntegration::Impl {
    std::vector<LensProfile> database;
};

LensfunIntegration::LensfunIntegration() : m_impl(std::make_unique<Impl>()) {
    init();
}

LensfunIntegration::~LensfunIntegration() = default;

bool LensfunIntegration::init() {
    m_impl->database.clear();

    // 1. Sony FE 24-70mm F2.8 GM II
    LensProfile sony2470;
    sony2470.lensMake = "Sony";
    sony2470.lensModel = "FE 24-70mm F2.8 GM II";
    sony2470.cameraMount = "Sony E";
    sony2470.focalLengthMin = 24.0f;
    sony2470.focalLengthMax = 70.0f;
    sony2470.k1 = -0.024f; // barrel distortion at 24mm
    sony2470.k2 = 0.006f;
    sony2470.v1 = -0.38f;
    sony2470.v2 = 0.12f;
    sony2470.tcaRed = 0.00032f;
    sony2470.tcaBlue = -0.00040f;
    m_impl->database.push_back(sony2470);

    // 2. Sony FE 16-35mm F2.8 GM II
    LensProfile sony1635;
    sony1635.lensMake = "Sony";
    sony1635.lensModel = "FE 16-35mm F2.8 GM II";
    sony1635.cameraMount = "Sony E";
    sony1635.focalLengthMin = 16.0f;
    sony1635.focalLengthMax = 35.0f;
    sony1635.k1 = -0.042f; // wide barrel
    sony1635.k2 = 0.012f;
    sony1635.v1 = -0.52f;
    sony1635.v2 = 0.20f;
    sony1635.tcaRed = 0.00055f;
    sony1635.tcaBlue = -0.00065f;
    m_impl->database.push_back(sony1635);

    // 3. Sigma 24-70mm F2.8 DG DN Art
    LensProfile sigma2470;
    sigma2470.lensMake = "Sigma";
    sigma2470.lensModel = "24-70mm F2.8 DG DN | Art";
    sigma2470.cameraMount = "Sony E";
    sigma2470.focalLengthMin = 24.0f;
    sigma2470.focalLengthMax = 70.0f;
    sigma2470.k1 = -0.028f;
    sigma2470.k2 = 0.007f;
    sigma2470.v1 = -0.42f;
    sigma2470.v2 = 0.14f;
    sigma2470.tcaRed = 0.00038f;
    sigma2470.tcaBlue = -0.00048f;
    m_impl->database.push_back(sigma2470);

    // 4. Canon RF 24-70mm F2.8 L IS USM
    LensProfile canon2470;
    canon2470.lensMake = "Canon";
    canon2470.lensModel = "RF24-70mm F2.8 L IS USM";
    canon2470.cameraMount = "Canon RF";
    canon2470.focalLengthMin = 24.0f;
    canon2470.focalLengthMax = 70.0f;
    canon2470.k1 = -0.022f;
    canon2470.k2 = 0.005f;
    canon2470.v1 = -0.34f;
    canon2470.v2 = 0.10f;
    canon2470.tcaRed = 0.00028f;
    canon2470.tcaBlue = -0.00035f;
    m_impl->database.push_back(canon2470);

    // 5. Hasselblad XCD 45mm F4
    LensProfile xcd45;
    xcd45.lensMake = "Hasselblad";
    xcd45.lensModel = "XCD 45mm F4 P";
    xcd45.cameraMount = "Hasselblad X";
    xcd45.focalLengthMin = 45.0f;
    xcd45.focalLengthMax = 45.0f;
    xcd45.k1 = -0.008f;
    xcd45.k2 = 0.001f;
    xcd45.v1 = -0.18f;
    xcd45.v2 = 0.04f;
    xcd45.tcaRed = 0.00010f;
    xcd45.tcaBlue = -0.00012f;
    m_impl->database.push_back(xcd45);

    return true;
}

bool LensfunIntegration::findProfile(const ExifMetadata& exif, LensProfile& outProfile) const {
    // 1. Try exact or substring match on lens model
    for (const auto& prof : m_impl->database) {
        if (!exif.lensModel.empty() && prof.lensModel.find(exif.lensModel) != std::string::npos) {
            outProfile = prof;
            return true;
        }
        if (!exif.make.empty() && prof.lensMake.find(exif.make) != std::string::npos) {
            outProfile = prof;
            return true;
        }
    }

    // Default fallback to standard 24-70 GM II profile
    if (!m_impl->database.empty()) {
        outProfile = m_impl->database[0];
        return true;
    }
    return false;
}

static inline float sampleBilinearChannel(const FloatRGBA* src, int32_t w, int32_t h, float x, float y, int channel) {
    x = std::clamp(x, 0.0f, static_cast<float>(w - 1));
    y = std::clamp(y, 0.0f, static_cast<float>(h - 1));

    int32_t x0 = static_cast<int32_t>(x);
    int32_t y0 = static_cast<int32_t>(y);
    int32_t x1 = std::min(x0 + 1, w - 1);
    int32_t y1 = std::min(y0 + 1, h - 1);

    float fx = x - x0;
    float fy = y - y0;

    auto getCh = [src, w, channel](int32_t px, int32_t py) -> float {
        const FloatRGBA& p = src[py * w + px];
        return (channel == 0) ? p.r : ((channel == 1) ? p.g : p.b);
    };

    float v00 = getCh(x0, y0);
    float v10 = getCh(x1, y0);
    float v01 = getCh(x0, y1);
    float v11 = getCh(x1, y1);

    float v0 = v00 * (1.0f - fx) + v10 * fx;
    float v1 = v01 * (1.0f - fx) + v11 * fx;
    return v0 * (1.0f - fy) + v1 * fy;
}

void LensfunIntegration::applyDistortionAndTCA(const FloatRGBA* src, FloatRGBA* dst,
                                              int32_t width, int32_t height,
                                              const LensProfile& profile,
                                              float distortionStrength,
                                              float tcaStrength) {
    float k1 = profile.k1 * (distortionStrength * 0.01f);
    float k2 = profile.k2 * (distortionStrength * 0.01f);
    float tcaR = profile.tcaRed * (tcaStrength * 0.01f);
    float tcaB = profile.tcaBlue * (tcaStrength * 0.01f);

    float cx = width * 0.5f;
    float cy = height * 0.5f;
    float maxR = std::sqrt(cx * cx + cy * cy);
    float invMaxR = 1.0f / maxR;

    for (int32_t y = 0; y < height; ++y) {
        float dy = y - cy;
        for (int32_t x = 0; x < width; ++x) {
            float dx = x - cx;
            float rNorm = std::sqrt(dx * dx + dy * dy) * invMaxR;
            float rNorm2 = rNorm * rNorm;
            float rNorm4 = rNorm2 * rNorm2;

            // Distortion factor: r_src = r_dst * (1 + k1*r^2 + k2*r^4)
            float distFactor = 1.0f + k1 * rNorm2 + k2 * rNorm4;

            // Base distorted coordinate (Green channel reference)
            float srcX_G = cx + dx * distFactor;
            float srcY_G = cy + dy * distFactor;

            // TCA scaling on Red and Blue
            float factorR = distFactor * (1.0f + tcaR * rNorm2);
            float factorB = distFactor * (1.0f + tcaB * rNorm2);

            float srcX_R = cx + dx * factorR;
            float srcY_R = cy + dy * factorR;

            float srcX_B = cx + dx * factorB;
            float srcY_B = cy + dy * factorB;

            float rSample = sampleBilinearChannel(src, width, height, srcX_R, srcY_R, 0);
            float gSample = sampleBilinearChannel(src, width, height, srcX_G, srcY_G, 1);
            float bSample = sampleBilinearChannel(src, width, height, srcX_B, srcY_B, 2);

            int32_t idx = y * width + x;
            dst[idx] = FloatRGBA(rSample, gSample, bSample, src[idx].a);
        }
    }
}

void LensfunIntegration::applyVignettingCorrection(const FloatRGBA* src, FloatRGBA* dst,
                                                  int32_t width, int32_t height,
                                                  const LensProfile& profile,
                                                  float vignettingStrength) {
    float v1 = profile.v1 * (vignettingStrength * 0.01f);
    float v2 = profile.v2 * (vignettingStrength * 0.01f);

    float cx = width * 0.5f;
    float cy = height * 0.5f;
    float maxR = std::sqrt(cx * cx + cy * cy);
    float invMaxR = 1.0f / maxR;

    for (int32_t y = 0; y < height; ++y) {
        float dy = y - cy;
        for (int32_t x = 0; x < width; ++x) {
            float dx = x - cx;
            float rNorm = std::sqrt(dx * dx + dy * dy) * invMaxR;
            float rNorm2 = rNorm * rNorm;
            float rNorm4 = rNorm2 * rNorm2;

            // Optical falloff: V(r) = 1 + v1*r^2 + v2*r^4 (where V < 1.0 towards edge)
            float falloff = 1.0f + v1 * rNorm2 + v2 * rNorm4;
            falloff = std::clamp(falloff, 0.15f, 1.0f);

            // Invert falloff to boost peripheral brightness
            float gain = 1.0f / falloff;

            int32_t idx = y * width + x;
            const FloatRGBA& p = src[idx];
            dst[idx] = FloatRGBA(p.r * gain, p.g * gain, p.b * gain, p.a);
        }
    }
}

void LensfunIntegration::applyDefringe(const FloatRGBA* src, FloatRGBA* dst,
                                      int32_t width, int32_t height,
                                      float purpleStrength, float greenStrength) {
    if (purpleStrength <= 0.0f && greenStrength <= 0.0f) {
        std::memcpy(dst, src, sizeof(FloatRGBA) * width * height);
        return;
    }

    float normPurple = purpleStrength * 0.01f;
    float normGreen = greenStrength * 0.01f;

    for (int32_t i = 0; i < width * height; ++i) {
        FloatRGBA p = src[i];
        float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;

        // Purple fringe check: High R + High B, Low G
        float purpleExcess = std::min(p.r, p.b) - p.g;
        if (purpleExcess > 0.02f && normPurple > 0.0f) {
            float desat = std::clamp(purpleExcess * normPurple * 2.0f, 0.0f, 1.0f);
            p.r = p.r * (1.0f - desat) + lum * desat;
            p.b = p.b * (1.0f - desat) + lum * desat;
        }

        // Green fringe check: High G, Low R and B
        float greenExcess = p.g - std::max(p.r, p.b);
        if (greenExcess > 0.02f && normGreen > 0.0f) {
            float desat = std::clamp(greenExcess * normGreen * 2.0f, 0.0f, 1.0f);
            p.g = p.g * (1.0f - desat) + lum * desat;
        }

        dst[i] = p;
    }
}

} // namespace apex
