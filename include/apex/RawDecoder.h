#pragma once

#include "Common.h"
#include <string>
#include <vector>
#include <memory>

namespace apex {

class RawDecoder {
public:
    RawDecoder();
    ~RawDecoder();

    // Open and inspect RAW file
    bool openFile(const std::string& filePath);

    // Open from memory buffer
    bool openBuffer(const uint8_t* data, size_t size);

    // Close and release file handles
    void close();

    // Image properties
    int32_t getWidth() const { return m_width; }
    int32_t getHeight() const { return m_height; }
    int32_t getRawWidth() const { return m_rawWidth; }
    int32_t getRawHeight() const { return m_rawHeight; }
    bool isLoaded() const { return m_isLoaded; }

    // Metadata
    const ExifMetadata& getMetadata() const { return m_metadata; }

    // Tile extraction with overlap padding for OOM-free processing
    // outPaddedRect receives the actual coordinates including padding within the image
    bool extractTile(int32_t tileX, int32_t tileY, 
                     int32_t tileW, int32_t tileH, 
                     int32_t padding, 
                     std::vector<FloatRGBA>& outBuffer,
                     Rect& outPaddedRect);

    // Synthetic high-res RAW generator for testing / benchmarks (e.g. 45MP-60MP)
    // Produces 14-bit linear floating point raw data with sky gradients, color patches, fine details, and noise
    bool generateSyntheticRaw(int32_t width, int32_t height, const ExifMetadata& metadata);

private:
    struct Impl;
    std::unique_ptr<Impl> m_impl;

    int32_t m_width = 0;
    int32_t m_height = 0;
    int32_t m_rawWidth = 0;
    int32_t m_rawHeight = 0;
    bool m_isLoaded = false;
    ExifMetadata m_metadata;

    // Linear 32-bit float image data storage (when loaded or generated)
    // In tile streaming mode, this can also stream line-by-line or tile-by-tile
    std::vector<FloatRGBA> m_linearBuffer;
};

} // namespace apex
