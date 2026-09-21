#include "apex/WaveformEngine.h"
#include <chrono>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <iostream>

namespace apex {

struct WaveformEngine::Impl {
    std::vector<uint32_t> countR;
    std::vector<uint32_t> countG;
    std::vector<uint32_t> countB;
};

WaveformEngine::WaveformEngine() : m_impl(std::make_unique<Impl>()) {}

WaveformEngine::~WaveformEngine() = default;

bool WaveformEngine::init() {
    // Check Vulkan availability
    m_isVulkanAccelerated = false;
    return true;
}

double WaveformEngine::computeFromRGBA8(const uint8_t* rgbaPixels,
                                        int32_t width, int32_t height,
                                        WaveformMode mode,
                                        int32_t waveW, int32_t waveH,
                                        WaveformData& outWaveform) {
    if (!rgbaPixels || width <= 0 || height <= 0 || waveW <= 0 || waveH <= 0) {
        return 0.0;
    }

    auto tStart = std::chrono::high_resolution_clock::now();

    outWaveform.width = waveW;
    outWaveform.height = waveH;
    outWaveform.mode = mode;
    outWaveform.rgbaPixels.resize(static_cast<size_t>(waveW) * waveH);

    // Prepare count buffers
    size_t totalBins = static_cast<size_t>(waveW) * waveH;
    if (m_impl->countR.size() != totalBins) {
        m_impl->countR.resize(totalBins);
        m_impl->countG.resize(totalBins);
        m_impl->countB.resize(totalBins);
    }
    std::fill(m_impl->countR.begin(), m_impl->countR.end(), 0u);
    std::fill(m_impl->countG.begin(), m_impl->countG.end(), 0u);
    std::fill(m_impl->countB.begin(), m_impl->countB.end(), 0u);

    // Adaptive subsampling for ultra-high resolution images to stay under 5ms
    int stepX = std::max(1, width / (waveW * 2));
    int stepY = std::max(1, height / 512);

    if (mode == WaveformMode::Histogram) {
        // Frequency Histogram mode (256 bins)
        std::vector<uint32_t> histR(256, 0);
        std::vector<uint32_t> histG(256, 0);
        std::vector<uint32_t> histB(256, 0);

        for (int y = 0; y < height; y += stepY) {
            const uint8_t* row = rgbaPixels + static_cast<size_t>(y) * width * 4;
            for (int x = 0; x < width; x += stepX) {
                const uint8_t* px = row + x * 4;
                histR[px[0]]++;
                histG[px[1]]++;
                histB[px[2]]++;
            }
        }

        uint32_t maxCount = 1;
        for (int i = 0; i < 256; ++i) {
            maxCount = std::max(maxCount, std::max(histR[i], std::max(histG[i], histB[i])));
        }

        // Draw histogram into output buffer
        // Background color: Matte Obsidian #0A0A0C
        std::fill(outWaveform.rgbaPixels.begin(), outWaveform.rgbaPixels.end(), 0xFF0C0A0A);

        float hScale = static_cast<float>(waveH - 1) / static_cast<float>(maxCount);
        for (int x = 0; x < waveW; ++x) {
            int bin = std::clamp((x * 256) / waveW, 0, 255);
            int barHR = static_cast<int>(histR[bin] * hScale);
            int barHG = static_cast<int>(histG[bin] * hScale);
            int barHB = static_cast<int>(histB[bin] * hScale);

            for (int y = 0; y < waveH; ++y) {
                int distFromBottom = (waveH - 1) - y;
                bool hasR = distFromBottom <= barHR;
                bool hasG = distFromBottom <= barHG;
                bool hasB = distFromBottom <= barHB;

                if (hasR || hasG || hasB) {
                    uint8_t r = hasR ? 220 : 0;
                    uint8_t g = hasG ? 220 : 0;
                    uint8_t b = hasB ? 220 : 0;
                    // Format: 0xAABBGGRR
                    outWaveform.rgbaPixels[y * waveW + x] = 0xEE000000 | (b << 16) | (g << 8) | r;
                }
            }
        }

        drawGraticuleLines(outWaveform, true);

        auto tEnd = std::chrono::high_resolution_clock::now();
        return std::chrono::duration<double, std::milli>(tEnd - tStart).count();
    }

    // Waveform modes: RgbOverlay, RgbParade, Luma
    const int H = waveH;
    const int W = waveW;
    const int subW = W / 3;

    for (int y = 0; y < height; y += stepY) {
        const uint8_t* row = rgbaPixels + static_cast<size_t>(y) * width * 4;
        for (int x = 0; x < width; x += stepX) {
            const uint8_t* px = row + x * 4;
            uint8_t r = px[0];
            uint8_t g = px[1];
            uint8_t b = px[2];

            // Invert Y so that 0 is bottom, H-1 is top
            int yR = (H - 1) - ((r * (H - 1)) / 255);
            int yG = (H - 1) - ((g * (H - 1)) / 255);
            int yB = (H - 1) - ((b * (H - 1)) / 255);

            if (mode == WaveformMode::RgbOverlay) {
                int col = (x * W) / width;
                if (col >= 0 && col < W) {
                    m_impl->countR[yR * W + col]++;
                    m_impl->countG[yG * W + col]++;
                    m_impl->countB[yB * W + col]++;
                }
            }
            else if (mode == WaveformMode::RgbParade) {
                int subCol = (x * subW) / width;
                if (subCol >= 0 && subCol < subW) {
                    int colR = subCol;
                    int colG = subW + subCol;
                    int colB = 2 * subW + subCol;

                    m_impl->countR[yR * W + colR]++;
                    m_impl->countG[yG * W + colG]++;
                    m_impl->countB[yB * W + colB]++;
                }
            }
            else if (mode == WaveformMode::Luma) {
                int luma = static_cast<int>(0.2126f * r + 0.7152f * g + 0.0722f * b);
                int yL = (H - 1) - ((luma * (H - 1)) / 255);
                int col = (x * W) / width;
                if (col >= 0 && col < W) {
                    m_impl->countR[yL * W + col]++;
                    m_impl->countG[yL * W + col]++;
                    m_impl->countB[yL * W + col]++;
                }
            }
        }
    }

    // Find maximum counts for dynamic tone-scaling of intensity
    uint32_t maxHit = 1;
    for (size_t i = 0; i < totalBins; ++i) {
        maxHit = std::max(maxHit, std::max(m_impl->countR[i], std::max(m_impl->countG[i], m_impl->countB[i])));
    }

    // Scale intensity using soft logarithmic response for cinema glow
    float gain = 255.0f / std::pow(static_cast<float>(maxHit), 0.55f);

    // Render visual pixels into outWaveform
    // Background: Deep Matte Black (0xFF0C0A0A)
    const uint32_t bgPixel = 0xFF0C0A0A;
    const uint32_t dividerPixel = 0xFF2D2F33; // Titanium gray column divider

    for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x) {
            size_t idx = static_cast<size_t>(y) * W + x;

            if (mode == WaveformMode::RgbParade && (x == subW || x == 2 * subW)) {
                outWaveform.rgbaPixels[idx] = dividerPixel;
                continue;
            }

            uint32_t cR = m_impl->countR[idx];
            uint32_t cG = m_impl->countG[idx];
            uint32_t cB = m_impl->countB[idx];

            if (cR == 0 && cG == 0 && cB == 0) {
                outWaveform.rgbaPixels[idx] = bgPixel;
                continue;
            }

            int valR = cR > 0 ? std::clamp(static_cast<int>(std::pow(cR, 0.55f) * gain + 35.0f), 0, 255) : 0;
            int valG = cG > 0 ? std::clamp(static_cast<int>(std::pow(cG, 0.55f) * gain + 35.0f), 0, 255) : 0;
            int valB = cB > 0 ? std::clamp(static_cast<int>(std::pow(cB, 0.55f) * gain + 35.0f), 0, 255) : 0;

            if (mode == WaveformMode::RgbParade) {
                // In parade, each partition shows its distinct color channel
                if (x < subW) {
                    valG = 0;
                    valB = 0;
                } else if (x < 2 * subW) {
                    valR = 0;
                    valB = 0;
                } else {
                    valR = 0;
                    valG = std::min(valG / 4, 30); // Subtle cinema cyan touch
                }
            }

            // Alpha is high where there is data, format 0xAABBGGRR
            uint8_t alpha = static_cast<uint8_t>(std::clamp(std::max(valR, std::max(valG, valB)) + 60, 0, 255));
            outWaveform.rgbaPixels[idx] = (alpha << 24) | (valB << 16) | (valG << 8) | valR;
        }
    }

    drawGraticuleLines(outWaveform, true);

    auto tEnd = std::chrono::high_resolution_clock::now();
    return std::chrono::duration<double, std::milli>(tEnd - tStart).count();
}

double WaveformEngine::computeFromFloatRGBA(const FloatRGBA* floatPixels,
                                            int32_t width, int32_t height,
                                            WaveformMode mode,
                                            int32_t waveW, int32_t waveH,
                                            WaveformData& outWaveform) {
    if (!floatPixels || width <= 0 || height <= 0) return 0.0;

    // Convert float to 8-bit temporary buffer for instant calculation
    std::vector<uint8_t> rgba8(static_cast<size_t>(width) * height * 4);
    for (size_t i = 0; i < static_cast<size_t>(width) * height; ++i) {
        rgba8[i * 4 + 0] = static_cast<uint8_t>(std::clamp(floatPixels[i].r * 255.0f, 0.0f, 255.0f));
        rgba8[i * 4 + 1] = static_cast<uint8_t>(std::clamp(floatPixels[i].g * 255.0f, 0.0f, 255.0f));
        rgba8[i * 4 + 2] = static_cast<uint8_t>(std::clamp(floatPixels[i].b * 255.0f, 0.0f, 255.0f));
        rgba8[i * 4 + 3] = static_cast<uint8_t>(std::clamp(floatPixels[i].a * 255.0f, 0.0f, 255.0f));
    }

    return computeFromRGBA8(rgba8.data(), width, height, mode, waveW, waveH, outWaveform);
}

void WaveformEngine::drawGraticuleLines(WaveformData& waveform, bool isDarkTheme) {
    int W = waveform.width;
    int H = waveform.height;
    if (W <= 0 || H <= 0 || waveform.rgbaPixels.size() < static_cast<size_t>(W) * H) return;

    // Graticule color: Hairline titanium gray (semi-transparent)
    uint32_t graticuleColor = isDarkTheme ? 0x448E8E93 : 0x442D2F33;
    uint32_t skinColor = 0x55FF7900; // Cine Amber line at 70% skin reference

    // Standard IRE signal levels
    // 100% (IRE 100 - White clip): y = 0
    // 70%  (IRE 70  - Skin reference): y = H * 0.30
    // 50%  (IRE 50  - Mid gray): y = H * 0.50
    // 18%  (IRE 18  - 18% Neutral Gray): y = H * 0.82
    // 0%   (IRE 0   - Black pedestal): y = H - 1

    int y100 = 0;
    int y70  = static_cast<int>(H * 0.30f);
    int y50  = static_cast<int>(H * 0.50f);
    int y18  = static_cast<int>(H * 0.82f);
    int y0   = H - 1;

    auto drawDottedLine = [&](int y, uint32_t color) {
        if (y < 0 || y >= H) return;
        for (int x = 0; x < W; ++x) {
            // Dotted pattern (3 on, 3 off)
            if ((x % 6) < 3) {
                size_t idx = static_cast<size_t>(y) * W + x;
                uint32_t cur = waveform.rgbaPixels[idx];
                // Simple alpha blend over current pixel
                waveform.rgbaPixels[idx] = (cur & 0xFF000000) != 0 ? cur | 0x00333333 : color;
            }
        }
    };

    drawDottedLine(y100, graticuleColor);
    drawDottedLine(y70, skinColor);
    drawDottedLine(y50, graticuleColor);
    drawDottedLine(y18, graticuleColor);
    drawDottedLine(y0, graticuleColor);
}

} // namespace apex
