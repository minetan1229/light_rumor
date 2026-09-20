#include "../include/ApexEngine.h"
#include <cmath>
#include <algorithm>
#include <random>

namespace apex {

// sRGB to Linear (IEC 61966-2-1)
float ColorPipeline::sRgbToLinear(float val) {
    val = std::clamp(val, 0.0f, 1.0f);
    if (val <= 0.04045f) {
        return val / 12.92f;
    }
    return std::pow((val + 0.055f) / 1.055f, 2.4f);
}

// Linear to sRGB (OETF)
float ColorPipeline::linearToSRgb(float val) {
    val = std::clamp(val, 0.0f, 1.0f);
    if (val <= 0.0031308f) {
        return val * 12.92f;
    }
    return 1.055f * std::pow(val, 1.0f / 2.4f) - 0.055f;
}

// Kelvin + Tint to RGB Multipliers (Planckian Locus approximation)
void ColorPipeline::kelvinTintToRgbGains(float kelvin, float tint, float& rGain, float& gGain, float& bGain) {
    float temp = kelvin / 100.0f;
    float r, g, b;

    // Red
    if (temp <= 66.0f) {
        r = 255.0f;
    } else {
        r = temp - 60.0f;
        r = 329.698727446f * std::pow(r, -0.1332047592f);
        r = std::clamp(r, 0.0f, 255.0f);
    }

    // Green
    if (temp <= 66.0f) {
        g = temp;
        g = 99.4708025861f * std::log(g) - 161.1195681661f;
        g = std::clamp(g, 0.0f, 255.0f);
    } else {
        g = temp - 60.0f;
        g = 288.1221695283f * std::pow(g, -0.0755148492f);
        g = std::clamp(g, 0.0f, 255.0f);
    }

    // Blue
    if (temp >= 66.0f) {
        b = 255.0f;
    } else if (temp <= 19.0f) {
        b = 0.0f;
    } else {
        b = temp - 10.0f;
        b = 138.5177312231f * std::log(b) - 305.0447927307f;
        b = std::clamp(b, 0.0f, 255.0f);
    }

    // 基準5500Kに対する正規化ゲイン
    rGain = 255.0f / std::max(r, 1.0f);
    gGain = 255.0f / std::max(g, 1.0f);
    bGain = 255.0f / std::max(b, 1.0f);

    // 色被り (Tint: -150 Green ～ +150 Magenta)
    float tintFactor = tint / 150.0f;
    if (tintFactor > 0.0f) {
        // Magenta寄り (Greenを下げる)
        gGain *= (1.0f - tintFactor * 0.35f);
    } else {
        // Green寄り (Magentaを下げる = RとBを下げる)
        float gBoost = -tintFactor * 0.35f;
        rGain *= (1.0f - gBoost);
        bGain *= (1.0f - gBoost);
    }
}

// 32bit浮動小数点リニア画像バッファへの現像適用
void ColorPipeline::processTile(
    float* rgbaBuffer,
    int width,
    int height,
    const DevelopParameters& params
) {
    float rGain, gGain, bGain;
    kelvinTintToRgbGains(params.temperatureKelvin, params.tint, rGain, gGain, bGain);

    // 露光倍率 (Linear Gain = 2^EV)
    float exposureGain = std::pow(2.0f, params.exposureEV);

    // コントラストスロープ
    float contrastFactor = (params.contrast >= 0.0f)
        ? (1.0f + params.contrast / 100.0f * 1.5f)
        : (1.0f + params.contrast / 100.0f * 0.7f);

    int totalPixels = width * height;

    #pragma omp parallel for if(totalPixels > 65536)
    for (int i = 0; i < totalPixels; ++i) {
        int idx = i * 4;
        float r = rgbaBuffer[idx + 0];
        float g = rgbaBuffer[idx + 1];
        float b = rgbaBuffer[idx + 2];

        // 1. ホワイトバランス & 色被り
        r *= rGain;
        g *= gGain;
        b *= bGain;

        // 2. 露光量適用
        r *= exposureGain;
        g *= exposureGain;
        b *= exposureGain;

        // 3. ハイライト復元 & シャドウリフト
        float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        if (params.highlights != 0.0f && lum > 0.5f) {
            float hlWeight = (lum - 0.5f) * 2.0f; // 0.0 ～ 1.0
            float factor = 1.0f + (params.highlights / 100.0f) * 0.5f * hlWeight;
            r *= factor; g *= factor; b *= factor;
        }
        if (params.shadows != 0.0f && lum < 0.5f) {
            float shWeight = (0.5f - lum) * 2.0f; // 0.0 ～ 1.0
            float factor = 1.0f + (params.shadows / 100.0f) * 0.7f * shWeight;
            r *= factor; g *= factor; b *= factor;
        }

        // 4. コントラスト (中間値0.18のリニアS字)
        r = std::clamp(0.18f + (r - 0.18f) * contrastFactor, 0.0f, 10.0f);
        g = std::clamp(0.18f + (g - 0.18f) * contrastFactor, 0.0f, 10.0f);
        b = std::clamp(0.18f + (b - 0.18f) * contrastFactor, 0.0f, 10.0f);

        // 5. 自然な彩度 (Vibrance) & 彩度 (Saturation)
        float maxVal = std::max({r, g, b});
        float minVal = std::min({r, g, b});
        float sat = (maxVal > 1e-5f) ? (maxVal - minVal) / maxVal : 0.0f;

        // 肌色 (R > G > B かつ (R-B) が大きい) を検出してVibrance効果を保護
        float skinProtection = 1.0f;
        if (r > g && g > b) {
            float skinMetric = (r - b) / std::max(r, 1e-4f);
            skinProtection = 1.0f - std::clamp(skinMetric, 0.0f, 0.6f);
        }

        // 彩度が低い部分ほど強くブーストするVibrance重み
        float vibAmount = (params.vibrance / 100.0f) * (1.0f - sat) * skinProtection;
        float totalSatFactor = 1.0f + (params.saturation / 100.0f) + vibAmount;
        totalSatFactor = std::max(0.0f, totalSatFactor);

        float currentLum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        r = currentLum + (r - currentLum) * totalSatFactor;
        g = currentLum + (g - currentLum) * totalSatFactor;
        b = currentLum + (b - currentLum) * totalSatFactor;

        // 6. モノクロ処理
        if (params.isMonochrome) {
            float monoVal = 0.299f * r + 0.587f * g + 0.114f * b;
            r = monoVal;
            g = monoVal;
            b = monoVal;
        }

        rgbaBuffer[idx + 0] = r;
        rgbaBuffer[idx + 1] = g;
        rgbaBuffer[idx + 2] = b;
    }
}

// TPDF (Triangular Probability Density Function) ディザリング量子化
void ColorPipeline::quantizeWithDither(
    const float* srcLinearRgba,
    uint8_t* dstRgb8,
    int width,
    int height
) {
    int totalPixels = width * height;

    // スレッドセーフな擬似乱数ジェネレータ
    std::mt19937 rng(1337);
    std::uniform_real_distribution<float> dist(-0.5f, 0.5f);

    for (int i = 0; i < totalPixels; ++i) {
        int srcIdx = i * 4;
        int dstIdx = i * 3;

        for (int c = 0; c < 3; ++c) {
            float lin = srcLinearRgba[srcIdx + c];
            float srgb = linearToSRgb(lin);

            // TPDF ディザ (2つの均一乱数の加算 = 三角形分布)
            float dither = (dist(rng) + dist(rng)) / 255.0f;
            float quantized = srgb * 255.0f + dither * 255.0f + 0.5f;

            dstRgb8[dstIdx + c] = static_cast<uint8_t>(std::clamp(quantized, 0.0f, 255.0f));
        }
    }
}

} // namespace apex
