#include "light_rumor/ExportPipeline.h"
#include "light_rumor/ImageWriter.h"
#include "light_rumor/DenoiseEngine.h"
#include "light_rumor/LensfunIntegration.h"
#include "light_rumor/MaskEngine.h"
#include <iostream>
#include <vector>
#include <cmath>
#include <algorithm>
#include <cstring>
#include <chrono>

namespace lightrumor {

namespace {

// Fast RGB <-> HSL conversion
inline void rgbToHsl(float r, float g, float b, float& h, float& s, float& l) {
    float safeR = std::max(0.0f, r);
    float safeG = std::max(0.0f, g);
    float safeB = std::max(0.0f, b);
    float maxVal = std::max({safeR, safeG, safeB});
    float minVal = std::min({safeR, safeG, safeB});
    float delta = maxVal - minVal;
    l = (maxVal + minVal) * 0.5f;

    if (delta < 1e-6f || maxVal < 1e-6f) {
        h = 0.0f;
        s = 0.0f;
    } else {
        if (l < 0.5f) {
            float denom = maxVal + minVal;
            s = delta / std::max(denom, 1e-5f);
        } else {
            float denom = 2.0f - maxVal - minVal;
            if (denom <= 1e-5f) {
                s = delta / std::max(maxVal, 1e-5f);
            } else {
                s = delta / denom;
            }
        }
        s = std::clamp(s, 0.0f, 1.0f);

        if (safeR == maxVal) {
            h = (safeG - safeB) / delta + (safeG < safeB ? 6.0f : 0.0f);
        } else if (safeG == maxVal) {
            h = (safeB - safeR) / delta + 2.0f;
        } else {
            h = (safeR - safeG) / delta + 4.0f;
        }
        h *= 60.0f; // 0 to 360 degrees
    }
}

inline float hueToRgb(float p, float q, float t) {
    if (t < 0.0f) t += 1.0f;
    if (t > 1.0f) t -= 1.0f;
    if (t < 1.0f / 6.0f) return p + (q - p) * 6.0f * t;
    if (t < 1.0f / 2.0f) return q;
    if (t < 2.0f / 3.0f) return p + (q - p) * (2.0f / 3.0f - t) * 6.0f;
    return p;
}

inline void hslToRgb(float h, float s, float l, float& r, float& g, float& b) {
    if (s < 1e-6f) {
        r = g = b = l;
        return;
    }
    float hNorm = h / 360.0f;
    float q = (l < 0.5f) ? (l * (1.0f + s)) : (l + s - l * s);
    float p = 2.0f * l - q;
    r = hueToRgb(p, q, hNorm + 1.0f / 3.0f);
    g = hueToRgb(p, q, hNorm);
    b = hueToRgb(p, q, hNorm - 1.0f / 3.0f);
}

template <typename T>
void applyFlip(std::vector<T>& buffer, int32_t imgW, int32_t imgH, bool flipH, bool flipV) {
    if (!flipH && !flipV) return;
    if (flipH) {
        #pragma omp parallel for schedule(static)
        for (int32_t y = 0; y < imgH; ++y) {
            T* row = &buffer[static_cast<size_t>(y) * imgW * 3];
            for (int32_t x = 0; x < imgW / 2; ++x) {
                int32_t oppX = imgW - 1 - x;
                std::swap(row[x * 3 + 0], row[oppX * 3 + 0]);
                std::swap(row[x * 3 + 1], row[oppX * 3 + 1]);
                std::swap(row[x * 3 + 2], row[oppX * 3 + 2]);
            }
        }
    }
    if (flipV) {
        #pragma omp parallel for schedule(static)
        for (int32_t y = 0; y < imgH / 2; ++y) {
            int32_t oppY = imgH - 1 - y;
            T* rowA = &buffer[static_cast<size_t>(y) * imgW * 3];
            T* rowB = &buffer[static_cast<size_t>(oppY) * imgW * 3];
            for (int32_t x = 0; x < imgW * 3; ++x) {
                std::swap(rowA[x], rowB[x]);
            }
        }
    }
}

template <typename T>
void applyStepRotation(std::vector<T>& buffer, int32_t& imgW, int32_t& imgH, int32_t steps) {
    int32_t normSteps = (steps % 4 + 4) % 4;
    if (normSteps == 0) return;

    if (normSteps == 2) {
        // 180° rotation
        int64_t totalPixels = static_cast<int64_t>(imgW) * imgH;
        #pragma omp parallel for schedule(static)
        for (int64_t i = 0; i < totalPixels / 2; ++i) {
            size_t opp = static_cast<size_t>(totalPixels - 1 - i);
            size_t cur = static_cast<size_t>(i);
            std::swap(buffer[cur * 3 + 0], buffer[opp * 3 + 0]);
            std::swap(buffer[cur * 3 + 1], buffer[opp * 3 + 1]);
            std::swap(buffer[cur * 3 + 2], buffer[opp * 3 + 2]);
        }
        return;
    }

    int32_t newW = imgH;
    int32_t newH = imgW;
    std::vector<T> rotated(static_cast<size_t>(newW) * newH * 3);

    if (normSteps == 1) {
        // 90° CW: (x, y) in old -> (imgH - 1 - y, x) in new
        #pragma omp parallel for schedule(static)
        for (int32_t y = 0; y < imgH; ++y) {
            for (int32_t x = 0; x < imgW; ++x) {
                int32_t nx = imgH - 1 - y;
                int32_t ny = x;
                size_t srcIdx = (static_cast<size_t>(y) * imgW + x) * 3;
                size_t dstIdx = (static_cast<size_t>(ny) * newW + nx) * 3;
                rotated[dstIdx + 0] = buffer[srcIdx + 0];
                rotated[dstIdx + 1] = buffer[srcIdx + 1];
                rotated[dstIdx + 2] = buffer[srcIdx + 2];
            }
        }
    } else if (normSteps == 3) {
        // 270° CW / 90° CCW: (x, y) in old -> (y, imgW - 1 - x) in new
        #pragma omp parallel for schedule(static)
        for (int32_t y = 0; y < imgH; ++y) {
            for (int32_t x = 0; x < imgW; ++x) {
                int32_t nx = y;
                int32_t ny = imgW - 1 - x;
                size_t srcIdx = (static_cast<size_t>(y) * imgW + x) * 3;
                size_t dstIdx = (static_cast<size_t>(ny) * newW + nx) * 3;
                rotated[dstIdx + 0] = buffer[srcIdx + 0];
                rotated[dstIdx + 1] = buffer[srcIdx + 1];
                rotated[dstIdx + 2] = buffer[srcIdx + 2];
            }
        }
    }

    buffer = std::move(rotated);
    imgW = newW;
    imgH = newH;
}

template <typename T>
void applyFineRotation(std::vector<T>& buffer, int32_t imgW, int32_t imgH, float degrees) {
    if (std::abs(degrees) < 1e-3f) return;
    float rad = -degrees * 3.14159265358979323846f / 180.0f; // inverse rotation for sampling
    float cosA = std::cos(rad);
    float sinA = std::sin(rad);
    float cx = imgW * 0.5f;
    float cy = imgH * 0.5f;

    std::vector<T> rotated(static_cast<size_t>(imgW) * imgH * 3);

    #pragma omp parallel for schedule(static)
    for (int32_t ny = 0; ny < imgH; ++ny) {
        float dy = ny - cy;
        for (int32_t nx = 0; nx < imgW; ++nx) {
            float dx = nx - cx;
            float sx = cx + (dx * cosA - dy * sinA);
            float sy = cy + (dx * sinA + dy * cosA);

            int32_t x0 = static_cast<int32_t>(std::floor(sx));
            int32_t y0 = static_cast<int32_t>(std::floor(sy));
            int32_t x1 = x0 + 1;
            int32_t y1 = y0 + 1;

            float fx = sx - x0;
            float fy = sy - y0;

            x0 = std::clamp(x0, 0, imgW - 1);
            x1 = std::clamp(x1, 0, imgW - 1);
            y0 = std::clamp(y0, 0, imgH - 1);
            y1 = std::clamp(y1, 0, imgH - 1);

            size_t dstIdx = (static_cast<size_t>(ny) * imgW + nx) * 3;
            size_t idx00 = (static_cast<size_t>(y0) * imgW + x0) * 3;
            size_t idx10 = (static_cast<size_t>(y0) * imgW + x1) * 3;
            size_t idx01 = (static_cast<size_t>(y1) * imgW + x0) * 3;
            size_t idx11 = (static_cast<size_t>(y1) * imgW + x1) * 3;

            for (int32_t c = 0; c < 3; ++c) {
                float v00 = buffer[idx00 + c];
                float v10 = buffer[idx10 + c];
                float v01 = buffer[idx01 + c];
                float v11 = buffer[idx11 + c];
                float val = (v00 * (1.0f - fx) + v10 * fx) * (1.0f - fy) +
                            (v01 * (1.0f - fx) + v11 * fx) * fy;
                rotated[dstIdx + c] = static_cast<T>(std::clamp(std::round(val), 0.0f, static_cast<float>(std::numeric_limits<T>::max())));
            }
        }
    }

    buffer = std::move(rotated);
}

// 8-color band centers in degrees: Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta
static const float bandCenters[8] = {
    0.0f,   // Red
    30.0f,  // Orange
    60.0f,  // Yellow
    120.0f, // Green
    180.0f, // Aqua
    240.0f, // Blue
    285.0f, // Purple
    330.0f  // Magenta
};

// Circular angular distance in [0, 180]
inline float angularDistance(float a, float b) {
    float d = std::abs(a - b);
    return d > 180.0f ? 360.0f - d : d;
}

} // namespace

ExportPipeline::ExportPipeline() : m_vulkan(std::make_unique<VulkanCompute>()) {
    m_vulkan->init();
}

ExportPipeline::~ExportPipeline() = default;

void ExportPipeline::processTileLinear(const std::vector<FloatRGBA>& inPaddedTile,
                                       int32_t paddedW, int32_t paddedH,
                                       int32_t validW, int32_t validH,
                                       int32_t padding,
                                       const DevelopmentParams& params,
                                       std::vector<FloatRGBA>& outValidTile,
                                       int32_t padLeft,
                                       int32_t padTop,
                                       int32_t tileOriginX,
                                       int32_t tileOriginY,
                                       int32_t fullWidth,
                                       int32_t fullHeight) {
    if (validW <= 0 || validH <= 0 || paddedW <= 0 || paddedH <= 0 ||
        inPaddedTile.size() < static_cast<size_t>(paddedW) * paddedH) {
        outValidTile.clear();
        return;
    }

    outValidTile.resize(static_cast<size_t>(validW) * validH);

    // 1. Calculate White Balance multipliers (2,000K to 50,000K)
    float kelvin = std::clamp(params.kelvin, 2000.0f, 50000.0f);
    float kelvinRatio = kelvin / 5500.0f;
    float rGain = std::pow(kelvinRatio, 0.65f);
    float bGain = std::pow(1.0f / kelvinRatio, 0.85f);
    float gGain = std::max(0.01f, 1.0f - (params.tint * 0.005f));
    // Normalize relative to G
    rGain /= gGain;
    bGain /= gGain;

    float exposureMult = std::pow(2.0f, params.exposureEV);

    // Temporary working buffer for the padded tile
    std::vector<FloatRGBA> workTile = inPaddedTile;
    size_t totalPadded = static_cast<size_t>(paddedW) * paddedH;

    // Pass 1: Pixel-wise Tone, WB, Color Mixer / Monochrome
    #pragma omp parallel for schedule(static)
    for (int64_t i = 0; i < static_cast<int64_t>(totalPadded); ++i) {
        FloatRGBA& p = workTile[i];

        // 1. White balance
        p.r *= rGain;
        p.b *= bGain;

        // Shadow tint
        float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
        float shadowWeight = std::pow(1.0f - std::clamp(lum, 0.0f, 1.0f), 2.0f);
        p.r += shadowWeight * (params.shadowTintR * 0.002f);
        p.g += shadowWeight * (params.shadowTintG * 0.002f);
        p.b += shadowWeight * (params.shadowTintB * 0.002f);

        // 2. Exposure
        p.r *= exposureMult;
        p.g *= exposureMult;
        p.b *= exposureMult;

        // 3. Atmospheric Dehaze
        if (std::abs(params.dehaze) > 1e-4f) {
            float atmos = 0.95f;
            if (params.dehaze > 0.0f) {
                float darkCh = std::min({p.r, p.g, p.b});
                float t = std::clamp(1.0f - 0.75f * darkCh, 0.15f, 1.0f);
                float factor = params.dehaze * 0.01f;
                float dehazedR = (p.r - atmos) / std::lerp(1.0f, t, factor * 0.6f) + atmos;
                float dehazedG = (p.g - atmos) / std::lerp(1.0f, t, factor * 0.6f) + atmos;
                float dehazedB = (p.b - atmos) / std::lerp(1.0f, t, factor * 0.6f) + atmos;
                p.r = std::max(0.0f, p.r * (1.0f - factor) + dehazedR * factor);
                p.g = std::max(0.0f, p.g * (1.0f - factor) + dehazedG * factor);
                p.b = std::max(0.0f, p.b * (1.0f - factor) + dehazedB * factor);
            } else {
                // Add atmospheric haze
                float hazeFactor = (-params.dehaze) * 0.01f;
                p.r = p.r * (1.0f - hazeFactor * 0.5f) + atmos * (hazeFactor * 0.5f);
                p.g = p.g * (1.0f - hazeFactor * 0.5f) + atmos * (hazeFactor * 0.5f);
                p.b = p.b * (1.0f - hazeFactor * 0.5f) + atmos * (hazeFactor * 0.5f);
            }
        }

        // 4. Primary Calibration
        if (std::abs(params.primaryRed.hueShift) > 1e-4f || std::abs(params.primaryRed.saturationShift) > 1e-4f ||
            std::abs(params.primaryGreen.hueShift) > 1e-4f || std::abs(params.primaryGreen.saturationShift) > 1e-4f ||
            std::abs(params.primaryBlue.hueShift) > 1e-4f || std::abs(params.primaryBlue.saturationShift) > 1e-4f) {
            float h, s, l;
            rgbToHsl(p.r, p.g, p.b, h, s, l);
            float wR = std::max(0.0f, 1.0f - std::abs(h - 0.0f) / 120.0f) + std::max(0.0f, 1.0f - std::abs(h - 360.0f) / 120.0f);
            float wG = std::max(0.0f, 1.0f - std::abs(h - 120.0f) / 120.0f);
            float wB = std::max(0.0f, 1.0f - std::abs(h - 240.0f) / 120.0f);
            float dH = (wR * params.primaryRed.hueShift + wG * params.primaryGreen.hueShift + wB * params.primaryBlue.hueShift) * 0.3f;
            float dS = (wR * params.primaryRed.saturationShift + wG * params.primaryGreen.saturationShift + wB * params.primaryBlue.saturationShift) * 0.01f;
            h = std::fmod(std::fmod(h + dH, 360.0f) + 360.0f, 360.0f);
            s = std::clamp(s * (1.0f + dS), 0.0f, 1.0f);
            hslToRgb(h, s, l, p.r, p.g, p.b);
        }

        // 5. Highlight recovery (compress when negative, expand when positive)
        if (params.highlights < 0.0f) {
            float hFactor = (-params.highlights) * 0.015f;
            auto recoverC = [hFactor](float c) {
                if (c > 0.75f) {
                    float excess = c - 0.75f;
                    return 0.75f + (excess / (1.0f + excess * hFactor));
                }
                return c;
            };
            p.r = recoverC(p.r);
            p.g = recoverC(p.g);
            p.b = recoverC(p.b);
        } else if (params.highlights > 0.0f) {
            float hBoost = 1.0f + params.highlights * 0.005f;
            auto boostC = [hBoost](float c) {
                if (c > 0.75f) {
                    return 0.75f + (c - 0.75f) * hBoost;
                }
                return c;
            };
            p.r = boostC(p.r);
            p.g = boostC(p.g);
            p.b = boostC(p.b);
        }

        // 6. Shadow lift
        if (std::abs(params.shadows) > 1e-4f) {
            float lumUpdated = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
            float sLift = std::pow(1.0f - std::clamp(lumUpdated, 0.0f, 1.0f), 3.0f) * (params.shadows * 0.004f);
            p.r = std::max(0.0f, p.r + sLift);
            p.g = std::max(0.0f, p.g + sLift);
            p.b = std::max(0.0f, p.b + sLift);
        }

        // 7. Contrast (S-curve around 0.18 middle gray pivot)
        if (std::abs(params.contrast) > 1e-4f) {
            float cFactor = params.contrast * 0.006f;
            auto applyContrast = [cFactor](float c) {
                if (c <= 0.0f) return 0.0f;
                float pivot = 0.18f;
                if (c < pivot) {
                    return pivot * std::pow(c / pivot, 1.0f + cFactor);
                } else {
                    float out = 1.0f - (1.0f - pivot) * std::pow(std::max(0.0f, 1.0f - c) / (1.0f - pivot), 1.0f + cFactor);
                    return std::max(pivot, out);
                }
            };
            p.r = applyContrast(p.r);
            p.g = applyContrast(p.g);
            p.b = applyContrast(p.b);
        }

        // 8. White & Black levels
        p.r = std::max(0.0f, p.r - params.blacks * 0.0005f) * (1.0f + params.whites * 0.005f);
        p.g = std::max(0.0f, p.g - params.blacks * 0.0005f) * (1.0f + params.whites * 0.005f);
        p.b = std::max(0.0f, p.b - params.blacks * 0.0005f) * (1.0f + params.whites * 0.005f);

        // 9. 8-Color Mixer or Monochrome Mode
        if (params.isMonochrome) {
            float h, s, l;
            rgbToHsl(p.r, p.g, p.b, h, s, l);

            // Compute weights across 8 optical color bands with strict 45 deg cutoff
            float totalWeight = 0.0f;
            float filteredWeight = 0.0f;
            for (int b = 0; b < 8; ++b) {
                float dist = angularDistance(h, bandCenters[b]);
                if (dist < 45.0f) {
                    float w = std::cos(dist * (3.14159265f / 90.0f));
                    totalWeight += w;
                    filteredWeight += w * params.monochromeWeights[b];
                }
            }
            float filterGain = (totalWeight > 1e-5f) ? (filteredWeight / totalWeight) * 8.0f : 1.0f;
            float gray = (0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b) * filterGain;
            gray = std::max(0.0f, gray);
            p.r = gray;
            p.g = gray;
            p.b = gray;
        } else {
            float h, s, l;
            rgbToHsl(p.r, p.g, p.b, h, s, l);

            if (s > 1e-4f) {
                float deltaH = 0.0f, deltaS = 0.0f, deltaL = 0.0f;
                float sumW = 0.0f;
                for (int b = 0; b < 8; ++b) {
                    float dist = angularDistance(h, bandCenters[b]);
                    if (dist < 45.0f) {
                        float w = std::cos(dist * (3.14159265f / 90.0f));
                        sumW += w;
                        deltaH += w * params.hslBands[b].hueShift;
                        deltaS += w * params.hslBands[b].saturation;
                        deltaL += w * params.hslBands[b].luminance;
                    }
                }
                if (sumW > 1e-5f) {
                    deltaH /= sumW;
                    deltaS /= sumW;
                    deltaL /= sumW;
                    h = std::fmod(std::fmod(h + deltaH, 360.0f) + 360.0f, 360.0f);
                    s = std::clamp(s * (1.0f + deltaS * 0.01f), 0.0f, 1.0f);
                    l = std::clamp(l * (1.0f + deltaL * 0.01f), 0.0f, 1.0f);
                    hslToRgb(h, s, l, p.r, p.g, p.b);
                }
            }

            // 10. Vibrance (skin tone protection) & Global Saturation
            if (std::abs(params.vibrance) > 1e-4f || std::abs(params.saturation) > 1e-4f) {
                float curLum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
                float maxC = std::max({p.r, p.g, p.b});
                float minC = std::min({p.r, p.g, p.b});
                float sat = (maxC > 1e-5f) ? (maxC - minC) / maxC : 0.0f;

                // Skin protection: Hue in [10, 50] deg centered around 25 deg
                float skinWeight = 0.0f;
                if (h >= 10.0f && h <= 50.0f) {
                    skinWeight = std::max(0.0f, 1.0f - (std::abs(h - 25.0f) / 20.0f));
                }

                float vibBoost = params.vibrance * 0.01f;
                if (vibBoost > 0.0f) {
                    vibBoost *= (1.0f - sat) * (1.0f - skinWeight * 0.85f);
                } else {
                    vibBoost *= sat * (1.0f - skinWeight * 0.85f);
                }
                float totalSatScale = (1.0f + vibBoost) * (1.0f + params.saturation * 0.01f);

                p.r = curLum + (p.r - curLum) * totalSatScale;
                p.g = curLum + (p.g - curLum) * totalSatScale;
                p.b = curLum + (p.b - curLum) * totalSatScale;
            }
        }

        // 11. Tone Curve 1D LUT (if present)
        if (params.toneCurveLUT.size() == 256) {
            auto applyLUT = [&](float val) {
                float norm = std::clamp(val, 0.0f, 1.0f) * 255.0f;
                int idx0 = static_cast<int>(norm);
                int idx1 = std::min(idx0 + 1, 255);
                float frac = norm - idx0;
                return params.toneCurveLUT[idx0] * (1.0f - frac) + params.toneCurveLUT[idx1] * frac;
            };
            p.r = applyLUT(p.r);
            p.g = applyLUT(p.g);
            p.b = applyLUT(p.b);
        }
    }

    // 12. Phase 5: Local Mask Layers & Retouch Operations
    if (!params.retouchOps.empty()) {
        MaskEngine::processRetouchOps(workTile, paddedW, paddedH, params.retouchOps, tileOriginX, tileOriginY, fullWidth, fullHeight);
    }
    if (!params.maskLayers.empty()) {
        MaskEngine::processMaskLayers(workTile, paddedW, paddedH, params.maskLayers, {}, tileOriginX, tileOriginY, fullWidth, fullHeight);
    }

    // Pass 2: Spatial Noise Reduction & Sharpening via DenoiseEngine
    thread_local DenoiseEngine s_denoiseEngine;
    s_denoiseEngine.processTile(workTile, paddedW, paddedH, validW, validH, padding, params, outValidTile, padLeft, padTop);
}

void ExportPipeline::quantizeTo8Bit(const std::vector<FloatRGBA>& linearTile,
                                    int32_t width, int32_t height,
                                    uint32_t tileStartX, uint32_t tileStartY,
                                    ColorSpace cs, bool enableDither,
                                    std::vector<uint8_t>& out8BitRGB) {
    if (width <= 0 || height <= 0) return;
    size_t numPixels = static_cast<size_t>(width) * height;
    out8BitRGB.resize(numPixels * 3);

    auto applyOETF = [cs](float val) -> float {
        if (val <= 0.0f) return 0.0f;
        if (cs == ColorSpace::LinearSRGB || cs == ColorSpace::ACEScg || cs == ColorSpace::LinearRec2020) {
            return val;
        } else if (cs == ColorSpace::AdobeRGB) {
            return std::pow(val, 1.0f / 2.19921875f);
        } else {
            // sRGB and Display P3
            if (val <= 0.0031308f) {
                return val * 12.92f;
            } else {
                return 1.055f * std::pow(val, 1.0f / 2.4f) - 0.055f;
            }
        }
    };

    for (int32_t y = 0; y < height; ++y) {
        uint32_t globalY = tileStartY + y;
        for (int32_t x = 0; x < width; ++x) {
            uint32_t globalX = tileStartX + x;
            size_t idx = static_cast<size_t>(y) * width + x;
            const FloatRGBA& p = linearTile[idx];

            float rGamma = applyOETF(p.r);
            float gGamma = applyOETF(p.g);
            float bGamma = applyOETF(p.b);

            size_t outIdx = idx * 3;
            out8BitRGB[outIdx + 0] = Dither::quantize8(rGamma, globalX, globalY, 0, enableDither);
            out8BitRGB[outIdx + 1] = Dither::quantize8(gGamma, globalX, globalY, 1, enableDither);
            out8BitRGB[outIdx + 2] = Dither::quantize8(bGamma, globalX, globalY, 2, enableDither);
        }
    }
}

void ExportPipeline::quantizeTo16Bit(const std::vector<FloatRGBA>& linearTile,
                                     int32_t width, int32_t height,
                                     uint32_t tileStartX, uint32_t tileStartY,
                                     ColorSpace cs, bool enableDither,
                                     std::vector<uint16_t>& out16BitRGB) {
    if (width <= 0 || height <= 0) return;
    size_t numPixels = static_cast<size_t>(width) * height;
    out16BitRGB.resize(numPixels * 3);

    auto applyOETF = [cs](float val) -> float {
        if (val <= 0.0f) return 0.0f;
        if (cs == ColorSpace::LinearSRGB || cs == ColorSpace::ACEScg || cs == ColorSpace::LinearRec2020) {
            return val;
        } else if (cs == ColorSpace::AdobeRGB) {
            return std::pow(val, 1.0f / 2.19921875f);
        } else {
            if (val <= 0.0031308f) {
                return val * 12.92f;
            } else {
                return 1.055f * std::pow(val, 1.0f / 2.4f) - 0.055f;
            }
        }
    };

    for (int32_t y = 0; y < height; ++y) {
        uint32_t globalY = tileStartY + y;
        for (int32_t x = 0; x < width; ++x) {
            uint32_t globalX = tileStartX + x;
            size_t idx = static_cast<size_t>(y) * width + x;
            const FloatRGBA& p = linearTile[idx];

            float rGamma = applyOETF(p.r);
            float gGamma = applyOETF(p.g);
            float bGamma = applyOETF(p.b);

            size_t outIdx = idx * 3;
            out16BitRGB[outIdx + 0] = Dither::quantize16(rGamma, globalX, globalY, 0, enableDither);
            out16BitRGB[outIdx + 1] = Dither::quantize16(gGamma, globalX, globalY, 1, enableDither);
            out16BitRGB[outIdx + 2] = Dither::quantize16(bGamma, globalX, globalY, 2, enableDither);
        }
    }
}

bool ExportPipeline::processImage(RawDecoder& decoder,
                                  const DevelopmentParams& params,
                                  const ExportOptions& options,
                                  const std::string& outputPath,
                                  ProgressCallback progressCallback) {
    if (!decoder.isLoaded()) {
        std::cerr << "[ExportPipeline] Decoder has no image loaded." << std::endl;
        return false;
    }

    int32_t imgW = decoder.getWidth();
    int32_t imgH = decoder.getHeight();
    int32_t tileSize = options.tileSize > 0 ? options.tileSize : 2048;
    int32_t padding = options.tilePadding >= 0 ? options.tilePadding : 16;

    int32_t numTilesX = (imgW + tileSize - 1) / tileSize;
    int32_t numTilesY = (imgH + tileSize - 1) / tileSize;
    int32_t totalTiles = numTilesX * numTilesY;

    if (progressCallback) {
        progressCallback(0.0f, "Starting development pipeline...");
    }

    // Allocate output buffer for the final image
    bool is16Bit = (options.format == ExportFormat::TIFF16 || options.format == ExportFormat::LinearDNG);
    std::vector<uint8_t> finalRgb8;
    std::vector<uint16_t> finalRgb16;

    if (is16Bit) {
        finalRgb16.resize(static_cast<size_t>(imgW) * imgH * 3);
    } else {
        finalRgb8.resize(static_cast<size_t>(imgW) * imgH * 3);
    }

    std::vector<FloatRGBA> inPaddedTile;
    std::vector<FloatRGBA> outValidTile;
    std::vector<uint8_t> tileRgb8;
    std::vector<uint16_t> tileRgb16;

    int32_t processedTiles = 0;

    for (int32_t ty = 0; ty < numTilesY; ++ty) {
        for (int32_t tx = 0; tx < numTilesX; ++tx) {
            int32_t tileX = tx * tileSize;
            int32_t tileY = ty * tileSize;
            int32_t validW = std::min(tileSize, imgW - tileX);
            int32_t validH = std::min(tileSize, imgH - tileY);

            Rect paddedRect;
            bool ok = decoder.extractTile(tileX, tileY, validW, validH, padding, inPaddedTile, paddedRect);
            if (!ok) {
                std::cerr << "[ExportPipeline] Failed to extract tile at (" << tileX << ", " << tileY << ")" << std::endl;
                return false;
            }

            // Execute tile pipeline (OOM-free 32-bit linear processing)
            int32_t padLeft = tileX - paddedRect.x;
            int32_t padTop = tileY - paddedRect.y;
            processTileLinear(inPaddedTile, paddedRect.width, paddedRect.height, validW, validH, padding, params, outValidTile, padLeft, padTop, paddedRect.x, paddedRect.y, imgW, imgH);

            // Quantize with TPDF dithering and color space OETF
            // For Linear DNG, bypass OETF gamma to preserve true linear radiance.
            if (is16Bit) {
                ColorSpace actualCs = (options.format == ExportFormat::LinearDNG) ? ColorSpace::LinearSRGB : params.outputColorSpace;
                quantizeTo16Bit(outValidTile, validW, validH, tileX, tileY, actualCs, params.enableDithering, tileRgb16);
                // Copy tile rows to full image buffer
                for (int32_t r = 0; r < validH; ++r) {
                    size_t srcOffset = static_cast<size_t>(r) * validW * 3;
                    size_t dstOffset = (static_cast<size_t>(tileY + r) * imgW + tileX) * 3;
                    std::memcpy(&finalRgb16[dstOffset], &tileRgb16[srcOffset], validW * 3 * sizeof(uint16_t));
                }
            } else {
                quantizeTo8Bit(outValidTile, validW, validH, tileX, tileY, params.outputColorSpace, params.enableDithering, tileRgb8);
                // Copy tile rows to full image buffer
                for (int32_t r = 0; r < validH; ++r) {
                    size_t srcOffset = static_cast<size_t>(r) * validW * 3;
                    size_t dstOffset = (static_cast<size_t>(tileY + r) * imgW + tileX) * 3;
                    std::memcpy(&finalRgb8[dstOffset], &tileRgb8[srcOffset], validW * 3);
                }
            }

            processedTiles++;
            if (progressCallback) {
                float pct = (static_cast<float>(processedTiles) / totalTiles) * 100.0f;
                progressCallback(pct, "Processing tile " + std::to_string(processedTiles) + "/" + std::to_string(totalTiles));
            }
        }
    }

    // 1. Crop geometry handling
    const auto& geo = params.geometry;
    bool hasCrop = (geo.cropW > 0.0f && geo.cropH > 0.0f &&
                   (geo.cropX > 1e-4f || geo.cropY > 1e-4f || geo.cropW < 0.9999f || geo.cropH < 0.9999f));
    if (hasCrop) {
        int32_t cropX = std::clamp(static_cast<int32_t>(std::round(geo.cropX * imgW)), 0, imgW - 1);
        int32_t cropY = std::clamp(static_cast<int32_t>(std::round(geo.cropY * imgH)), 0, imgH - 1);
        int32_t cropW = std::clamp(static_cast<int32_t>(std::round(geo.cropW * imgW)), 1, imgW - cropX);
        int32_t cropH = std::clamp(static_cast<int32_t>(std::round(geo.cropH * imgH)), 1, imgH - cropY);

        if (is16Bit) {
            std::vector<uint16_t> cropped(static_cast<size_t>(cropW) * cropH * 3);
            for (int32_t y = 0; y < cropH; ++y) {
                const uint16_t* srcRow = &finalRgb16[(static_cast<size_t>(cropY + y) * imgW + cropX) * 3];
                uint16_t* dstRow = &cropped[static_cast<size_t>(y) * cropW * 3];
                std::memcpy(dstRow, srcRow, cropW * 3 * sizeof(uint16_t));
            }
            finalRgb16 = std::move(cropped);
        } else {
            std::vector<uint8_t> cropped(static_cast<size_t>(cropW) * cropH * 3);
            for (int32_t y = 0; y < cropH; ++y) {
                const uint8_t* srcRow = &finalRgb8[(static_cast<size_t>(cropY + y) * imgW + cropX) * 3];
                uint8_t* dstRow = &cropped[static_cast<size_t>(y) * cropW * 3];
                std::memcpy(dstRow, srcRow, cropW * 3);
            }
            finalRgb8 = std::move(cropped);
        }
        imgW = cropW;
        imgH = cropH;
    }

    // 2. Flip & Rotation geometry handling
    if (is16Bit) {
        applyFlip(finalRgb16, imgW, imgH, geo.flipHorizontal, geo.flipVertical);
        applyStepRotation(finalRgb16, imgW, imgH, geo.rotationSteps);
        applyFineRotation(finalRgb16, imgW, imgH, geo.rotationDegrees);
    } else {
        applyFlip(finalRgb8, imgW, imgH, geo.flipHorizontal, geo.flipVertical);
        applyStepRotation(finalRgb8, imgW, imgH, geo.rotationSteps);
        applyFineRotation(finalRgb8, imgW, imgH, geo.rotationDegrees);
    }

    // 3. High-quality Bilinear Downscaling (e.g. 2048px for SNS)
    if (options.maxLongEdge > 0 && std::max(imgW, imgH) > options.maxLongEdge) {
        float scale = static_cast<float>(options.maxLongEdge) / std::max(imgW, imgH);
        int32_t newW = std::max(1, static_cast<int32_t>(std::round(imgW * scale)));
        int32_t newH = std::max(1, static_cast<int32_t>(std::round(imgH * scale)));

        if (is16Bit) {
            std::vector<uint16_t> resized(static_cast<size_t>(newW) * newH * 3);
            #pragma omp parallel for schedule(static)
            for (int32_t ny = 0; ny < newH; ++ny) {
                float sy = (ny + 0.5f) / scale - 0.5f;
                int32_t y0 = std::clamp(static_cast<int32_t>(std::floor(sy)), 0, imgH - 1);
                int32_t y1 = std::clamp(y0 + 1, 0, imgH - 1);
                float wy = sy - y0;

                for (int32_t nx = 0; nx < newW; ++nx) {
                    float sx = (nx + 0.5f) / scale - 0.5f;
                    int32_t x0 = std::clamp(static_cast<int32_t>(std::floor(sx)), 0, imgW - 1);
                    int32_t x1 = std::clamp(x0 + 1, 0, imgW - 1);
                    float wx = sx - x0;

                    for (int c = 0; c < 3; ++c) {
                        float v00 = finalRgb16[(static_cast<size_t>(y0) * imgW + x0) * 3 + c];
                        float v10 = finalRgb16[(static_cast<size_t>(y0) * imgW + x1) * 3 + c];
                        float v01 = finalRgb16[(static_cast<size_t>(y1) * imgW + x0) * 3 + c];
                        float v11 = finalRgb16[(static_cast<size_t>(y1) * imgW + x1) * 3 + c];

                        float val = (v00 * (1.0f - wx) + v10 * wx) * (1.0f - wy) +
                                    (v01 * (1.0f - wx) + v11 * wx) * wy;
                        resized[(static_cast<size_t>(ny) * newW + nx) * 3 + c] = static_cast<uint16_t>(std::clamp(val + 0.5f, 0.0f, 65535.0f));
                    }
                }
            }
            finalRgb16 = std::move(resized);
        } else {
            std::vector<uint8_t> resized(static_cast<size_t>(newW) * newH * 3);
            #pragma omp parallel for schedule(static)
            for (int32_t ny = 0; ny < newH; ++ny) {
                float sy = (ny + 0.5f) / scale - 0.5f;
                int32_t y0 = std::clamp(static_cast<int32_t>(std::floor(sy)), 0, imgH - 1);
                int32_t y1 = std::clamp(y0 + 1, 0, imgH - 1);
                float wy = sy - y0;

                for (int32_t nx = 0; nx < newW; ++nx) {
                    float sx = (nx + 0.5f) / scale - 0.5f;
                    int32_t x0 = std::clamp(static_cast<int32_t>(std::floor(sx)), 0, imgW - 1);
                    int32_t x1 = std::clamp(x0 + 1, 0, imgW - 1);
                    float wx = sx - x0;

                    for (int c = 0; c < 3; ++c) {
                        float v00 = finalRgb8[(static_cast<size_t>(y0) * imgW + x0) * 3 + c];
                        float v10 = finalRgb8[(static_cast<size_t>(y0) * imgW + x1) * 3 + c];
                        float v01 = finalRgb8[(static_cast<size_t>(y1) * imgW + x0) * 3 + c];
                        float v11 = finalRgb8[(static_cast<size_t>(y1) * imgW + x1) * 3 + c];

                        float val = (v00 * (1.0f - wx) + v10 * wx) * (1.0f - wy) +
                                    (v01 * (1.0f - wx) + v11 * wx) * wy;
                        resized[(static_cast<size_t>(ny) * newW + nx) * 3 + c] = static_cast<uint8_t>(std::clamp(val + 0.5f, 0.0f, 255.0f));
                    }
                }
            }
            finalRgb8 = std::move(resized);
        }
        imgW = newW;
        imgH = newH;
    }

    // 3. Apply watermark if requested for 8-bit outputs
    if (options.enableWatermark && !options.watermarkText.empty() && !is16Bit) {
        ImageWriter::renderWatermark8(finalRgb8, imgW, imgH, options.watermarkText, true);
    }

    // 4. Write final output file with Exif metadata
    const ExifMetadata* metaPtr = options.embedExif ? &decoder.getMetadata() : nullptr;
    bool writeOk = false;

    if (options.format == ExportFormat::JPEG) {
        writeOk = ImageWriter::writeJPEG(outputPath, finalRgb8.data(), imgW, imgH, options.jpegQuality, options.chromaSubsampling, metaPtr);
    } else if (options.format == ExportFormat::LinearDNG) {
        writeOk = ImageWriter::writeLinearDNG(outputPath, finalRgb16.data(), imgW, imgH, metaPtr);
    } else if (options.format == ExportFormat::TIFF16) {
        writeOk = ImageWriter::writeTIFF16(outputPath, finalRgb16.data(), imgW, imgH, metaPtr);
    } else if (options.format == ExportFormat::TIFF8) {
        writeOk = ImageWriter::writeTIFF8(outputPath, finalRgb8.data(), imgW, imgH, metaPtr);
    } else if (options.format == ExportFormat::WebP) {
        writeOk = ImageWriter::writeWebP(outputPath, finalRgb8.data(), imgW, imgH, options.jpegQuality, metaPtr);
    } else {
        // Default to JPEG
        writeOk = ImageWriter::writeJPEG(outputPath, finalRgb8.data(), imgW, imgH, options.jpegQuality, options.chromaSubsampling, metaPtr);
    }

    if (progressCallback && writeOk) {
        progressCallback(100.0f, "Export completed successfully.");
    }

    return writeOk;
}

} // namespace lightrumor
