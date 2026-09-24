package com.lightrumor

import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

data class ExportRecipeConfig(
    val id: String,
    val name: String,
    val format: ExportFormat,
    val colorSpace: ColorSpace,
    val quality: Int = 95,
    val maxDimension: Int = 0, // 0 = original resolution, e.g. 2048 for social media
    val applyEdgeSharpening: Boolean = false,
    val sharpeningAmount: Float = 0.0f,
    val enableWatermark: Boolean = false
)

data class ExifWatermarkConfig(
    val enabled: Boolean = true,
    val cameraModel: String = "ILCE-7RM5",
    val lensModel: String = "FE 24-70mm F2.8 GM II",
    val focalLength: Double = 50.0,
    val aperture: Double = 2.8,
    val shutterSpeed: Double = 1.0 / 250.0,
    val iso: Int = 100,
    val copyright: String = "© 2026 DUFFY PHOTOGRAPHY",
    val style: WatermarkStyle = WatermarkStyle.BOTTOM_MARGIN_GALLERY
) {
    fun formatExposureLine(): String {
        val shutterStr = if (shutterSpeed < 1.0 && shutterSpeed > 0.0) {
            "1/${(1.0 / shutterSpeed + 0.5).toInt()}s"
        } else {
            "${shutterSpeed}s"
        }
        val fStr = String.format(Locale.ROOT, "f/%.1f", aperture)
        val flStr = "${focalLength.toInt()}mm"
        return "$cameraModel  |  $lensModel  |  $flStr  $fStr  $shutterStr  ISO $iso  |  $copyright"
    }
}

enum class WatermarkStyle {
    BOTTOM_MARGIN_GALLERY, // Art gallery polaroid / paper matte margin
    CORNER_OVERLAY         // Minimalist bottom-right corner overlay
}

data class MultiExportProgress(
    val recipeId: String,
    val recipeName: String,
    val progressPercent: Float,
    val statusMessage: String,
    val isFinished: Boolean = false,
    val outputFilePath: String? = null
)

class MultiExportRecipeManager(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val isCancelled = java.util.concurrent.atomic.AtomicBoolean(false)

    companion object {
        // Standard Triple-Master Recipe Preset
        val RECIPE_PRINT_MASTER_TIFF16 = ExportRecipeConfig(
            id = "recipe_print_16bit",
            name = "Print Master 16-bit TIFF (AdobeRGB)",
            format = ExportFormat.TIFF16,
            colorSpace = ColorSpace.AdobeRGB,
            maxDimension = 0,
            applyEdgeSharpening = false,
            enableWatermark = false
        )

        val RECIPE_WEB_MASTER_WEBP = ExportRecipeConfig(
            id = "recipe_web_webp",
            name = "Web Master Ultra-Quality WebP (sRGB)",
            format = ExportFormat.WebP,
            colorSpace = ColorSpace.sRGB,
            quality = 95,
            maxDimension = 0,
            applyEdgeSharpening = false,
            enableWatermark = false
        )

        val RECIPE_SNS_OPTIMIZED_JPEG = ExportRecipeConfig(
            id = "recipe_sns_jpeg",
            name = "SNS 2048px Optimized JPEG (sRGB + High-Pass Sharpness)",
            format = ExportFormat.JPEG,
            colorSpace = ColorSpace.sRGB,
            quality = 92,
            maxDimension = 2048,
            applyEdgeSharpening = true,
            sharpeningAmount = 45.0f,
            enableWatermark = true
        )

        val STANDARD_MASTER_RECIPES = listOf(
            RECIPE_PRINT_MASTER_TIFF16,
            RECIPE_WEB_MASTER_WEBP,
            RECIPE_SNS_OPTIMIZED_JPEG
        )
    }

    /**
     * Execute simultaneous parallel export across multiple recipes with 1 single invocation.
     */
    fun executeMultiExport(
        inputRawPath: String,
        outputDir: String,
        baseFilename: String,
        params: DevelopmentParams,
        recipes: List<ExportRecipeConfig> = STANDARD_MASTER_RECIPES,
        watermark: ExifWatermarkConfig = ExifWatermarkConfig(),
        context: android.content.Context? = null,
        progressListener: (MultiExportProgress) -> Unit
    ): Job {
        return coroutineScope.launch {
            val outDirectory = File(outputDir)
            if (!outDirectory.exists()) outDirectory.mkdirs()

            // Run recipes sequentially to prevent LMK (Low Memory Killer) kill on mobile
            for (recipe in recipes) {
                if (!coroutineScope.isActive || isCancelled.get()) break
                val ext = when (recipe.format) {
                    ExportFormat.TIFF16, ExportFormat.TIFF8 -> "tif"
                    ExportFormat.WebP -> "webp"
                    ExportFormat.JPEG -> "jpg"
                    ExportFormat.LinearDNG -> "dng"
                }
                val outName = "${baseFilename}_${recipe.id}.${ext}"
                val outFile = File(outDirectory, outName).absolutePath

                withContext(Dispatchers.Main) {
                    progressListener(
                        MultiExportProgress(
                            recipeId = recipe.id,
                            recipeName = recipe.name,
                            progressPercent = 0.0f,
                            statusMessage = "Starting ${recipe.name}..."
                        )
                    )
                }

                val recipeParams = params.deepCopy().apply {
                    outputColorSpace = recipe.colorSpace
                    if (recipe.applyEdgeSharpening) {
                        sharpeningAmount = recipe.sharpeningAmount
                    }
                }
                val config = ExportConfig(
                    format = recipe.format,
                    jpegQuality = recipe.quality,
                    chromaSubsampling = ChromaSubsampling.YUV444,
                    tileSize = 2048,
                    tilePadding = 16,
                    maxLongEdge = recipe.maxDimension,
                    enableWatermark = recipe.enableWatermark,
                    watermarkText = if (recipe.enableWatermark) watermark.formatExposureLine() else ""
                )

                // Execute export with full development parameters
                val success = LightRumorNativeEngine.exportPhoto(
                    inputPath = inputRawPath,
                    outputPath = outFile,
                    config = config,
                    params = recipeParams,
                    callback = object : ProgressCallback {
                        override fun onProgress(progressPercent: Float, statusMessage: String) {
                            if (!coroutineScope.isActive || isCancelled.get()) return
                            coroutineScope.launch(Dispatchers.Main) {
                                progressListener(
                                    MultiExportProgress(
                                        recipeId = recipe.id,
                                        recipeName = recipe.name,
                                        progressPercent = progressPercent,
                                        statusMessage = statusMessage
                                    )
                                )
                            }
                        }
                    }
                )

                if (success && context != null) {
                    try {
                        val mimeType = when (recipe.format) {
                            ExportFormat.TIFF16, ExportFormat.TIFF8 -> "image/tiff"
                            ExportFormat.JPEG -> "image/jpeg"
                            ExportFormat.WebP -> "image/webp"
                            ExportFormat.LinearDNG -> "image/x-adobe-dng"
                        }
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(outFile),
                            arrayOf(mimeType),
                            null
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                withContext(Dispatchers.Main) {
                    progressListener(
                        MultiExportProgress(
                            recipeId = recipe.id,
                            recipeName = recipe.name,
                            progressPercent = 100.0f,
                            statusMessage = if (success) "Completed" else "Export Failed",
                            isFinished = true,
                            outputFilePath = if (success) outFile else null
                        )
                    )
                }
            }
        }
    }

    /**
     * Cancels any running multi-export jobs and releases coroutine resources.
     */
    fun cancel() {
        isCancelled.set(true)
        coroutineScope.cancel()
    }
}

