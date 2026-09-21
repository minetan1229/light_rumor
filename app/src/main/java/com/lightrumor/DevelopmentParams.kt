package com.lightrumor

enum class ExportFormat(val id: Int) {
    JPEG(0),
    TIFF16(1),
    TIFF8(2),
    WebP(3),
    LinearDNG(4)
}

enum class ChromaSubsampling(val id: Int) {
    YUV444(0),
    YUV420(1)
}

enum class ColorSpace(val id: Int) {
    LinearRec2020(0),
    LinearSRGB(1),
    ACEScg(2),
    sRGB(3),
    DisplayP3(4),
    AdobeRGB(5)
}

enum class AspectRatioMode(val id: Int, val label: String, val ratio: Float) {
    ORIGINAL(0, "Original", 0f),
    FREE(1, "Free", 0f),
    RATIO_1X1(2, "1:1", 1.0f),
    RATIO_4X5(3, "4:5", 0.8f),
    RATIO_3X2(4, "3:2", 1.5f),
    RATIO_16X9(5, "16:9", 1.7778f),
    RATIO_2X1(6, "2:1", 2.0f),
    RATIO_65X24(7, "65:24 XPAN", 2.7083f),
    GOLDEN(8, "1.618", 1.618f)
}

enum class CompositionGuide(val id: Int, val label: String) {
    NONE(0, "None"),
    RULE_OF_THIRDS(1, "3rds"),
    GOLDEN_RATIO(2, "Golden"),
    GOLDEN_SPIRAL(3, "Spiral"),
    DIAGONALS(4, "Diagonals"),
    GRID(5, "Grid")
}

data class HSLBandAdjust(
    var hueShift: Float = 0.0f,
    var saturation: Float = 0.0f,
    var luminance: Float = 0.0f
)

data class CropTransformParams(
    var cropX: Float = 0.0f,
    var cropY: Float = 0.0f,
    var cropW: Float = 1.0f,
    var cropH: Float = 1.0f,
    var aspectRatio: AspectRatioMode = AspectRatioMode.ORIGINAL,
    var rotationDegrees: Float = 0.0f,
    var rotationSteps: Int = 0,
    var flipHorizontal: Boolean = false,
    var flipVertical: Boolean = false,
    var perspectiveVertical: Float = 0.0f,
    var perspectiveHorizontal: Float = 0.0f,
    var distortion: Float = 0.0f,
    var guide: CompositionGuide = CompositionGuide.NONE
)

data class PrimaryCalibration(
    var hueShift: Float = 0.0f,
    var saturationShift: Float = 0.0f
)

data class SplitToningParams(
    var highlightsHue: Float = 0.0f,
    var highlightsSat: Float = 0.0f,
    var shadowsHue: Float = 0.0f,
    var shadowsSat: Float = 0.0f,
    var balance: Float = 0.0f
)

data class LensCorrectionParams(
    var enableProfileCorrection: Boolean = false,
    var distortionCorrection: Float = 100.0f,
    var vignettingCorrection: Float = 100.0f,
    var chromaticAberration: Float = 100.0f,
    var defringePurple: Float = 0.0f,
    var defringeGreen: Float = 0.0f
)

data class DevelopmentParams(
    var kelvin: Float = 5500.0f,
    var tint: Float = 0.0f,
    var shadowTintR: Float = 0.0f,
    var shadowTintG: Float = 0.0f,
    var shadowTintB: Float = 0.0f,
    var exposureEV: Float = 0.0f,
    var contrast: Float = 0.0f,
    var highlights: Float = 0.0f,
    var shadows: Float = 0.0f,
    var whites: Float = 0.0f,
    var blacks: Float = 0.0f,
    var vibrance: Float = 0.0f,
    var saturation: Float = 0.0f,
    var dehaze: Float = 0.0f,
    var clarity: Float = 0.0f,
    var texture: Float = 0.0f,
    var isMonochrome: Boolean = false,
    var hslBands: Array<HSLBandAdjust> = Array(8) { HSLBandAdjust() },
    var monochromeWeights: FloatArray = floatArrayOf(0.18f, 0.24f, 0.22f, 0.16f, 0.08f, 0.06f, 0.03f, 0.03f),
    var luminanceNR: Float = 0.0f,
    var luminanceNRDetail: Float = 50.0f,
    var luminanceNRContrast: Float = 0.0f,
    var chromaNR: Float = 0.0f,
    var chromaNRDetail: Float = 50.0f,
    var chromaNRSmoothness: Float = 50.0f,
    var sharpeningAmount: Float = 0.0f,
    var sharpeningRadius: Float = 1.0f,
    var sharpeningDetail: Float = 25.0f,
    var sharpeningMasking: Float = 20.0f,
    var sharpeningPreviewMask: Boolean = false,
    var primaryRed: PrimaryCalibration = PrimaryCalibration(),
    var primaryGreen: PrimaryCalibration = PrimaryCalibration(),
    var primaryBlue: PrimaryCalibration = PrimaryCalibration(),
    var splitToning: SplitToningParams = SplitToningParams(),
    var lensCorrection: LensCorrectionParams = LensCorrectionParams(),
    var geometry: CropTransformParams = CropTransformParams(),
    var outputColorSpace: ColorSpace = ColorSpace.sRGB,
    var enableDithering: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DevelopmentParams
        if (!monochromeWeights.contentEquals(other.monochromeWeights)) return false
        return true
    }

    override fun hashCode(): Int {
        return monochromeWeights.contentHashCode()
    }

    fun deepCopy(): DevelopmentParams = copy(
        hslBands = hslBands.map { it.copy() }.toTypedArray(),
        monochromeWeights = monochromeWeights.clone(),
        primaryRed = primaryRed.copy(),
        primaryGreen = primaryGreen.copy(),
        primaryBlue = primaryBlue.copy(),
        splitToning = splitToning.copy(),
        lensCorrection = lensCorrection.copy(),
        geometry = geometry.copy()
    )
}

// -------------------------------------------------------------------------
// Phase 5: Mask & Retouch Data Models
// -------------------------------------------------------------------------

enum class MaskType(val id: Int, val displayName: String) {
    LINEAR_GRADIENT(0, "LINEAR"),
    RADIAL_GRADIENT(1, "RADIAL"),
    POLYGON_BEZIER(2, "POLYGON"),
    BRUSH(3, "BRUSH"),
    LUMINANCE_RANGE(4, "LUMA"),
    COLOR_RANGE(5, "COLOR"),
    DEPTH_MAP(6, "DEPTH"),
    SOBEL_EDGE(7, "EDGE")
}

enum class BooleanOp(val id: Int, val displayName: String) {
    REPLACE(0, "SET"),
    UNION(1, "ADD"),
    SUBTRACT(2, "SUB"),
    INTERSECT(3, "INT")
}

data class MaskPoint(val x: Float, val y: Float)

data class BrushStrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
    val radius: Float = 25.0f,
    val flow: Float = 1.0f,
    val isEraser: Boolean = false
)

data class LocalAdjustmentState(
    var exposureEV: Float = 0.0f,
    var contrast: Float = 0.0f,
    var highlights: Float = 0.0f,
    var shadows: Float = 0.0f,
    var whites: Float = 0.0f,
    var blacks: Float = 0.0f,
    var kelvinOffset: Float = 0.0f,
    var tintOffset: Float = 0.0f,
    var saturation: Float = 0.0f,
    var clarity: Float = 0.0f,
    var dehaze: Float = 0.0f
)

data class MaskLayerState(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "Mask Layer",
    var enabled: Boolean = true,
    var inverted: Boolean = false,
    var opacity: Float = 1.0f,
    var type: MaskType = MaskType.RADIAL_GRADIENT,
    var booleanOp: BooleanOp = BooleanOp.UNION,

    // Linear
    var linearStartX: Float = 0.2f,
    var linearStartY: Float = 0.2f,
    var linearEndX: Float = 0.8f,
    var linearEndY: Float = 0.8f,
    var linearFeather: Float = 0.2f,

    // Radial
    var radialCenterX: Float = 0.5f,
    var radialCenterY: Float = 0.5f,
    var radialRadiusX: Float = 0.3f,
    var radialRadiusY: Float = 0.3f,
    var radialAngle: Float = 0.0f,
    var radialFeather: Float = 0.5f,

    // Polygon
    var polygonVertices: List<MaskPoint> = emptyList(),

    // Brush
    var brushStrokes: List<BrushStrokePoint> = emptyList(),
    var brushRadius: Float = 30.0f,
    var brushFeather: Float = 0.5f,

    // Luminance Range
    var lumaMin: Float = 0.0f,
    var lumaMax: Float = 1.0f,
    var lumaFeather: Float = 0.1f,

    // Color Range
    var colorTargetHue: Float = 0.0f,
    var colorTargetSat: Float = 0.0f,
    var colorTargetLum: Float = 0.5f,
    var colorTolHue: Float = 30.0f,
    var colorTolSat: Float = 0.3f,
    var colorTolLum: Float = 0.3f,

    // Depth
    var depthMin: Float = 0.0f,
    var depthMax: Float = 1.0f,

    // Sobel
    var edgeThreshold: Float = 0.15f,

    // Local Adjustments
    var adjustments: LocalAdjustmentState = LocalAdjustmentState()
) {
    fun deepCopy(): MaskLayerState = copy(
        polygonVertices = polygonVertices.toList(),
        brushStrokes = brushStrokes.toList(),
        adjustments = adjustments.copy()
    )
}

data class RetouchStrokeState(
    val id: String = java.util.UUID.randomUUID().toString(),
    var isHeal: Boolean = true,
    var sourceX: Float = 0.0f,
    var sourceY: Float = 0.0f,
    var targetX: Float = 0.0f,
    var targetY: Float = 0.0f,
    var radius: Float = 25.0f,
    var feather: Float = 0.5f,
    var opacity: Float = 1.0f
)

data class ExportConfig(
    val format: ExportFormat = ExportFormat.JPEG,
    val jpegQuality: Int = 98,
    val chromaSubsampling: ChromaSubsampling = ChromaSubsampling.YUV444,
    val tileSize: Int = 2048,
    val tilePadding: Int = 16
)

enum class PickStatus(val value: Int) {
    REJECTED(-1),
    NONE(0),
    PICKED(1)
}

enum class ColorLabel(val id: Int, val labelName: String, val hexColor: Long) {
    NONE(0, "None", 0x00000000),
    RED(1, "Red", 0xFFE53935),
    YELLOW(2, "Yellow", 0xFFFDD835),
    GREEN(3, "Green", 0xFF43A047),
    BLUE(4, "Blue", 0xFF1E88E5),
    PURPLE(5, "Purple", 0xFF8E24AA)
}

data class BatchSyncOptions(
    var syncWhiteBalance: Boolean = true,
    var syncBasicTone: Boolean = true,
    var syncColorMixer: Boolean = true,
    var syncDetailNR: Boolean = true,
    var syncToneCurve: Boolean = false,
    var syncCropGeometry: Boolean = false,
    var syncRatingAndLabel: Boolean = false
) {
    fun merge(src: DevelopmentParams, dst: DevelopmentParams) {
        if (syncWhiteBalance) {
            dst.kelvin = src.kelvin
            dst.tint = src.tint
            dst.shadowTintR = src.shadowTintR
            dst.shadowTintG = src.shadowTintG
            dst.shadowTintB = src.shadowTintB
            dst.primaryRed = src.primaryRed.copy()
            dst.primaryGreen = src.primaryGreen.copy()
            dst.primaryBlue = src.primaryBlue.copy()
        }
        if (syncBasicTone) {
            dst.exposureEV = src.exposureEV
            dst.contrast = src.contrast
            dst.highlights = src.highlights
            dst.shadows = src.shadows
            dst.whites = src.whites
            dst.blacks = src.blacks
            dst.vibrance = src.vibrance
            dst.saturation = src.saturation
            dst.dehaze = src.dehaze
            dst.clarity = src.clarity
            dst.texture = src.texture
        }
        if (syncColorMixer) {
            dst.isMonochrome = src.isMonochrome
            dst.hslBands = src.hslBands.map { it.copy() }.toTypedArray()
            dst.monochromeWeights = src.monochromeWeights.clone()
        }
        if (syncDetailNR) {
            dst.luminanceNR = src.luminanceNR
            dst.luminanceNRDetail = src.luminanceNRDetail
            dst.luminanceNRContrast = src.luminanceNRContrast
            dst.chromaNR = src.chromaNR
            dst.chromaNRDetail = src.chromaNRDetail
            dst.chromaNRSmoothness = src.chromaNRSmoothness
            dst.sharpeningAmount = src.sharpeningAmount
            dst.sharpeningRadius = src.sharpeningRadius
            dst.sharpeningDetail = src.sharpeningDetail
            dst.sharpeningMasking = src.sharpeningMasking
        }
        if (syncCropGeometry) {
            dst.geometry = src.geometry.copy()
        }
    }
}

