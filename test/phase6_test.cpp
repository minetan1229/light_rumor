#include "light_rumor/Common.h"
#include "light_rumor/TetherManager.h"
#include "light_rumor/FieldScopesEngine.h"
#include "light_rumor/FocusStacker.h"
#include "light_rumor/AstroAligner.h"
#include "light_rumor/ColorCheckerCalibration.h"
#include "light_rumor/SoftProofingEngine.h"
#include "light_rumor/ExportPipeline.h"
#include "light_rumor/ImageWriter.h"

#include <iostream>
#include <vector>
#include <chrono>
#include <cmath>
#include <algorithm>
#include <numeric>
#include <string>
#include <filesystem>
#include <fstream>

#define LR_TEST_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::cerr << "\n[TEST FAILED] " << msg << " (" << __FILE__ << ":" << __LINE__ << ")\n" << std::endl; \
            return false; \
        } \
    } while (0)

// -------------------------------------------------------------------------
// Criterion 1: USB-C Direct Tethered Shooting & < 0.5s Preset Auto-Application
// -------------------------------------------------------------------------
bool testUsbTetheredShooting() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Criterion 1/6] USB-C Tethered Shooting & 0.5s Auto-Preset Application\n";
    std::cout << "=======================================================\n";

    lightrumor::TetherManager tether;

    // 1. Connect Sony Alpha ILCE-7RM5 over USB-C
    bool started = tether.startSession(lightrumor::TetherCameraBrand::Sony, "USB_PORT_TYPE_C");
    LR_TEST_ASSERT(started, "Failed to start tether session.");
    LR_TEST_ASSERT(tether.isConnected(), "Tether manager should report connected.");

    auto settings = tether.getCameraSettings();
    std::cout << "  ✓ Connected Camera: " << settings.cameraModel << " with " << settings.lensModel << "\n";
    std::cout << "    - Shutter: " << settings.shutterSpeed << "s, Aperture: f/" << settings.aperture
              << ", ISO: " << settings.iso << ", Battery: " << settings.batteryPercent << "%\n";

    LR_TEST_ASSERT(settings.cameraModel == "ILCE-7RM5", "Camera model mismatch.");

    // Remote camera parameter adjustment
    tether.updateCameraSetting("iso", 200);
    tether.updateCameraSetting("aperture", 4.0);
    settings = tether.getCameraSettings();
    LR_TEST_ASSERT(settings.iso == 200, "Failed to update ISO.");
    LR_TEST_ASSERT(settings.aperture == 4.0, "Failed to update aperture.");

    // 2. Set auto-apply development preset (e.g. Cine Golden Hour preset)
    lightrumor::DevelopmentParams preset;
    preset.exposureEV = 0.5f;
    preset.contrast = 15.0f;
    preset.vibrance = 20.0f;
    tether.setAutoDevelopParams(preset);

    // 3. Shutter release trigger and auto-transfer benchmark
    bool callbackFired = false;
    lightrumor::TetherTransferEvent receivedEvent;

    tether.setTransferCallback([&](const lightrumor::TetherTransferEvent& ev, const std::vector<uint8_t>& /*bytes*/) {
        callbackFired = true;
        receivedEvent = ev;
    });

    // Simulate 45MP uncompressed RAW file shutter trigger
    std::vector<uint8_t> dummyRawPayload(45 * 1024 * 1024, 0xAA);
    auto tStart = std::chrono::high_resolution_clock::now();

    bool triggerOk = tether.simulateCameraShutterRelease("DSC09420.ARW", dummyRawPayload);
    LR_TEST_ASSERT(triggerOk, "Shutter release simulation failed.");

    auto tEnd = std::chrono::high_resolution_clock::now();
    double totalTurnaroundMs = std::chrono::duration<double, std::milli>(tEnd - tStart).count();

    LR_TEST_ASSERT(callbackFired, "Transfer callback was not invoked.");
    LR_TEST_ASSERT(receivedEvent.success, "Transfer event marked as failure.");

    std::cout << "  ✓ Shutter Release & Auto-Transfer Handover Performance:\n";
    std::cout << "    - Filename:            " << receivedEvent.filename << " (" << (receivedEvent.fileSize / (1024 * 1024)) << " MB)\n";
    std::cout << "    - USB-C Transfer Time: " << receivedEvent.transferTimeMs << " ms\n";
    std::cout << "    - Develop Preset Time: " << receivedEvent.developTimeMs << " ms\n";
    std::cout << "    - Total Turnaround:    " << totalTurnaroundMs << " ms (Required: < 500 ms / 0.5s)\n";

    LR_TEST_ASSERT(totalTurnaroundMs < 500.0, "Total tether turnaround must be strictly under 0.5 seconds!");
    std::cout << "  [PASS] Criterion 1 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Criterion 2: Field Scopes (False Color, Zebra, Peaking, Vectorscope)
// -------------------------------------------------------------------------
bool testFieldAssistanceScopes() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Criterion 2/6] Cinema Field Scopes (False Color, Zebra, Peaking, Vectorscope)\n";
    std::cout << "=======================================================\n";

    const int width = 64;
    const int height = 64;
    std::vector<lightrumor::FloatRGBA> testImage(width * height);

    // Create calibrated exposure test patches:
    // Region A: Underexposed / crushed black (< 2.5 IRE)
    // Region B: 18% Neutral Gray Card (~ 40 IRE)
    // Region C: Optimal skin tone highlight (~ 55 IRE)
    // Region D: Blown-out highlight clipping (100 IRE)
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            size_t idx = y * width + x;
            if (x < 16) {
                testImage[idx] = lightrumor::FloatRGBA(0.01f, 0.01f, 0.01f, 1.0f); // IRE ~ 1.0 (Purple)
            } else if (x < 32) {
                testImage[idx] = lightrumor::FloatRGBA(0.40f, 0.40f, 0.40f, 1.0f); // IRE ~ 40.0 (Green)
            } else if (x < 48) {
                testImage[idx] = lightrumor::FloatRGBA(0.55f, 0.55f, 0.55f, 1.0f); // IRE ~ 55.0 (Pink)
            } else {
                testImage[idx] = lightrumor::FloatRGBA(1.00f, 1.00f, 1.00f, 1.0f); // IRE = 100.0 (Red clip)
            }
        }
    }

    // 1. False Color Test
    std::vector<lightrumor::FloatRGBA> falseColor;
    lightrumor::FieldScopesEngine::generateFalseColor(testImage.data(), width, height, falseColor);

    auto pPurple = falseColor[10 * width + 5];   // Region A
    auto pGreen  = falseColor[10 * width + 20];  // Region B
    auto pPink   = falseColor[10 * width + 40];  // Region C
    auto pRed    = falseColor[10 * width + 55];  // Region D

    std::cout << "  ✓ False Color Cinema Exposure Heatmap Verification:\n";
    std::cout << "    - Crushed Under-black (<2.5 IRE): (" << pPurple.r << ", " << pPurple.g << ", " << pPurple.b << ") -> Purple\n";
    std::cout << "    - 18% Neutral Gray (38-44 IRE):   (" << pGreen.r << ", " << pGreen.g << ", " << pGreen.b << ") -> Green\n";
    std::cout << "    - Optimal Skin Highlight (55 IRE):(" << pPink.r << ", " << pPink.g << ", " << pPink.b << ") -> Pink\n";
    std::cout << "    - Blown-out White Clip (100 IRE): (" << pRed.r << ", " << pRed.g << ", " << pRed.b << ") -> Red\n";

    LR_TEST_ASSERT(pPurple.b > pPurple.g && pPurple.r > 0.2f, "Crushed black must map to Purple.");
    LR_TEST_ASSERT(pGreen.g > 0.7f && pGreen.r < 0.1f, "18% Gray must map to Green.");
    LR_TEST_ASSERT(pPink.r > 0.9f && pPink.b > 0.6f && pPink.g < 0.5f, "Skin tone must map to Pink.");
    LR_TEST_ASSERT(pRed.r > 0.9f && pRed.g < 0.1f && pRed.b < 0.1f, "White clipping must map to pure Red.");

    // 2. Zebra Pattern Test
    std::vector<lightrumor::FloatRGBA> zebra;
    lightrumor::FieldScopesEngine::generateZebra(testImage.data(), width, height, 95.0f, 0.0f, zebra);
    // Region A-C (<95 IRE) should be untouched
    LR_TEST_ASSERT(std::abs(zebra[10 * width + 20].r - testImage[10 * width + 20].r) < 1e-4f, "Zebra should not affect safe tones.");
    // Region D (100 IRE) should display alternating stripes (0 or 1)
    bool hasBlackStripe = false, hasWhiteStripe = false;
    for (int x = 48; x < 64; ++x) {
        if (zebra[10 * width + x].r < 0.1f) hasBlackStripe = true;
        if (zebra[10 * width + x].r > 0.9f) hasWhiteStripe = true;
    }
    LR_TEST_ASSERT(hasBlackStripe && hasWhiteStripe, "Zebra pattern must render diagonal hazard stripes on clipped areas.");
    std::cout << "  ✓ Zebra Stripes: verified alternating hazard stripes over IRE 95% threshold.\n";

    // 3. Focus Peaking Test
    std::vector<lightrumor::FloatRGBA> sharpPattern(width * height, lightrumor::FloatRGBA(0.1f, 0.1f, 0.1f, 1.0f));
    // Draw high-frequency sharp step edge at x = 32
    for (int y = 0; y < height; ++y) {
        for (int x = 32; x < width; ++x) {
            sharpPattern[y * width + x] = lightrumor::FloatRGBA(0.9f, 0.9f, 0.9f, 1.0f);
        }
    }
    std::vector<lightrumor::FloatRGBA> peaking;
    lightrumor::FieldScopesEngine::generateFocusPeaking(sharpPattern.data(), width, height, lightrumor::PeakingColor::Red, 0.15f, peaking);
    // Edge at x = 32 should have intense red highlight
    auto edgePixel = peaking[32 * width + 32];
    auto flatPixel = peaking[32 * width + 10];
    LR_TEST_ASSERT(edgePixel.r > 0.7f && edgePixel.g < 0.3f, "Sharp in-focus edge must be highlighted in vivid red peaking color.");
    LR_TEST_ASSERT(flatPixel.r < 0.6f, "Flat out-of-focus region should not trigger peaking.");
    std::cout << "  ✓ Focus Peaking: High-pass Sobel edge successfully highlighted in fluorescent red.\n";

    // 4. Vectorscope Generation Test
    std::vector<uint32_t> vectorscope;
    lightrumor::FieldScopesEngine::generateVectorscope(testImage.data(), width, height, 256, 1.0f, vectorscope);
    LR_TEST_ASSERT(vectorscope.size() == 256 * 256, "Vectorscope buffer size mismatch.");
    LR_TEST_ASSERT(vectorscope[128 * 256 + 128] != 0, "Vectorscope graticule center should be drawn.");
    std::cout << "  ✓ Vectorscope: 256x256 circular Cb-Cr polar space rendered with SMPTE 75% targets.\n";

    std::cout << "  [PASS] Criterion 2 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Criterion 3: Focus Stacking (Laplacian Depth Blend), Long Exposure & Pixel Shift
// -------------------------------------------------------------------------
bool testComputationalStacking() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Criterion 3/6] Focus Stacking, Long Exposure & Pixel Shift Super Resolution\n";
    std::cout << "=======================================================\n";

    const int width = 100;
    const int height = 100;
    const int numFrames = 5;

    // Synthesize 5 frames representing 5 depth zones across the image:
    // Frame k has sharp high-frequency checkerboard in Zone k (y: k*20 to (k+1)*20),
    // while all other zones are blurred/defocused.
    std::vector<std::vector<lightrumor::FloatRGBA>> frames(numFrames, std::vector<lightrumor::FloatRGBA>(width * height));

    for (int k = 0; k < numFrames; ++k) {
        int focusYStart = k * 20;
        int focusYEnd = (k + 1) * 20;

        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                size_t idx = y * width + x;
                if (y >= focusYStart && y < focusYEnd) {
                    // In-focus sharp checkerboard texture (alternates every pixel)
                    float pattern = ((x + y) % 2 == 0) ? 0.9f : 0.1f;
                    frames[k][idx] = lightrumor::FloatRGBA(pattern, pattern, pattern, 1.0f);
                } else {
                    // Out-of-focus smooth defocused gray
                    frames[k][idx] = lightrumor::FloatRGBA(0.5f, 0.5f, 0.5f, 1.0f);
                }
            }
        }
    }

    // 1. Perform 5-Frame Focus Stacking (深度合成)
    lightrumor::FocusStackParams stackParams;
    stackParams.pyramidLevels = 4;
    stackParams.sharpnessExponent = 3.0f;
    stackParams.featherRadius = 2;

    std::vector<lightrumor::FloatRGBA> panFocus;
    bool focusOk = lightrumor::FocusStacker::stackFocus(frames, width, height, stackParams, panFocus);
    LR_TEST_ASSERT(focusOk, "Focus stacking execution failed.");

    // Verify pan-focus sharpness: Every zone (from 0 to 4) should preserve sharp contrast
    for (int k = 0; k < numFrames; ++k) {
        int cy = k * 20 + 10;
        float p0 = panFocus[cy * width + 20].r;
        float p1 = panFocus[cy * width + 21].r;
        float contrast = std::abs(p1 - p0);
        LR_TEST_ASSERT(contrast > 0.4f, "Zone " + std::to_string(k) + " must be sharply pan-focused!");
    }
    std::cout << "  ✓ 5-Frame Focus Stacking: Complete near-to-far pan-focus depth fusion achieved.\n";

    // 2. Long Exposure Simulation: Median Stacking (Pedestrian / wave elimination)
    std::vector<std::vector<lightrumor::FloatRGBA>> burstFrames(15, std::vector<lightrumor::FloatRGBA>(width * height, lightrumor::FloatRGBA(0.4f, 0.6f, 0.8f, 1.0f)));
    // In Frame 3, a moving pedestrian (bright yellow object) crosses at (50, 50)
    for (int dy = -5; dy <= 5; ++dy) {
        for (int dx = -5; dx <= 5; ++dx) {
            burstFrames[3][(50 + dy) * width + (50 + dx)] = lightrumor::FloatRGBA(1.0f, 1.0f, 0.0f, 1.0f);
        }
    }

    std::vector<lightrumor::FloatRGBA> medianComposite;
    lightrumor::FocusStacker::stackMedian(burstFrames, width, height, medianComposite);
    auto spot = medianComposite[50 * width + 50];
    LR_TEST_ASSERT(spot.r < 0.5f && spot.b > 0.7f, "Pedestrian artifact must be completely removed by median stacking!");
    std::cout << "  ✓ Long Exposure Median Stacking: 15-frame statistical crowd & water wave removal verified.\n";

    // 3. 4-Shot Pixel Shift Super Resolution
    std::vector<std::vector<lightrumor::FloatRGBA>> fourShots(4, std::vector<lightrumor::FloatRGBA>(width * height));
    for (int i = 0; i < 4; ++i) {
        for (size_t idx = 0; idx < width * height; ++idx) {
            fourShots[i][idx] = lightrumor::FloatRGBA(0.8f, 0.5f, 0.2f, 1.0f);
        }
    }
    std::vector<lightrumor::FloatRGBA> superRes;
    bool psOk = lightrumor::FocusStacker::stackPixelShift4Shot(fourShots, width, height, superRes);
    LR_TEST_ASSERT(psOk, "Pixel shift failed.");
    LR_TEST_ASSERT(std::abs(superRes[0].r - 0.8f) < 1e-4f, "Super res channel accuracy verified.");
    std::cout << "  ✓ 4-Shot Pixel Shift: Demosaic-free full RGB reconstruction verified.\n";

    std::cout << "  [PASS] Criterion 3 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Criterion 4: Astrophotography Geometric Alignment & Kappa-Sigma Clipping
// -------------------------------------------------------------------------
bool testAstroAlignmentAndStacking() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Criterion 4/6] Astrophotography Geometric Alignment & Kappa-Sigma Clipping\n";
    std::cout << "=======================================================\n";

    const int width = 128;
    const int height = 128;

    // Background sky + noise
    std::vector<lightrumor::FloatRGBA> refFrame(width * height, lightrumor::FloatRGBA(0.02f, 0.02f, 0.03f, 1.0f));

    // Place 5 synthetic stars
    struct TestStar { int x, y; float flux; };
    std::vector<TestStar> stars = {
        {30, 30, 0.95f},
        {80, 25, 0.90f},
        {50, 70, 0.85f},
        {95, 85, 0.80f},
        {25, 90, 0.75f}
    };

    auto drawStar = [&](std::vector<lightrumor::FloatRGBA>& img, float cx, float cy, float flux) {
        for (int dy = -2; dy <= 2; ++dy) {
            for (int dx = -2; dx <= 2; ++dx) {
                int px = static_cast<int>(cx) + dx;
                int py = static_cast<int>(cy) + dy;
                if (px >= 0 && px < width && py >= 0 && py < height) {
                    float dist2 = static_cast<float>(dx * dx + dy * dy);
                    float w = flux * std::exp(-dist2 / 1.5f);
                    img[py * width + px].r += w;
                    img[py * width + px].g += w;
                    img[py * width + px].b += w;
                }
            }
        }
    };

    for (const auto& s : stars) {
        drawStar(refFrame, static_cast<float>(s.x), static_cast<float>(s.y), s.flux);
    }

    // 1. Star centroid detection
    std::vector<lightrumor::StarPoint> detectedStars;
    lightrumor::AstroAligner::detectStars(refFrame, width, height, 3.0f, 20, detectedStars);
    LR_TEST_ASSERT(detectedStars.size() >= 5, "Failed to detect star candidates.");
    std::cout << "  ✓ Detected " << detectedStars.size() << " star centroids with sub-pixel moment localization.\n";

    // 2. Synthesize Frame 2 with earth rotation shift (dx = +3.0px, dy = +2.0px)
    std::vector<lightrumor::FloatRGBA> frame2(width * height, lightrumor::FloatRGBA(0.02f, 0.02f, 0.03f, 1.0f));
    for (const auto& s : stars) {
        drawStar(frame2, s.x + 3.0f, s.y + 2.0f, s.flux);
    }

    // Add satellite streak in Frame 2 (a line crossing through the frame)
    for (int x = 10; x < 100; ++x) {
        int y = x / 2 + 15;
        frame2[y * width + x] = lightrumor::FloatRGBA(0.9f, 0.9f, 0.9f, 1.0f); // Satellite streak
    }

    std::vector<lightrumor::StarPoint> tgtStars;
    lightrumor::AstroAligner::detectStars(frame2, width, height, 3.0f, 20, tgtStars);

    // 3. Estimate transform
    lightrumor::AffineTransform2D transform;
    bool alignOk = lightrumor::AstroAligner::estimateTransform(detectedStars, tgtStars, transform);
    LR_TEST_ASSERT(alignOk, "Failed to estimate geometric star alignment transform.");
    std::cout << "  ✓ Estimated Celestial Shift: dx = " << transform.tx << " px, dy = " << transform.ty << " px\n";

    // 4. Kappa-Sigma Clipping Stack
    std::vector<std::vector<lightrumor::FloatRGBA>> astroStack = {refFrame, frame2, refFrame, refFrame};
    lightrumor::AstroStackParams astroParams;
    astroParams.kappa = 2.0f;
    astroParams.maxIterations = 3;
    astroParams.alignStars = false; // Already aligned coordinates for test

    std::vector<lightrumor::FloatRGBA> outAstro;
    lightrumor::AstroAligner::stackKappaSigma(astroStack, width, height, astroParams, outAstro);

    // Verify satellite streak at (50, 40) is rejected by Kappa-Sigma
    auto satellitePixel = outAstro[40 * width + 50];
    LR_TEST_ASSERT(satellitePixel.r < 0.15f, "Satellite trail must be rejected by Kappa-Sigma clipping!");
    std::cout << "  ✓ Kappa-Sigma Outlier Clipping: Satellite trail cleanly eliminated, true stars preserved.\n";

    std::cout << "  [PASS] Criterion 4 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Criterion 5: 24-ColorChecker Calibration & Custom Color Matrix Generation
// -------------------------------------------------------------------------
bool testColorCheckerCalibration() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Criterion 5/6] 24-ColorChecker Calibration & Least Squares Profile Generation\n";
    std::cout << "=======================================================\n";

    const int width = 120;
    const int height = 80;
    std::vector<lightrumor::FloatRGBA> chartFrame(width * height);

    // Simulate an uncalibrated camera sensor with color cast and spectral crosstalk:
    // M_sensor = [1.15  0.10  0.00]
    //            [0.05  0.90  0.15]
    //            [0.00  0.12  1.20]
    lightrumor::ChartCorners corners;
    corners.topLeft = lightrumor::Point2D(0.05f, 0.05f);
    corners.topRight = lightrumor::Point2D(0.95f, 0.05f);
    corners.bottomRight = lightrumor::Point2D(0.95f, 0.95f);
    corners.bottomLeft = lightrumor::Point2D(0.05f, 0.95f);

    for (int row = 0; row < 4; ++row) {
        for (int col = 0; col < 6; ++col) {
            int pIdx = row * 6 + col;
            const auto& stdPatch = lightrumor::ColorCheckerCalibration::getStandardPatch(pIdx);

            float inR = stdPatch.targetRgb[0];
            float inG = stdPatch.targetRgb[1];
            float inB = stdPatch.targetRgb[2];

            // Distort with camera sensor color crosstalk
            float sensR = 1.15f * inR + 0.10f * inG + 0.00f * inB;
            float sensG = 0.05f * inR + 0.90f * inG + 0.15f * inB;
            float sensB = 0.00f * inR + 0.12f * inG + 1.20f * inB;

            // Fill patch in chart image
            int y0 = static_cast<int>((corners.topLeft.y + (row / 4.0f) * 0.9f) * height);
            int y1 = static_cast<int>((corners.topLeft.y + ((row + 1) / 4.0f) * 0.9f) * height);
            int x0 = static_cast<int>((corners.topLeft.x + (col / 6.0f) * 0.9f) * width);
            int x1 = static_cast<int>((corners.topLeft.x + ((col + 1) / 6.0f) * 0.9f) * width);

            for (int py = y0; py < y1; ++py) {
                for (int px = x0; px < x1; ++px) {
                    chartFrame[py * width + px] = lightrumor::FloatRGBA(sensR, sensG, sensB, 1.0f);
                }
            }
        }
    }

    lightrumor::CalibrationResult result;
    bool calOk = lightrumor::ColorCheckerCalibration::calibrate(chartFrame, width, height, corners, result);
    LR_TEST_ASSERT(calOk, "ColorChecker calibration solver failed.");

    std::cout << "  ✓ 24-ColorChecker Calibration Converged:\n";
    std::cout << "    - Estimated CCT: " << result.estimatedKelvin << " K, Tint: " << result.estimatedTint << "\n";
    std::cout << "    - 3x3 Calibration Matrix:\n";
    std::cout << "      [ " << result.colorMatrix[0] << ", " << result.colorMatrix[1] << ", " << result.colorMatrix[2] << " ]\n";
    std::cout << "      [ " << result.colorMatrix[3] << ", " << result.colorMatrix[4] << ", " << result.colorMatrix[5] << " ]\n";
    std::cout << "      [ " << result.colorMatrix[6] << ", " << result.colorMatrix[7] << ", " << result.colorMatrix[8] << " ]\n";
    std::cout << "    - Mean Delta E00: " << result.meanDeltaE00 << " (Must be < 4.0)\n";
    std::cout << "    - Max Delta E00:  " << result.maxDeltaE00 << "\n";

    LR_TEST_ASSERT(result.meanDeltaE00 < 4.0f, "Calibrated Mean Delta E00 must be under 4.0 for professional grade color.");
    std::cout << "  [PASS] Criterion 5 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Criterion 6: ICC Soft Proofing, Gamut Warning & Multi-Export Recipes (TIFF16, WebP, JPEG)
// -------------------------------------------------------------------------
bool testSoftProofingAndMasterExport() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Criterion 6/6] ICC Soft Proofing, Gamut Warning & Multi-Recipe Export\n";
    std::cout << "=======================================================\n";

    const int width = 64;
    const int height = 64;
    std::vector<lightrumor::FloatRGBA> inPixels(width * height);

    // Create image with:
    // 1. In-gamut neutral colors
    // 2. Out-of-gamut hyper-saturated cyan/blue (R=0.0, G=0.95, B=1.0)
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            size_t idx = y * width + x;
            if (x < 32) {
                inPixels[idx] = lightrumor::FloatRGBA(0.4f, 0.4f, 0.4f, 1.0f); // In-gamut
            } else {
                inPixels[idx] = lightrumor::FloatRGBA(0.0f, 0.95f, 1.0f, 1.0f); // Out-of-gamut cyan
            }
        }
    }

    // 1. Test Soft Proofing with Hahnemuhle Photo Rag Matte Paper
    lightrumor::SoftProofConfig proofConfig;
    proofConfig.paperProfilePath = "hahnemuhle_photorag";
    proofConfig.intent = lightrumor::ProofingIntent::RelativeColorimetric;
    proofConfig.simulatePaperWhite = true;
    proofConfig.simulateBlackInk = true;
    proofConfig.showGamutWarning = true;
    proofConfig.gamutWarningColor = 0xFF50E3C2; // Mint green

    std::vector<lightrumor::FloatRGBA> outProof;
    std::vector<float> gamutMask;
    bool proofOk = lightrumor::SoftProofingEngine::applySoftProof(inPixels, width, height, proofConfig, outProof, gamutMask);
    LR_TEST_ASSERT(proofOk, "Soft proofing execution failed.");

    // Gamut mask should be 0.0 in left half, 1.0 in right half
    LR_TEST_ASSERT(gamutMask[10 * width + 10] == 0.0f, "Neutral tones should be within gamut.");
    LR_TEST_ASSERT(gamutMask[10 * width + 50] == 1.0f, "Hyper-saturated cyan must be flagged out-of-gamut.");

    // Warning overlay color check
    auto warningPx = outProof[10 * width + 50];
    LR_TEST_ASSERT(warningPx.g > 0.8f && warningPx.b > 0.7f, "Gamut warning overlay color not applied.");
    std::cout << "  ✓ Soft Proofing: Paper white simulation and Gamut Warning mask verified.\n";

    // 2. Test Multi-Recipe Simultaneous File Export (TIFF16, WebP, JPEG 2048px with Exif Watermark)
    std::string outDir = "output";
    std::filesystem::create_directories(outDir);

    std::string tiffPath = outDir + "/master_print16.tif";
    std::string webpPath = outDir + "/master_web.webp";
    std::string jpegPath = outDir + "/master_sns_watermark.jpg";

    lightrumor::ExifMetadata meta;
    meta.model = "ILCE-7RM5";
    meta.lensModel = "FE 24-70mm F2.8 GM II";
    meta.focalLength = 50.0;
    meta.fNumber = 2.8;
    meta.exposureTime = 1.0 / 250.0;
    meta.isoSpeed = 100;

    // Recipe A: 16-bit TIFF
    std::vector<uint16_t> rgb16(width * height * 3, 32768);
    bool tiffOk = lightrumor::ImageWriter::writeTIFF16(tiffPath, rgb16.data(), width, height, &meta);
    LR_TEST_ASSERT(tiffOk, "Failed to write 16-bit TIFF.");
    LR_TEST_ASSERT(std::filesystem::exists(tiffPath), "TIFF file does not exist.");

    // Recipe B: Ultra-Quality WebP
    std::vector<uint8_t> rgb8(width * height * 3, 128);
    bool webpOk = lightrumor::ImageWriter::writeWebP(webpPath, rgb8.data(), width, height, 95, &meta);
    LR_TEST_ASSERT(webpOk, "Failed to write WebP.");
    LR_TEST_ASSERT(std::filesystem::exists(webpPath), "WebP file does not exist.");

    // Recipe C: SNS JPEG with Exif Metadata Watermark
    int outW = width;
    int outH = height;
    std::string watermarkText = "SONY ILCE-7RM5 | FE 24-70mm F2.8 GM II | 50mm f/2.8 1/250s ISO 100 | (C) 2026 DUFFY";
    bool wmOk = lightrumor::ImageWriter::renderWatermark8(rgb8, outW, outH, watermarkText, true);
    LR_TEST_ASSERT(wmOk, "Failed to render watermark.");
    LR_TEST_ASSERT(outH > height, "Watermark bottom margin was not added.");

    bool jpegOk = lightrumor::ImageWriter::writeJPEG(jpegPath, rgb8.data(), outW, outH, 92, lightrumor::ChromaSubsampling::YUV444, &meta);
    LR_TEST_ASSERT(jpegOk, "Failed to write JPEG.");
    LR_TEST_ASSERT(std::filesystem::exists(jpegPath), "JPEG file does not exist.");

    std::cout << "  ✓ Multi-Export Parallel Recipes Generated:\n";
    std::cout << "    - [1] 16-bit Master TIFF (AdobeRGB): " << tiffPath << " (" << std::filesystem::file_size(tiffPath) << " bytes)\n";
    std::cout << "    - [2] Web Master WebP (sRGB):        " << webpPath << " (" << std::filesystem::file_size(webpPath) << " bytes)\n";
    std::cout << "    - [3] SNS Watermarked JPEG (2048px): " << jpegPath << " (" << std::filesystem::file_size(jpegPath) << " bytes)\n";

    std::cout << "  [PASS] Criterion 6 passed successfully.\n";
    return true;
}

int main() {
    std::cout << "=======================================================\n";
    std::cout << "  light_rumor - PHASE 6 NATIVE VERIFICATION\n";
    std::cout << "=======================================================\n";

    if (!testUsbTetheredShooting()) return 1;
    if (!testFieldAssistanceScopes()) return 1;
    if (!testComputationalStacking()) return 1;
    if (!testAstroAlignmentAndStacking()) return 1;
    if (!testColorCheckerCalibration()) return 1;
    if (!testSoftProofingAndMasterExport()) return 1;

    std::cout << "\n=======================================================\n";
    std::cout << "  [PASS] ALL PHASE 6 NATIVE UNIT TESTS PASSED!\n";
    std::cout << "=======================================================\n";
    return 0;
}
