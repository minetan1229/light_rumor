#pragma once

#include "Common.h"
#include <vector>
#include <memory>
#include <string>

namespace apex {

class VulkanCompute {
public:
    VulkanCompute();
    ~VulkanCompute();

    // Initialize Vulkan compute context
    bool init();

    // Check if Vulkan compute is ready and available
    bool isAvailable() const { return m_isAvailable; }

    // Execute compute shader pipeline on a tile
    bool processTile(const std::vector<FloatRGBA>& inPaddedTile,
                     int32_t paddedW, int32_t paddedH,
                     int32_t tileW, int32_t tileH,
                     int32_t padding,
                     const DevelopmentParams& params,
                     std::vector<FloatRGBA>& outValidTile);

private:
    struct Impl;
    std::unique_ptr<Impl> m_impl;
    bool m_isAvailable = false;
};

} // namespace apex
