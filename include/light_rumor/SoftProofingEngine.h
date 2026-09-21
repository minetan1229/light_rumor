#pragma once

#include "Common.h"
#include <vector>
#include <string>
#include <memory>

namespace apex {

struct PaperProfileInfo {
    std::string profileId;
    std::string manufacturer; // Canon, Epson, Hahnemuhle, Ilford
    std::string paperName;    // Photo Paper Pro Platinum, Traditional Photo Paper, Photo Rag 308
    float paperWhiteDmax = 2.4f; // Optical density
    float paperWhiteTintLab[3] = {96.5f, 0.2f, 2.8f}; // Natural warm white or bright white
    bool supportsBPC = true;
};

/**
 * LittleCMS (lcms2) ICC Soft Proofing & Gamut Warning Engine.
 * Simulates printer ink/paper reproduction, paper white tint, black ink density,
 * and detects out-of-gamut colors.
 */
class SoftProofingEngine {
public:
    SoftProofingEngine();
    ~SoftProofingEngine();

    /**
     * Apply soft-proofing transformation to image buffer.
     * @param inPixels Source linear or sRGB FloatRGBA pixels
     * @param width Image width
     * @param height Image height
     * @param config Soft proofing settings (intent, paper white, black ink, gamut warning)
     * @param outProofPixels Output soft-proofed image pixels for display
     * @param outGamutMask Optional out-of-gamut mask (1.0 = out of gamut, 0.0 = within gamut)
     */
    static bool applySoftProof(const std::vector<FloatRGBA>& inPixels,
                              int32_t width, int32_t height,
                              const SoftProofConfig& config,
                              std::vector<FloatRGBA>& outProofPixels,
                              std::vector<float>& outGamutMask);

    /**
     * Detect if an individual RGB color is out of gamut for the specified paper profile.
     */
    static bool isOutOfGamut(float r, float g, float b, const SoftProofConfig& config);

    /**
     * Get list of built-in professional fine-art paper profiles.
     */
    static std::vector<PaperProfileInfo> getStandardPaperProfiles();
};

} // namespace apex
