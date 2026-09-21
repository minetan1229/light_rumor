#include "apex/ColorCheckerCalibration.h"
#include <cmath>
#include <algorithm>
#include <iostream>
#include <numeric>

namespace apex {

namespace {

// Standard ColorChecker Classic 24 patches data (CIE L*a*b* D65 & Reference sRGB)
const ColorCheckerPatchData STANDARD_PATCHES[24] = {
    // Row 1: Natural colors
    {0,  "Dark skin",      {37.99f,  13.56f,  14.06f}, {0.451f, 0.322f, 0.255f}, {0,0,0}, {0,0,0}, 0},
    {1,  "Light skin",     {65.71f,  18.13f,  17.81f}, {0.769f, 0.584f, 0.506f}, {0,0,0}, {0,0,0}, 0},
    {2,  "Blue sky",       {49.93f,  -4.88f, -21.93f}, {0.384f, 0.478f, 0.616f}, {0,0,0}, {0,0,0}, 0},
    {3,  "Foliage",        {43.14f, -13.10f,  21.91f}, {0.345f, 0.424f, 0.239f}, {0,0,0}, {0,0,0}, 0},
    {4,  "Blue flower",    {55.11f,   8.84f, -25.40f}, {0.506f, 0.502f, 0.690f}, {0,0,0}, {0,0,0}, 0},
    {5,  "Bluish green",   {70.72f, -33.40f,  -0.20f}, {0.388f, 0.733f, 0.667f}, {0,0,0}, {0,0,0}, 0},

    // Row 2: Miscellaneous
    {6,  "Orange",         {62.66f,  36.07f,  57.10f}, {0.835f, 0.478f, 0.165f}, {0,0,0}, {0,0,0}, 0},
    {7,  "Purplish blue",  {40.02f,  10.41f, -45.96f}, {0.314f, 0.357f, 0.647f}, {0,0,0}, {0,0,0}, 0},
    {8,  "Moderate red",   {51.12f,  48.24f,  16.25f}, {0.761f, 0.318f, 0.365f}, {0,0,0}, {0,0,0}, 0},
    {9,  "Purple",         {30.33f,  22.98f, -21.59f}, {0.345f, 0.231f, 0.412f}, {0,0,0}, {0,0,0}, 0},
    {10, "Yellow green",   {72.53f, -23.71f,  60.47f}, {0.612f, 0.729f, 0.220f}, {0,0,0}, {0,0,0}, 0},
    {11, "Orange yellow",  {71.94f,  19.36f,  67.86f}, {0.871f, 0.616f, 0.157f}, {0,0,0}, {0,0,0}, 0},

    // Row 3: Primary & Secondary
    {12, "Blue",           {28.78f,  14.18f, -50.30f}, {0.169f, 0.231f, 0.576f}, {0,0,0}, {0,0,0}, 0},
    {13, "Green",          {55.26f, -38.34f,  31.37f}, {0.275f, 0.584f, 0.282f}, {0,0,0}, {0,0,0}, 0},
    {14, "Red",            {42.10f,  53.38f,  28.19f}, {0.698f, 0.196f, 0.235f}, {0,0,0}, {0,0,0}, 0},
    {15, "Yellow",         {81.73f,   4.04f,  79.82f}, {0.929f, 0.776f, 0.090f}, {0,0,0}, {0,0,0}, 0},
    {16, "Magenta",        {51.94f,  49.99f, -14.57f}, {0.733f, 0.306f, 0.533f}, {0,0,0}, {0,0,0}, 0},
    {17, "Cyan",           {51.04f, -28.63f, -28.64f}, {0.000f, 0.525f, 0.635f}, {0,0,0}, {0,0,0}, 0},

    // Row 4: Grayscale ramp
    {18, "White 9.5",      {96.54f,  -0.43f,   1.19f}, {0.957f, 0.957f, 0.953f}, {0,0,0}, {0,0,0}, 0},
    {19, "Neutral 8",      {81.26f,  -0.64f,  -0.34f}, {0.784f, 0.788f, 0.784f}, {0,0,0}, {0,0,0}, 0},
    {20, "Neutral 6.5",    {66.77f,  -0.73f,  -0.50f}, {0.627f, 0.635f, 0.631f}, {0,0,0}, {0,0,0}, 0},
    {21, "Neutral 5",      {50.87f,  -0.15f,  -0.27f}, {0.471f, 0.475f, 0.471f}, {0,0,0}, {0,0,0}, 0},
    {22, "Neutral 3.5",    {35.66f,  -0.42f,  -1.23f}, {0.329f, 0.333f, 0.333f}, {0,0,0}, {0,0,0}, 0},
    {23, "Black 2",        {20.46f,  -0.08f,  -0.97f}, {0.196f, 0.196f, 0.196f}, {0,0,0}, {0,0,0}, 0}
};

// Invert 3x3 matrix using Cramer's rule
bool invert3x3(const float m[9], float inv[9]) {
    float det = m[0] * (m[4] * m[8] - m[5] * m[7]) -
                m[1] * (m[3] * m[8] - m[5] * m[6]) +
                m[2] * (m[3] * m[7] - m[4] * m[6]);

    if (std::abs(det) < 1e-8f) return false;
    float invDet = 1.0f / det;

    inv[0] =  (m[4] * m[8] - m[5] * m[7]) * invDet;
    inv[1] = -(m[1] * m[8] - m[2] * m[7]) * invDet;
    inv[2] =  (m[1] * m[5] - m[2] * m[4]) * invDet;

    inv[3] = -(m[3] * m[8] - m[5] * m[6]) * invDet;
    inv[4] =  (m[0] * m[8] - m[2] * m[6]) * invDet;
    inv[5] = -(m[0] * m[5] - m[2] * m[3]) * invDet;

    inv[6] =  (m[3] * m[7] - m[4] * m[6]) * invDet;
    inv[7] = -(m[0] * m[7] - m[1] * m[6]) * invDet;
    inv[8] =  (m[0] * m[4] - m[1] * m[3]) * invDet;

    return true;
}

} // namespace

ColorCheckerCalibration::ColorCheckerCalibration() = default;
ColorCheckerCalibration::~ColorCheckerCalibration() = default;

void ColorCheckerCalibration::rgbToLab(float r, float g, float b, float outLab[3]) {
    // 1. Linear sRGB to CIE XYZ (D65)
    r = std::clamp(r, 0.0f, 1.0f);
    g = std::clamp(g, 0.0f, 1.0f);
    b = std::clamp(b, 0.0f, 1.0f);

    float X = 0.4124564f * r + 0.3575761f * g + 0.1804375f * b;
    float Y = 0.2126729f * r + 0.7151522f * g + 0.0721750f * b;
    float Z = 0.0193339f * r + 0.1191920f * g + 0.9503041f * b;

    // D65 reference white
    constexpr float Xn = 0.95047f;
    constexpr float Yn = 1.00000f;
    constexpr float Zn = 1.08883f;

    auto f = [](float t) -> float {
        constexpr float delta = 6.0f / 29.0f;
        constexpr float delta3 = delta * delta * delta;
        if (t > delta3) return std::cbrt(t);
        return t / (3.0f * delta * delta) + 4.0f / 29.0f;
    };

    float fx = f(X / Xn);
    float fy = f(Y / Yn);
    float fz = f(Z / Zn);

    outLab[0] = 116.0f * fy - 16.0f;
    outLab[1] = 500.0f * (fx - fy);
    outLab[2] = 200.0f * (fy - fz);
}

void ColorCheckerCalibration::labToRgb(const float lab[3], float& outR, float& outG, float& outB) {
    constexpr float Xn = 0.95047f;
    constexpr float Yn = 1.00000f;
    constexpr float Zn = 1.08883f;

    float fy = (lab[0] + 16.0f) / 116.0f;
    float fx = lab[1] / 500.0f + fy;
    float fz = fy - lab[2] / 200.0f;

    auto finv = [](float t) -> float {
        constexpr float delta = 6.0f / 29.0f;
        if (t > delta) return t * t * t;
        return 3.0f * delta * delta * (t - 4.0f / 29.0f);
    };

    float X = Xn * finv(fx);
    float Y = Yn * finv(fy);
    float Z = Zn * finv(fz);

    // XYZ to linear sRGB
    outR = std::clamp( 3.2404542f * X - 1.5371385f * Y - 0.4985314f * Z, 0.0f, 1.0f);
    outG = std::clamp(-0.9692660f * X + 1.8760108f * Y + 0.0415560f * Z, 0.0f, 1.0f);
    outB = std::clamp( 0.0556434f * X - 0.2040259f * Y + 1.0572252f * Z, 0.0f, 1.0f);
}

const ColorCheckerPatchData& ColorCheckerCalibration::getStandardPatch(int32_t patchIndex) {
    patchIndex = std::clamp(patchIndex, 0, 23);
    static bool s_initialized = false;
    static ColorCheckerPatchData s_patches[24];
    if (!s_initialized) {
        for (int i = 0; i < 24; ++i) {
            s_patches[i] = STANDARD_PATCHES[i];
            labToRgb(s_patches[i].targetLab, s_patches[i].targetRgb[0], s_patches[i].targetRgb[1], s_patches[i].targetRgb[2]);
        }
        s_initialized = true;
    }
    return s_patches[patchIndex];
}

float ColorCheckerCalibration::calculateDeltaE00(const float lab1[3], const float lab2[3]) {
    // CIEDE2000 implementation
    float L1 = lab1[0], a1 = lab1[1], b1 = lab1[2];
    float L2 = lab2[0], a2 = lab2[1], b2 = lab2[2];

    float C1 = std::hypot(a1, b1);
    float C2 = std::hypot(a2, b2);
    float Cbar = (C1 + C2) * 0.5f;

    float Cbar7 = std::pow(Cbar, 7.0f);
    float G = 0.5f * (1.0f - std::sqrt(Cbar7 / (Cbar7 + 6103515625.0f))); // 25^7 = 6103515625

    float a1p = (1.0f + G) * a1;
    float a2p = (1.0f + G) * a2;

    float C1p = std::hypot(a1p, b1);
    float C2p = std::hypot(a2p, b2);

    float h1p = std::atan2(b1, a1p);
    if (h1p < 0.0f) h1p += 2.0f * 3.14159265f;
    float h2p = std::atan2(b2, a2p);
    if (h2p < 0.0f) h2p += 2.0f * 3.14159265f;

    float dLp = L2 - L1;
    float dCp = C2p - C1p;

    float dhp = 0.0f;
    if (C1p * C2p > 1e-6f) {
        float diffH = h2p - h1p;
        if (std::abs(diffH) <= 3.14159265f) dhp = diffH;
        else if (diffH > 3.14159265f) dhp = diffH - 2.0f * 3.14159265f;
        else dhp = diffH + 2.0f * 3.14159265f;
    }
    float dHp = 2.0f * std::sqrt(C1p * C2p) * std::sin(dhp * 0.5f);

    float Lbarp = (L1 + L2) * 0.5f;
    float Cbarp = (C1p + C2p) * 0.5f;

    float hbarp = 0.0f;
    if (C1p * C2p > 1e-6f) {
        if (std::abs(h1p - h2p) <= 3.14159265f) hbarp = (h1p + h2p) * 0.5f;
        else if (h1p + h2p < 2.0f * 3.14159265f) hbarp = (h1p + h2p + 2.0f * 3.14159265f) * 0.5f;
        else hbarp = (h1p + h2p - 2.0f * 3.14159265f) * 0.5f;
    }

    float T = 1.0f - 0.17f * std::cos(hbarp - 30.0f * 3.14159265f / 180.0f)
                   + 0.24f * std::cos(2.0f * hbarp)
                   + 0.32f * std::cos(3.0f * hbarp + 6.0f * 3.14159265f / 180.0f)
                   - 0.20f * std::cos(4.0f * hbarp - 63.0f * 3.14159265f / 180.0f);

    float dTheta = 30.0f * 3.14159265f / 180.0f * std::exp(-std::pow((hbarp * 180.0f / 3.14159265f - 275.0f) / 25.0f, 2.0f));
    float Cbarp7 = std::pow(Cbarp, 7.0f);
    float RC = 2.0f * std::sqrt(Cbarp7 / (Cbarp7 + 6103515625.0f));
    float RT = -std::sin(2.0f * dTheta) * RC;

    float SL = 1.0f + (0.015f * std::pow(Lbarp - 50.0f, 2.0f)) / std::sqrt(20.0f + std::pow(Lbarp - 50.0f, 2.0f));
    float SC = 1.0f + 0.045f * Cbarp;
    float SH = 1.0f + 0.015f * Cbarp * T;

    float termL = dLp / SL;
    float termC = dCp / SC;
    float termH = dHp / SH;

    return std::sqrt(termL * termL + termC * termC + termH * termH + RT * termC * termH);
}

bool ColorCheckerCalibration::samplePatches(const std::vector<FloatRGBA>& frame,
                                           int32_t width, int32_t height,
                                           const ChartCorners& corners,
                                           std::vector<ColorCheckerPatchData>& outPatches) {
    if (frame.empty() || width <= 0 || height <= 0) return false;

    outPatches.resize(24);

    // 4 rows, 6 columns
    for (int row = 0; row < 4; ++row) {
        for (int col = 0; col < 6; ++col) {
            int idx = row * 6 + col;
            outPatches[idx] = getStandardPatch(idx);

            // Normalized coordinates of cell center within chart
            float u = (col + 0.5f) / 6.0f;
            float v = (row + 0.5f) / 4.0f;

            // Bilinear interpolation between 4 corners
            // Top edge: TL -> TR
            float topX = corners.topLeft.x * (1.0f - u) + corners.topRight.x * u;
            float topY = corners.topLeft.y * (1.0f - u) + corners.topRight.y * u;

            // Bottom edge: BL -> BR
            float botX = corners.bottomLeft.x * (1.0f - u) + corners.bottomRight.x * u;
            float botY = corners.bottomLeft.y * (1.0f - u) + corners.bottomRight.y * u;

            float cx = (topX * (1.0f - v) + botX * v) * width;
            float cy = (topY * (1.0f - v) + botY * v) * height;

            // Sample 50% central window of patch
            float patchW = (width / 6.0f) * 0.25f;
            float patchH = (height / 4.0f) * 0.25f;
            int rW = std::max(1, static_cast<int>(patchW * 0.5f));
            int rH = std::max(1, static_cast<int>(patchH * 0.5f));

            double sumR = 0.0, sumG = 0.0, sumB = 0.0;
            int count = 0;

            int startX = std::clamp(static_cast<int>(cx) - rW, 0, width - 1);
            int endX   = std::clamp(static_cast<int>(cx) + rW, 0, width - 1);
            int startY = std::clamp(static_cast<int>(cy) - rH, 0, height - 1);
            int endY   = std::clamp(static_cast<int>(cy) + rH, 0, height - 1);

            for (int py = startY; py <= endY; ++py) {
                for (int px = startX; px <= endX; ++px) {
                    const auto& p = frame[py * width + px];
                    sumR += p.r;
                    sumG += p.g;
                    sumB += p.b;
                    count++;
                }
            }

            if (count > 0) {
                outPatches[idx].measuredRgb[0] = static_cast<float>(sumR / count);
                outPatches[idx].measuredRgb[1] = static_cast<float>(sumG / count);
                outPatches[idx].measuredRgb[2] = static_cast<float>(sumB / count);
            }
        }
    }

    return true;
}

bool ColorCheckerCalibration::calibrate(const std::vector<FloatRGBA>& frame,
                                       int32_t width, int32_t height,
                                       const ChartCorners& corners,
                                       CalibrationResult& outResult) {
    std::vector<ColorCheckerPatchData> patches;
    if (!samplePatches(frame, width, height, corners, patches)) {
        return false;
    }

    // 1. Estimate illuminant from Neutral Gray row (Patches 19 to 22)
    float whiteR = 0.0f, whiteG = 0.0f, whiteB = 0.0f;
    int neutralCount = 0;
    for (int i = 19; i <= 22; ++i) {
        whiteR += patches[i].measuredRgb[0];
        whiteG += patches[i].measuredRgb[1];
        whiteB += patches[i].measuredRgb[2];
        neutralCount++;
    }
    whiteR /= neutralCount;
    whiteG /= neutralCount;
    whiteB /= neutralCount;

    // Estimate Kelvin and Tint
    float rbRatio = whiteR / std::max(whiteB, 1e-4f);
    outResult.estimatedKelvin = std::clamp(5500.0f / std::max(rbRatio, 0.1f), 2000.0f, 12000.0f);
    outResult.estimatedTint = (whiteG - (whiteR + whiteB) * 0.5f) * 100.0f;

    // 2. Solve Regularized Constrained Least Squares: M * X ≈ Y
    // Matrix X: (24 x 3) measured RGBs
    // Matrix Y: (24 x 3) target standard RGBs
    // Compute Normal equations: (X^T * X + lambda * I) * M^T = X^T * Y + lambda * I
    float XtX[9] = {0.0f};
    float XtY[9] = {0.0f};

    for (int p = 0; p < 24; ++p) {
        float x0 = patches[p].measuredRgb[0];
        float x1 = patches[p].measuredRgb[1];
        float x2 = patches[p].measuredRgb[2];

        float y0 = patches[p].targetRgb[0];
        float y1 = patches[p].targetRgb[1];
        float y2 = patches[p].targetRgb[2];

        // Higher weighting for neutral row to preserve grayscale neutrality
        float w = (p >= 18) ? 3.0f : 1.0f;

        XtX[0] += w * x0 * x0; XtX[1] += w * x0 * x1; XtX[2] += w * x0 * x2;
        XtX[3] += w * x1 * x0; XtX[4] += w * x1 * x1; XtX[5] += w * x1 * x2;
        XtX[6] += w * x2 * x0; XtX[7] += w * x2 * x1; XtX[8] += w * x2 * x2;

        XtY[0] += w * x0 * y0; XtY[1] += w * x0 * y1; XtY[2] += w * x0 * y2;
        XtY[3] += w * x1 * y0; XtY[4] += w * x1 * y1; XtY[5] += w * x1 * y2;
        XtY[6] += w * x2 * y0; XtY[7] += w * x2 * y1; XtY[8] += w * x2 * y2;
    }

    // Tikhonov regularization (lambda = 0.05) towards Identity matrix
    constexpr float lambda = 0.05f;
    XtX[0] += lambda;
    XtX[4] += lambda;
    XtX[8] += lambda;

    XtY[0] += lambda;
    XtY[4] += lambda;
    XtY[8] += lambda;

    float invXtX[9];
    if (!invert3x3(XtX, invXtX)) {
        return false;
    }

    // M^T = inv(XtX) * XtY
    float Mt[9];
    for (int r = 0; r < 3; ++r) {
        for (int c = 0; c < 3; ++c) {
            Mt[r * 3 + c] = invXtX[r * 3 + 0] * XtY[0 * 3 + c] +
                            invXtX[r * 3 + 1] * XtY[1 * 3 + c] +
                            invXtX[r * 3 + 2] * XtY[2 * 3 + c];
        }
    }

    // Transpose to get M
    for (int r = 0; r < 3; ++r) {
        for (int c = 0; c < 3; ++c) {
            outResult.colorMatrix[r * 3 + c] = Mt[c * 3 + r];
        }
    }

    // 3. Evaluate Calibrated Colors and Delta E
    float sumDeltaE = 0.0f;
    float maxDeltaE = 0.0f;

    for (int p = 0; p < 24; ++p) {
        float inR = patches[p].measuredRgb[0];
        float inG = patches[p].measuredRgb[1];
        float inB = patches[p].measuredRgb[2];

        float calR = outResult.colorMatrix[0] * inR + outResult.colorMatrix[1] * inG + outResult.colorMatrix[2] * inB;
        float calG = outResult.colorMatrix[3] * inR + outResult.colorMatrix[4] * inG + outResult.colorMatrix[5] * inB;
        float calB = outResult.colorMatrix[6] * inR + outResult.colorMatrix[7] * inG + outResult.colorMatrix[8] * inB;

        patches[p].calibratedRgb[0] = std::clamp(calR, 0.0f, 1.0f);
        patches[p].calibratedRgb[1] = std::clamp(calG, 0.0f, 1.0f);
        patches[p].calibratedRgb[2] = std::clamp(calB, 0.0f, 1.0f);

        float calLab[3];
        rgbToLab(patches[p].calibratedRgb[0], patches[p].calibratedRgb[1], patches[p].calibratedRgb[2], calLab);

        float dE = calculateDeltaE00(calLab, patches[p].targetLab);
        patches[p].deltaE00 = dE;

        sumDeltaE += dE;
        maxDeltaE = std::max(maxDeltaE, dE);
    }

    outResult.meanDeltaE00 = sumDeltaE / 24.0f;
    outResult.maxDeltaE00 = maxDeltaE;
    outResult.patches = std::move(patches);
    outResult.success = true;

    return true;
}

} // namespace apex
