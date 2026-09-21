#include "light_rumor/DenoiseEngine.h"
#include <iostream>
#include <vector>
#include <cmath>
#include <algorithm>
#include <cstring>
#include <array>

namespace lightrumor {

namespace {

static const auto s_expTable = []() {
    std::array<float, 512> table;
    for (size_t i = 0; i < 512; ++i) {
        float v = static_cast<float>(i) * (16.0f / 512.0f);
        table[i] = std::exp(-v);
    }
    return table;
}();

inline float fastExp(float x) {
    if (x <= 0.0f) return 1.0f;
    if (x >= 16.0f) return 0.0f;
    size_t idx = static_cast<size_t>(x * (512.0f / 16.0f));
    return s_expTable[std::min(idx, size_t(511))];
}

} // namespace

struct DenoiseEngine::Impl {
    bool isGpuActive = false;
};

DenoiseEngine::DenoiseEngine() : m_impl(std::make_unique<Impl>()) {}
DenoiseEngine::~DenoiseEngine() = default;

bool DenoiseEngine::init() {
    m_impl->isGpuActive = false;
    return true;
}

void DenoiseEngine::applyLuminanceNR(const FloatRGBA* src, FloatRGBA* dst,
                                     int32_t width, int32_t height,
                                     float strength, float detail, float contrast) {
    if (strength <= 0.0f) {
        std::memcpy(dst, src, sizeof(FloatRGBA) * width * height);
        return;
    }

    float normStrength = strength * 0.01f;
    float normDetail = detail * 0.01f;
    float normContrast = contrast * 0.01f;

    float sigmaR = 0.02f + normStrength * 0.20f * (1.0f - normDetail * 0.6f);
    float invR2 = 1.0f / (2.0f * sigmaR * sigmaR);

    // Pre-calculate luminance array
    std::vector<float> luma(static_cast<size_t>(width) * height);
    for (int32_t i = 0; i < width * height; ++i) {
        luma[i] = 0.2126f * src[i].r + 0.7152f * src[i].g + 0.0722f * src[i].b;
    }

    #pragma omp parallel for schedule(static)
    for (int32_t y = 0; y < height; ++y) {
        int32_t yPrev = std::clamp(y - 1, 0, height - 1);
        int32_t yNext = std::clamp(y + 1, 0, height - 1);
        const float* rowPrev = &luma[yPrev * width];
        const float* rowCurr = &luma[y * width];
        const float* rowNext = &luma[yNext * width];

        for (int32_t x = 0; x < width; ++x) {
            int32_t idx = y * width + x;
            float centerLum = rowCurr[x];

            float lumSum = 0.0f;
            float weightSum = 0.0f;

            int32_t xPrev = std::clamp(x - 1, 0, width - 1);
            int32_t xNext = std::clamp(x + 1, 0, width - 1);

            // 3x3 unrolled sampling
            const float* rows[3] = { rowPrev, rowCurr, rowNext };
            int32_t cols[3] = { xPrev, x, xNext };

            for (int r = 0; r < 3; ++r) {
                float dy2 = static_cast<float>((r - 1) * (r - 1));
                const float* rPtr = rows[r];
                for (int c = 0; c < 3; ++c) {
                    float nLum = rPtr[cols[c]];
                    float dist2 = dy2 + static_cast<float>((c - 1) * (c - 1));
                    float lumDiff = nLum - centerLum;
                    float w = fastExp(dist2 * 0.5f + (lumDiff * lumDiff) * invR2);
                    lumSum += nLum * w;
                    weightSum += w;
                }
            }

            float filteredLum = (weightSum > 0.0f) ? (lumSum / weightSum) : centerLum;
            float blendedLum = centerLum * (1.0f - normStrength) + filteredLum * normStrength;

            if (normContrast > 0.0f) {
                float shadowProtection = std::pow(1.0f - std::clamp(centerLum, 0.0f, 1.0f), 2.0f);
                blendedLum = blendedLum * (1.0f - shadowProtection * normContrast * 0.5f) +
                             centerLum * (shadowProtection * normContrast * 0.5f);
            }

            float lumRatio = (centerLum > 1e-5f) ? (blendedLum / centerLum) : 1.0f;
            dst[idx].r = std::max(0.0f, src[idx].r * lumRatio);
            dst[idx].g = std::max(0.0f, src[idx].g * lumRatio);
            dst[idx].b = std::max(0.0f, src[idx].b * lumRatio);
            dst[idx].a = src[idx].a;
        }
    }
}

void DenoiseEngine::applyChromaNR(const FloatRGBA* src, FloatRGBA* dst,
                                  int32_t width, int32_t height,
                                  float strength, float detail, float smoothness) {
    (void)detail;
    if (strength <= 0.0f) {
        std::memcpy(dst, src, sizeof(FloatRGBA) * width * height);
        return;
    }

    float normStrength = strength * 0.01f;
    float normSmooth = smoothness * 0.01f;
    float radius = 1.0f + normSmooth * 2.0f;
    float invRadius2 = 1.0f / (2.0f * radius * radius);

    std::vector<float> luma(static_cast<size_t>(width) * height);
    std::vector<float> chromaR(static_cast<size_t>(width) * height);
    std::vector<float> chromaB(static_cast<size_t>(width) * height);

    #pragma omp parallel for schedule(static)
    for (int32_t i = 0; i < width * height; ++i) {
        float L = 0.2126f * src[i].r + 0.7152f * src[i].g + 0.0722f * src[i].b;
        luma[i] = L;
        chromaR[i] = src[i].r - L;
        chromaB[i] = src[i].b - L;
    }

    std::vector<float> tempCr = chromaR;
    std::vector<float> tempCb = chromaB;
    std::vector<float> outCr = chromaR;
    std::vector<float> outCb = chromaB;

    int passes = (normStrength > 0.5f) ? 2 : 1;
    for (int p = 0; p < passes; ++p) {
        #pragma omp parallel for schedule(static)
        for (int32_t y = 0; y < height; ++y) {
            int32_t y0 = std::clamp(y - 1, 0, height - 1);
            int32_t y1 = y;
            int32_t y2 = std::clamp(y + 1, 0, height - 1);
            const float* rowCr0 = &tempCr[y0 * width];
            const float* rowCr1 = &tempCr[y1 * width];
            const float* rowCr2 = &tempCr[y2 * width];
            const float* rowCb0 = &tempCb[y0 * width];
            const float* rowCb1 = &tempCb[y1 * width];
            const float* rowCb2 = &tempCb[y2 * width];

            for (int32_t x = 0; x < width; ++x) {
                int32_t idx = y * width + x;
                int32_t x0 = std::clamp(x - 1, 0, width - 1);
                int32_t x1 = x;
                int32_t x2 = std::clamp(x + 1, 0, width - 1);

                const float* crRows[3] = { rowCr0, rowCr1, rowCr2 };
                const float* cbRows[3] = { rowCb0, rowCb1, rowCb2 };
                int32_t cols[3] = { x0, x1, x2 };

                float sumR = 0.0f;
                float sumB = 0.0f;
                float weightSum = 0.0f;

                for (int r = 0; r < 3; ++r) {
                    float dy2 = static_cast<float>((r - 1) * (r - 1));
                    const float* crPtr = crRows[r];
                    const float* cbPtr = cbRows[r];
                    for (int c = 0; c < 3; ++c) {
                        float dist2 = dy2 + static_cast<float>((c - 1) * (c - 1));
                        float w = fastExp(dist2 * invRadius2);
                        int32_t cx = cols[c];
                        sumR += crPtr[cx] * w;
                        sumB += cbPtr[cx] * w;
                        weightSum += w;
                    }
                }

                float filteredCr = (weightSum > 0.0f) ? (sumR / weightSum) : tempCr[idx];
                float filteredCb = (weightSum > 0.0f) ? (sumB / weightSum) : tempCb[idx];

                outCr[idx] = tempCr[idx] * (1.0f - normStrength) + filteredCr * normStrength;
                outCb[idx] = tempCb[idx] * (1.0f - normStrength) + filteredCb * normStrength;
            }
        }
        tempCr = outCr;
        tempCb = outCb;
    }

    #pragma omp parallel for schedule(static)
    for (int32_t i = 0; i < width * height; ++i) {
        float L = luma[i];
        float finalR = std::max(0.0f, L + outCr[i]);
        float finalB = std::max(0.0f, L + outCb[i]);
        float finalG = std::max(0.0f, (L - 0.2126f * finalR - 0.0722f * finalB) / 0.7152f);
        dst[i] = FloatRGBA(finalR, finalG, finalB, src[i].a);
    }
}

void DenoiseEngine::applySharpening(const FloatRGBA* src, FloatRGBA* dst,
                                    int32_t width, int32_t height,
                                    float amount, float radius, float detail,
                                    float masking, bool previewMask) {
    (void)radius;
    (void)detail;
    if (amount <= 0.0f && !previewMask) {
        std::memcpy(dst, src, sizeof(FloatRGBA) * width * height);
        return;
    }

    float normAmount = amount * 0.01f;
    float normMasking = masking * 0.01f;
    float maskThreshold = normMasking * 0.12f;

    std::vector<float> luma(static_cast<size_t>(width) * height);
    #pragma omp parallel for schedule(static)
    for (int32_t i = 0; i < width * height; ++i) {
        luma[i] = 0.2126f * src[i].r + 0.7152f * src[i].g + 0.0722f * src[i].b;
    }

    #pragma omp parallel for schedule(static)
    for (int32_t y = 0; y < height; ++y) {
        int32_t yPrev = std::clamp(y - 1, 0, height - 1);
        int32_t yNext = std::clamp(y + 1, 0, height - 1);
        const float* rowPrev = &luma[yPrev * width];
        const float* rowCurr = &luma[y * width];
        const float* rowNext = &luma[yNext * width];

        for (int32_t x = 0; x < width; ++x) {
            int32_t xPrev = std::clamp(x - 1, 0, width - 1);
            int32_t xNext = std::clamp(x + 1, 0, width - 1);

            int32_t idx = y * width + x;
            float cL = rowCurr[x];
            float lL = rowCurr[xPrev];
            float rL = rowCurr[xNext];
            float tL = rowPrev[x];
            float bL = rowNext[x];

            // Gradient magnitude for edge masking
            float gradMag = std::abs(rL - lL) + std::abs(bL - tL);

            // Compute edge weight: 0.0 for flat areas (sky/skin), up to 1.0 for real edges
            float edgeWeight = 1.0f;
            if (maskThreshold > 1e-5f) {
                edgeWeight = std::clamp((gradMag - maskThreshold) / std::max(maskThreshold * 0.5f, 0.005f), 0.0f, 1.0f);
            }

            // Preview mode: render pure B&W edge mask
            if (previewMask) {
                dst[idx] = FloatRGBA(edgeWeight, edgeWeight, edgeWeight, 1.0f);
                continue;
            }

            // Unsharp mask Laplacian
            float laplacian = (lL + rL + tL + bL) * 0.25f - cL;
            float sharpBoost = -laplacian * normAmount * 1.5f * edgeWeight;

            float newLum = std::max(0.0f, cL + sharpBoost);
            float ratio = (cL > 1e-5f) ? (newLum / cL) : 1.0f;

            dst[idx] = FloatRGBA(
                std::max(0.0f, src[idx].r * ratio),
                std::max(0.0f, src[idx].g * ratio),
                std::max(0.0f, src[idx].b * ratio),
                src[idx].a
            );
        }
    }
}

bool DenoiseEngine::processTile(const std::vector<FloatRGBA>& inPaddedTile,
                                int32_t paddedW, int32_t paddedH,
                                int32_t validW, int32_t validH,
                                int32_t padding,
                                const DevelopmentParams& params,
                                std::vector<FloatRGBA>& outValidTile,
                                int32_t padLeft,
                                int32_t padTop) {
    outValidTile.resize(static_cast<size_t>(validW) * validH);
    size_t totalPadded = static_cast<size_t>(paddedW) * paddedH;

    int32_t actualPadX = (padLeft >= 0) ? padLeft : ((paddedW > validW) ? padding : 0);
    int32_t actualPadY = (padTop >= 0) ? padTop : ((paddedH > validH) ? padding : 0);
    actualPadX = std::clamp(actualPadX, 0, std::max(0, paddedW - validW));
    actualPadY = std::clamp(actualPadY, 0, std::max(0, paddedH - validH));

    bool needLuma = (params.luminanceNR > 0.0f);
    bool needChroma = (params.chromaNR > 0.0f);
    bool needSharp = (params.sharpeningAmount > 0.0f || params.sharpeningPreviewMask);

    if (!needLuma && !needChroma && !needSharp) {
        for (int32_t vy = 0; vy < validH; ++vy) {
            int32_t py = vy + actualPadY;
            const FloatRGBA* srcRow = &inPaddedTile[static_cast<size_t>(py) * paddedW + actualPadX];
            FloatRGBA* dstRow = &outValidTile[static_cast<size_t>(vy) * validW];
            std::memcpy(dstRow, srcRow, sizeof(FloatRGBA) * validW);
        }
        return true;
    }

    std::vector<FloatRGBA> stage1(totalPadded);
    std::vector<FloatRGBA> stage2;
    std::vector<FloatRGBA> stage3;
    const FloatRGBA* currentSrc = inPaddedTile.data();

    // 1. Luminance NR
    if (needLuma) {
        applyLuminanceNR(currentSrc, stage1.data(), paddedW, paddedH,
                         params.luminanceNR, params.luminanceNRDetail, params.luminanceNRContrast);
        currentSrc = stage1.data();
    }

    // 2. Chroma NR (False-color removal)
    if (needChroma) {
        stage2.resize(totalPadded);
        applyChromaNR(currentSrc, stage2.data(), paddedW, paddedH,
                      params.chromaNR, params.chromaNRDetail, params.chromaNRSmoothness);
        currentSrc = stage2.data();
    }

    // 3. Sharpening & Edge Masking
    if (needSharp) {
        stage3.resize(totalPadded);
        applySharpening(currentSrc, stage3.data(), paddedW, paddedH,
                        params.sharpeningAmount, params.sharpeningRadius, params.sharpeningDetail,
                        params.sharpeningMasking, params.sharpeningPreviewMask);
        currentSrc = stage3.data();
    }

    // Extract valid inner tile (discard padding)
    for (int32_t vy = 0; vy < validH; ++vy) {
        int32_t py = vy + actualPadY;
        const FloatRGBA* srcRow = &currentSrc[static_cast<size_t>(py) * paddedW + actualPadX];
        FloatRGBA* dstRow = &outValidTile[static_cast<size_t>(vy) * validW];
        std::memcpy(dstRow, srcRow, sizeof(FloatRGBA) * validW);
    }

    return true;
}

} // namespace lightrumor
