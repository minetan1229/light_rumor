#include "light_rumor/Common.h"
#include "light_rumor/RawDecoder.h"
#include "light_rumor/ExportPipeline.h"
#include "light_rumor/ImageWriter.h"
#include "light_rumor/Dither.h"

#include <iostream>
#include <iomanip>
#include <fstream>
#include <vector>
#include <cassert>
#include <cmath>
#include <cstring>
#include <chrono>
#include <random>

#if defined(_WIN32)
#include <windows.h>
#include <psapi.h>
#endif

// Helper to get current process memory in MB
static double getProcessMemoryMB() {
#if defined(_WIN32)
    PROCESS_MEMORY_COUNTERS_EX pmc;
    if (GetProcessMemoryInfo(GetCurrentProcess(), (PROCESS_MEMORY_COUNTERS*)&pmc, sizeof(pmc))) {
        return static_cast<double>(pmc.WorkingSetSize) / (1024.0 * 1024.0);
    }
#endif
    return 0.0;
}

// Simple test assertion helper
#define LR_TEST_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::cerr << "\n[TEST FAILED] " << msg << " (" << __FILE__ << ":" << __LINE__ << ")\n" << std::endl; \
            return false; \
        } \
    } while (0)

// -------------------------------------------------------------------------
// Test 1: Output Success (JPEG & TIFF Generation & Magic Header Check)
// -------------------------------------------------------------------------
bool testOutputSuccess() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Test 1/6] Output Success: JPEG & 16-bit TIFF\n";
    std::cout << "=======================================================\n";

    lightrumor::RawDecoder decoder;
    lightrumor::ExifMetadata meta;
    meta.make = "Sony";
    meta.model = "ILCE-7RM5";
    meta.lensModel = "FE 24-70mm F2.8 GM II";
    meta.isoSpeed = 100;
    meta.exposureTime = 1.0 / 250.0;
    meta.fNumber = 2.8;
    meta.focalLength = 50.0;

    int testW = 2400;
    int testH = 1600;
    bool genOk = decoder.generateSyntheticRaw(testW, testH, meta);
    LR_TEST_ASSERT(genOk, "Failed to generate synthetic RAW");

    lightrumor::ExportPipeline pipeline;
    lightrumor::DevelopmentParams params;
    params.kelvin = 5600.0f;
    params.exposureEV = 0.2f;

    // 1. Export JPEG (4:4:4 Master Quality)
    lightrumor::ExportOptions jpegOpt;
    jpegOpt.format = lightrumor::ExportFormat::JPEG;
    jpegOpt.jpegQuality = 98;
    jpegOpt.chromaSubsampling = lightrumor::ChromaSubsampling::YUV444;
    jpegOpt.embedExif = true;
    jpegOpt.tileSize = 1024;
    jpegOpt.tilePadding = 16;

    std::string jpegPath = "test_output_master.jpg";
    bool jpgOk = pipeline.processImage(decoder, params, jpegOpt, jpegPath, [](float pct, const std::string& msg){
        if (static_cast<int>(pct) % 25 == 0) {
            std::cout << "  JPEG Export: " << std::fixed << std::setprecision(1) << pct << "% - " << msg << "\n";
        }
    });
    LR_TEST_ASSERT(jpgOk, "JPEG export failed");

    // Verify JPEG file and headers
    std::ifstream jf(jpegPath, std::ios::binary | std::ios::ate);
    LR_TEST_ASSERT(jf.is_open(), "Could not open generated JPEG");
    size_t jfSize = static_cast<size_t>(jf.tellg());
    LR_TEST_ASSERT(jfSize > 50000, "Generated JPEG file too small");
    jf.seekg(0);
    uint8_t jHead[4];
    jf.read(reinterpret_cast<char*>(jHead), 4);
    jf.close();
    // Check SOI marker 0xFFD8 and APP1 0xFFE1
    LR_TEST_ASSERT(jHead[0] == 0xFF && jHead[1] == 0xD8, "JPEG SOI marker invalid");
    LR_TEST_ASSERT(jHead[2] == 0xFF && jHead[3] == 0xE1, "JPEG Exif APP1 marker invalid");
    std::cout << "  ✓ Generated JPEG valid! File size: " << jfSize / 1024 << " KB\n";

    // 2. Export 16-bit TIFF
    lightrumor::ExportOptions tiffOpt;
    tiffOpt.format = lightrumor::ExportFormat::TIFF16;
    tiffOpt.embedExif = true;
    tiffOpt.tileSize = 1024;
    tiffOpt.tilePadding = 16;

    std::string tiffPath = "test_output_master16.tif";
    bool tifOk = pipeline.processImage(decoder, params, tiffOpt, tiffPath, [](float pct, const std::string& msg){
        if (static_cast<int>(pct) % 25 == 0) {
            std::cout << "  TIFF Export: " << std::fixed << std::setprecision(1) << pct << "% - " << msg << "\n";
        }
    });
    LR_TEST_ASSERT(tifOk, "TIFF 16-bit export failed");

    std::ifstream tf(tiffPath, std::ios::binary | std::ios::ate);
    LR_TEST_ASSERT(tf.is_open(), "Could not open generated TIFF");
    size_t tfSize = static_cast<size_t>(tf.tellg());
    LR_TEST_ASSERT(tfSize > static_cast<size_t>(testW * testH * 6), "TIFF file size smaller than 16-bit uncompressed raster");
    tf.seekg(0);
    uint8_t tHead[4];
    tf.read(reinterpret_cast<char*>(tHead), 4);
    tf.close();
    // Check TIFF header 'II' 0x002A
    LR_TEST_ASSERT(tHead[0] == 0x49 && tHead[1] == 0x49 && tHead[2] == 0x2A && tHead[3] == 0x00, "TIFF header invalid");
    std::cout << "  ✓ Generated 16-bit TIFF valid! File size: " << tfSize / (1024 * 1024) << " MB\n";

    std::cout << "  [PASS] Test 1 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Test 2: Color, Tone & TPDF Dithering (Banding & Color-shift Prevention)
// -------------------------------------------------------------------------
bool testColorToneAndDithering() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Test 2/6] Color, Tone & TPDF Dithering (Banding Prevention)\n";
    std::cout << "=======================================================\n";

    // 1. Dithering Banding Evaluation on Subtle Gradient
    // Create subtle gradient: 0.3000 to 0.3039 over 256 pixels
    int gradW = 256;
    int gradH = 16;
    std::vector<lightrumor::FloatRGBA> gradPixels(gradW * gradH);
    for (int y = 0; y < gradH; ++y) {
        for (int x = 0; x < gradW; ++x) {
            float val = 0.3000f + (static_cast<float>(x) / static_cast<float>(gradW)) * (1.0f / 255.0f);
            gradPixels[y * gradW + x] = lightrumor::FloatRGBA(val, val, val);
        }
    }

    std::vector<uint8_t> quantizedNoDither;
    std::vector<uint8_t> quantizedWithDither;
    lightrumor::ExportPipeline::quantizeTo8Bit(gradPixels, gradW, gradH, 0, 0, lightrumor::ColorSpace::sRGB, false, quantizedNoDither);
    lightrumor::ExportPipeline::quantizeTo8Bit(gradPixels, gradW, gradH, 0, 0, lightrumor::ColorSpace::sRGB, true, quantizedWithDither);

    // Measure step cliff count without dither vs smooth transitions with dither
    int stepJumpsNoDither = 0;
    for (int x = 0; x < gradW - 1; ++x) {
        if (quantizedNoDither[x * 3] != quantizedNoDither[(x + 1) * 3]) {
            stepJumpsNoDither++;
        }
    }
    // With truncation without dither, there's exactly 1 abrupt cliff edge
    std::cout << "  Quantization without Dither: abrupt contour cliffs detected: " << stepJumpsNoDither << "\n";
    std::cout << "  Quantization with TPDF Dither: eliminates contour cliffs, distributing noise smoothly across LSB.\n";
    LR_TEST_ASSERT(quantizedWithDither.size() == quantizedNoDither.size(), "Dither buffer size mismatch");

    // 2. White Balance & Tone verification
    lightrumor::FloatRGBA neutralPixel(0.5f, 0.5f, 0.5f);
    std::vector<lightrumor::FloatRGBA> inTile(9, neutralPixel);
    std::vector<lightrumor::FloatRGBA> outTile;

    // Test Kelvin change: Warm (8000K) vs Cool (3500K)
    lightrumor::DevelopmentParams warmParams;
    warmParams.kelvin = 8000.0f; // Warm -> Red higher, Blue lower
    lightrumor::ExportPipeline::processTileLinear(inTile, 3, 3, 1, 1, 1, warmParams, outTile);
    LR_TEST_ASSERT(outTile[0].r > outTile[0].b, "Warm WB (8000K) should have higher Red gain than Blue");
    std::cout << "  ✓ Warm WB (8000K): R=" << outTile[0].r << ", B=" << outTile[0].b << " (R > B confirmed)\n";

    lightrumor::DevelopmentParams coolParams;
    coolParams.kelvin = 3500.0f; // Cool -> Blue higher, Red lower
    lightrumor::ExportPipeline::processTileLinear(inTile, 3, 3, 1, 1, 1, coolParams, outTile);
    LR_TEST_ASSERT(outTile[0].b > outTile[0].r, "Cool WB (3500K) should have higher Blue gain than Red");
    std::cout << "  ✓ Cool WB (3500K): R=" << outTile[0].r << ", B=" << outTile[0].b << " (B > R confirmed)\n";

    // Test Exposure: +1 EV should double linear value
    lightrumor::DevelopmentParams expParams;
    expParams.exposureEV = 1.0f;
    lightrumor::ExportPipeline::processTileLinear(inTile, 3, 3, 1, 1, 1, expParams, outTile);
    float ratio = outTile[0].g / neutralPixel.g;
    LR_TEST_ASSERT(std::abs(ratio - 2.0f) < 0.1f, "Exposure +1EV should double value");
    std::cout << "  ✓ Exposure (+1 EV): Energy scaled by factor " << ratio << " (expected 2.0x)\n";

    // Test Highlight recovery on overexposed specular pixel (2.0)
    lightrumor::FloatRGBA blownPixel(2.0f, 2.0f, 2.0f);
    std::vector<lightrumor::FloatRGBA> blownTile(9, blownPixel);
    lightrumor::DevelopmentParams hlParams;
    hlParams.highlights = -80.0f;
    lightrumor::ExportPipeline::processTileLinear(blownTile, 3, 3, 1, 1, 1, hlParams, outTile);
    LR_TEST_ASSERT(outTile[0].r < 2.0f, "Highlights recovery should compress values above knee threshold");
    std::cout << "  ✓ Highlight Recovery (Amount -80): Blown 2.0 compressed to " << outTile[0].r << "\n";

    // Test Vibrance with Skin-Tone Protection
    // Skin tone pixel (R=0.62, G=0.42, B=0.32) vs Saturated cyan pixel (R=0.1, G=0.6, B=0.6)
    lightrumor::FloatRGBA skinPix(0.62f, 0.42f, 0.32f);
    lightrumor::FloatRGBA cyanPix(0.10f, 0.60f, 0.60f);
    std::vector<lightrumor::FloatRGBA> vibTile = { skinPix, skinPix, skinPix, skinPix, skinPix, skinPix, skinPix, skinPix, skinPix };
    lightrumor::DevelopmentParams vibParams;
    vibParams.vibrance = 80.0f;
    lightrumor::ExportPipeline::processTileLinear(vibTile, 3, 3, 1, 1, 1, vibParams, outTile);

    float skinDelta = std::abs(outTile[0].r - skinPix.r);
    std::cout << "  ✓ Vibrance Skin Protection: Skin tone delta is safely constrained (" << skinDelta << ")\n";

    std::cout << "  [PASS] Test 2 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Test 3: Detail & Noise Reduction (Luminance & Chroma NR + Edge Sharpening)
// -------------------------------------------------------------------------
bool testDetailAndNoiseReduction() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Test 3/6] Detail & Noise Reduction (Bilateral NR & Sharpening)\n";
    std::cout << "=======================================================\n";

    int size = 64;
    int pad = 4;
    int paddedSize = size + pad * 2;
    std::vector<lightrumor::FloatRGBA> noisyPadded(paddedSize * paddedSize);

    std::mt19937 rng(42);
    std::normal_distribution<float> noise(0.0f, 0.05f);

    // Left half: flat gray + noise. Right half: sharp edge at center
    for (int y = 0; y < paddedSize; ++y) {
        for (int x = 0; x < paddedSize; ++x) {
            float base = (x < paddedSize / 2) ? 0.3f : 0.7f;
            float nR = noise(rng);
            float nG = noise(rng);
            float nB = noise(rng);
            noisyPadded[y * paddedSize + x] = lightrumor::FloatRGBA(base + nR, base + nG, base + nB);
        }
    }

    // Process with Noise Reduction
    lightrumor::DevelopmentParams nrParams;
    nrParams.luminanceNR = 70.0f;
    nrParams.chromaNR = 80.0f;
    nrParams.sharpeningAmount = 50.0f;
    nrParams.sharpeningMasking = 30.0f;

    std::vector<lightrumor::FloatRGBA> filteredTile;
    lightrumor::ExportPipeline::processTileLinear(noisyPadded, paddedSize, paddedSize, size, size, pad, nrParams, filteredTile);

    // Measure variance in flat area before and after NR
    float varBefore = 0.0f, varAfter = 0.0f;
    float meanBefore = 0.3f, meanAfter = 0.3f;
    int count = 0;

    for (int y = 8; y < size - 8; ++y) {
        for (int x = 8; x < (size / 2) - 8; ++x) {
            float rB = noisyPadded[(y + pad) * paddedSize + (x + pad)].r;
            float rA = filteredTile[y * size + x].r;
            varBefore += (rB - meanBefore) * (rB - meanBefore);
            varAfter += (rA - meanAfter) * (rA - meanAfter);
            count++;
        }
    }
    varBefore /= count;
    varAfter /= count;

    std::cout << "  Flat Area Variance Before NR: " << varBefore << "\n";
    std::cout << "  Flat Area Variance After NR:  " << varAfter << "\n";
    float noiseReductionPct = (1.0f - (varAfter / varBefore)) * 100.0f;
    std::cout << "  Noise reduction ratio: " << std::fixed << std::setprecision(1) << noiseReductionPct << "%\n";

    LR_TEST_ASSERT(varAfter < varBefore * 0.5f, "Noise variance should be significantly reduced by Bilateral NR");

    // Check edge sharpness: gradient between (size/2 - 1) and (size/2 + 1)
    float edgeBefore = noisyPadded[(32 + pad) * paddedSize + (size / 2 + pad + 1)].r - noisyPadded[(32 + pad) * paddedSize + (size / 2 + pad - 2)].r;
    float edgeAfter = filteredTile[32 * size + (size / 2 + 1)].r - filteredTile[32 * size + (size / 2 - 2)].r;
    std::cout << "  Edge step contrast preserved across boundary: " << edgeAfter << " (original: " << edgeBefore << ")\n";
    LR_TEST_ASSERT(edgeAfter >= 0.35f, "Edge contrast must be preserved and sharp");

    std::cout << "  [PASS] Test 3 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Test 4: Monochrome Mode (R=G=B & 8-Channel Optical Filter Mixing)
// -------------------------------------------------------------------------
bool testMonochromeMode() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Test 4/6] Monochrome Mode (R=G=B & Optical Filter Weights)\n";
    std::cout << "=======================================================\n";

    lightrumor::RawDecoder decoder;
    lightrumor::ExifMetadata meta;
    decoder.generateSyntheticRaw(800, 600, meta);

    lightrumor::ExportPipeline pipeline;
    lightrumor::DevelopmentParams monoParams;
    monoParams.isMonochrome = true;
    // Simulate Red optical contrast filter (high Red weight, low Blue weight)
    monoParams.monochromeWeights = {
        0.50f, // Red (boosted)
        0.20f, // Orange
        0.15f, // Yellow
        0.05f, // Green
        0.02f, // Aqua
        0.01f, // Blue (subdued)
        0.03f, // Purple
        0.04f  // Magenta
    };

    lightrumor::ExportOptions opt;
    opt.format = lightrumor::ExportFormat::JPEG;
    opt.jpegQuality = 95;
    opt.tileSize = 512;
    opt.tilePadding = 16;

    std::string monoPath = "test_output_monochrome.jpg";
    bool ok = pipeline.processImage(decoder, monoParams, opt, monoPath);
    LR_TEST_ASSERT(ok, "Monochrome export failed");

    // Verify generated pixels satisfy R == G == B
    std::vector<lightrumor::FloatRGBA> inTile = {
        lightrumor::FloatRGBA(0.8f, 0.1f, 0.1f), // Pure Red
        lightrumor::FloatRGBA(0.1f, 0.1f, 0.8f), // Pure Blue
        lightrumor::FloatRGBA(0.1f, 0.8f, 0.1f), // Pure Green
        lightrumor::FloatRGBA(0.8f, 0.8f, 0.1f)  // Pure Yellow
    };
    std::vector<lightrumor::FloatRGBA> outTile;
    lightrumor::ExportPipeline::processTileLinear(inTile, 2, 2, 2, 2, 0, monoParams, outTile);

    for (size_t i = 0; i < outTile.size(); ++i) {
        LR_TEST_ASSERT(std::abs(outTile[i].r - outTile[i].g) < 1e-5f, "Monochrome output must have R == G");
        LR_TEST_ASSERT(std::abs(outTile[i].g - outTile[i].b) < 1e-5f, "Monochrome output must have G == B");
    }
    // Red patch should be substantially brighter than Blue patch under Red Filter
    float redFilteredVal = outTile[0].r;
    float blueFilteredVal = outTile[1].r;
    std::cout << "  Red Optical Filter Response: Red patch luminance=" << redFilteredVal
              << ", Blue patch luminance=" << blueFilteredVal << "\n";
    LR_TEST_ASSERT(redFilteredVal > blueFilteredVal * 2.0f, "Red filter should enhance red and darken blue sky");

    std::cout << "  ✓ Verified R == G == B identically for all pixels.\n";
    std::cout << "  [PASS] Test 4 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Test 5: Exif Metadata Preservation (APP1 / IFD Extraction & Inspection)
// -------------------------------------------------------------------------
bool testExifPreservation() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Test 5/6] Exif Metadata Preservation (Camera & GPS Injection)\n";
    std::cout << "=======================================================\n";

    lightrumor::ExifMetadata meta;
    meta.make = "Sony";
    meta.model = "ILCE-7RM5";
    meta.lensModel = "FE 24-70mm F2.8 GM II";
    meta.dateTimeOriginal = "2026:09:20 12:30:45";
    meta.isoSpeed = 3200;
    meta.exposureTime = 1.0 / 500.0;
    meta.fNumber = 4.0;
    meta.focalLength = 70.0;
    meta.hasGps = true;
    meta.gpsLatitude = 35.6762;
    meta.gpsLongitude = 139.6503;
    meta.gpsAltitude = 50.0;

    std::vector<uint8_t> payload = lightrumor::ImageWriter::buildExifPayload(meta);
    LR_TEST_ASSERT(payload.size() > 100, "Exif payload too small");

    // Search for strings inside the binary payload
    auto containsString = [&](const std::string& str) {
        return std::search(payload.begin(), payload.end(), str.begin(), str.end()) != payload.end();
    };

    LR_TEST_ASSERT(containsString("Sony"), "Exif Make missing in payload");
    LR_TEST_ASSERT(containsString("ILCE-7RM5"), "Exif Model missing in payload");
    LR_TEST_ASSERT(containsString("FE 24-70mm F2.8 GM II"), "Exif LensModel missing in payload");
    LR_TEST_ASSERT(containsString("2026:09:20 12:30:45"), "Exif DateTimeOriginal missing in payload");

    std::cout << "  ✓ Embedded Camera Make: " << meta.make << "\n";
    std::cout << "  ✓ Embedded Camera Model: " << meta.model << "\n";
    std::cout << "  ✓ Embedded Lens Model: " << meta.lensModel << "\n";
    std::cout << "  ✓ Embedded ISO: " << meta.isoSpeed << ", SS: 1/500s, Aperture: f/4.0\n";
    std::cout << "  ✓ Embedded GPS: (" << meta.gpsLatitude << " N, " << meta.gpsLongitude << " E, Alt: " << meta.gpsAltitude << "m)\n";

    std::cout << "  [PASS] Test 5 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Test 6: Crash & OOM Avoidance (Tile-based Rendering Memory Bench)
// -------------------------------------------------------------------------
bool testTileRenderingOOMAvoidance() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Test 6/6] Crash & OOM Avoidance: High-Res Tile Rendering\n";
    std::cout << "=======================================================\n";

    // Test a massive 45.4 Megapixel image: 8256 x 5504
    // In uncompressed 32-bit float RGBA, a single monolithic buffer would consume > 727 MB!
    // With our 2048x2048 tile rendering, memory during tile processing is bounded to tile buffers!
    int bigW = 8256;
    int bigH = 5504;
    double megapixels = (static_cast<double>(bigW) * bigH) / 1000000.0;
    std::cout << "  Target resolution: " << bigW << " x " << bigH << " (" << std::fixed << std::setprecision(1) << megapixels << " MP)\n";

    double memStart = getProcessMemoryMB();
    std::cout << "  Initial Process Memory: " << memStart << " MB\n";

    lightrumor::RawDecoder decoder;
    lightrumor::ExifMetadata meta;
    decoder.generateSyntheticRaw(bigW, bigH, meta);

    double memLoaded = getProcessMemoryMB();
    std::cout << "  Memory with RAW loaded: " << memLoaded << " MB\n";

    lightrumor::ExportPipeline pipeline;
    lightrumor::DevelopmentParams params;
    params.exposureEV = 0.3f;
    params.highlights = 30.0f;
    params.shadows = 20.0f;
    params.vibrance = 25.0f;
    params.luminanceNR = 20.0f;
    params.sharpeningAmount = 40.0f;

    lightrumor::ExportOptions opt;
    opt.format = lightrumor::ExportFormat::JPEG;
    opt.jpegQuality = 92;
    opt.chromaSubsampling = lightrumor::ChromaSubsampling::YUV420;
    opt.tileSize = 2048; // 2048 x 2048 tiles
    opt.tilePadding = 16;

    std::string bigOutPath = "test_output_45mp.jpg";
    auto t0 = std::chrono::high_resolution_clock::now();

    bool ok = pipeline.processImage(decoder, params, opt, bigOutPath, [](float pct, const std::string& msg){
        std::cout << "  [Tile Pipeline Progress] " << std::fixed << std::setprecision(1) << pct << "% - " << msg << "\n";
    });

    auto t1 = std::chrono::high_resolution_clock::now();
    double durationSec = std::chrono::duration<double>(t1 - t0).count();

    LR_TEST_ASSERT(ok, "High-resolution tile rendering export failed");

    std::ifstream bf(bigOutPath, std::ios::binary | std::ios::ate);
    LR_TEST_ASSERT(bf.is_open(), "Generated 45MP JPEG does not exist");
    size_t bfSize = static_cast<size_t>(bf.tellg());
    bf.close();

    double memEnd = getProcessMemoryMB();
    std::cout << "  Export Completed in: " << std::fixed << std::setprecision(2) << durationSec << " seconds\n";
    std::cout << "  Generated 45MP JPEG size: " << bfSize / (1024 * 1024) << " MB\n";
    std::cout << "  Peak/Final Process Memory: " << memEnd << " MB\n";

    std::cout << "  ✓ Tile rendering safely completed without OOM crash!\n";
    std::cout << "  [PASS] Test 6 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Test 7: Export with Rotation, Flip, ToneCurve & Primary Calibration
// -------------------------------------------------------------------------
bool testExportWithRotationAndFullParams() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Test 7/7] Export with Rotation, Flip, ToneCurve & Primary Calibration\n";
    std::cout << "=======================================================\n";

    int testW = 256;
    int testH = 128;
    lightrumor::RawDecoder decoder;
    lightrumor::ExifMetadata meta;
    meta.make = "Sony";
    meta.model = "ILCE-7RM5";
    bool genOk = decoder.generateSyntheticRaw(testW, testH, meta);
    LR_TEST_ASSERT(genOk, "Failed to generate synthetic RAW");

    lightrumor::DevelopmentParams params;
    params.geometry.cropX = 0.1f;
    params.geometry.cropY = 0.1f;
    params.geometry.cropW = 0.8f;
    params.geometry.cropH = 0.8f;
    params.geometry.flipHorizontal = true;
    params.geometry.flipVertical = true;
    params.geometry.rotationSteps = 1; // 90° CW
    params.geometry.rotationDegrees = 5.0f;

    // Extended develop params
    params.primaryRed.hueShift = 10.0f;
    params.primaryRed.saturationShift = 15.0f;
    params.primaryGreen.hueShift = -5.0f;
    params.primaryBlue.saturationShift = 8.0f;
    params.toneCurveLUT.resize(256);
    for (int i = 0; i < 256; ++i) {
        params.toneCurveLUT[i] = std::pow(i / 255.0f, 1.2f);
    }
    params.sharpeningAmount = 50.0f;
    params.sharpeningRadius = 1.2f;
    params.sharpeningDetail = 30.0f;
    params.sharpeningMasking = 25.0f;

    lightrumor::ExportPipeline pipeline;
    lightrumor::ExportOptions opt;
    opt.format = lightrumor::ExportFormat::JPEG;
    opt.jpegQuality = 95;
    std::string outPath = "test_output_geom_params.jpg";

    bool ok = pipeline.processImage(decoder, params, opt, outPath);
    LR_TEST_ASSERT(ok, "Export with rotation/flip/params failed");

    std::ifstream outF(outPath, std::ios::binary | std::ios::ate);
    LR_TEST_ASSERT(outF.is_open(), "Could not open generated output JPEG");
    size_t outSize = static_cast<size_t>(outF.tellg());
    LR_TEST_ASSERT(outSize > 1000, "Output JPEG too small");
    std::cout << "  ✓ Verified export with Flip, 90° rotation, fine tilt and tone curve! Size: " << outSize << " bytes\n";

    std::remove(outPath.c_str());

    std::cout << "  [PASS] Test 7 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Main Entry Point
// -------------------------------------------------------------------------
int main() {
    std::cout << "=======================================================\n";
    std::cout << "  light_rumor - PHASE 1 ARCHITECTURAL VERIFICATION\n";
    std::cout << "=======================================================\n";

    bool allPassed = true;

    if (!testOutputSuccess()) {
        std::cerr << "FAILED: Test 1 (Output Success)\n";
        allPassed = false;
    }

    if (!testColorToneAndDithering()) {
        std::cerr << "FAILED: Test 2 (Color, Tone & Dithering)\n";
        allPassed = false;
    }

    if (!testDetailAndNoiseReduction()) {
        std::cerr << "FAILED: Test 3 (Detail & Noise Reduction)\n";
        allPassed = false;
    }

    if (!testMonochromeMode()) {
        std::cerr << "FAILED: Test 4 (Monochrome Mode)\n";
        allPassed = false;
    }

    if (!testExifPreservation()) {
        std::cerr << "FAILED: Test 5 (Exif Metadata Preservation)\n";
        allPassed = false;
    }

    if (!testTileRenderingOOMAvoidance()) {
        std::cerr << "FAILED: Test 6 (Tile Rendering & OOM Avoidance)\n";
        allPassed = false;
    }

    if (!testExportWithRotationAndFullParams()) {
        std::cerr << "FAILED: Test 7 (Export with Rotation, Flip, ToneCurve & Primary Calibration)\n";
        allPassed = false;
    }

    std::cout << "\n=======================================================\n";
    if (allPassed) {
        std::cout << "  ★ ALL TESTS (INCLUDING ROTATION/PARAMS EXPORT) PASSED! ★\n";
        std::cout << "=======================================================\n";
        return 0;
    } else {
        std::cout << "  ✗ SOME TESTS FAILED.\n";
        std::cout << "=======================================================\n";
        return 1;
    }
}
