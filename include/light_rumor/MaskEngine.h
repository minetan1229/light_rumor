#pragma once

#include "light_rumor/Common.h"
#include <vector>
#include <string>
#include <memory>

namespace lightrumor {

/**
 * MaskEngine: Phase 5 Multi-Layer Local Masking & Retouch Engine.
 * Supports:
 * - 9 local mask types: Linear, Radial, Polygon, Brush (pressure sensitive),
 *   Luminance Range, Color Range, Depth Map, Sobel Edge, and Boolean compositions.
 * - Boolean Operations: Replace, Union, Subtract, Intersect, Invert.
 * - Retouch Operations: Clone Stamp and Poisson Image Editing (Laplacian membrane healing).
 * - Full 32-bit linear floating point precision with OpenMP acceleration.
 */
class MaskEngine {
public:
    MaskEngine() = default;
    ~MaskEngine() = default;

    // Evaluate an individual mask layer into a normalized weight map [0.0, 1.0]
    static void evaluateSingleMask(const MaskLayer& layer,
                                   const std::vector<FloatRGBA>& inPixels,
                                   int32_t width, int32_t height,
                                   const std::vector<float>& depthBuffer,
                                   std::vector<float>& outMask,
                                   int32_t offsetX = 0, int32_t offsetY = 0,
                                   int32_t fullWidth = -1, int32_t fullHeight = -1);

    // Evaluate multiple mask layers using their Boolean combination operations
    static void evaluateCompositeMask(const std::vector<MaskLayer>& layers,
                                      const std::vector<FloatRGBA>& inPixels,
                                      int32_t width, int32_t height,
                                      const std::vector<float>& depthBuffer,
                                      std::vector<float>& outCompositeMask,
                                      int32_t offsetX = 0, int32_t offsetY = 0,
                                      int32_t fullWidth = -1, int32_t fullHeight = -1);

    // Apply local photographic adjustments modulated by mask weights
    static void applyLocalAdjustments(std::vector<FloatRGBA>& pixels,
                                      int32_t width, int32_t height,
                                      const std::vector<float>& maskWeights,
                                      const LocalAdjustmentParams& adj);

    // Process all enabled mask layers in sequential layer order
    static void processMaskLayers(std::vector<FloatRGBA>& pixels,
                                  int32_t width, int32_t height,
                                  const std::vector<MaskLayer>& layers,
                                  const std::vector<float>& depthBuffer = {},
                                  int32_t offsetX = 0, int32_t offsetY = 0,
                                  int32_t fullWidth = -1, int32_t fullHeight = -1);

    // Retouch: Clone Stamp (direct pixel copy with soft feather)
    static void applyCloneStamp(std::vector<FloatRGBA>& pixels,
                                int32_t width, int32_t height,
                                const RetouchOperation& op,
                                int32_t offsetX = 0, int32_t offsetY = 0,
                                int32_t fullWidth = -1, int32_t fullHeight = -1);

    // Retouch: Poisson Image Editing (seamless gradient membrane solver for dust/blemish healing)
    static void applyPoissonHeal(std::vector<FloatRGBA>& pixels,
                                 int32_t width, int32_t height,
                                 const RetouchOperation& op,
                                 int32_t maxIterations = 40,
                                 int32_t offsetX = 0, int32_t offsetY = 0,
                                 int32_t fullWidth = -1, int32_t fullHeight = -1);

    // Process all retouch operations
    static void processRetouchOps(std::vector<FloatRGBA>& pixels,
                                  int32_t width, int32_t height,
                                  const std::vector<RetouchOperation>& ops,
                                  int32_t offsetX = 0, int32_t offsetY = 0,
                                  int32_t fullWidth = -1, int32_t fullHeight = -1);

    // Helper functions for mask math
    static float evaluateLinearAt(float x, float y, const Point2D& start, const Point2D& end,
                                  float feather, EasingMode easing, bool invert);

    static float evaluateRadialAt(float x, float y, const Point2D& center,
                                  float rx, float ry, float angleRad,
                                  float feather, bool invert);

    static float evaluatePolygonAt(float x, float y, const std::vector<Point2D>& vertices,
                                   float feather, bool invert);

    static void rasterizeBrushStrokes(const std::vector<BrushStrokePoint>& strokes,
                                      float baseRadius, float feather,
                                      int32_t width, int32_t height,
                                      std::vector<float>& outMask,
                                      int32_t offsetX = 0, int32_t offsetY = 0,
                                      int32_t fullWidth = -1, int32_t fullHeight = -1);

    static float evaluateLuminanceAt(const FloatRGBA& p, float lumaMin, float lumaMax,
                                     float fLow, float fHigh, bool invert);

    static float evaluateColorRangeAt(const FloatRGBA& p, float targetH, float targetS, float targetL,
                                      float tolH, float tolS, float tolL, float feather, bool invert);

    static float evaluateDepthAt(float depthVal, float dMin, float dMax, float feather, bool invert);

    static void computeSobelEdgeMask(const std::vector<FloatRGBA>& pixels,
                                     int32_t width, int32_t height,
                                     float threshold, float feather, bool invert,
                                     std::vector<float>& outMask);
};

/**
 * PoissonSolver: Discrete Laplacian Membrane Difference Solver for Seamless Retouch.
 * Solves Delta d = 0 in Omega with Dirichlet boundary d = f* - g on boundary dOmega.
 */
class PoissonSolver {
public:
    static void solve(const std::vector<FloatRGBA>& destImage,
                      int32_t width, int32_t height,
                      int32_t srcOffsetX, int32_t srcOffsetY,
                      const std::vector<float>& maskWeights,
                      int32_t minX, int32_t minY, int32_t maxX, int32_t maxY,
                      int32_t iterations,
                      std::vector<FloatRGBA>& outHealedImage);
};

} // namespace lightrumor
