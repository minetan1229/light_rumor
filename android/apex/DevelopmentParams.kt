package com.apexfield.engine

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

data class DevelopmentParams(
    var kelvin: Float = 5500.0f,
    var tint: Float = 0.0f,
    var exposureEV: Float = 0.0f,
    var contrast: Float = 0.0f,
    var highlights: Float = 0.0f,
    var shadows: Float = 0.0f,
    var whites: Float = 0.0f,
    var blacks: Float = 0.0f,
    var vibrance: Float = 0.0f,
    var saturation: Float = 0.0f,
    var isMonochrome: Boolean = false,
    var luminanceNR: Float = 0.0f,
    var chromaNR: Float = 0.0f,
    var sharpeningAmount: Float = 0.0f,
    var outputColorSpace: ColorSpace = ColorSpace.sRGB,
    var enableDithering: Boolean = true
)

data class ExportConfig(
    val format: ExportFormat = ExportFormat.JPEG,
    val jpegQuality: Int = 98,
    val chromaSubsampling: ChromaSubsampling = ChromaSubsampling::YUV444,
    val tileSize: Int = 2048,
    val tilePadding: Int = 16
)
