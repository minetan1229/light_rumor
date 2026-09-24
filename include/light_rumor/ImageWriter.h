#pragma once

#include "light_rumor/Common.h"
#include <string>
#include <vector>
#include <cstdint>

namespace lightrumor {

class ImageWriter {
public:
    // Write 8-bit RGB image to JPEG in memory buffer
    static bool writeJPEGMemory(const uint8_t* rgbData,
                                int32_t width, int32_t height,
                                int32_t quality,
                                ChromaSubsampling subsampling,
                                const ExifMetadata* metadata,
                                std::vector<uint8_t>& outJpegBytes);

    // Write 8-bit RGB image to JPEG with Exif metadata
    static bool writeJPEG(const std::string& filePath,
                          const uint8_t* rgbData,
                          int32_t width, int32_t height,
                          int32_t quality,
                          ChromaSubsampling subsampling,
                          const ExifMetadata* metadata);

    // Write 16-bit RGB image to TIFF with Exif metadata
    static bool writeTIFF16(const std::string& filePath,
                            const uint16_t* rgb16Data,
                            int32_t width, int32_t height,
                            const ExifMetadata* metadata);

    // Write 16-bit RGB image to Linear DNG with Exif metadata
    static bool writeLinearDNG(const std::string& filePath,
                               const uint16_t* rgb16Data,
                               int32_t width, int32_t height,
                               const ExifMetadata* metadata);

    // Write 8-bit RGB image to TIFF with Exif metadata
    static bool writeTIFF8(const std::string& filePath,
                           const uint8_t* rgb8Data,
                           int32_t width, int32_t height,
                           const ExifMetadata* metadata);

    // Write 8-bit RGB image to WebP format with Exif metadata
    static bool writeWebP(const std::string& filePath,
                          const uint8_t* rgbData,
                          int32_t width, int32_t height,
                          int32_t quality,
                          const ExifMetadata* metadata);

    // Render elegant minimal typography watermark onto 8-bit RGB buffer
    static bool renderWatermark8(std::vector<uint8_t>& rgbData,
                                 int32_t& inOutWidth, int32_t& inOutHeight,
                                 const std::string& text,
                                 bool addBottomMargin = false);

    // Build raw APP1 Exif binary payload (returns byte vector)
    static std::vector<uint8_t> buildExifPayload(const ExifMetadata& metadata);
};

} // namespace lightrumor
