#pragma once

#include "light_rumor/Common.h"
#include "light_rumor/RawDecoder.h"
#include "light_rumor/VulkanCompute.h"
#include "light_rumor/Dither.h"
#include <string>
#include <vector>
#include <memory>

namespace lightrumor {

class ExportPipeline {
public:
    ExportPipeline();
    ~ExportPipeline();

    // Process a full image using tile-based rendering (OOM-free architecture)
    // Streams 2048x2048 tiles with overlap padding
    bool processImage(RawDecoder& decoder,
                      const DevelopmentParams& params,
                      const ExportOptions& options,
                      const std::string& outputPath,
                      ProgressCallback progressCallback = nullptr);

    // Process a single tile buffer in 32-bit linear space (used internally or for testing)
    static void processTileLinear(const std::vector<FloatRGBA>& inPaddedTile,
                                  int32_t paddedW, int32_t paddedH,
                                  int32_t validW, int32_t validH,
                                  int32_t padding,
                                  const DevelopmentParams& params,
                                  std::vector<FloatRGBA>& outValidTile,
                                  int32_t padLeft = -1,
                                  int32_t padTop = -1);

    // Apply color space transfer function (OETF) and TPDF dithering to 8-bit RGBA
    static void quantizeTo8Bit(const std::vector<FloatRGBA>& linearTile,
                               int32_t width, int32_t height,
                               uint32_t tileStartX, uint32_t tileStartY,
                               ColorSpace cs, bool enableDither,
                               std::vector<uint8_t>& out8BitRGB);

    // Apply color space transfer function (OETF) and TPDF dithering to 16-bit RGB
    static void quantizeTo16Bit(const std::vector<FloatRGBA>& linearTile,
                                int32_t width, int32_t height,
                                uint32_t tileStartX, uint32_t tileStartY,
                                ColorSpace cs, bool enableDither,
                                std::vector<uint16_t>& out16BitRGB);

private:
    std::unique_ptr<VulkanCompute> m_vulkan;
};

} // namespace lightrumor
