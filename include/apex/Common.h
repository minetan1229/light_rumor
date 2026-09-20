#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <array>
#include <memory>
#include <functional>
#include <algorithm>
#include <cmath>

namespace apex {

// Color space enum
enum class ColorSpace : uint32_t {
    LinearRec2020 = 0,
    LinearSRGB = 1,
    ACEScg = 2,
    sRGB = 3,
    DisplayP3 = 4,
    AdobeRGB = 5
};

// Chroma subsampling format for JPEG
enum class ChromaSubsampling : uint32_t {
    YUV444 = 0, // Highest quality, no chroma downsampling
    YUV420 = 1  // Standard compression
};

// Output file format
enum class ExportFormat : uint32_t {
    JPEG = 0,
    TIFF16 = 1,
    TIFF8 = 2,
    WebP = 3,
    LinearDNG = 4
};

// RGBA 32-bit floating point pixel in linear space
struct alignas(16) FloatRGBA {
    float r = 0.0f;
    float g = 0.0f;
    float b = 0.0f;
    float a = 1.0f;

    FloatRGBA() = default;
    constexpr FloatRGBA(float r_, float g_, float b_, float a_ = 1.0f) : r(r_), g(g_), b(b_), a(a_) {}
};

// Image rectangle / tile bounds
struct Rect {
    int32_t x = 0;
    int32_t y = 0;
    int32_t width = 0;
    int32_t height = 0;

    bool isValid() const { return width > 0 && height > 0; }
};

// Exif & Camera metadata structure
struct ExifMetadata {
    std::string make = "Sony";
    std::string model = "ILCE-7RM5";
    std::string lensModel = "FE 24-70mm F2.8 GM II";
    std::string dateTimeOriginal = "2026:09:20 12:30:45";
    std::string software = "Project APEX FIELD 1.0";
    
    // Exposure parameters
    double exposureTime = 1.0 / 250.0; // seconds (e.g. 1/250s)
    double fNumber = 2.8;              // f-stop (e.g. f/2.8)
    uint32_t isoSpeed = 100;           // ISO rating
    double focalLength = 50.0;         // mm
    double exposureBias = 0.0;         // EV
    
    // GPS Information
    bool hasGps = true;
    double gpsLatitude = 35.6762;      // Tokyo degrees North
    double gpsLongitude = 139.6503;    // Tokyo degrees East
    double gpsAltitude = 42.5;         // meters
};

// 8-color HSL mixer parameters
// Color bands: Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta
struct HSLBandAdjust {
    float hueShift = 0.0f;    // -100.0 to +100.0 degrees equivalent
    float saturation = 0.0f;  // -100.0 to +100.0 percent
    float luminance = 0.0f;   // -100.0 to +100.0 percent
};

// Full development parameters (32-bit linear processing)
struct DevelopmentParams {
    // 1. White Balance & Tint
    float kelvin = 5500.0f;       // 2000.0K to 12000.0K
    float tint = 0.0f;            // -100.0 to +100.0 (Green to Magenta)
    float shadowTintR = 0.0f;     // -50.0 to +50.0
    float shadowTintG = 0.0f;     // -50.0 to +50.0
    float shadowTintB = 0.0f;     // -50.0 to +50.0

    // 2. Basic Tone & Vibrance
    float exposureEV = 0.0f;      // -5.0 to +5.0 EV stops
    float contrast = 0.0f;        // -100.0 to +100.0
    float highlights = 0.0f;      // -100.0 to +100.0 (Highlight recovery)
    float shadows = 0.0f;         // -100.0 to +100.0 (Shadow lift)
    float whites = 0.0f;          // -100.0 to +100.0
    float blacks = 0.0f;          // -100.0 to +100.0
    float vibrance = 0.0f;        // -100.0 to +100.0 (Skin-protected saturation)
    float saturation = 0.0f;      // -100.0 to +100.0 (Global saturation)

    // 3. Tone Curve (1D LUT control points)
    // Normalized input/output pairs: 0.0 = black, 1.0 = white
    std::vector<float> toneCurveLUT; // 256 samples, identity if empty

    // 4. 8-Color Mixer & Monochrome
    bool isMonochrome = false;
    // 8-color mixer: Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta
    std::array<HSLBandAdjust, 8> hslBands;
    // 8-channel B&W optical filter weights (default standard panchromatic response)
    std::array<float, 8> monochromeWeights = {
        0.18f, // Red
        0.24f, // Orange
        0.22f, // Yellow
        0.16f, // Green
        0.08f, // Aqua
        0.06f, // Blue
        0.03f, // Purple
        0.03f  // Magenta
    };

    // 5. Detail & Noise Reduction
    float luminanceNR = 0.0f;       // 0.0 to 100.0
    float chromaNR = 0.0f;          // 0.0 to 100.0 (false-color removal)
    float sharpeningAmount = 0.0f;  // 0.0 to 150.0
    float sharpeningRadius = 1.0f;  // 0.5 to 3.0 pixels
    float sharpeningMasking = 20.0f;// 0.0 to 100.0 (edge masking threshold)

    // 6. Color Space & Dithering
    ColorSpace outputColorSpace = ColorSpace::sRGB;
    bool enableDithering = true;    // TPDF dithering to eliminate banding
};

// Export configuration
struct ExportOptions {
    ExportFormat format = ExportFormat::JPEG;
    int32_t jpegQuality = 98; // 1 to 100
    ChromaSubsampling chromaSubsampling = ChromaSubsampling::YUV444; // 4:4:4 for master quality
    bool embedExif = true;
    int32_t tileSize = 2048;   // 2048x2048 tiles to avoid OOM
    int32_t tilePadding = 16;  // Overlap padding to prevent seam artifacts
    bool useGpu = true;        // Vulkan acceleration when available
};

// Progress callback: percentage 0.0 to 100.0, status message
using ProgressCallback = std::function<void(float progressPercent, const std::string& status)>;

} // namespace apex
