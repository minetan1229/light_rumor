package com.lightrumor

interface ProgressCallback {
    fun onProgress(progressPercent: Float, statusMessage: String)
}

typealias ApexNativeEngine = LightRumorNativeEngine

object LightRumorNativeEngine {
    init {
        try {
            System.loadLibrary("light_rumor_engine")
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("LightRumorNativeEngine: Native library light_rumor_engine not found or loaded in desktop test: " + e.message)
        }
    }

    external fun nativeInit(): Boolean

    external fun nativeExtractThumbnail(filePath: String): ByteArray?

    external fun nativeProcessRaw(
        inputPath: String,
        outputPath: String,
        format: Int,
        jpegQuality: Int,
        chromaSubsampling: Int,
        kelvin: Float,
        tint: Float,
        exposureEV: Float,
        contrast: Float,
        highlights: Float,
        shadows: Float,
        whites: Float,
        blacks: Float,
        vibrance: Float,
        saturation: Float,
        isMonochrome: Boolean,
        luminanceNR: Float,
        chromaNR: Float,
        sharpeningAmount: Float,
        outputColorSpace: Int,
        enableDithering: Boolean,
        callback: ProgressCallback?
    ): Boolean

    fun exportPhoto(
        inputPath: String,
        outputPath: String,
        config: ExportConfig,
        params: DevelopmentParams,
        callback: ProgressCallback?
    ): Boolean {
        return nativeProcessRaw(
            inputPath = inputPath,
            outputPath = outputPath,
            format = config.format.id,
            jpegQuality = config.jpegQuality,
            chromaSubsampling = config.chromaSubsampling.id,
            kelvin = params.kelvin,
            tint = params.tint,
            exposureEV = params.exposureEV,
            contrast = params.contrast,
            highlights = params.highlights,
            shadows = params.shadows,
            whites = params.whites,
            blacks = params.blacks,
            vibrance = params.vibrance,
            saturation = params.saturation,
            isMonochrome = params.isMonochrome,
            luminanceNR = params.luminanceNR,
            chromaNR = params.chromaNR,
            sharpeningAmount = params.sharpeningAmount,
            outputColorSpace = params.outputColorSpace.id,
            enableDithering = params.enableDithering,
            callback = callback
        )
    }

    fun extractThumbnailBytes(filePath: String): ByteArray? {
        return try {
            nativeExtractThumbnail(filePath)
        } catch (e: Throwable) {
            null
        }
    }

    external fun nativeComputeWaveform(
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        mode: Int,
        waveW: Int,
        waveH: Int
    ): IntArray?

    fun computeWaveform(
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        mode: Int = 0,
        waveW: Int = 512,
        waveH: Int = 256
    ): IntArray {
        try {
            val result = nativeComputeWaveform(rgbaBytes, width, height, mode, waveW, waveH)
            if (result != null && result.isNotEmpty()) return result
        } catch (e: Throwable) {
            // Fall through to Kotlin fallback
        }

        // Pure Kotlin SIMD-like fast fallback for preview / testing
        val outPixels = IntArray(waveW * waveH)
        val countR = IntArray(waveW * waveH)
        val countG = IntArray(waveW * waveH)
        val countB = IntArray(waveW * waveH)

        val stepX = (width / (waveW * 2)).coerceAtLeast(1)
        val stepY = (height / 256).coerceAtLeast(1)
        val subW = waveW / 3

        for (y in 0 until height step stepY) {
            val rowOffset = y * width * 4
            for (x in 0 until width step stepX) {
                val idx = rowOffset + x * 4
                if (idx + 3 >= rgbaBytes.size) break
                val r = rgbaBytes[idx].toInt() and 0xFF
                val g = rgbaBytes[idx + 1].toInt() and 0xFF
                val b = rgbaBytes[idx + 2].toInt() and 0xFF

                val yR = (waveH - 1) - ((r * (waveH - 1)) / 255)
                val yG = (waveH - 1) - ((g * (waveH - 1)) / 255)
                val yB = (waveH - 1) - ((b * (waveH - 1)) / 255)

                if (mode == 0) { // RGB Overlay
                    val col = (x * waveW) / width
                    if (col in 0 until waveW) {
                        countR[yR * waveW + col]++
                        countG[yG * waveW + col]++
                        countB[yB * waveW + col]++
                    }
                } else if (mode == 1) { // RGB Parade
                    val subCol = (x * subW) / width
                    if (subCol in 0 until subW) {
                        countR[yR * waveW + subCol]++
                        countG[yG * waveW + (subW + subCol)]++
                        countB[yB * waveW + (2 * subW + subCol)]++
                    }
                }
            }
        }

        // Colorize
        val bg = 0xFF0C0A0A.toInt()
        val divColor = 0xFF2D2F33.toInt()

        for (y in 0 until waveH) {
            for (x in 0 until waveW) {
                val pIdx = y * waveW + x
                if (mode == 1 && (x == subW || x == 2 * subW)) {
                    outPixels[pIdx] = divColor
                    continue
                }
                val r = (countR[pIdx] * 12).coerceIn(0, 255)
                val g = (countG[pIdx] * 12).coerceIn(0, 255)
                val b = (countB[pIdx] * 12).coerceIn(0, 255)
                if (r == 0 && g == 0 && b == 0) {
                    outPixels[pIdx] = bg
                } else {
                    outPixels[pIdx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return outPixels
    }

    // -------------------------------------------------------------------------
    // Phase 5: Native Mask Evaluation & Retouching
    // -------------------------------------------------------------------------

    external fun nativeEvaluateRadialMask(
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
        angleRad: Float,
        feather: Float,
        invert: Boolean
    ): FloatArray?

    external fun nativeApplyPoissonHeal(
        pixels: IntArray,
        width: Int,
        height: Int,
        srcX: Float,
        srcY: Float,
        dstX: Float,
        dstY: Float,
        radius: Float,
        feather: Float,
        iterations: Int
    ): IntArray?

    fun evaluateRadialMask(
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
        angleRad: Float = 0.0f,
        feather: Float = 0.5f,
        invert: Boolean = false
    ): FloatArray {
        try {
            val res = nativeEvaluateRadialMask(width, height, centerX, centerY, radiusX, radiusY, angleRad, feather, invert)
            if (res != null) return res
        } catch (e: Throwable) {
            // Fallback
        }

        val out = FloatArray(width * height)
        val cosA = kotlin.math.cos(-angleRad)
        val sinA = kotlin.math.sin(-angleRad)
        val rx = radiusX.coerceAtLeast(0.0001f)
        val ry = radiusY.coerceAtLeast(0.0001f)
        val innerR = (1.0f - feather).coerceAtLeast(0.0f)

        for (y in 0 until height) {
            val ny = y.toFloat() / height
            val dy = ny - centerY
            for (x in 0 until width) {
                val nx = x.toFloat() / width
                val dx = nx - centerX
                val rotX = dx * cosA - dy * sinA
                val rotY = dx * sinA + dy * cosA
                val dist = kotlin.math.sqrt((rotX * rotX) / (rx * rx) + (rotY * rotY) / (ry * ry))
                var w = if (dist >= 1.0f) 0.0f else if (dist <= innerR) 1.0f else {
                    val t = ((dist - innerR) / (1.0f - innerR)).coerceIn(0.0f, 1.0f)
                    1.0f - (t * t * (3.0f - 2.0f * t))
                }
                if (invert) w = 1.0f - w
                out[y * width + x] = w
            }
        }
        return out
    }

    external fun nativeComputeFieldScope(
        pixels: IntArray,
        width: Int,
        height: Int,
        mode: Int,
        zebraThresholdIRE: Float,
        peakingColor: Int,
        peakingThreshold: Float
    ): IntArray?

    external fun nativeApplySoftProof(
        pixels: IntArray,
        width: Int,
        height: Int,
        profilePath: String,
        intent: Int,
        simulatePaperWhite: Boolean,
        simulateBlackInk: Boolean,
        showGamutWarning: Boolean,
        gamutWarningColor: Int
    ): IntArray?

    external fun nativeProcessRawMultiRecipe(
        inputPath: String,
        outputPath: String,
        format: Int,
        colorSpace: Int,
        quality: Int,
        maxDimension: Int,
        applySharpening: Boolean,
        sharpeningAmount: Float,
        enableWatermark: Boolean,
        watermarkText: String
    ): Boolean
}

