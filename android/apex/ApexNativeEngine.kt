package com.apexfield.engine

interface ProgressCallback {
    fun onProgress(progressPercent: Float, statusMessage: String)
}

object ApexNativeEngine {
    init {
        try {
            System.loadLibrary("apex_engine")
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("ApexNativeEngine: Native library apex_engine not found or loaded in desktop test: " + e.message)
        }
    }

    external fun nativeInit(): Boolean

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
}
