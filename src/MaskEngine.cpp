#include "apex/MaskEngine.h"
#include <cmath>
#include <algorithm>
#include <iostream>
#include <cstring>

namespace apex {

namespace {

inline float smoothstep(float edge0, float edge1, float x) {
    float t = std::clamp((x - edge0) / (edge1 - edge0 + 1e-7f), 0.0f, 1.0f);
    return t * t * (3.0f - 2.0f * t);
}

inline void rgbToHsl(float r, float g, float b, float& h, float& s, float& l) {
    float maxVal = std::max({r, g, b});
    float minVal = std::min({r, g, b});
    float delta = maxVal - minVal;
    l = (maxVal + minVal) * 0.5f;

    if (delta < 1e-6f) {
        h = 0.0f;
        s = 0.0f;
    } else {
        s = (l < 0.5f) ? (delta / (maxVal + minVal)) : (delta / (2.0f - maxVal - minVal));
        if (r == maxVal) {
            h = (g - b) / delta + (g < b ? 6.0f : 0.0f);
        } else if (g == maxVal) {
            h = (b - r) / delta + 2.0f;
        } else {
            h = (r - g) / delta + 4.0f;
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

} // namespace

// -------------------------------------------------------------------------
// Mask Mathematical Evaluation Functions
// -------------------------------------------------------------------------

float MaskEngine::evaluateLinearAt(float x, float y, const Point2D& start, const Point2D& end,
                                   float feather, EasingMode easing, bool invert) {
    float vx = end.x - start.x;
    float vy = end.y - start.y;
    float lenSq = vx * vx + vy * vy;
    if (lenSq < 1e-7f) {
        return invert ? 1.0f : 0.0f;
    }

    float t = ((x - start.x) * vx + (y - start.y) * vy) / lenSq;
    t = std::clamp(t, 0.0f, 1.0f);

    if (easing == EasingMode::Smoothstep) {
        t = smoothstep(0.0f, 1.0f, t);
    }

    if (feather > 0.0f && feather < 1.0f) {
        float fLow = feather * 0.5f;
        float fHigh = 1.0f - fLow;
        if (t < fLow) {
            t = (fLow > 1e-5f) ? (t / fLow) * 0.5f : 0.0f;
        } else if (t > fHigh) {
            t = 0.5f + ((t - fHigh) / std::max(1e-5f, 1.0f - fHigh)) * 0.5f;
        }
    }

    if (invert) {
        t = 1.0f - t;
    }
    return std::clamp(t, 0.0f, 1.0f);
}

float MaskEngine::evaluateRadialAt(float x, float y, const Point2D& center,
                                   float rx, float ry, float angleRad,
                                   float feather, bool invert) {
    float dx = x - center.x;
    float dy = y - center.y;

    float cosA = std::cos(-angleRad);
    float sinA = std::sin(-angleRad);

    float rxRot = dx * cosA - dy * sinA;
    float ryRot = dx * sinA + dy * cosA;

    rx = std::max(1e-5f, rx);
    ry = std::max(1e-5f, ry);

    float distNorm = std::sqrt((rxRot * rxRot) / (rx * rx) + (ryRot * ryRot) / (ry * ry));

    float w = 0.0f;
    if (distNorm < 1.0f) {
        float f = std::clamp(feather, 0.01f, 1.0f);
        float innerR = std::max(0.0f, 1.0f - f);
        if (distNorm <= innerR) {
            w = 1.0f;
        } else {
            w = 1.0f - smoothstep(innerR, 1.0f, distNorm);
        }
    }

    if (invert) {
        w = 1.0f - w;
    }
    return std::clamp(w, 0.0f, 1.0f);
}

float MaskEngine::evaluatePolygonAt(float x, float y, const std::vector<Point2D>& vertices,
                                    float feather, bool invert) {
    size_t n = vertices.size();
    if (n < 3) return invert ? 1.0f : 0.0f;

    bool inside = false;
    float minDistSq = 1e9f;

    for (size_t i = 0, j = n - 1; i < n; j = i++) {
        const Point2D& vi = vertices[i];
        const Point2D& vj = vertices[j];

        // Ray-casting point-in-polygon test
        if (((vi.y > y) != (vj.y > y)) &&
            (x < (vj.x - vi.x) * (y - vi.y) / (vj.y - vi.y + 1e-7f) + vi.x)) {
            inside = !inside;
        }

        // Distance to edge segment for anti-aliasing feather
        float segX = vj.x - vi.x;
        float segY = vj.y - vi.y;
        float segLenSq = segX * segX + segY * segY;
        float t = (segLenSq > 1e-7f) ? std::clamp(((x - vi.x) * segX + (y - vi.y) * segY) / segLenSq, 0.0f, 1.0f) : 0.0f;
        float projX = vi.x + t * segX;
        float projY = vi.y + t * segY;
        float dSq = (x - projX) * (x - projX) + (y - projY) * (y - projY);
        minDistSq = std::min(minDistSq, dSq);
    }

    float d = std::sqrt(minDistSq);
    float f = std::max(1e-4f, feather);
    float edgeFactor = std::clamp(d / f, 0.0f, 1.0f);

    float w = inside ? (0.5f + 0.5f * edgeFactor) : std::max(0.0f, 0.5f - 0.5f * edgeFactor);
    if (invert) {
        w = 1.0f - w;
    }
    return std::clamp(w, 0.0f, 1.0f);
}

void MaskEngine::rasterizeBrushStrokes(const std::vector<BrushStrokePoint>& strokes,
                                       float baseRadius, float feather,
                                       int32_t width, int32_t height,
                                       std::vector<float>& outMask) {
    if (outMask.size() != static_cast<size_t>(width) * height) {
        outMask.assign(static_cast<size_t>(width) * height, 0.0f);
    }

    for (const auto& pt : strokes) {
        // Stylus pressure modulation: pressure in [0, 1] linearly modulates radius and flow
        float p = std::clamp(pt.pressure, 0.01f, 1.0f);
        float effRadius = pt.radius > 0.0f ? pt.radius * p : baseRadius * p;
        float effFlow = std::clamp(pt.flow * p, 0.0f, 1.0f);

        // Convert normalized coordinates if in [0, 1], otherwise assume pixel coordinates
        float px = (pt.x <= 1.0f && pt.y <= 1.0f && pt.x >= 0.0f && pt.y >= 0.0f) ? pt.x * width : pt.x;
        float py = (pt.x <= 1.0f && pt.y <= 1.0f && pt.x >= 0.0f && pt.y >= 0.0f) ? pt.y * height : pt.y;

        int32_t minX = std::max(0, static_cast<int32_t>(std::floor(px - effRadius)));
        int32_t maxX = std::min(width - 1, static_cast<int32_t>(std::ceil(px + effRadius)));
        int32_t minY = std::max(0, static_cast<int32_t>(std::floor(py - effRadius)));
        int32_t maxY = std::min(height - 1, static_cast<int32_t>(std::ceil(py + effRadius)));

        float f = std::clamp(feather, 0.01f, 1.0f);
        float innerR = std::max(0.0f, effRadius * (1.0f - f));

        for (int32_t cy = minY; cy <= maxY; ++cy) {
            for (int32_t cx = minX; cx <= maxX; ++cx) {
                float dist = std::sqrt((cx - px) * (cx - px) + (cy - py) * (cy - py));
                if (dist <= effRadius) {
                    float falloff = (dist <= innerR) ? 1.0f : (1.0f - smoothstep(innerR, effRadius, dist));
                    float stampAlpha = falloff * effFlow;
                    size_t idx = static_cast<size_t>(cy) * width + cx;

                    if (pt.isEraser) {
                        outMask[idx] = std::max(0.0f, outMask[idx] - stampAlpha);
                    } else {
                        outMask[idx] = std::min(1.0f, outMask[idx] + stampAlpha);
                    }
                }
            }
        }
    }
}

float MaskEngine::evaluateLuminanceAt(const FloatRGBA& p, float lumaMin, float lumaMax,
                                      float fLow, float fHigh, bool invert) {
    // Rec.709 Luminance
    float luma = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;

    fLow = std::max(1e-4f, fLow);
    fHigh = std::max(1e-4f, fHigh);

    float wLow = smoothstep(lumaMin - fLow, lumaMin, luma);
    float wHigh = 1.0f - smoothstep(lumaMax, lumaMax + fHigh, luma);
    float w = std::clamp(wLow * wHigh, 0.0f, 1.0f);

    if (invert) {
        w = 1.0f - w;
    }
    return w;
}

float MaskEngine::evaluateColorRangeAt(const FloatRGBA& p, float targetH, float targetS, float targetL,
                                       float tolH, float tolS, float tolL, float feather, bool invert) {
    float h, s, l;
    rgbToHsl(p.r, p.g, p.b, h, s, l);

    float dH = std::abs(h - targetH);
    if (dH > 180.0f) dH = 360.0f - dH;
    float dS = std::abs(s - targetS);
    float dL = std::abs(l - targetL);

    float f = std::max(1e-4f, feather);
    float wH = 1.0f - smoothstep(tolH, tolH + f * 45.0f, dH);
    float wS = 1.0f - smoothstep(tolS, tolS + f * 0.3f, dS);
    float wL = 1.0f - smoothstep(tolL, tolL + f * 0.3f, dL);

    float w = std::clamp(wH * wS * wL, 0.0f, 1.0f);
    if (invert) {
        w = 1.0f - w;
    }
    return w;
}

float MaskEngine::evaluateDepthAt(float depthVal, float dMin, float dMax, float feather, bool invert) {
    float f = std::max(1e-4f, feather);
    float wLow = smoothstep(dMin - f, dMin, depthVal);
    float wHigh = 1.0f - smoothstep(dMax, dMax + f, depthVal);
    float w = std::clamp(wLow * wHigh, 0.0f, 1.0f);

    if (invert) {
        w = 1.0f - w;
    }
    return w;
}

void MaskEngine::computeSobelEdgeMask(const std::vector<FloatRGBA>& pixels,
                                      int32_t width, int32_t height,
                                      float threshold, float feather, bool invert,
                                      std::vector<float>& outMask) {
    outMask.resize(static_cast<size_t>(width) * height);
    float f = std::max(1e-4f, feather);

    #pragma omp parallel for schedule(static)
    for (int32_t y = 0; y < height; ++y) {
        for (int32_t x = 0; x < width; ++x) {
            float luma[3][3];
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dx = -1; dx <= 1; ++dx) {
                    int cx = std::clamp(x + dx, 0, width - 1);
                    int cy = std::clamp(y + dy, 0, height - 1);
                    const FloatRGBA& c = pixels[cy * width + cx];
                    luma[dy + 1][dx + 1] = 0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b;
                }
            }

            float gx = (-1.0f * luma[0][0] + 1.0f * luma[0][2]) +
                       (-2.0f * luma[1][0] + 2.0f * luma[1][2]) +
                       (-1.0f * luma[2][0] + 1.0f * luma[2][2]);

            float gy = (-1.0f * luma[0][0] - 2.0f * luma[0][1] - 1.0f * luma[0][2]) +
                       ( 1.0f * luma[2][0] + 2.0f * luma[2][1] + 1.0f * luma[2][2]);

            float grad = std::sqrt(gx * gx + gy * gy);
            float w = smoothstep(threshold - f, threshold + f, grad);

            if (invert) {
                w = 1.0f - w;
            }
            outMask[y * width + x] = std::clamp(w, 0.0f, 1.0f);
        }
    }
}

// -------------------------------------------------------------------------
// Single Mask & Composite Mask Pipeline
// -------------------------------------------------------------------------

void MaskEngine::evaluateSingleMask(const MaskLayer& layer,
                                    const std::vector<FloatRGBA>& inPixels,
                                    int32_t width, int32_t height,
                                    const std::vector<float>& depthBuffer,
                                    std::vector<float>& outMask) {
    size_t total = static_cast<size_t>(width) * height;
    outMask.resize(total);

    if (layer.type == MaskType::Brush) {
        std::fill(outMask.begin(), outMask.end(), 0.0f);
        rasterizeBrushStrokes(layer.brushStrokes, layer.brushBaseRadius, layer.brushFeather, width, height, outMask);
        if (layer.inverted) {
            for (size_t i = 0; i < total; ++i) {
                outMask[i] = 1.0f - outMask[i];
            }
        }
        if (layer.opacity < 1.0f) {
            for (size_t i = 0; i < total; ++i) {
                outMask[i] *= layer.opacity;
            }
        }
        return;
    }

    if (layer.type == MaskType::SobelEdge) {
        computeSobelEdgeMask(inPixels, width, height, layer.edgeThreshold, layer.edgeFeather, layer.inverted, outMask);
        if (layer.opacity < 1.0f) {
            for (size_t i = 0; i < total; ++i) {
                outMask[i] *= layer.opacity;
            }
        }
        return;
    }

    bool hasDepth = (depthBuffer.size() == total);

    #pragma omp parallel for schedule(static)
    for (int64_t i = 0; i < static_cast<int64_t>(total); ++i) {
        int32_t x = static_cast<int32_t>(i % width);
        int32_t y = static_cast<int32_t>(i / width);
        float normX = static_cast<float>(x) / std::max(1, width - 1);
        float normY = static_cast<float>(y) / std::max(1, height - 1);

        float w = 0.0f;
        switch (layer.type) {
            case MaskType::LinearGradient:
                w = evaluateLinearAt(normX, normY, layer.linearStart, layer.linearEnd,
                                     layer.linearFeather, layer.linearEasing, layer.inverted);
                break;
            case MaskType::RadialGradient:
                w = evaluateRadialAt(normX, normY, layer.radialCenter,
                                     layer.radialRadiusX, layer.radialRadiusY, layer.radialAngle,
                                     layer.radialFeather, layer.inverted);
                break;
            case MaskType::PolygonBezier:
                w = evaluatePolygonAt(normX, normY, layer.polygonVertices,
                                      layer.polygonFeather, layer.inverted);
                break;
            case MaskType::LuminanceRange:
                w = evaluateLuminanceAt(inPixels[i], layer.lumaMin, layer.lumaMax,
                                        layer.lumaFeatherLow, layer.lumaFeatherHigh, layer.inverted);
                break;
            case MaskType::ColorRange:
                w = evaluateColorRangeAt(inPixels[i], layer.colorTargetHue, layer.colorTargetSat, layer.colorTargetLum,
                                         layer.colorTolHue, layer.colorTolSat, layer.colorTolLum,
                                         layer.colorFeather, layer.inverted);
                break;
            case MaskType::DepthMap:
                w = hasDepth ? evaluateDepthAt(depthBuffer[i], layer.depthMin, layer.depthMax, layer.depthFeather, layer.inverted) : 0.0f;
                break;
            default:
                w = 1.0f;
                break;
        }

        outMask[i] = std::clamp(w * layer.opacity, 0.0f, 1.0f);
    }
}

void MaskEngine::evaluateCompositeMask(const std::vector<MaskLayer>& layers,
                                       const std::vector<FloatRGBA>& inPixels,
                                       int32_t width, int32_t height,
                                       const std::vector<float>& depthBuffer,
                                       std::vector<float>& outCompositeMask) {
    size_t total = static_cast<size_t>(width) * height;
    outCompositeMask.assign(total, 0.0f);

    bool firstLayer = true;
    std::vector<float> layerMask(total);

    for (const auto& layer : layers) {
        if (!layer.enabled) continue;

        evaluateSingleMask(layer, inPixels, width, height, depthBuffer, layerMask);

        if (firstLayer || layer.booleanOp == BooleanOp::Replace) {
            std::copy(layerMask.begin(), layerMask.end(), outCompositeMask.begin());
            firstLayer = false;
        } else {
            #pragma omp parallel for schedule(static)
            for (int64_t i = 0; i < static_cast<int64_t>(total); ++i) {
                float base = outCompositeMask[i];
                float incoming = layerMask[i];
                float combined = incoming;

                switch (layer.booleanOp) {
                    case BooleanOp::Replace:
                        combined = incoming;
                        break;
                    case BooleanOp::Union:
                        combined = std::min(1.0f, base + incoming);
                        break;
                    case BooleanOp::Subtract:
                        combined = std::max(0.0f, base - incoming);
                        break;
                    case BooleanOp::Intersect:
                        combined = base * incoming;
                        break;
                }
                outCompositeMask[i] = std::clamp(combined, 0.0f, 1.0f);
            }
        }
    }
}

// -------------------------------------------------------------------------
// Local Photographic Adjustments
// -------------------------------------------------------------------------

void MaskEngine::applyLocalAdjustments(std::vector<FloatRGBA>& pixels,
                                      int32_t width, int32_t height,
                                      const std::vector<float>& maskWeights,
                                      const LocalAdjustmentParams& adj) {
    size_t total = static_cast<size_t>(width) * height;

    float expGain = std::pow(2.0f, adj.exposureEV);

    #pragma omp parallel for schedule(static)
    for (int64_t i = 0; i < static_cast<int64_t>(total); ++i) {
        float w = maskWeights[i];
        if (w <= 1e-5f) continue;

        FloatRGBA& p = pixels[i];

        // 1. Exposure adjustment modulated by mask weight
        float pixelExpGain = 1.0f + (expGain - 1.0f) * w;
        p.r *= pixelExpGain;
        p.g *= pixelExpGain;
        p.b *= pixelExpGain;

        // 2. White Balance Shift (Kelvin & Tint)
        if (std::abs(adj.kelvinOffset) > 1.0f || std::abs(adj.tintOffset) > 0.01f) {
            float kShift = (adj.kelvinOffset / 5000.0f) * w;
            float tShift = (adj.tintOffset * 0.005f) * w;
            p.r *= (1.0f - kShift);
            p.b *= (1.0f + kShift);
            p.g *= (1.0f - tShift);
        }

        // 3. Contrast adjustment around 0.18 middle gray
        if (std::abs(adj.contrast) > 1e-4f) {
            float effContrast = 1.0f + (adj.contrast * 0.005f) * w;
            p.r = std::pow(std::max(0.0f, p.r / 0.18f), effContrast) * 0.18f;
            p.g = std::pow(std::max(0.0f, p.g / 0.18f), effContrast) * 0.18f;
            p.b = std::pow(std::max(0.0f, p.b / 0.18f), effContrast) * 0.18f;
        }

        // 4. Highlights recovery & Shadow lift
        if (std::abs(adj.highlights) > 1e-4f) {
            float maxC = std::max({p.r, p.g, p.b});
            if (maxC > 0.75f) {
                float excess = maxC - 0.75f;
                float comp = 0.75f + excess / (1.0f + excess * (adj.highlights * 0.008f * w));
                p.r *= (comp / maxC);
                p.g *= (comp / maxC);
                p.b *= (comp / maxC);
            }
        }
        if (std::abs(adj.shadows) > 1e-4f) {
            float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
            float lift = std::pow(1.0f - std::clamp(lum, 0.0f, 1.0f), 3.0f) * (adj.shadows * 0.0035f * w);
            p.r = std::max(0.0f, p.r + lift);
            p.g = std::max(0.0f, p.g + lift);
            p.b = std::max(0.0f, p.b + lift);
        }

        // 5. Whites & Blacks
        if (std::abs(adj.whites) > 1e-4f || std::abs(adj.blacks) > 1e-4f) {
            p.r = std::max(0.0f, p.r - (adj.blacks * 0.0005f * w)) * (1.0f + (adj.whites * 0.005f * w));
            p.g = std::max(0.0f, p.g - (adj.blacks * 0.0005f * w)) * (1.0f + (adj.whites * 0.005f * w));
            p.b = std::max(0.0f, p.b - (adj.blacks * 0.0005f * w)) * (1.0f + (adj.whites * 0.005f * w));
        }

        // 6. Saturation
        if (std::abs(adj.saturation) > 1e-4f) {
            float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
            float satScale = 1.0f + (adj.saturation * 0.01f) * w;
            p.r = lum + (p.r - lum) * satScale;
            p.g = lum + (p.g - lum) * satScale;
            p.b = lum + (p.b - lum) * satScale;
        }

        // 7. Clarity / Dehaze
        if (std::abs(adj.clarity) > 1e-4f) {
            float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
            float midGain = 1.0f + (adj.clarity * 0.003f * w) * (1.0f - std::abs(lum - 0.5f) * 2.0f);
            p.r *= midGain;
            p.g *= midGain;
            p.b *= midGain;
        }
    }
}

void MaskEngine::processMaskLayers(std::vector<FloatRGBA>& pixels,
                                   int32_t width, int32_t height,
                                   const std::vector<MaskLayer>& layers,
                                   const std::vector<float>& depthBuffer) {
    if (layers.empty()) return;

    std::vector<float> maskBuffer;
    for (const auto& layer : layers) {
        if (!layer.enabled) continue;
        evaluateSingleMask(layer, pixels, width, height, depthBuffer, maskBuffer);
        applyLocalAdjustments(pixels, width, height, maskBuffer, layer.adjustments);
    }
}

// -------------------------------------------------------------------------
// Retouch Operations: Clone Stamp & Poisson Image Editing
// -------------------------------------------------------------------------

void MaskEngine::applyCloneStamp(std::vector<FloatRGBA>& pixels,
                                 int32_t width, int32_t height,
                                 const RetouchOperation& op) {
    float dstPxX = (op.targetPos.x <= 1.0f) ? op.targetPos.x * width : op.targetPos.x;
    float dstPxY = (op.targetPos.y <= 1.0f) ? op.targetPos.y * height : op.targetPos.y;
    float srcPxX = (op.sourcePos.x <= 1.0f) ? op.sourcePos.x * width : op.sourcePos.x;
    float srcPxY = (op.sourcePos.y <= 1.0f) ? op.sourcePos.y * height : op.sourcePos.y;

    float offX = srcPxX - dstPxX;
    float offY = srcPxY - dstPxY;

    int32_t minX = std::max(0, static_cast<int32_t>(std::floor(dstPxX - op.radius)));
    int32_t maxX = std::min(width - 1, static_cast<int32_t>(std::ceil(dstPxX + op.radius)));
    int32_t minY = std::max(0, static_cast<int32_t>(std::floor(dstPxY - op.radius)));
    int32_t maxY = std::min(height - 1, static_cast<int32_t>(std::ceil(dstPxY + op.radius)));

    float innerR = std::max(0.0f, op.radius * (1.0f - op.feather));

    std::vector<FloatRGBA> orig = pixels;

    for (int32_t y = minY; y <= maxY; ++y) {
        for (int32_t x = minX; x <= maxX; ++x) {
            float dist = std::sqrt((x - dstPxX) * (x - dstPxX) + (y - dstPxY) * (y - dstPxY));
            if (dist <= op.radius) {
                float falloff = (dist <= innerR) ? 1.0f : (1.0f - smoothstep(innerR, op.radius, dist));
                float alpha = falloff * op.opacity;

                int32_t sx = std::clamp(static_cast<int32_t>(std::round(x + offX)), 0, width - 1);
                int32_t sy = std::clamp(static_cast<int32_t>(std::round(y + offY)), 0, height - 1);

                const FloatRGBA& srcPixel = orig[sy * width + sx];
                FloatRGBA& dstPixel = pixels[y * width + x];

                dstPixel.r = dstPixel.r * (1.0f - alpha) + srcPixel.r * alpha;
                dstPixel.g = dstPixel.g * (1.0f - alpha) + srcPixel.g * alpha;
                dstPixel.b = dstPixel.b * (1.0f - alpha) + srcPixel.b * alpha;
            }
        }
    }
}

void MaskEngine::applyPoissonHeal(std::vector<FloatRGBA>& pixels,
                                  int32_t width, int32_t height,
                                  const RetouchOperation& op,
                                  int32_t maxIterations) {
    float dstPxX = (op.targetPos.x <= 1.0f) ? op.targetPos.x * width : op.targetPos.x;
    float dstPxY = (op.targetPos.y <= 1.0f) ? op.targetPos.y * height : op.targetPos.y;
    float srcPxX = (op.sourcePos.x <= 1.0f) ? op.sourcePos.x * width : op.sourcePos.x;
    float srcPxY = (op.sourcePos.y <= 1.0f) ? op.sourcePos.y * height : op.sourcePos.y;

    int32_t offX = static_cast<int32_t>(std::round(srcPxX - dstPxX));
    int32_t offY = static_cast<int32_t>(std::round(srcPxY - dstPxY));

    int32_t pad = static_cast<int32_t>(std::ceil(op.radius)) + 4;
    int32_t minX = std::max(0, static_cast<int32_t>(std::floor(dstPxX)) - pad);
    int32_t maxX = std::min(width - 1, static_cast<int32_t>(std::ceil(dstPxX)) + pad);
    int32_t minY = std::max(0, static_cast<int32_t>(std::floor(dstPxY)) - pad);
    int32_t maxY = std::min(height - 1, static_cast<int32_t>(std::ceil(dstPxY)) + pad);

    std::vector<float> maskWeights(static_cast<size_t>(width) * height, 0.0f);
    float innerR = std::max(0.0f, op.radius * (1.0f - op.feather));

    for (int32_t y = minY; y <= maxY; ++y) {
        for (int32_t x = minX; x <= maxX; ++x) {
            float dist = std::sqrt((x - dstPxX) * (x - dstPxX) + (y - dstPxY) * (y - dstPxY));
            if (dist <= op.radius) {
                float falloff = (dist <= innerR) ? 1.0f : (1.0f - smoothstep(innerR, op.radius, dist));
                maskWeights[y * width + x] = falloff * op.opacity;
            }
        }
    }

    std::vector<FloatRGBA> healed;
    PoissonSolver::solve(pixels, width, height, offX, offY, maskWeights, minX, minY, maxX, maxY, maxIterations, healed);
    pixels = std::move(healed);
}

void MaskEngine::processRetouchOps(std::vector<FloatRGBA>& pixels,
                                   int32_t width, int32_t height,
                                   const std::vector<RetouchOperation>& ops) {
    for (const auto& op : ops) {
        if (op.isHeal) {
            applyPoissonHeal(pixels, width, height, op);
        } else {
            applyCloneStamp(pixels, width, height, op);
        }
    }
}

// -------------------------------------------------------------------------
// PoissonSolver Implementation (Discrete Laplacian Membrane Difference)
// -------------------------------------------------------------------------

void PoissonSolver::solve(const std::vector<FloatRGBA>& destImage,
                          int32_t width, int32_t height,
                          int32_t srcOffsetX, int32_t srcOffsetY,
                          const std::vector<float>& maskWeights,
                          int32_t minX, int32_t minY, int32_t maxX, int32_t maxY,
                          int32_t iterations,
                          std::vector<FloatRGBA>& outHealedImage) {
    size_t total = static_cast<size_t>(width) * height;
    outHealedImage = destImage;

    // Difference field membrane d = f - g
    // At boundary / unmasked pixels, d = f* - g
    std::vector<FloatRGBA> diffPing(total);
    std::vector<FloatRGBA> diffPong(total);

    for (int32_t y = minY; y <= maxY; ++y) {
        for (int32_t x = minX; x <= maxX; ++x) {
            size_t idx = static_cast<size_t>(y) * width + x;
            int32_t sx = std::clamp(x + srcOffsetX, 0, width - 1);
            int32_t sy = std::clamp(y + srcOffsetY, 0, height - 1);
            const FloatRGBA& dst = destImage[idx];
            const FloatRGBA& src = destImage[sy * width + sx];
            if (maskWeights[idx] < 0.01f) {
                diffPing[idx] = FloatRGBA(dst.r - src.r, dst.g - src.g, dst.b - src.b, 1.0f);
            } else {
                diffPing[idx] = FloatRGBA(0.0f, 0.0f, 0.0f, 1.0f);
            }
            diffPong[idx] = diffPing[idx];
        }
    }

    // Jacobi relaxation for Delta d = 0 inside Omega
    for (int iter = 0; iter < iterations; ++iter) {
        for (int32_t y = minY + 1; y < maxY; ++y) {
            for (int32_t x = minX + 1; x < maxX; ++x) {
                size_t idx = static_cast<size_t>(y) * width + x;
                float m = maskWeights[idx];
                if (m > 0.01f) {
                    size_t left  = idx - 1;
                    size_t right = idx + 1;
                    size_t up    = idx - width;
                    size_t down  = idx + width;

                    diffPong[idx].r = 0.25f * (diffPing[left].r + diffPing[right].r + diffPing[up].r + diffPing[down].r);
                    diffPong[idx].g = 0.25f * (diffPing[left].g + diffPing[right].g + diffPing[up].g + diffPing[down].g);
                    diffPong[idx].b = 0.25f * (diffPing[left].b + diffPing[right].b + diffPing[up].b + diffPing[down].b);
                } else {
                    diffPong[idx] = diffPing[idx];
                }
            }
        }
        std::swap(diffPing, diffPong);
    }

    // Final seamless composite: f = g + d
    for (int32_t y = minY; y <= maxY; ++y) {
        for (int32_t x = minX; x <= maxX; ++x) {
            size_t idx = static_cast<size_t>(y) * width + x;
            float m = maskWeights[idx];
            if (m > 0.0f) {
                int32_t sx = std::clamp(x + srcOffsetX, 0, width - 1);
                int32_t sy = std::clamp(y + srcOffsetY, 0, height - 1);
                const FloatRGBA& src = destImage[sy * width + sx];
                const FloatRGBA& d = diffPing[idx];

                float healedR = std::max(0.0f, src.r + d.r);
                float healedG = std::max(0.0f, src.g + d.g);
                float healedB = std::max(0.0f, src.b + d.b);

                FloatRGBA& out = outHealedImage[idx];
                out.r = out.r * (1.0f - m) + healedR * m;
                out.g = out.g * (1.0f - m) + healedG * m;
                out.b = out.b * (1.0f - m) + healedB * m;
            }
        }
    }
}

} // namespace apex
