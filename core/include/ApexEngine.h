#pragma once

#include <cstdint>
#include <vector>
#include <string>
#include <functional>

namespace apex {

struct DevelopParameters {
    // 露光 & トーン
    float exposureEV = 0.0f;          // ±5.0 EV
    float contrast = 0.0f;            // -100.0f ～ +100.0f
    float highlights = 0.0f;          // -100.0f ～ +100.0f
    float shadows = 0.0f;             // -100.0f ～ +100.0f
    float whites = 0.0f;              // -100.0f ～ +100.0f
    float blacks = 0.0f;              // -100.0f ～ +100.0f

    // ホワイトバランス & 色被り
    float temperatureKelvin = 5500.0f; // 2000K ～ 50000K
    float tint = 0.0f;                // -150.0f (Green) ～ +150.0f (Magenta)
    float shadowTint = 0.0f;          // -100.0f ～ +100.0f

    // 彩度
    float vibrance = 0.0f;            // -100.0f ～ +100.0f (肌色保護自然な彩度)
    float saturation = 0.0f;          // -100.0f ～ +100.0f

    // 質感
    float clarity = 0.0f;             // -100.0f ～ +100.0f
    float dehaze = 0.0f;              // -100.0f ～ +100.0f

    // モノクロ
    bool isMonochrome = false;
    float bwMixerWeights[8] = { 0.20f, 0.25f, 0.25f, 0.15f, 0.05f, 0.05f, 0.03f, 0.02f }; // R, O, Y, G, C, B, P, M

    // カラーミキサー (8色 HSL: R, O, Y, G, C, B, P, M)
    float hslHue[8] = { 0.0f };
    float hslSaturation[8] = { 0.0f };
    float hslLuminance[8] = { 0.0f };

    // ディテール & ノイズ低減
    float sharpenAmount = 40.0f;      // 0 ～ 150
    float sharpenRadius = 1.0f;       // 0.5 ～ 3.0
    float sharpenMasking = 0.0f;      // 0 ～ 100
    float luminanceDenoise = 0.0f;    // 0 ～ 100
    float colorDenoise = 25.0f;       // 0 ～ 100
};

enum class OutputFormat {
    JPEG_HIGH_QUALITY, // 4:4:4 Subsampling
    JPEG_STANDARD,     // 4:2:0 Subsampling
    TIFF_16BIT,
    WEBP_LOSSLESS,
    LINEAR_DNG
};

struct ExportConfig {
    OutputFormat format = OutputFormat::JPEG_HIGH_QUALITY;
    int quality = 95;                  // 1 ～ 100 (JPEG/WebP)
    int tileSize = 2048;               // タイル分割サイズ (OOM防止)
    bool applyDithering = true;        // TPDFディザリング
    std::string outputPath;
};

using ProgressCallback = std::function<void(float progress, const std::string& statusMessage)>;

class ColorPipeline {
public:
    static float sRgbToLinear(float sRgbVal);
    static float linearToSRgb(float linearVal);
    static void kelvinTintToRgbGains(float kelvin, float tint, float& rGain, float& gGain, float& bGain);

    // 32bit浮動小数点リニア画像バッファに対して現像パラメータを適用
    static void processTile(
        float* rgbaBuffer,
        int width,
        int height,
        const DevelopParameters& params
    );

    // 32bit浮動小数点から8bit整数へのTPDFディザリング量子化
    static void quantizeWithDither(
        const float* srcLinearRgba,
        uint8_t* dstRgb8,
        int width,
        int height
    );
};

class TileProcessor {
public:
    static bool processAndExport(
        const std::string& inputPath,
        const ExportConfig& config,
        const DevelopParameters& params,
        ProgressCallback progressCallback
    );
};

} // namespace apex
