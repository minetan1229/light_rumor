#include "light_rumor/SoftProofingEngine.h"
#include "light_rumor/ColorCheckerCalibration.h"
#include <cmath>
#include <algorithm>
#include <iostream>

namespace apex {

namespace {

const std::vector<PaperProfileInfo> STANDARD_PROFILES = {
    {"canon_pt101", "Canon", "Photo Paper Pro Platinum (PT-101)", 2.45f, {97.2f, 0.5f, -1.8f}, true},
    {"epson_traditional", "Epson", "Traditional Photo Paper (Baryta)", 2.38f, {96.8f, 0.3f, 1.2f}, true},
    {"hahnemuhle_photorag", "Hahnemühle", "Photo Rag 308 (Matte Cotton)", 1.72f, {95.5f, 0.4f, 3.2f}, true},
    {"ilford_gold_fibre", "Ilford", "Galerie Gold Fibre Gloss", 2.35f, {96.5f, 0.2f, 0.8f}, true}
};

} // namespace

SoftProofingEngine::SoftProofingEngine() = default;
SoftProofingEngine::~SoftProofingEngine() = default;

std::vector<PaperProfileInfo> SoftProofingEngine::getStandardPaperProfiles() {
    return STANDARD_PROFILES;
}

bool SoftProofingEngine::isOutOfGamut(float r, float g, float b, const SoftProofConfig& config) {
    float lab[3];
    ColorCheckerCalibration::rgbToLab(r, g, b, lab);

    float L = lab[0];
    float a = lab[1];
    float bVal = lab[2];
    float C = std::hypot(a, bVal);
    float hueRad = std::atan2(bVal, a);
    if (hueRad < 0.0f) hueRad += 2.0f * 3.14159265f;
    float hueDeg = hueRad * 180.0f / 3.14159265f;

    // Determine target paper profile
    float maxChroma = 70.0f;
    float minLuma = 5.0f;

    if (config.paperProfilePath.find("photorag") != std::string::npos ||
        config.paperProfilePath.find("matte") != std::string::npos) {
        // Matte paper has more restricted gamut and higher black point (Dmax ~ 1.72 -> L_min ~ 14.5)
        minLuma = 14.5f;
        maxChroma = 58.0f;
    } else {
        // Glossy / Baryta (Dmax ~ 2.4 -> L_min ~ 6.0)
        minLuma = 6.0f;
        maxChroma = 78.0f;
    }

    // Cyan/Blue gamut dip in physical pigment inks (180° - 250°)
    if (hueDeg >= 170.0f && hueDeg <= 260.0f) {
        maxChroma *= 0.72f;
    }

    // Gamut boundary reduces at extreme dark and bright ends
    float lumaFactor = 1.0f - std::pow(std::abs(L - 50.0f) / 50.0f, 2.0f);
    float boundaryChroma = maxChroma * std::max(0.2f, lumaFactor);

    if (L < minLuma || C > boundaryChroma) {
        return true;
    }

    return false;
}

bool SoftProofingEngine::applySoftProof(const std::vector<FloatRGBA>& inPixels,
                                       int32_t width, int32_t height,
                                       const SoftProofConfig& config,
                                       std::vector<FloatRGBA>& outProofPixels,
                                       std::vector<float>& outGamutMask) {
    if (inPixels.empty() || width <= 0 || height <= 0) return false;
    const size_t total = static_cast<size_t>(width) * height;

    outProofPixels.resize(total);
    outGamutMask.assign(total, 0.0f);

    // Profile lookup
    float paperWhiteGain = 0.97f;
    float blackLift = 0.015f; // minimum display black lift for physical ink simulation
    float warmTintB = 0.012f;

    if (config.paperProfilePath.find("photorag") != std::string::npos) {
        paperWhiteGain = 0.955f;
        blackLift = 0.055f; // Matte ink black lift
        warmTintB = 0.025f; // Cream warm base
    }

    uint32_t gwColor = config.gamutWarningColor;
    float gwR = ((gwColor >> 16) & 0xFF) / 255.0f;
    float gwG = ((gwColor >> 8) & 0xFF) / 255.0f;
    float gwB = (gwColor & 0xFF) / 255.0f;

    #pragma omp parallel for
    for (int64_t idx = 0; idx < static_cast<int64_t>(total); ++idx) {
        const auto& p = inPixels[idx];
        bool oom = isOutOfGamut(p.r, p.g, p.b, config);

        if (oom) {
            outGamutMask[idx] = 1.0f;
        }

        if (config.showGamutWarning && oom) {
            // Overlay gamut warning color
            outProofPixels[idx] = FloatRGBA(gwR, gwG, gwB, 1.0f);
            continue;
        }

        // Simulate paper & ink physics
        float r = p.r;
        float g = p.g;
        float b = p.b;

        if (config.intent == ProofingIntent::Perceptual) {
            // Smooth gamut compression (desaturate highlights slightly to preserve texture)
            float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
            float satFactor = (luma > 0.8f) ? 0.92f : 0.96f;
            r = luma + (r - luma) * satFactor;
            g = luma + (g - luma) * satFactor;
            b = luma + (b - luma) * satFactor;
        } else if (config.intent == ProofingIntent::RelativeColorimetric) {
            // Clip out-of-gamut chroma without affecting in-gamut tones
            if (oom) {
                float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                r = luma + (r - luma) * 0.82f;
                g = luma + (g - luma) * 0.82f;
                b = luma + (b - luma) * 0.82f;
            }
        }

        // 1. Simulate Black Ink (lift pedestal to paper minimum reflectance)
        if (config.simulateBlackInk) {
            r = blackLift + r * (1.0f - blackLift);
            g = blackLift + g * (1.0f - blackLift);
            b = blackLift + b * (1.0f - blackLift);
        }

        // 2. Simulate Paper White (scale max white and apply subtle paper substrate warmth)
        if (config.simulatePaperWhite) {
            r *= paperWhiteGain;
            g *= paperWhiteGain;
            b = b * paperWhiteGain + warmTintB * (1.0f - r); // warm paper base
        }

        outProofPixels[idx] = FloatRGBA(
            std::clamp(r, 0.0f, 1.0f),
            std::clamp(g, 0.0f, 1.0f),
            std::clamp(b, 0.0f, 1.0f),
            p.a
        );
    }

    return true;
}

} // namespace apex
