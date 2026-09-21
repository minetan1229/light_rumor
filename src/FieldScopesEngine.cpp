#include "light_rumor/FieldScopesEngine.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <iostream>

namespace apex {

FieldScopesEngine::FieldScopesEngine() = default;
FieldScopesEngine::~FieldScopesEngine() = default;

FloatRGBA FieldScopesEngine::mapIREToFalseColor(float ire) {
    if (ire < 2.5f) {
        return FloatRGBA(0.35f, 0.0f, 0.55f, 1.0f); // Purple: severe underexposure (<2.5 IRE)
    } else if (ire < 20.0f) {
        return FloatRGBA(0.0f, 0.20f, 0.80f, 1.0f); // Blue: deep shadows (2.5 - 20 IRE)
    } else if (ire < 38.0f) {
        return FloatRGBA(0.0f, 0.55f, 0.65f, 1.0f); // Cyan: low midtones (20 - 38 IRE)
    } else if (ire <= 44.0f) {
        return FloatRGBA(0.0f, 0.85f, 0.15f, 1.0f); // Green: 18% Neutral Gray Card (38 - 44 IRE)
    } else if (ire < 52.0f) {
        return FloatRGBA(0.45f, 0.45f, 0.45f, 1.0f); // Mid-grey (44 - 52 IRE)
    } else if (ire <= 58.0f) {
        return FloatRGBA(1.0f, 0.42f, 0.71f, 1.0f);  // Pink: optimal skin highlight (52 - 58 IRE)
    } else if (ire < 70.0f) {
        return FloatRGBA(0.65f, 0.65f, 0.45f, 1.0f); // High midtones (58 - 70 IRE)
    } else if (ire < 85.0f) {
        return FloatRGBA(0.95f, 0.95f, 0.15f, 1.0f); // Straw / Yellow: highlights (70 - 85 IRE)
    } else if (ire < 98.0f) {
        return FloatRGBA(1.0f, 0.50f, 0.0f, 1.0f);  // Orange: near-clipping (85 - 98 IRE)
    } else {
        return FloatRGBA(1.0f, 0.0f, 0.0f, 1.0f);   // Red: blown-out white clipping (>= 98 IRE)
    }
}

void FieldScopesEngine::generateFalseColor(const FloatRGBA* inPixels,
                                           int32_t width, int32_t height,
                                           std::vector<FloatRGBA>& outPixels) {
    if (!inPixels || width <= 0 || height <= 0) return;
    const size_t total = static_cast<size_t>(width) * height;
    outPixels.resize(total);

    #pragma omp parallel for
    for (int64_t i = 0; i < static_cast<int64_t>(total); ++i) {
        float ire = calculateIRE(inPixels[i].r, inPixels[i].g, inPixels[i].b);
        outPixels[i] = mapIREToFalseColor(ire);
    }
}

void FieldScopesEngine::generateZebra(const FloatRGBA* inPixels,
                                      int32_t width, int32_t height,
                                      float thresholdIRE,
                                      float animPhase,
                                      std::vector<FloatRGBA>& outPixels) {
    if (!inPixels || width <= 0 || height <= 0) return;
    const size_t total = static_cast<size_t>(width) * height;
    outPixels.resize(total);

    #pragma omp parallel for
    for (int32_t y = 0; y < height; ++y) {
        for (int32_t x = 0; x < width; ++x) {
            size_t idx = static_cast<size_t>(y) * width + x;
            float ire = calculateIRE(inPixels[idx].r, inPixels[idx].g, inPixels[idx].b);

            if (ire >= thresholdIRE) {
                // 45-degree diagonal hazard stripes with phase shift
                int32_t stripe = (static_cast<int32_t>(x + y + animPhase)) % 16;
                if (stripe < 8) {
                    outPixels[idx] = FloatRGBA(0.0f, 0.0f, 0.0f, 1.0f); // Black stripe
                } else {
                    outPixels[idx] = FloatRGBA(1.0f, 1.0f, 1.0f, 1.0f); // White stripe
                }
            } else {
                outPixels[idx] = inPixels[idx];
            }
        }
    }
}

void FieldScopesEngine::generateFocusPeaking(const FloatRGBA* inPixels,
                                            int32_t width, int32_t height,
                                            PeakingColor color,
                                            float threshold,
                                            std::vector<FloatRGBA>& outPixels) {
    if (!inPixels || width <= 0 || height <= 0) return;
    const size_t total = static_cast<size_t>(width) * height;
    outPixels.resize(total);

    FloatRGBA peakRgba(1.0f, 0.0f, 0.2f, 1.0f); // Red
    if (color == PeakingColor::Yellow) peakRgba = FloatRGBA(1.0f, 1.0f, 0.0f, 1.0f);
    else if (color == PeakingColor::Blue) peakRgba = FloatRGBA(0.0f, 0.85f, 1.0f, 1.0f);
    else if (color == PeakingColor::White) peakRgba = FloatRGBA(1.0f, 1.0f, 1.0f, 1.0f);

    auto getLuma = [&](int32_t x, int32_t y) -> float {
        x = std::clamp(x, 0, width - 1);
        y = std::clamp(y, 0, height - 1);
        const auto& p = inPixels[y * width + x];
        return 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    };

    #pragma omp parallel for
    for (int32_t y = 0; y < height; ++y) {
        for (int32_t x = 0; x < width; ++x) {
            size_t idx = static_cast<size_t>(y) * width + x;

            // 3x3 Sobel edge filter
            float p00 = getLuma(x - 1, y - 1);
            float p10 = getLuma(x,     y - 1);
            float p20 = getLuma(x + 1, y - 1);

            float p01 = getLuma(x - 1, y);
            float p21 = getLuma(x + 1, y);

            float p02 = getLuma(x - 1, y + 1);
            float p12 = getLuma(x,     y + 1);
            float p22 = getLuma(x + 1, y + 1);

            float gx = (p20 + 2.0f * p21 + p22) - (p00 + 2.0f * p01 + p02);
            float gy = (p02 + 2.0f * p12 + p22) - (p00 + 2.0f * p10 + p20);
            float edgeMag = std::sqrt(gx * gx + gy * gy);

            if (edgeMag > threshold) {
                // Focus peak highlight
                outPixels[idx] = FloatRGBA(
                    inPixels[idx].r * 0.15f + peakRgba.r * 0.85f,
                    inPixels[idx].g * 0.15f + peakRgba.g * 0.85f,
                    inPixels[idx].b * 0.15f + peakRgba.b * 0.85f,
                    1.0f
                );
            } else {
                // Subtle monochrome tint background for enhanced edge contrast
                float l = getLuma(x, y);
                outPixels[idx] = FloatRGBA(
                    inPixels[idx].r * 0.5f + l * 0.5f,
                    inPixels[idx].g * 0.5f + l * 0.5f,
                    inPixels[idx].b * 0.5f + l * 0.5f,
                    1.0f
                );
            }
        }
    }
}

void FieldScopesEngine::generateVectorscope(const FloatRGBA* inPixels,
                                           int32_t width, int32_t height,
                                           int32_t scopeDim,
                                           float gain,
                                           std::vector<uint32_t>& outScopeRgba) {
    if (!inPixels || width <= 0 || height <= 0 || scopeDim <= 0) return;
    size_t totalBins = static_cast<size_t>(scopeDim) * scopeDim;
    outScopeRgba.assign(totalBins, 0xFF0A0A0C); // Obsidian background

    std::vector<uint32_t> accum(totalBins, 0);

    // Step through pixels
    int stepX = std::max(1, width / 512);
    int stepY = std::max(1, height / 512);

    for (int y = 0; y < height; y += stepY) {
        for (int x = 0; x < width; x += stepX) {
            const auto& px = inPixels[y * width + x];

            // Cb = -0.168736*R - 0.331264*G + 0.5*B
            // Cr =  0.5*R - 0.418688*G - 0.081312*B
            float cb = -0.168736f * px.r - 0.331264f * px.g + 0.5f * px.b;
            float cr =  0.500000f * px.r - 0.418688f * px.g - 0.081312f * px.b;

            // Map Cb (horizontal, left - right) and Cr (vertical, top - bottom)
            int vx = static_cast<int>((cb * gain + 0.5f) * scopeDim);
            int vy = static_cast<int>((-cr * gain + 0.5f) * scopeDim);

            if (vx >= 0 && vx < scopeDim && vy >= 0 && vy < scopeDim) {
                accum[vy * scopeDim + vx]++;
            }
        }
    }

    // Find max hit count
    uint32_t maxHit = 1;
    for (uint32_t c : accum) {
        maxHit = std::max(maxHit, c);
    }

    float logGain = 255.0f / std::pow(static_cast<float>(maxHit), 0.5f);

    // Render cinema vectorscope trace
    for (int y = 0; y < scopeDim; ++y) {
        for (int x = 0; x < scopeDim; ++x) {
            size_t idx = static_cast<size_t>(y) * scopeDim + x;
            uint32_t count = accum[idx];
            if (count > 0) {
                int val = std::clamp(static_cast<int>(std::pow(count, 0.5f) * logGain + 40.0f), 0, 255);
                // Vectorscope phosphor trace color: Cyan-Green glow
                uint8_t r = static_cast<uint8_t>(val * 0.2f);
                uint8_t g = static_cast<uint8_t>(val);
                uint8_t b = static_cast<uint8_t>(val * 0.7f);
                outScopeRgba[idx] = 0xFF000000 | (b << 16) | (g << 8) | r;
            }
        }
    }

    drawVectorscopeGraticule(outScopeRgba, scopeDim);
}

void FieldScopesEngine::drawVectorscopeGraticule(std::vector<uint32_t>& scopePixels, int32_t dim) {
    if (dim <= 0 || scopePixels.size() < static_cast<size_t>(dim) * dim) return;

    float center = dim * 0.5f;
    float maxRadius = dim * 0.45f;
    uint32_t gridColor = 0xFF35393F; // Subtle titanium grid
    uint32_t targetColor = 0xFF8E8E93; // SMPTE 75% target boxes
    uint32_t skinColor = 0xFFFF9500; // I-line / Skin Tone indicator

    auto setPixelIfDark = [&](int x, int y, uint32_t color) {
        if (x >= 0 && x < dim && y >= 0 && y < dim) {
            size_t idx = static_cast<size_t>(y) * dim + x;
            uint32_t c = scopePixels[idx];
            if ((c & 0x00FFFFFF) < 0x00404040) {
                scopePixels[idx] = color;
            }
        }
    };

    // 1. Center Crosshairs
    int cI = static_cast<int>(center);
    for (int i = 0; i < dim; ++i) {
        if (i % 4 != 0) {
            setPixelIfDark(i, cI, gridColor);
            setPixelIfDark(cI, i, gridColor);
        }
    }

    // 2. Circular Rings (100% and 75% saturation boundary)
    for (int deg = 0; deg < 360; ++deg) {
        float rad = deg * 3.14159265f / 180.0f;
        int x100 = static_cast<int>(center + maxRadius * std::cos(rad));
        int y100 = static_cast<int>(center + maxRadius * std::sin(rad));
        setPixelIfDark(x100, y100, gridColor);

        int x75 = static_cast<int>(center + (maxRadius * 0.75f) * std::cos(rad));
        int y75 = static_cast<int>(center + (maxRadius * 0.75f) * std::sin(rad));
        if (deg % 2 == 0) {
            setPixelIfDark(x75, y75, gridColor);
        }
    }

    // 3. Skin Tone / I-Line (Angle ~ 123° / 57° from horizontal)
    // Standard cinema skin tone line in Cb-Cr space: Cb < 0, Cr > 0 (top-left)
    float skinAngle = 123.0f * 3.14159265f / 180.0f;
    for (float r = 10.0f; r < maxRadius; r += 1.0f) {
        int sx = static_cast<int>(center - r * std::cos(skinAngle - 3.14159265f / 2.0f));
        int sy = static_cast<int>(center - r * std::sin(skinAngle - 3.14159265f / 2.0f));
        if (static_cast<int>(r) % 4 != 0) {
            setPixelIfDark(sx, sy, skinColor);
        }
    }

    // 4. SMPTE 75% Target Boxes for R, Mg, B, Cy, G, Yl
    struct Target {
        float r, g, b;
    };
    Target targets[6] = {
        {0.75f, 0.00f, 0.00f}, // Red
        {0.75f, 0.00f, 0.75f}, // Magenta
        {0.00f, 0.00f, 0.75f}, // Blue
        {0.00f, 0.75f, 0.75f}, // Cyan
        {0.00f, 0.75f, 0.00f}, // Green
        {0.75f, 0.75f, 0.00f}  // Yellow
    };

    for (const auto& t : targets) {
        float cb = -0.168736f * t.r - 0.331264f * t.g + 0.5f * t.b;
        float cr =  0.500000f * t.r - 0.418688f * t.g - 0.081312f * t.b;

        int tx = static_cast<int>((cb * 2.0f + 0.5f) * dim);
        int ty = static_cast<int>((-cr * 2.0f + 0.5f) * dim);

        // Draw 5x5 square
        for (int dy = -2; dy <= 2; ++dy) {
            for (int dx = -2; dx <= 2; ++dx) {
                if (std::abs(dx) == 2 || std::abs(dy) == 2) {
                    setPixelIfDark(tx + dx, ty + dy, targetColor);
                }
            }
        }
    }
}

} // namespace apex
