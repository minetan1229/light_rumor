package com.apex.field.engine

data class DevelopParameters(
    var exposureEV: Float = 0.0f,
    var contrast: Float = 0.0f,
    var highlights: Float = 0.0f,
    var shadows: Float = 0.0f,
    var temperatureKelvin: Float = 5500.0f,
    var tint: Float = 0.0f,
    var vibrance: Float = 0.0f,
    var saturation: Float = 0.0f,
    var isMonochrome: Boolean = false
)

object ApexNativeEngine {
    init {
        System.loadLibrary("apexfield")
    }

    external fun nativeProcessBuffer(
        buffer: FloatArray,
        width: Int,
        height: Int,
        exposureEV: Float,
        contrast: Float,
        highlights: Float,
        shadows: Float,
        kelvin: Float,
        tint: Float,
        vibrance: Float,
        saturation: Float,
        isMonochrome: Boolean
    )

    fun process(buffer: FloatArray, width: Int, height: Int, params: DevelopParameters) {
        nativeProcessBuffer(
            buffer,
            width,
            height,
            params.exposureEV,
            params.contrast,
            params.highlights,
            params.shadows,
            params.temperatureKelvin,
            params.tint,
            params.vibrance,
            params.saturation,
            params.isMonochrome
        )
    }
}
