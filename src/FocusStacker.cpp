#include "light_rumor/FocusStacker.h"
#include <cmath>
#include <algorithm>
#include <numeric>
#include <iostream>
#include <vector>
#include <omp.h>

namespace lightrumor {

FocusStacker::FocusStacker() = default;
FocusStacker::~FocusStacker() = default;

void FocusStacker::computeSharpnessMap(const std::vector<FloatRGBA>& frame,
                                      int32_t width, int32_t height,
                                      int32_t windowRadius,
                                      std::vector<float>& outSharpness) {
    if (frame.empty() || width <= 0 || height <= 0) return;
    const size_t total = static_cast<size_t>(width) * height;
    outSharpness.assign(total, 0.0f);

    std::vector<float> ml(total, 0.0f);

    auto getLuma = [&](int32_t x, int32_t y) -> float {
        x = std::clamp(x, 0, width - 1);
        y = std::clamp(y, 0, height - 1);
        const auto& p = frame[y * width + x];
        return 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    };

    // 1. Calculate Modified Laplacian (ML)
    #pragma omp parallel for
    for (int32_t y = 0; y < height; ++y) {
        for (int32_t x = 0; x < width; ++x) {
            float c = getLuma(x, y);
            float dx = std::abs(2.0f * c - getLuma(x - 1, y) - getLuma(x + 1, y));
            float dy = std::abs(2.0f * c - getLuma(x, y - 1) - getLuma(x, y + 1));
            ml[y * width + x] = dx + dy;
        }
    }

    // 2. Sum Modified Laplacian (SML) over local window
    int r = std::max(1, windowRadius);
    #pragma omp parallel for
    for (int32_t y = 0; y < height; ++y) {
        for (int32_t x = 0; x < width; ++x) {
            float sum = 0.0f;
            for (int dy = -r; dy <= r; ++dy) {
                int py = std::clamp(y + dy, 0, height - 1);
                for (int dx = -r; dx <= r; ++dx) {
                    int px = std::clamp(x + dx, 0, width - 1);
                    sum += ml[py * width + px];
                }
            }
            outSharpness[y * width + x] = sum;
        }
    }
}

bool FocusStacker::stackFocus(const std::vector<std::vector<FloatRGBA>>& frames,
                              int32_t width, int32_t height,
                              const FocusStackParams& params,
                              std::vector<FloatRGBA>& outPanFocus) {
    if (frames.empty() || width <= 0 || height <= 0) return false;
    const size_t numFrames = frames.size();
    if (numFrames == 1) {
        outPanFocus = frames[0];
        return true;
    }

    const size_t total = static_cast<size_t>(width) * height;
    for (size_t i = 0; i < numFrames; ++i) {
        if (frames[i].size() < total) return false;
    }
    outPanFocus.assign(total, FloatRGBA(0.0f, 0.0f, 0.0f, 1.0f));

    // 1. Compute sharpness maps for all frames
    std::vector<std::vector<float>> sharpnessMaps(numFrames);
    for (size_t i = 0; i < numFrames; ++i) {
        computeSharpnessMap(frames[i], width, height, params.featherRadius, sharpnessMaps[i]);
    }

    // 2. Compute exponential weights with soft normalization
    float expP = std::clamp(params.sharpnessExponent, 1.0f, 8.0f);
    std::vector<std::vector<float>> weights(numFrames, std::vector<float>(total, 0.0f));

    #pragma omp parallel for
    for (int64_t idx = 0; idx < static_cast<int64_t>(total); ++idx) {
        float sumWeight = 0.0f;
        for (size_t i = 0; i < numFrames; ++i) {
            float s = std::max(sharpnessMaps[i][idx], 1e-6f);
            float w = std::pow(s, expP);
            weights[i][idx] = w;
            sumWeight += w;
        }

        if (sumWeight > 0.0f) {
            float invSum = 1.0f / sumWeight;
            for (size_t i = 0; i < numFrames; ++i) {
                weights[i][idx] *= invSum;
            }
        } else {
            float uniformWeight = 1.0f / static_cast<float>(numFrames);
            for (size_t i = 0; i < numFrames; ++i) {
                weights[i][idx] = uniformWeight;
            }
        }
    }

    // 3. Smooth blend weights if feathering is requested
    if (params.featherRadius > 1) {
        int fr = params.featherRadius;
        for (size_t i = 0; i < numFrames; ++i) {
            std::vector<float> smoothed(total, 0.0f);
            #pragma omp parallel for
            for (int32_t y = 0; y < height; ++y) {
                for (int32_t x = 0; x < width; ++x) {
                    float s = 0.0f;
                    int count = 0;
                    for (int dy = -fr; dy <= fr; ++dy) {
                        int py = std::clamp(y + dy, 0, height - 1);
                        for (int dx = -fr; dx <= fr; ++dx) {
                            int px = std::clamp(x + dx, 0, width - 1);
                            s += weights[i][py * width + px];
                            count++;
                        }
                    }
                    smoothed[y * width + x] = s / static_cast<float>(count);
                }
            }
            weights[i] = std::move(smoothed);
        }

        // Re-normalize smoothed weights
        #pragma omp parallel for
        for (int64_t idx = 0; idx < static_cast<int64_t>(total); ++idx) {
            float sumW = 0.0f;
            for (size_t i = 0; i < numFrames; ++i) sumW += weights[i][idx];
            if (sumW > 0.0f) {
                float invSum = 1.0f / sumW;
                for (size_t i = 0; i < numFrames; ++i) weights[i][idx] *= invSum;
            }
        }
    }

    // 4. Synthesize pan-focus output
    #pragma omp parallel for
    for (int64_t idx = 0; idx < static_cast<int64_t>(total); ++idx) {
        float r = 0.0f, g = 0.0f, b = 0.0f, a = 1.0f;
        for (size_t i = 0; i < numFrames; ++i) {
            float w = weights[i][idx];
            const auto& p = frames[i][idx];
            r += p.r * w;
            g += p.g * w;
            b += p.b * w;
        }
        outPanFocus[idx] = FloatRGBA(r, g, b, a);
    }

    return true;
}

bool FocusStacker::stackMedian(const std::vector<std::vector<FloatRGBA>>& frames,
                               int32_t width, int32_t height,
                               std::vector<FloatRGBA>& outComposite) {
    if (frames.empty() || width <= 0 || height <= 0) return false;
    const size_t numFrames = frames.size();
    const size_t total = static_cast<size_t>(width) * height;
    for (size_t i = 0; i < numFrames; ++i) {
        if (frames[i].size() < total) return false;
    }
    outComposite.assign(total, FloatRGBA(0.0f, 0.0f, 0.0f, 1.0f));

    const size_t midIdx = numFrames / 2;

    int numThreads = omp_get_max_threads();
    std::vector<std::vector<float>> threadRVals(numThreads);
    std::vector<std::vector<float>> threadGVals(numThreads);
    std::vector<std::vector<float>> threadBVals(numThreads);
    if (numFrames > 128) {
        for (int i = 0; i < numThreads; ++i) {
            threadRVals[i].resize(numFrames);
            threadGVals[i].resize(numFrames);
            threadBVals[i].resize(numFrames);
        }
    }

    #pragma omp parallel for schedule(static)
    for (int64_t idx = 0; idx < static_cast<int64_t>(total); ++idx) {
        if (numFrames <= 128) {
            float rVals[128];
            float gVals[128];
            float bVals[128];

            for (size_t i = 0; i < numFrames; ++i) {
                const auto& p = frames[i][idx];
                rVals[i] = p.r;
                gVals[i] = p.g;
                bVals[i] = p.b;
            }

            std::nth_element(rVals, rVals + midIdx, rVals + numFrames);
            std::nth_element(gVals, gVals + midIdx, gVals + numFrames);
            std::nth_element(bVals, bVals + midIdx, bVals + numFrames);

            outComposite[idx] = FloatRGBA(rVals[midIdx], gVals[midIdx], bVals[midIdx], 1.0f);
        } else {
            int tid = std::clamp(omp_get_thread_num(), 0, static_cast<int>(threadRVals.size()) - 1);
            auto& rVals = threadRVals[tid];
            auto& gVals = threadGVals[tid];
            auto& bVals = threadBVals[tid];

            for (size_t i = 0; i < numFrames; ++i) {
                const auto& p = frames[i][idx];
                rVals[i] = p.r;
                gVals[i] = p.g;
                bVals[i] = p.b;
            }

            std::nth_element(rVals.begin(), rVals.begin() + midIdx, rVals.end());
            std::nth_element(gVals.begin(), gVals.begin() + midIdx, gVals.end());
            std::nth_element(bVals.begin(), bVals.begin() + midIdx, bVals.end());

            outComposite[idx] = FloatRGBA(rVals[midIdx], gVals[midIdx], bVals[midIdx], 1.0f);
        }
    }

    return true;
}

bool FocusStacker::stackMean(const std::vector<std::vector<FloatRGBA>>& frames,
                             int32_t width, int32_t height,
                             std::vector<FloatRGBA>& outComposite) {
    if (frames.empty() || width <= 0 || height <= 0) return false;
    const size_t numFrames = frames.size();
    const size_t total = static_cast<size_t>(width) * height;
    for (size_t i = 0; i < numFrames; ++i) {
        if (frames[i].size() < total) return false;
    }
    outComposite.assign(total, FloatRGBA(0.0f, 0.0f, 0.0f, 1.0f));

    const float invN = 1.0f / static_cast<float>(numFrames);

    #pragma omp parallel for
    for (int64_t idx = 0; idx < static_cast<int64_t>(total); ++idx) {
        float r = 0.0f, g = 0.0f, b = 0.0f;
        for (size_t i = 0; i < numFrames; ++i) {
            const auto& p = frames[i][idx];
            r += p.r;
            g += p.g;
            b += p.b;
        }
        outComposite[idx] = FloatRGBA(r * invN, g * invN, b * invN, 1.0f);
    }

    return true;
}

bool FocusStacker::stackMultipleExposure(const std::vector<std::vector<FloatRGBA>>& frames,
                                         int32_t width, int32_t height,
                                         MultiExposureBlendMode mode,
                                         const std::vector<float>& opacities,
                                         std::vector<FloatRGBA>& outComposite) {
    if (frames.empty() || width <= 0 || height <= 0) return false;
    const size_t numFrames = frames.size();
    const size_t total = static_cast<size_t>(width) * height;
    for (size_t i = 0; i < numFrames; ++i) {
        if (frames[i].size() < total) return false;
    }

    outComposite = frames[0];

    for (size_t i = 1; i < numFrames; ++i) {
        float opacity = (i < opacities.size()) ? opacities[i] : 1.0f;
        const auto& layer = frames[i];

        #pragma omp parallel for
        for (int64_t idx = 0; idx < static_cast<int64_t>(total); ++idx) {
            auto& base = outComposite[idx];
            const auto& blend = layer[idx];

            float r = base.r, g = base.g, b = base.b;

            switch (mode) {
                case MultiExposureBlendMode::Additive:
                    r += blend.r * opacity;
                    g += blend.g * opacity;
                    b += blend.b * opacity;
                    break;
                case MultiExposureBlendMode::Average:
                    r = (r * (1.0f - opacity * 0.5f)) + (blend.r * opacity * 0.5f);
                    g = (g * (1.0f - opacity * 0.5f)) + (blend.g * opacity * 0.5f);
                    b = (b * (1.0f - opacity * 0.5f)) + (blend.b * opacity * 0.5f);
                    break;
                case MultiExposureBlendMode::Screen: {
                    float cr = std::clamp(r, 0.0f, 1.0f);
                    float cg = std::clamp(g, 0.0f, 1.0f);
                    float cb = std::clamp(b, 0.0f, 1.0f);
                    float cbr = std::clamp(blend.r * opacity, 0.0f, 1.0f);
                    float cbg = std::clamp(blend.g * opacity, 0.0f, 1.0f);
                    float cbb = std::clamp(blend.b * opacity, 0.0f, 1.0f);
                    r = 1.0f - (1.0f - cr) * (1.0f - cbr);
                    g = 1.0f - (1.0f - cg) * (1.0f - cbg);
                    b = 1.0f - (1.0f - cb) * (1.0f - cbb);
                    break;
                }
                case MultiExposureBlendMode::Lighten:
                    r = std::max(r, blend.r * opacity);
                    g = std::max(g, blend.g * opacity);
                    b = std::max(b, blend.b * opacity);
                    break;
                case MultiExposureBlendMode::Darken:
                    r = std::min(r, blend.r * opacity + (1.0f - opacity));
                    g = std::min(g, blend.g * opacity + (1.0f - opacity));
                    b = std::min(b, blend.b * opacity + (1.0f - opacity));
                    break;
            }

            base.r = r;
            base.g = g;
            base.b = b;
        }
    }

    return true;
}

bool FocusStacker::stackPixelShift4Shot(const std::vector<std::vector<FloatRGBA>>& fourShots,
                                        int32_t width, int32_t height,
                                        std::vector<FloatRGBA>& outSuperRes) {
    if (fourShots.size() < 4 || width <= 0 || height <= 0) return false;
    const size_t total = static_cast<size_t>(width) * height;
    for (size_t i = 0; i < 4; ++i) {
        if (fourShots[i].size() < total) return false;
    }
    outSuperRes.assign(total, FloatRGBA(0.0f, 0.0f, 0.0f, 1.0f));

    // Shot 0: (0, 0)
    // Shot 1: (1, 0) - shifted 1 px Right
    // Shot 2: (1, 1) - shifted 1 px Right & 1 px Down
    // Shot 3: (0, 1) - shifted 1 px Down
    // Full color reconstruction with zero demosaicing artifacts:
    #pragma omp parallel for
    for (int32_t y = 0; y < height; ++y) {
        for (int32_t x = 0; x < width; ++x) {
            size_t idx = static_cast<size_t>(y) * width + x;

            auto sampleAt = [&](const std::vector<FloatRGBA>& shot, int32_t sx, int32_t sy) -> const FloatRGBA& {
                sx = std::clamp(sx, 0, width - 1);
                sy = std::clamp(sy, 0, height - 1);
                return shot[static_cast<size_t>(sy) * width + sx];
            };

            // Sample each shot taking shift into consideration:
            // When sensor shifts right/down (+1), optical scene on sensor moves left/up (-1)
            const auto& s0 = sampleAt(fourShots[0], x, y);
            const auto& s1 = sampleAt(fourShots[1], x - 1, y);
            const auto& s2 = sampleAt(fourShots[2], x - 1, y - 1);
            const auto& s3 = sampleAt(fourShots[3], x, y - 1);

            // Reconstruct full RGB without interpolation
            // using exact Bayer phase alignment for each shift.
            int bayerPhase = (y % 2) * 2 + (x % 2);
            float r = 0.0f, g = 0.0f, b = 0.0f;
            switch (bayerPhase) {
                case 0: // R position
                    r = s0.r;
                    g = (s1.g + s3.g) * 0.5f;
                    b = s2.b;
                    break;
                case 1: // Gr position
                    r = s1.r;
                    g = (s0.g + s2.g) * 0.5f;
                    b = s3.b;
                    break;
                case 2: // Gb position
                    r = s3.r;
                    g = (s0.g + s2.g) * 0.5f;
                    b = s1.b;
                    break;
                case 3: // B position
                    r = s2.r;
                    g = (s1.g + s3.g) * 0.5f;
                    b = s0.b;
                    break;
            }

            outSuperRes[idx] = FloatRGBA(r, g, b, 1.0f);
        }
    }

    return true;
}

} // namespace lightrumor
