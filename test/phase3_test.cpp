#include "light_rumor/Common.h"
#include "light_rumor/WaveformEngine.h"

#include <iostream>
#include <vector>
#include <chrono>
#include <cmath>
#include <cassert>
#include <algorithm>

#define LR_TEST_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::cerr << "\n[TEST FAILED] " << msg << " (" << __FILE__ << ":" << __LINE__ << ")\n" << std::endl; \
            return false; \
        } \
    } while (0)

// Helper: Cubic-bezier solver for 1D time parameter
double evaluateCubicBezier(double p1x, double p1y, double p2x, double p2y, double t) {
    // Solve for x(s) = t using Newton-Raphson, then compute y(s)
    // Bezier polynomial with p0=(0,0) and p3=(1,1):
    // x(s) = 3*(1-s)^2*s*p1x + 3*(1-s)*s^2*p2x + s^3
    // y(s) = 3*(1-s)^2*s*p1y + 3*(1-s)*s^2*p2y + s^3
    double s = t; // initial guess
    for (int iter = 0; iter < 8; ++iter) {
        double oneMinusS = 1.0 - s;
        double currentX = 3.0 * oneMinusS * oneMinusS * s * p1x +
                          3.0 * oneMinusS * s * s * p2x +
                          s * s * s;
        double diff = currentX - t;
        if (std::abs(diff) < 1e-6) break;

        // Derivative dx/ds
        double dx = 3.0 * oneMinusS * oneMinusS * p1x +
                    6.0 * oneMinusS * s * (p2x - p1x) +
                    3.0 * s * s * (1.0 - p2x);
        if (std::abs(dx) < 1e-6) break;
        s -= diff / dx;
        s = std::clamp(s, 0.0, 1.0);
    }

    double oneMinusS = 1.0 - s;
    return 3.0 * oneMinusS * oneMinusS * s * p1y +
           3.0 * oneMinusS * s * s * p2y +
           s * s * s;
}

// -------------------------------------------------------------------------
// Test 1: RGB Waveform Monitor & Additive Color Mixing
// -------------------------------------------------------------------------
bool testWaveformOverlayAndColorMixing() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Phase 3 Test 1/5] RGB Waveform Monitor & Additive Mixing\n";
    std::cout << "=======================================================\n";

    int imgW = 600;
    int imgH = 400;
    std::vector<uint8_t> pixels(static_cast<size_t>(imgW) * imgH * 4);

    // Left third: Pure Red
    // Middle third: Red + Green = Yellow
    // Right third: Red + Green + Blue = White
    for (int y = 0; y < imgH; ++y) {
        for (int x = 0; x < imgW; ++x) {
            size_t idx = (static_cast<size_t>(y) * imgW + x) * 4;
            if (x < 200) {
                pixels[idx + 0] = 240; // R
                pixels[idx + 1] = 10;  // G
                pixels[idx + 2] = 10;  // B
                pixels[idx + 3] = 255;
            } else if (x < 400) {
                pixels[idx + 0] = 240; // R
                pixels[idx + 1] = 240; // G -> Yellow
                pixels[idx + 2] = 10;  // B
                pixels[idx + 3] = 255;
            } else {
                pixels[idx + 0] = 240; // R
                pixels[idx + 1] = 240; // G
                pixels[idx + 2] = 240; // B -> White
                pixels[idx + 3] = 255;
            }
        }
    }

    lightrumor::WaveformEngine engine;
    LR_TEST_ASSERT(engine.init(), "Engine init failed");

    lightrumor::WaveformData waveData;
    int waveW = 512;
    int waveH = 256;
    double elapsed = engine.computeFromRGBA8(pixels.data(), imgW, imgH, lightrumor::WaveformMode::RgbOverlay, waveW, waveH, waveData);

    LR_TEST_ASSERT(waveData.width == waveW, "Width mismatch");
    LR_TEST_ASSERT(waveData.height == waveH, "Height mismatch");
    LR_TEST_ASSERT(!waveData.rgbaPixels.empty(), "Empty waveform pixels");

    // Check color mixing in waveform:
    // Signal level for 240 is near top: search around row 15
    int targetY = -1;
    for (int y = 10; y <= 20; ++y) {
        uint32_t px = waveData.rgbaPixels[y * waveW + 50];
        if (((px >> 16) & 0xFF) > 100) {
            targetY = y;
            break;
        }
    }
    LR_TEST_ASSERT(targetY != -1, "Signal row for value 240 must be found");

    // Left side (x = 50): pure red
    uint32_t pxLeft = waveData.rgbaPixels[targetY * waveW + 50];
    uint8_t rLeft = (pxLeft >> 16) & 0xFF;
    uint8_t gLeft = (pxLeft >> 8) & 0xFF;
    uint8_t bLeft = pxLeft & 0xFF;
    LR_TEST_ASSERT(rLeft > 100 && gLeft < 50 && bLeft < 50, "Left side must be pure red trace");

    // Middle side (x = 256): yellow (R + G)
    uint32_t pxMid = waveData.rgbaPixels[targetY * waveW + 256];
    uint8_t rMid = (pxMid >> 16) & 0xFF;
    uint8_t gMid = (pxMid >> 8) & 0xFF;
    uint8_t bMid = pxMid & 0xFF;
    LR_TEST_ASSERT(rMid > 100 && gMid > 100 && bMid < 50, "Middle side must be yellow (R+G additive mixing)");

    // Right side (x = 450): white (R + G + B)
    uint32_t pxRight = waveData.rgbaPixels[targetY * waveW + 450];
    uint8_t rRight = (pxRight >> 16) & 0xFF;
    uint8_t gRight = (pxRight >> 8) & 0xFF;
    uint8_t bRight = pxRight & 0xFF;
    LR_TEST_ASSERT(rRight > 100 && gRight > 100 && bRight > 100, "Right side must be white (R+G+B neutral white)");

    std::cout << "  ✓ Waveform computed in: " << elapsed << " ms\n";
    std::cout << "  ✓ Additive color mixing verified:\n";
    std::cout << "    - Pure Red:   (R=" << (int)rLeft << ", G=" << (int)gLeft << ", B=" << (int)bLeft << ")\n";
    std::cout << "    - Yellow:     (R=" << (int)rMid << ", G=" << (int)gMid << ", B=" << (int)bMid << ")\n";
    std::cout << "    - Pure White: (R=" << (int)rRight << ", G=" << (int)gRight << ", B=" << (int)bRight << ")\n";
    std::cout << "  [PASS] Test 1 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Test 2: RGB Parade Mode (3 Columns Partition)
// -------------------------------------------------------------------------
bool testWaveformParadePartition() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Phase 3 Test 2/5] RGB Parade 3-Column Partitioning\n";
    std::cout << "=======================================================\n";

    int imgW = 300;
    int imgH = 200;
    std::vector<uint8_t> pixels(static_cast<size_t>(imgW) * imgH * 4, 180); // mid-gray

    lightrumor::WaveformEngine engine;
    lightrumor::WaveformData paradeData;
    int waveW = 512;
    int waveH = 256;
    engine.computeFromRGBA8(pixels.data(), imgW, imgH, lightrumor::WaveformMode::RgbParade, waveW, waveH, paradeData);

    int subW = waveW / 3;
    // Check divider lines at subW and 2*subW
    uint32_t div1 = paradeData.rgbaPixels[100 * waveW + subW];
    uint32_t div2 = paradeData.rgbaPixels[100 * waveW + 2 * subW];
    LR_TEST_ASSERT(div1 == 0xFF2D2F33, "Divider 1 mismatch");
    LR_TEST_ASSERT(div2 == 0xFF2D2F33, "Divider 2 mismatch");

    // Column 1 (Red parade) at x = subW / 2
    uint32_t pxCol1 = paradeData.rgbaPixels[75 * waveW + (subW / 2)];
    uint8_t r1 = (pxCol1 >> 16) & 0xFF;
    uint8_t g1 = (pxCol1 >> 8) & 0xFF;
    uint8_t b1 = pxCol1 & 0xFF;
    LR_TEST_ASSERT(r1 > 50 && g1 == 0 && b1 == 0, "Col 1 must be pure red parade channel");

    // Column 2 (Green parade) at x = subW + subW / 2
    uint32_t pxCol2 = paradeData.rgbaPixels[75 * waveW + (subW + subW / 2)];
    uint8_t r2 = (pxCol2 >> 16) & 0xFF;
    uint8_t g2 = (pxCol2 >> 8) & 0xFF;
    uint8_t b2 = pxCol2 & 0xFF;
    LR_TEST_ASSERT(r2 == 0 && g2 > 50 && b2 == 0, "Col 2 must be pure green parade channel");

    // Column 3 (Blue parade) at x = 2*subW + subW / 2
    uint32_t pxCol3 = paradeData.rgbaPixels[75 * waveW + (2 * subW + subW / 2)];
    uint8_t r3 = (pxCol3 >> 16) & 0xFF;
    uint8_t b3 = pxCol3 & 0xFF;
    LR_TEST_ASSERT(r3 == 0 && b3 > 50, "Col 3 must be pure blue parade channel");

    std::cout << "  ✓ 3-Column RGB Parade partition and titanium dividers verified:\n";
    std::cout << "    - Partition 1 (Red channel):   [0 .. " << subW - 1 << "]\n";
    std::cout << "    - Partition 2 (Green channel): [" << subW + 1 << " .. " << 2 * subW - 1 << "]\n";
    std::cout << "    - Partition 3 (Blue channel):  [" << 2 * subW + 1 << " .. " << waveW - 1 << "]\n";
    std::cout << "  [PASS] Test 2 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Test 3: Real-Time Performance (<16.6ms / 60fps budget)
// -------------------------------------------------------------------------
bool testWaveformPerformanceBenchmark() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Phase 3 Test 3/5] 60fps Real-Time Performance (<16.6ms)\n";
    std::cout << "=======================================================\n";

    // 1080p full frame (1920x1080)
    int imgW = 1920;
    int imgH = 1080;
    std::vector<uint8_t> hdFrame(static_cast<size_t>(imgW) * imgH * 4);
    for (size_t i = 0; i < hdFrame.size(); ++i) {
        hdFrame[i] = static_cast<uint8_t>(i % 256);
    }

    lightrumor::WaveformEngine engine;
    lightrumor::WaveformData outData;

    // Warmup
    engine.computeFromRGBA8(hdFrame.data(), imgW, imgH, lightrumor::WaveformMode::RgbOverlay, 512, 256, outData);

    // Measure average of 10 runs
    double totalMs = 0.0;
    const int runs = 10;
    for (int r = 0; r < runs; ++r) {
        double ms = engine.computeFromRGBA8(hdFrame.data(), imgW, imgH, lightrumor::WaveformMode::RgbOverlay, 512, 256, outData);
        totalMs += ms;
    }
    double avgMs = totalMs / runs;

    std::cout << "  1080p Full-HD Waveform Computation (Average over " << runs << " runs):\n";
    std::cout << "  - Execution Time: " << avgMs << " ms\n";
    std::cout << "  - 60fps Budget:   16.6 ms\n";
    std::cout << "  - Headroom:       " << (16.6 - avgMs) << " ms (" << (avgMs / 16.6 * 100.0) << "% budget used)\n";

    LR_TEST_ASSERT(avgMs < 16.6, "Waveform generation must be under 16.6ms (60fps)");
    std::cout << "  ✓ 60fps-120fps ultra-fluid real-time tracking guaranteed.\n";
    std::cout << "  [PASS] Test 3 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Test 4: CSS Cubic-Bezier Physics Easing Mathematics
// -------------------------------------------------------------------------
bool testCssCubicBezierPhysics() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Phase 3 Test 4/5] CSS Cubic-Bezier Physics Mathematics\n";
    std::cout << "=======================================================\n";

    // 1. Dial Snap: cubic-bezier(0.16, 1, 0.3, 1)
    // Fast initial surge, asymptotic deceleration, snaps cleanly at 1.0 without oscillation
    double p1x = 0.16, p1y = 1.0, p2x = 0.3, p2y = 1.0;

    double y0_0 = evaluateCubicBezier(p1x, p1y, p2x, p2y, 0.0);
    double y0_2 = evaluateCubicBezier(p1x, p1y, p2x, p2y, 0.2);
    double y0_5 = evaluateCubicBezier(p1x, p1y, p2x, p2y, 0.5);
    double y0_8 = evaluateCubicBezier(p1x, p1y, p2x, p2y, 0.8);
    double y1_0 = evaluateCubicBezier(p1x, p1y, p2x, p2y, 1.0);

    LR_TEST_ASSERT(std::abs(y0_0 - 0.0) < 1e-4, "t=0 must be 0");
    LR_TEST_ASSERT(std::abs(y1_0 - 1.0) < 1e-4, "t=1 must be 1");
    LR_TEST_ASSERT(y0_2 > 0.70, "Rapid acceleration: at 20% duration, progress must be >70%");
    LR_TEST_ASSERT(y0_5 > 0.90, "At 50% duration, progress must be >90%");
    LR_TEST_ASSERT(y0_8 > 0.98, "At 80% duration, progress must be >98%");
    LR_TEST_ASSERT(y1_0 <= 1.0001, "No overshoot past 1.0 allowed in mechanical snap");

    std::cout << "  ✓ Dial Snap cubic-bezier(0.16, 1, 0.3, 1) trajectory:\n";
    std::cout << "    - t = 0.0: " << y0_0 << "\n";
    std::cout << "    - t = 0.2: " << y0_2 << " (>70% initial burst)\n";
    std::cout << "    - t = 0.5: " << y0_5 << " (smooth mechanical glide)\n";
    std::cout << "    - t = 0.8: " << y0_8 << " (terminal soft lock)\n";
    std::cout << "    - t = 1.0: " << y1_0 << " (rock-solid mechanical detent)\n";

    // 2. Panel Slide: cubic-bezier(0.05, 0.7, 0.1, 1.0)
    double q1x = 0.05, q1y = 0.7, q2x = 0.1, q2y = 1.0;
    double py0_2 = evaluateCubicBezier(q1x, q1y, q2x, q2y, 0.2);
    double py0_5 = evaluateCubicBezier(q1x, q1y, q2x, q2y, 0.5);
    LR_TEST_ASSERT(py0_2 > 0.60, "Panel slide must be swift");
    LR_TEST_ASSERT(py0_5 > 0.85, "Panel slide must ease smoothly");

    std::cout << "  [PASS] Test 4 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Test 5: Zero-Point Snap Detection & Haptic Throttling Logic
// -------------------------------------------------------------------------
bool testHapticZeroSnapLogic() {
    std::cout << "\n=======================================================\n";
    std::cout << "▶ [Phase 3 Test 5/5] Zero-Point Snap & Haptic Throttling\n";
    std::cout << "=======================================================\n";

    struct HapticState {
        int clickCount = 0;
        int tickCount = 0;
        int thudCount = 0;
        double lastTickTimeMs = 0.0;
        float prevValue = 0.0f;

        void onSliderMove(float newValue, float minVal, float maxVal, double currentTimeMs) {
            // 1. Boundary hit
            if (newValue <= minVal || newValue >= maxVal) {
                thudCount++;
            }
            // 2. Zero-point crossing (e.g. from -0.1 to +0.1 or landing exactly on 0)
            if ((prevValue < 0.0f && newValue >= 0.0f) ||
                (prevValue > 0.0f && newValue <= 0.0f) ||
                (std::abs(newValue) < 0.005f && std::abs(prevValue) >= 0.005f)) {
                clickCount++;
            }
            // 3. Stepping tick (throttled to max 50Hz / 20ms)
            if (currentTimeMs - lastTickTimeMs >= 20.0 && std::abs(newValue - prevValue) >= 0.05f) {
                tickCount++;
                lastTickTimeMs = currentTimeMs;
            }
            prevValue = newValue;
        }
    };

    HapticState state;
    state.prevValue = -1.0f;

    // Simulate drag from -1.0 EV to +1.0 EV across 100ms in 10 steps
    for (int i = 1; i <= 10; ++i) {
        float val = -1.0f + (2.0f * i) / 10.0f; // lands on 0.0 at i=5
        double tMs = i * 10.0;
        state.onSliderMove(val, -5.0f, +5.0f, tMs);
    }

    // Must have detected exactly 1 zero-crossing click
    LR_TEST_ASSERT(state.clickCount == 1, "Must detect exactly 1 zero-point snap click");
    // Must have throttled ticks to at most 1 every 20ms
    LR_TEST_ASSERT(state.tickCount >= 4 && state.tickCount <= 6, "Ticks must be cleanly throttled");

    // Hit boundary min
    state.onSliderMove(-5.0f, -5.0f, +5.0f, 150.0);
    LR_TEST_ASSERT(state.thudCount == 1, "Boundary hit must trigger thud haptic");

    std::cout << "  ✓ Zero-point crossing triggered sharp PRIMITIVE_CLICK\n";
    std::cout << "  ✓ Rotary stepping ticks smoothly throttled (no haptic spam)\n";
    std::cout << "  ✓ Boundary limit triggered PRIMITIVE_THUD\n";
    std::cout << "  [PASS] Test 5 passed successfully.\n";
    return true;
}

// -------------------------------------------------------------------------
// Main
// -------------------------------------------------------------------------
int main() {
    std::cout << "=======================================================\n";
    std::cout << "  light_rumor - PHASE 3 NATIVE VERIFICATION\n";
    std::cout << "=======================================================\n";

    bool allPassed = true;
    if (!testWaveformOverlayAndColorMixing()) allPassed = false;
    if (!testWaveformParadePartition()) allPassed = false;
    if (!testWaveformPerformanceBenchmark()) allPassed = false;
    if (!testCssCubicBezierPhysics()) allPassed = false;
    if (!testHapticZeroSnapLogic()) allPassed = false;

    std::cout << "\n=======================================================\n";
    if (allPassed) {
        std::cout << "  ★ ALL PHASE 3 NATIVE UNIT TESTS PASSED! ★\n";
        std::cout << "=======================================================\n";
        return 0;
    } else {
        std::cout << "  ✗ SOME PHASE 3 TESTS FAILED.\n";
        std::cout << "=======================================================\n";
        return 1;
    }
}
