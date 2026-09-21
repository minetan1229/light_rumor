#include "light_rumor/AstroAligner.h"
#include <cmath>
#include <algorithm>
#include <numeric>
#include <random>
#include <iostream>

namespace lightrumor {

AstroAligner::AstroAligner() = default;
AstroAligner::~AstroAligner() = default;

void AstroAligner::detectStars(const std::vector<FloatRGBA>& frame,
                               int32_t width, int32_t height,
                               float minSNR,
                               int32_t maxCandidates,
                               std::vector<StarPoint>& outStars) {
    outStars.clear();
    if (frame.empty() || width <= 4 || height <= 4) return;

    const size_t total = static_cast<size_t>(width) * height;

    // 1. Estimate background level and noise floor
    // Subsample pixels to estimate sky background mean and standard deviation
    double sumLuma = 0.0;
    double sumSqLuma = 0.0;
    size_t sampleCount = 0;
    int step = std::max(1, static_cast<int>(std::sqrt(total / 10000.0f)));

    for (size_t i = 0; i < total; i += step) {
        float luma = 0.2126f * frame[i].r + 0.7152f * frame[i].g + 0.0722f * frame[i].b;
        sumLuma += luma;
        sumSqLuma += luma * luma;
        sampleCount++;
    }

    float meanSky = static_cast<float>(sumLuma / sampleCount);
    float varSky = std::max(0.0f, static_cast<float>(sumSqLuma / sampleCount - meanSky * meanSky));
    float sigmaSky = std::sqrt(varSky);
    float threshold = meanSky + minSNR * std::max(sigmaSky, 0.005f);

    auto getLuma = [&](int x, int y) -> float {
        const auto& p = frame[y * width + x];
        return 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    };

    // 2. Local Peak Detection and Centroid Localization
    std::vector<StarPoint> candidates;

    for (int y = 2; y < height - 2; ++y) {
        for (int x = 2; x < width - 2; ++x) {
            float c = getLuma(x, y);
            if (c <= threshold) continue;

            // Check 8-connected local maximum
            if (c > getLuma(x - 1, y) && c > getLuma(x + 1, y) &&
                c > getLuma(x, y - 1) && c > getLuma(x, y + 1) &&
                c > getLuma(x - 1, y - 1) && c > getLuma(x + 1, y - 1) &&
                c > getLuma(x - 1, y + 1) && c > getLuma(x + 1, y + 1)) {

                // Sub-pixel intensity centroid in 5x5 window
                double sumW = 0.0;
                double sumWx = 0.0;
                double sumWy = 0.0;

                for (int dy = -2; dy <= 2; ++dy) {
                    for (int dx = -2; dx <= 2; ++dx) {
                        float l = getLuma(x + dx, y + dy);
                        float w = std::max(0.0f, l - meanSky);
                        sumW += w;
                        sumWx += w * (x + dx);
                        sumWy += w * (y + dy);
                    }
                }

                if (sumW > 1e-4) {
                    StarPoint star;
                    star.x = static_cast<float>(sumWx / sumW);
                    star.y = static_cast<float>(sumWy / sumW);
                    star.flux = static_cast<float>(sumW);
                    star.snr = (c - meanSky) / std::max(sigmaSky, 0.005f);
                    candidates.push_back(star);
                }
            }
        }
    }

    // Sort by brightness (flux) descending
    std::sort(candidates.begin(), candidates.end(), [](const StarPoint& a, const StarPoint& b) {
        return a.flux > b.flux;
    });

    if (maxCandidates > 0 && static_cast<int32_t>(candidates.size()) > maxCandidates) {
        candidates.resize(maxCandidates);
    }

    outStars = std::move(candidates);
}

struct StarTriangle {
    size_t i0, i1, i2;
    float r1, r2; // side ratio descriptors
    float sideA;  // longest side
};

bool AstroAligner::estimateTransform(const std::vector<StarPoint>& refStars,
                                    const std::vector<StarPoint>& targetStars,
                                    AffineTransform2D& outTransform) {
    outTransform = AffineTransform2D(); // Identity default
    if (refStars.size() < 3 || targetStars.size() < 3) return false;

    // Use top stars for triangle descriptors
    const size_t Nref = std::min(refStars.size(), size_t(30));
    const size_t Ntgt = std::min(targetStars.size(), size_t(30));

    auto buildTriangles = [](const std::vector<StarPoint>& stars, size_t count) {
        std::vector<StarTriangle> tris;
        for (size_t i = 0; i < count; ++i) {
            for (size_t j = i + 1; j < count; ++j) {
                float d01 = std::hypot(stars[i].x - stars[j].x, stars[i].y - stars[j].y);
                if (d01 < 4.0f) continue;
                for (size_t k = j + 1; k < count; ++k) {
                    float d12 = std::hypot(stars[j].x - stars[k].x, stars[j].y - stars[k].y);
                    float d20 = std::hypot(stars[k].x - stars[i].x, stars[k].y - stars[i].y);
                    if (d12 < 4.0f || d20 < 4.0f) continue;

                    std::array<float, 3> sides = {d01, d12, d20};
                    std::sort(sides.begin(), sides.end(), std::greater<float>());

                    StarTriangle tri;
                    tri.i0 = i; tri.i1 = j; tri.i2 = k;
                    tri.sideA = sides[0];
                    tri.r1 = sides[1] / sides[0];
                    tri.r2 = sides[2] / sides[0];
                    tris.push_back(tri);
                }
            }
        }
        return tris;
    };

    auto refTris = buildTriangles(refStars, Nref);
    auto tgtTris = buildTriangles(targetStars, Ntgt);

    // Match triangles by invariant ratios r1, r2
    struct MatchPair {
        size_t refIdx;
        size_t tgtIdx;
    };
    std::vector<MatchPair> pointPairs;

    const float tol = 0.025f; // ratio tolerance
    for (const auto& rT : refTris) {
        for (const auto& tT : tgtTris) {
            if (std::abs(rT.r1 - tT.r1) < tol && std::abs(rT.r2 - tT.r2) < tol) {
                // Approximate match: pair the centroid of this triangle
                pointPairs.push_back({rT.i0, tT.i0});
                pointPairs.push_back({rT.i1, tT.i1});
                pointPairs.push_back({rT.i2, tT.i2});
            }
        }
    }

    if (pointPairs.size() < 3) {
        // Fallback: estimate simple translation based on brightest star difference
        outTransform.tx = targetStars[0].x - refStars[0].x;
        outTransform.ty = targetStars[0].y - refStars[0].y;
        return true;
    }

    // RANSAC Affine estimation
    size_t bestInliers = 0;
    AffineTransform2D bestTransform;
    std::mt19937 rng(42);

    for (int iter = 0; iter < 100; ++iter) {
        // Pick 3 random point pairs
        size_t idx0 = rng() % pointPairs.size();
        size_t idx1 = rng() % pointPairs.size();
        size_t idx2 = rng() % pointPairs.size();
        if (idx0 == idx1 || idx1 == idx2 || idx0 == idx2) continue;

        const auto& pR0 = refStars[pointPairs[idx0].refIdx];
        const auto& pT0 = targetStars[pointPairs[idx0].tgtIdx];
        const auto& pR1 = refStars[pointPairs[idx1].refIdx];
        const auto& pT1 = targetStars[pointPairs[idx1].tgtIdx];

        // Solve similarity (scale, rotation, translation) from 2 points
        float dxR = pR1.x - pR0.x;
        float dyR = pR1.y - pR0.y;
        float dxT = pT1.x - pT0.x;
        float dyT = pT1.y - pT0.y;

        float lenR2 = dxR * dxR + dyR * dyR;
        if (lenR2 < 1e-4f) continue;

        float a = (dxT * dxR + dyT * dyR) / lenR2;
        float b = (dyT * dxR - dxT * dyR) / lenR2;
        float tx = pT0.x - (a * pR0.x - b * pR0.y);
        float ty = pT0.y - (b * pR0.x + a * pR0.y);

        AffineTransform2D cand;
        cand.m00 = a; cand.m01 = -b; cand.tx = tx;
        cand.m10 = b; cand.m11 =  a; cand.ty = ty;

        // Count inliers
        size_t inliers = 0;
        for (const auto& pair : pointPairs) {
            const auto& pR = refStars[pair.refIdx];
            const auto& pT = targetStars[pair.tgtIdx];
            auto mapped = cand.transform(pR.x, pR.y);
            float dist = std::hypot(mapped.x - pT.x, mapped.y - pT.y);
            if (dist < 2.5f) { // 2.5 pixel inlier threshold
                inliers++;
            }
        }

        if (inliers > bestInliers) {
            bestInliers = inliers;
            bestTransform = cand;
        }
    }

    if (bestInliers >= 3) {
        outTransform = bestTransform;
        return true;
    }

    // Default: use brightest star offset
    outTransform.tx = targetStars[0].x - refStars[0].x;
    outTransform.ty = targetStars[0].y - refStars[0].y;
    return true;
}

void AstroAligner::warpFrame(const std::vector<FloatRGBA>& inFrame,
                            int32_t width, int32_t height,
                            const AffineTransform2D& transform,
                            std::vector<FloatRGBA>& outWarped) {
    if (inFrame.empty() || width <= 0 || height <= 0) return;
    const size_t total = static_cast<size_t>(width) * height;
    outWarped.assign(total, FloatRGBA(0.0f, 0.0f, 0.0f, 1.0f));

    #pragma omp parallel for
    for (int32_t y = 0; y < height; ++y) {
        for (int32_t x = 0; x < width; ++x) {
            // Transform reference coordinate (x, y) to target coordinate (sx, sy)
            Point2D srcP = transform.transform(static_cast<float>(x), static_cast<float>(y));
            float sx = srcP.x;
            float sy = srcP.y;

            if (sx < 0.0f || sx >= width - 1 || sy < 0.0f || sy >= height - 1) {
                continue;
            }

            int x0 = static_cast<int>(std::floor(sx));
            int y0 = static_cast<int>(std::floor(sy));
            int x1 = x0 + 1;
            int y1 = y0 + 1;

            float fx = sx - x0;
            float fy = sy - y0;

            const auto& p00 = inFrame[y0 * width + x0];
            const auto& p10 = inFrame[y0 * width + x1];
            const auto& p01 = inFrame[y1 * width + x0];
            const auto& p11 = inFrame[y1 * width + x1];

            float w00 = (1.0f - fx) * (1.0f - fy);
            float w10 = fx * (1.0f - fy);
            float w01 = (1.0f - fx) * fy;
            float w11 = fx * fy;

            outWarped[y * width + x] = FloatRGBA(
                p00.r * w00 + p10.r * w10 + p01.r * w01 + p11.r * w11,
                p00.g * w00 + p10.g * w10 + p01.g * w01 + p11.g * w11,
                p00.b * w00 + p10.b * w10 + p01.b * w01 + p11.b * w11,
                1.0f
            );
        }
    }
}

bool AstroAligner::stackKappaSigma(const std::vector<std::vector<FloatRGBA>>& frames,
                                   int32_t width, int32_t height,
                                   const AstroStackParams& params,
                                   std::vector<FloatRGBA>& outStacked) {
    if (frames.empty() || width <= 0 || height <= 0) return false;
    const size_t numFrames = frames.size();
    if (numFrames == 1) {
        outStacked = frames[0];
        return true;
    }

    const size_t total = static_cast<size_t>(width) * height;
    outStacked.assign(total, FloatRGBA(0.0f, 0.0f, 0.0f, 1.0f));

    // 1. Align frames to reference frame (Frame 0)
    std::vector<std::vector<FloatRGBA>> alignedFrames(numFrames);
    alignedFrames[0] = frames[0];

    if (params.alignStars) {
        std::vector<StarPoint> refStars;
        detectStars(frames[0], width, height, params.minStarSNR, params.maxStarCandidates, refStars);

        for (size_t i = 1; i < numFrames; ++i) {
            std::vector<StarPoint> tgtStars;
            detectStars(frames[i], width, height, params.minStarSNR, params.maxStarCandidates, tgtStars);

            AffineTransform2D t;
            if (estimateTransform(refStars, tgtStars, t)) {
                warpFrame(frames[i], width, height, t, alignedFrames[i]);
            } else {
                alignedFrames[i] = frames[i];
            }
        }
    } else {
        for (size_t i = 1; i < numFrames; ++i) alignedFrames[i] = frames[i];
    }

    // 2. Kappa-Sigma Clipping Stacking
    float kappa = params.kappa;
    int maxIter = std::clamp(params.maxIterations, 1, 5);

    #pragma omp parallel for
    for (int64_t idx = 0; idx < static_cast<int64_t>(total); ++idx) {
        auto clipChannel = [&](auto channelGetter) -> float {
            if (numFrames <= 128) {
                float vals[128];
                uint8_t valid[128];
                for (size_t i = 0; i < numFrames; ++i) {
                    vals[i] = channelGetter(alignedFrames[i][idx]);
                    valid[i] = 1;
                }

                for (int iter = 0; iter < maxIter; ++iter) {
                    float currentValid[128];
                    size_t validCount = 0;
                    for (size_t i = 0; i < numFrames; ++i) {
                        if (valid[i]) {
                            currentValid[validCount++] = vals[i];
                        }
                    }

                    if (validCount <= 2) break;

                    std::sort(currentValid, currentValid + validCount);
                    float medianVal = currentValid[validCount / 2];

                    float absDiffs[128];
                    for (size_t i = 0; i < validCount; ++i) {
                        absDiffs[i] = std::abs(currentValid[i] - medianVal);
                    }
                    std::sort(absDiffs, absDiffs + validCount);
                    float mad = absDiffs[validCount / 2];
                    float sigma = std::max(1.4826f * mad, 0.005f);

                    bool changed = false;
                    for (size_t i = 0; i < numFrames; ++i) {
                        if (valid[i] && std::abs(vals[i] - medianVal) > kappa * sigma) {
                            valid[i] = 0;
                            changed = true;
                        }
                    }
                    if (!changed) break;
                }

                float finalSum = 0.0f;
                int count = 0;
                for (size_t i = 0; i < numFrames; ++i) {
                    if (valid[i]) {
                        finalSum += vals[i];
                        count++;
                    }
                }
                return count > 0 ? (finalSum / count) : vals[0];
            } else {
                std::vector<float> vals(numFrames);
                for (size_t i = 0; i < numFrames; ++i) {
                    vals[i] = channelGetter(alignedFrames[i][idx]);
                }

                std::vector<bool> valid(numFrames, true);

                for (int iter = 0; iter < maxIter; ++iter) {
                    std::vector<float> currentValid;
                    currentValid.reserve(numFrames);
                    for (size_t i = 0; i < numFrames; ++i) {
                        if (valid[i]) currentValid.push_back(vals[i]);
                    }

                    if (currentValid.size() <= 2) break;

                    std::sort(currentValid.begin(), currentValid.end());
                    float medianVal = currentValid[currentValid.size() / 2];

                    // Robust MAD (Median Absolute Deviation) estimator: sigma = 1.4826 * MAD
                    std::vector<float> absDiffs(currentValid.size());
                    for (size_t i = 0; i < currentValid.size(); ++i) {
                        absDiffs[i] = std::abs(currentValid[i] - medianVal);
                    }
                    std::sort(absDiffs.begin(), absDiffs.end());
                    float mad = absDiffs[absDiffs.size() / 2];
                    float sigma = std::max(1.4826f * mad, 0.005f);

                    bool changed = false;
                    for (size_t i = 0; i < numFrames; ++i) {
                        if (valid[i] && std::abs(vals[i] - medianVal) > kappa * sigma) {
                            valid[i] = false;
                            changed = true;
                        }
                    }
                    if (!changed) break;
                }

                // Average remaining valid samples
                float finalSum = 0.0f;
                int count = 0;
                for (size_t i = 0; i < numFrames; ++i) {
                    if (valid[i]) {
                        finalSum += vals[i];
                        count++;
                    }
                }
                return count > 0 ? (finalSum / count) : vals[0];
            }
        };

        float r = clipChannel([](const FloatRGBA& p) { return p.r; });
        float g = clipChannel([](const FloatRGBA& p) { return p.g; });
        float b = clipChannel([](const FloatRGBA& p) { return p.b; });

        outStacked[idx] = FloatRGBA(r, g, b, 1.0f);
    }

    return true;
}

} // namespace lightrumor
