#pragma once


#include <cstdint>
#include <string>
#include <vector>
#include <array>
#include <memory>
#include <functional>
#include <algorithm>
#include <cmath>

namespace lightrumor {

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
    std::string software = "light_rumor 1.0";
    
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

// -------------------------------------------------------------------------
// Phase 4: Crop, Transform & Extended Engine Structures
// -------------------------------------------------------------------------

enum class AspectRatioMode : uint32_t {
    Original   = 0,
    Free       = 1,
    Ratio1x1   = 2,
    Ratio4x5   = 3,
    Ratio3x2   = 4,
    Ratio16x9  = 5,
    Ratio2x1   = 6,
    Ratio65x24 = 7, // Hasselblad XPan Panorama (2.7083:1)
    GoldenRatio= 8  // 1.618:1
};

enum class CompositionGuide : uint32_t {
    None         = 0,
    RuleOfThirds = 1,
    GoldenRatio  = 2,
    GoldenSpiral = 3,
    Diagonals    = 4,
    Grid         = 5
};

struct CropTransformParams {
    float cropX = 0.0f; // normalized 0..1
    float cropY = 0.0f;
    float cropW = 1.0f;
    float cropH = 1.0f;
    AspectRatioMode aspectRatio = AspectRatioMode::Original;
    float rotationDegrees = 0.0f; // fine rotation -45.0 to +45.0 deg
    int32_t rotationSteps = 0;    // 0: 0°, 1: 90° CW, 2: 180°, 3: 270° CW
    bool flipHorizontal = false;
    bool flipVertical = false;
    float perspectiveVertical = 0.0f;   // -100 to +100
    float perspectiveHorizontal = 0.0f; // -100 to +100
    float distortion = 0.0f;            // -100 to +100
    CompositionGuide guide = CompositionGuide::None;
};

struct PrimaryCalibration {
    float hueShift = 0.0f;        // -100 to +100
    float saturationShift = 0.0f; // -100 to +100
};

struct ColorWheelAdjust {
    float hue = 0.0f;        // 0 to 360 degrees
    float saturation = 0.0f; // 0 to 100%
    float luminance = 0.0f;  // -100 to +100%
};

struct SplitToningParams {
    float highlightsHue = 0.0f; // 0 to 360
    float highlightsSat = 0.0f; // 0 to 100
    float shadowsHue = 0.0f;    // 0 to 360
    float shadowsSat = 0.0f;    // 0 to 100
    float balance = 0.0f;       // -100 to +100
};

struct ColorGradingParams {
    ColorWheelAdjust shadows;
    ColorWheelAdjust midtones;
    ColorWheelAdjust highlights;
    ColorWheelAdjust global;
    float blending = 50.0f; // 0 to 100%
    float balance = 0.0f;   // -100 to +100%
};

struct LensCorrectionParams {
    bool enableProfileCorrection = false;
    float distortionCorrection = 100.0f; // %
    float vignettingCorrection = 100.0f; // %
    float chromaticAberration = 100.0f;  // %
    float defringePurple = 0.0f;         // 0 to 100
    float defringeGreen = 0.0f;          // 0 to 100
};

// -------------------------------------------------------------------------
// Phase 5: 9 Local Masks & Retouch Operations
// -------------------------------------------------------------------------

enum class MaskType : uint32_t {
    LinearGradient  = 0,
    RadialGradient  = 1,
    PolygonBezier   = 2,
    Brush           = 3,
    LuminanceRange  = 4,
    ColorRange      = 5,
    DepthMap        = 6,
    SobelEdge       = 7
};

enum class BooleanOp : uint32_t {
    Replace   = 0,
    Union     = 1, // Additive
    Subtract  = 2, // Subtractive
    Intersect = 3  // Multiplicative
};

enum class EasingMode : uint32_t {
    Linear     = 0,
    Smoothstep = 1
};

struct Point2D {
    float x = 0.0f; // normalized 0.0 to 1.0
    float y = 0.0f;

    Point2D() = default;
    constexpr Point2D(float x_, float y_) : x(x_), y(y_) {}
};

struct BrushStrokePoint {
    float x = 0.0f;
    float y = 0.0f;
    float pressure = 1.0f; // Stylus pressure sensitivity 0.0 to 1.0
    float radius = 25.0f;  // Base radius in pixels
    float flow = 1.0f;     // 0.0 to 1.0
    bool isEraser = false; // Eraser mode

    BrushStrokePoint() = default;
    constexpr BrushStrokePoint(float x_, float y_, float p_ = 1.0f, float r_ = 25.0f, float f_ = 1.0f, bool er_ = false)
        : x(x_), y(y_), pressure(p_), radius(r_), flow(f_), isEraser(er_) {}
};

// Local Tone & Color Adjustments applicable to any individual Mask Layer
struct LocalAdjustmentParams {
    float exposureEV = 0.0f;      // -5.0 to +5.0 EV stops
    float contrast = 0.0f;        // -100.0 to +100.0
    float highlights = 0.0f;      // -100.0 to +100.0
    float shadows = 0.0f;         // -100.0 to +100.0
    float whites = 0.0f;          // -100.0 to +100.0
    float blacks = 0.0f;          // -100.0 to +100.0
    float kelvinOffset = 0.0f;    // -3000.0K to +3000.0K shift
    float tintOffset = 0.0f;      // -100.0 to +100.0
    float saturation = 0.0f;      // -100.0 to +100.0
    float clarity = 0.0f;         // -100.0 to +100.0
    float dehaze = 0.0f;          // -100.0 to +100.0
};

struct MaskLayer {
    std::string id;
    std::string name = "Mask Layer";
    bool enabled = true;
    bool inverted = false;
    float opacity = 1.0f;         // 0.0 to 1.0
    MaskType type = MaskType::RadialGradient;
    BooleanOp booleanOp = BooleanOp::Union;

    // 1. Linear Gradient parameters
    Point2D linearStart = {0.2f, 0.2f};
    Point2D linearEnd = {0.8f, 0.8f};
    float linearFeather = 0.2f;
    EasingMode linearEasing = EasingMode::Linear;

    // 2. Radial / Elliptical Gradient parameters
    Point2D radialCenter = {0.5f, 0.5f};
    float radialRadiusX = 0.3f;
    float radialRadiusY = 0.3f;
    float radialAngle = 0.0f;     // Radians
    float radialFeather = 0.5f;   // 0.0 (sharp) to 1.0 (smooth)

    // 3. Polygon & Bezier parameters
    std::vector<Point2D> polygonVertices;
    float polygonFeather = 0.02f;

    // 4. Freehand Brush parameters
    std::vector<BrushStrokePoint> brushStrokes;
    float brushBaseRadius = 30.0f;
    float brushFeather = 0.5f;
    float brushDensity = 1.0f;

    // 5. Luminance Range parameters
    float lumaMin = 0.0f;         // 0.0 to 1.0
    float lumaMax = 1.0f;
    float lumaFeatherLow = 0.1f;
    float lumaFeatherHigh = 0.1f;

    // 6. Color Range parameters
    float colorTargetHue = 0.0f;  // 0 to 360 deg
    float colorTargetSat = 0.0f;  // 0 to 1.0
    float colorTargetLum = 0.5f;  // 0 to 1.0
    float colorTolHue = 30.0f;    // deg tolerance
    float colorTolSat = 0.3f;
    float colorTolLum = 0.3f;
    float colorFeather = 0.2f;

    // 7. Depth Map parameters
    float depthMin = 0.0f;        // 0.0 to 1.0
    float depthMax = 1.0f;
    float depthFeather = 0.15f;

    // 8. Edge / Sobel parameters
    float edgeThreshold = 0.15f;
    float edgeFeather = 0.05f;

    // Local photographic adjustments for this mask
    LocalAdjustmentParams adjustments;
};

// Retouch Operation (Clone Stamp & Poisson Healing Brush)
struct RetouchOperation {
    bool isHeal = true;           // false = Clone Stamp, true = Poisson Heal
    Point2D sourcePos = {0.0f, 0.0f};
    Point2D targetPos = {0.0f, 0.0f};
    float radius = 25.0f;         // In pixels
    float feather = 0.5f;         // 0.0 to 1.0
    float opacity = 1.0f;         // 0.0 to 1.0
};

// Full development parameters (32-bit linear processing)
struct DevelopmentParams {
    // 1. White Balance & Tint
    float kelvin = 5500.0f;       // 2000.0K to 50000.0K
    float tint = 0.0f;            // -150.0 to +150.0 (Green to Magenta)
    float shadowTintR = 0.0f;     // -50.0 to +50.0
    float shadowTintG = 0.0f;     // -50.0 to +50.0
    float shadowTintB = 0.0f;     // -50.0 to +50.0

    // 2. Camera Primary Calibration
    PrimaryCalibration primaryRed;
    PrimaryCalibration primaryGreen;
    PrimaryCalibration primaryBlue;

    // 3. Basic Tone, Presence & Vibrance
    float exposureEV = 0.0f;      // -5.0 to +5.0 EV stops
    float contrast = 0.0f;        // -100.0 to +100.0
    float highlights = 0.0f;      // -100.0 to +100.0 (Highlight recovery)
    float shadows = 0.0f;         // -100.0 to +100.0 (Shadow lift)
    float whites = 0.0f;          // -100.0 to +100.0
    float blacks = 0.0f;          // -100.0 to +100.0
    float vibrance = 0.0f;        // -100.0 to +100.0 (Skin-protected saturation)
    float saturation = 0.0f;      // -100.0 to +100.0 (Global saturation)
    float dehaze = 0.0f;          // -100.0 to +100.0 (Atmospheric scattering removal)
    float clarity = 0.0f;         // -100.0 to +100.0 (Mid-frequency contrast)
    float texture = 0.0f;         // -100.0 to +100.0 (High-frequency micro-contrast)

    // 4. Tone Curve (1D LUT control points)
    // Normalized input/output pairs: 0.0 = black, 1.0 = white
    std::vector<float> toneCurveLUT; // 256 samples, identity if empty

    // 5. 8-Color Mixer & Monochrome
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

    // 6. Detail & Noise Reduction
    float luminanceNR = 0.0f;           // 0.0 to 100.0
    float luminanceNRDetail = 50.0f;    // 0.0 to 100.0
    float luminanceNRContrast = 0.0f;   // 0.0 to 100.0
    float chromaNR = 0.0f;              // 0.0 to 100.0 (false-color removal)
    float chromaNRDetail = 50.0f;       // 0.0 to 100.0
    float chromaNRSmoothness = 50.0f;   // 0.0 to 100.0
    float sharpeningAmount = 0.0f;      // 0.0 to 150.0
    float sharpeningRadius = 1.0f;      // 0.5 to 3.0 pixels
    float sharpeningDetail = 25.0f;     // 0.0 to 100.0
    float sharpeningMasking = 20.0f;    // 0.0 to 100.0 (edge masking threshold)
    bool sharpeningPreviewMask = false; // B&W edge mask visualizer preview

    // 7. Split Toning & Color Grading
    SplitToningParams splitToning;
    ColorGradingParams colorGrading;

    // 8. 3D LUT (.cube)
    std::string lut3DPath;
    float lutOpacity = 100.0f;          // 0.0 to 100.0%

    // 9. Lens Correction
    LensCorrectionParams lensCorrection;

    // 10. Geometry & Crop Transform
    CropTransformParams geometry;

    // 11. Color Space & Dithering
    ColorSpace outputColorSpace = ColorSpace::sRGB;
    bool enableDithering = true;        // TPDF dithering to eliminate banding

    // 12. Phase 5: Local Mask Layers & Retouch Operations
    std::vector<MaskLayer> maskLayers;
    std::vector<RetouchOperation> retouchOps;
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
    int32_t maxLongEdge = 0;   // 0 = original resolution, e.g. 2048 for SNS
    bool enableWatermark = false;
    std::string watermarkText;
};

// Progress callback: percentage 0.0 to 100.0, status message
using ProgressCallback = std::function<void(float progressPercent, const std::string& status)>;

// -------------------------------------------------------------------------
// Phase 2: Culling, Rating, Sidecar & Batch Sync Structures
// -------------------------------------------------------------------------

enum class PickStatus : int32_t {
    Rejected = -1,
    None = 0,
    Picked = 1
};

enum class ColorLabel : int32_t {
    None = 0,
    Red = 1,
    Yellow = 2,
    Green = 3,
    Blue = 4,
    Purple = 5
};

struct BatchSyncMask {
    static constexpr uint32_t WhiteBalance       = 1 << 0;
    static constexpr uint32_t BasicTone          = 1 << 1;
    static constexpr uint32_t ColorMixer         = 1 << 2;
    static constexpr uint32_t DetailNR           = 1 << 3;
    static constexpr uint32_t ToneCurve          = 1 << 4;
    static constexpr uint32_t RatingLabel        = 1 << 5;
    static constexpr uint32_t GeometryTransform  = 1 << 6;
    static constexpr uint32_t ColorGrading       = 1 << 7;
    static constexpr uint32_t LensCorrection     = 1 << 8;
    static constexpr uint32_t LocalMasks         = 1 << 9;
    static constexpr uint32_t AllBasic           = WhiteBalance | BasicTone | ColorMixer | DetailNR;
    static constexpr uint32_t All                = 0xFFFFFFFF;
};

struct CullingItemMetadata {
    std::string id;
    std::string filePath;
    int32_t rating = 0;              // 0 to 5 stars
    PickStatus pickStatus = PickStatus::None;
    ColorLabel colorLabel = ColorLabel::None;
    int32_t width = 0;
    int32_t height = 0;
    std::string cameraModel;
    double exposureTime = 0.0;
    double fNumber = 0.0;
    uint32_t isoSpeed = 0;
    double focalLength = 0.0;
    bool isRaw = false;
};

// Batch Sync parameter merger: selectively copies fields from src to dst based on mask
inline void mergeDevelopmentParams(const DevelopmentParams& src, DevelopmentParams& dst, uint32_t mask) {
    if (mask & BatchSyncMask::WhiteBalance) {
        dst.kelvin = src.kelvin;
        dst.tint = src.tint;
        dst.shadowTintR = src.shadowTintR;
        dst.shadowTintG = src.shadowTintG;
        dst.shadowTintB = src.shadowTintB;
        dst.primaryRed = src.primaryRed;
        dst.primaryGreen = src.primaryGreen;
        dst.primaryBlue = src.primaryBlue;
    }
    if (mask & BatchSyncMask::BasicTone) {
        dst.exposureEV = src.exposureEV;
        dst.contrast = src.contrast;
        dst.highlights = src.highlights;
        dst.shadows = src.shadows;
        dst.whites = src.whites;
        dst.blacks = src.blacks;
        dst.vibrance = src.vibrance;
        dst.saturation = src.saturation;
        dst.dehaze = src.dehaze;
        dst.clarity = src.clarity;
        dst.texture = src.texture;
    }
    if (mask & BatchSyncMask::ColorMixer) {
        dst.isMonochrome = src.isMonochrome;
        dst.hslBands = src.hslBands;
        dst.monochromeWeights = src.monochromeWeights;
    }
    if (mask & BatchSyncMask::DetailNR) {
        dst.luminanceNR = src.luminanceNR;
        dst.luminanceNRDetail = src.luminanceNRDetail;
        dst.luminanceNRContrast = src.luminanceNRContrast;
        dst.chromaNR = src.chromaNR;
        dst.chromaNRDetail = src.chromaNRDetail;
        dst.chromaNRSmoothness = src.chromaNRSmoothness;
        dst.sharpeningAmount = src.sharpeningAmount;
        dst.sharpeningRadius = src.sharpeningRadius;
        dst.sharpeningDetail = src.sharpeningDetail;
        dst.sharpeningMasking = src.sharpeningMasking;
    }
    if (mask & BatchSyncMask::ToneCurve) {
        dst.toneCurveLUT = src.toneCurveLUT;
    }
    if (mask & BatchSyncMask::GeometryTransform) {
        dst.geometry = src.geometry;
    }
    if (mask & BatchSyncMask::ColorGrading) {
        dst.splitToning = src.splitToning;
        dst.colorGrading = src.colorGrading;
        dst.lut3DPath = src.lut3DPath;
        dst.lutOpacity = src.lutOpacity;
    }
    if (mask & BatchSyncMask::LensCorrection) {
        dst.lensCorrection = src.lensCorrection;
    }
    if (mask & BatchSyncMask::LocalMasks) {
        dst.maskLayers = src.maskLayers;
        dst.retouchOps = src.retouchOps;
    }
    if (mask == BatchSyncMask::All) {
        dst.outputColorSpace = src.outputColorSpace;
        dst.enableDithering = src.enableDithering;
    }
}

// -------------------------------------------------------------------------
// Phase 3: Instrument & Waveform Structures
// -------------------------------------------------------------------------
enum class WaveformMode : uint32_t {
    RgbOverlay = 0, // Cinema standard: R, G, B overlaid additively (R+G=Y, G+B=C, R+B=M, R+G+B=White)
    RgbParade  = 1, // 3-Column partitioned parade: Red (left), Green (mid), Blue (right)
    Histogram  = 2, // Standard full-frame frequency histogram
    Luma       = 3  // Cinema luminance waveform (Rec.709 luma)
};

struct WaveformData {
    int32_t width = 512;
    int32_t height = 256;
    WaveformMode mode = WaveformMode::RgbOverlay;
    std::vector<uint32_t> rgbaPixels; // Premultiplied or direct RGBA pixels (0xAABBGGRR / 0xAARRGGBB)
};

// -------------------------------------------------------------------------
// Phase 6: Field Tools, Computational Stacking, ColorChecker & Master Export
// -------------------------------------------------------------------------

// USB-C Tether shooting states and brand classification
enum class TetherCameraBrand : uint32_t {
    Unknown  = 0,
    Sony     = 1,
    Canon    = 2,
    Nikon    = 3,
    Fujifilm = 4,
    Panasonic= 5
};

enum class TetherState : uint32_t {
    Disconnected = 0,
    Connecting   = 1,
    Connected    = 2,
    Transferring = 3,
    Processing   = 4,
    Ready        = 5,
    Error        = 6
};

struct CameraExposureSettings {
    double shutterSpeed = 1.0 / 250.0; // Seconds
    double aperture = 2.8;             // f-number
    uint32_t iso = 100;                // ISO
    double exposureBias = 0.0;         // EV
    uint32_t batteryPercent = 95;      // %
    std::string whiteBalance = "Auto (White Priority)";
    std::string cameraModel = "ILCE-7RM5";
    std::string lensModel = "FE 24-70mm F2.8 GM II";
};

struct TetherTransferEvent {
    std::string filename;
    uint64_t fileSize = 0;
    double transferTimeMs = 0.0;
    double developTimeMs = 0.0;
    bool success = false;
    std::string previewId;
};

// Field Assistance Scopes
enum class FieldScopeMode : uint32_t {
    None         = 0,
    FalseColor   = 1,
    Zebra        = 2,
    FocusPeaking = 3,
    Vectorscope  = 4,
    Waveform     = 5,
    RgbParade    = 6
};

enum class PeakingColor : uint32_t {
    Red    = 0,
    Yellow = 1,
    Blue   = 2,
    White  = 3
};

struct FieldScopeConfig {
    FieldScopeMode mode = FieldScopeMode::None;
    float zebraThresholdIRE = 95.0f; // 95% to 100%
    PeakingColor peakingColor = PeakingColor::Red;
    float peakingThreshold = 0.15f;  // Edge sensitivity
    bool falseColorShowScale = true; // Show IRE sidebar
    float vectorscopeGain = 1.0f;
    float animPhase = 0.0f;          // Moving zebra stripe phase
};

// Computational Stacking
enum class StackingAlgorithm : uint32_t {
    FocusStacking            = 0, // Laplacian pyramid sharpness depth blend
    AstroKappaSigma          = 1, // Astro alignment + Kappa-Sigma clipping
    MedianLongExposure       = 2, // Median stacking for crowd/wave removal
    MeanLongExposure         = 3, // Linear average for smooth water & noise reduction
    MultipleExposure         = 4, // Multi-layer creative exposure blending
    PixelShiftSuperResolution= 5  // 4-shot sensor shift demosaicless full RGB
};

enum class MultiExposureBlendMode : uint32_t {
    Additive = 0,
    Average  = 1,
    Screen   = 2,
    Lighten  = 3, // 比較明
    Darken   = 4  // 比較暗
};

struct FocusStackParams {
    int32_t pyramidLevels = 4;
    float sharpnessExponent = 3.0f;
    int32_t featherRadius = 3;
    bool alignFrames = true;
};

struct AstroStackParams {
    float kappa = 2.0f;          // Sigma threshold for outlier clipping
    int32_t maxIterations = 3;   // Kappa-Sigma iterations
    int32_t maxStarCandidates = 150;
    float minStarSNR = 3.5f;     // Minimum SNR above sky background
    bool alignStars = true;      // Affine/Homography RANSAC alignment
};

// 24-ColorChecker Calibration Target
struct ColorCheckerPatchData {
    int32_t patchIndex = 0;      // 0 to 23
    std::string name;
    float targetLab[3] = {0.0f, 0.0f, 0.0f}; // Reference CIE L*a*b* D50/D65
    float targetRgb[3] = {0.0f, 0.0f, 0.0f}; // Reference sRGB (0..1)
    float measuredRgb[3] = {0.0f, 0.0f, 0.0f}; // Sampled from camera frame
    float calibratedRgb[3] = {0.0f, 0.0f, 0.0f};
    float deltaE00 = 0.0f;       // CIEDE2000 color difference
};

struct CalibrationResult {
    bool success = false;
    std::array<float, 9> colorMatrix = { // 3x3 camera sensor -> target XYZ/sRGB matrix
        1.0f, 0.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 1.0f
    };
    float estimatedKelvin = 5500.0f;
    float estimatedTint = 0.0f;
    float meanDeltaE00 = 0.0f;
    float maxDeltaE00 = 0.0f;
    std::vector<ColorCheckerPatchData> patches;
};

// ICC Soft Proofing
enum class ProofingIntent : uint32_t {
    Perceptual           = 0, // 知覚的
    RelativeColorimetric = 1, // 相対的色度 (standard for photography)
    Saturation           = 2, // 彩度
    AbsoluteColorimetric = 3  // 絶対的色度 (includes paper tint)
};

struct SoftProofConfig {
    bool enabled = false;
    ProofingIntent intent = ProofingIntent::RelativeColorimetric;
    bool simulatePaperWhite = true;
    bool simulateBlackInk = true;
    bool showGamutWarning = false;
    uint32_t gamutWarningColor = 0xFF50E3C2; // Vivid mint/teal overlay
    std::string paperProfilePath;
};

// Multi-Export Recipe
struct ExportRecipe {
    std::string recipeName;
    ExportFormat format = ExportFormat::JPEG;
    ColorSpace colorSpace = ColorSpace::sRGB;
    int32_t jpegQuality = 95;
    int32_t maxDimension = 0; // 0 = Full original resolution, e.g. 2048 for SNS
    bool applyEdgeSharpening = false;
    float sharpeningStrength = 0.0f;
    bool enableWatermark = false;
};

} // namespace lightrumor

namespace light_rumor {
    using namespace lightrumor;
}
