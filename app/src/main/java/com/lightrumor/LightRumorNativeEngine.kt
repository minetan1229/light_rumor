package com.lightrumor

interface ProgressCallback {
    fun onProgress(progressPercent: Float, statusMessage: String)
}

object LightRumorNativeEngine {
    val isAvailable: Boolean

    init {
        var loaded = false
        try {
            System.loadLibrary("light_rumor_engine")
            loaded = true
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("LightRumorNativeEngine: Native library light_rumor_engine not found or loaded in desktop test: " + e.message)
        } catch (e: Throwable) {
            System.err.println("LightRumorNativeEngine: Failed to load native library: " + e.message)
        }
        isAvailable = loaded
    }

    external fun nativeInit(): Boolean

    fun initNative(): Boolean {
        if (!isAvailable) return false
        return try {
            nativeInit()
        } catch (e: Throwable) {
            false
        }
    }

    external fun nativeExtractThumbnail(filePath: String): ByteArray?

    external fun nativeProcessRaw(
        inputPath: String,
        outputPath: String,
        format: Int,
        jpegQuality: Int,
        chromaSubsampling: Int,
        kelvin: Float,
        tint: Float,
        shadowTintR: Float,
        shadowTintG: Float,
        shadowTintB: Float,
        exposureEV: Float,
        contrast: Float,
        highlights: Float,
        shadows: Float,
        whites: Float,
        blacks: Float,
        vibrance: Float,
        saturation: Float,
        dehaze: Float,
        clarity: Float,
        texture: Float,
        isMonochrome: Boolean,
        monochromeWeights: FloatArray,
        hslBands: FloatArray,
        primaryCalibration: FloatArray,
        splitToning: FloatArray,
        toneCurveLUT: FloatArray,
        luminanceNR: Float,
        luminanceNRDetail: Float,
        luminanceNRContrast: Float,
        chromaNR: Float,
        chromaNRDetail: Float,
        chromaNRSmoothness: Float,
        sharpeningAmount: Float,
        sharpeningRadius: Float,
        sharpeningDetail: Float,
        sharpeningMasking: Float,
        outputColorSpace: Int,
        enableDithering: Boolean,
        enableLensCorrection: Boolean,
        distortionCorrection: Float,
        vignettingCorrection: Float,
        chromaticAberration: Float,
        defringePurple: Float,
        defringeGreen: Float,
        cropX: Float,
        cropY: Float,
        cropW: Float,
        cropH: Float,
        rotationDegrees: Float,
        rotationSteps: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        perspectiveVertical: Float,
        perspectiveHorizontal: Float,
        distortion: Float,
        maxLongEdge: Int,
        enableWatermark: Boolean,
        watermarkText: String,
        callback: ProgressCallback?
    ): Boolean

    fun exportPhoto(
        inputPath: String,
        outputPath: String,
        config: ExportConfig,
        params: DevelopmentParams,
        callback: ProgressCallback?
    ): Boolean {
        if (!isAvailable) return false
        val hslArray = FloatArray(24)
        for (i in 0 until 8) {
            val band = if (i < params.hslBands.size) params.hslBands[i] else HSLBandAdjust()
            hslArray[i * 3 + 0] = band.hueShift
            hslArray[i * 3 + 1] = band.saturation
            hslArray[i * 3 + 2] = band.luminance
        }
        val primaryCalib = floatArrayOf(
            params.primaryRed.hueShift, params.primaryRed.saturationShift,
            params.primaryGreen.hueShift, params.primaryGreen.saturationShift,
            params.primaryBlue.hueShift, params.primaryBlue.saturationShift
        )
        val splitTone = floatArrayOf(
            params.splitToning.highlightsHue, params.splitToning.highlightsSat,
            params.splitToning.shadowsHue, params.splitToning.shadowsSat,
            params.splitToning.balance
        )
        val monoWeights = if (params.monochromeWeights.size == 8) {
            params.monochromeWeights
        } else {
            floatArrayOf(0.18f, 0.24f, 0.22f, 0.16f, 0.08f, 0.06f, 0.03f, 0.03f)
        }
        return try {
            nativeProcessRaw(
                inputPath = inputPath,
                outputPath = outputPath,
                format = config.format.id,
                jpegQuality = config.jpegQuality,
                chromaSubsampling = config.chromaSubsampling.id,
                kelvin = params.kelvin,
                tint = params.tint,
                shadowTintR = params.shadowTintR,
                shadowTintG = params.shadowTintG,
                shadowTintB = params.shadowTintB,
                exposureEV = params.exposureEV,
                contrast = params.contrast,
                highlights = params.highlights,
                shadows = params.shadows,
                whites = params.whites,
                blacks = params.blacks,
                vibrance = params.vibrance,
                saturation = params.saturation,
                dehaze = params.dehaze,
                clarity = params.clarity,
                texture = params.texture,
                isMonochrome = params.isMonochrome,
                monochromeWeights = monoWeights,
                hslBands = hslArray,
                primaryCalibration = primaryCalib,
                splitToning = splitTone,
                toneCurveLUT = params.toneCurveLUT,
                luminanceNR = params.luminanceNR,
                luminanceNRDetail = params.luminanceNRDetail,
                luminanceNRContrast = params.luminanceNRContrast,
                chromaNR = params.chromaNR,
                chromaNRDetail = params.chromaNRDetail,
                chromaNRSmoothness = params.chromaNRSmoothness,
                sharpeningAmount = params.sharpeningAmount,
                sharpeningRadius = params.sharpeningRadius,
                sharpeningDetail = params.sharpeningDetail,
                sharpeningMasking = params.sharpeningMasking,
                outputColorSpace = params.outputColorSpace.id,
                enableDithering = params.enableDithering,
                enableLensCorrection = params.lensCorrection.enableProfileCorrection,
                distortionCorrection = params.lensCorrection.distortionCorrection,
                vignettingCorrection = params.lensCorrection.vignettingCorrection,
                chromaticAberration = params.lensCorrection.chromaticAberration,
                defringePurple = params.lensCorrection.defringePurple,
                defringeGreen = params.lensCorrection.defringeGreen,
                cropX = params.geometry.cropX,
                cropY = params.geometry.cropY,
                cropW = params.geometry.cropW,
                cropH = params.geometry.cropH,
                rotationDegrees = params.geometry.rotationDegrees,
                rotationSteps = params.geometry.rotationSteps,
                flipHorizontal = params.geometry.flipHorizontal,
                flipVertical = params.geometry.flipVertical,
                perspectiveVertical = params.geometry.perspectiveVertical,
                perspectiveHorizontal = params.geometry.perspectiveHorizontal,
                distortion = params.geometry.distortion,
                maxLongEdge = config.maxLongEdge,
                enableWatermark = config.enableWatermark,
                watermarkText = config.watermarkText,
                callback = callback
            )
        } catch (e: Throwable) {
            false
        }
    }

    fun extractThumbnailBytes(filePath: String): ByteArray? {
        if (!isAvailable) return null
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
        if (isAvailable) {
            try {
                val result = nativeComputeWaveform(rgbaBytes, width, height, mode, waveW, waveH)
                if (result != null && result.isNotEmpty()) return result
            } catch (e: Throwable) {
                // Fall through to Kotlin fallback
            }
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
                } else if (mode == 2) { // Histogram
                    val colR = (r * (waveW - 1)) / 255
                    val colG = (g * (waveW - 1)) / 255
                    val colB = (b * (waveW - 1)) / 255
                    countR[colR]++
                    countG[colG]++
                    countB[colB]++
                }
            }
        }

        // Colorize
        val bg = 0xFF0C0A0A.toInt()
        val divColor = 0xFF2D2F33.toInt()

        if (mode == 2) {
            val maxCount = (0 until waveW).maxOfOrNull {
                maxOf(countR[it], countG[it], countB[it])
            }?.coerceAtLeast(1) ?: 1

            for (x in 0 until waveW) {
                val hR = (countR[x].toLong() * waveH / maxCount).toInt().coerceIn(0, waveH)
                val hG = (countG[x].toLong() * waveH / maxCount).toInt().coerceIn(0, waveH)
                val hB = (countB[x].toLong() * waveH / maxCount).toInt().coerceIn(0, waveH)

                for (y in 0 until waveH) {
                    val pIdx = y * waveW + x
                    val fromBottom = (waveH - 1) - y
                    val r = if (fromBottom < hR) 220 else 0
                    val g = if (fromBottom < hG) 220 else 0
                    val b = if (fromBottom < hB) 220 else 0

                    if (r == 0 && g == 0 && b == 0) {
                        outPixels[pIdx] = bg
                    } else {
                        outPixels[pIdx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
            return outPixels
        }

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
        if (isAvailable) {
            try {
                val res = nativeEvaluateRadialMask(width, height, centerX, centerY, radiusX, radiusY, angleRad, feather, invert)
                if (res != null) return res
            } catch (e: Throwable) {
                // Fallback
            }
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

    fun applyPoissonHeal(
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
    ): IntArray? {
        if (!isAvailable) return null
        return try {
            nativeApplyPoissonHeal(pixels, width, height, srcX, srcY, dstX, dstY, radius, feather, iterations)
        } catch (e: Throwable) {
            null
        }
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

    fun computeFieldScope(
        pixels: IntArray,
        width: Int,
        height: Int,
        mode: Int,
        zebraThresholdIRE: Float,
        peakingColor: Int,
        peakingThreshold: Float
    ): IntArray? {
        if (!isAvailable) return null
        return try {
            nativeComputeFieldScope(pixels, width, height, mode, zebraThresholdIRE, peakingColor, peakingThreshold)
        } catch (e: Throwable) {
            null
        }
    }

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

    fun applySoftProof(
        pixels: IntArray,
        width: Int,
        height: Int,
        profilePath: String,
        intent: Int,
        simulatePaperWhite: Boolean,
        simulateBlackInk: Boolean,
        showGamutWarning: Boolean,
        gamutWarningColor: Int
    ): IntArray? {
        if (!isAvailable) return null
        return try {
            nativeApplySoftProof(pixels, width, height, profilePath, intent, simulatePaperWhite, simulateBlackInk, showGamutWarning, gamutWarningColor)
        } catch (e: Throwable) {
            null
        }
    }

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

    fun processRawMultiRecipe(
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
    ): Boolean {
        if (!isAvailable) return false
        return try {
            nativeProcessRawMultiRecipe(inputPath, outputPath, format, colorSpace, quality, maxDimension, applySharpening, sharpeningAmount, enableWatermark, watermarkText)
        } catch (e: Throwable) {
            false
        }
    }
}

