package com.lightrumor

import java.io.File
import java.util.UUID

/**
 * Data model for Lightroom-compatible XMP Presets.
 */
data class XmpPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val description: String = "",
    val author: String = "light_rumor",
    val params: DevelopmentParams
)

/**
 * XmpPresetParser: Lightroom XMP Preset Engine.
 * Supports:
 * - Direct import/parsing of standard Adobe Camera Raw (.xmp) presets
 * - Export and serialization to standard .xmp preset files
 * - Preset categories (Landscape, Portrait, Film, Monochrome, Urban)
 * - Amount slider scaling (0% to 200%)
 * - Zero AI dependencies, zero emojis, pure photographic precision.
 */
object XmpPresetParser {

    val CATEGORIES = listOf("ALL", "LANDSCAPE", "PORTRAIT", "FILM", "MONOCHROME", "URBAN")

    /**
     * Linearly blends development parameters between base state and preset state by amount [0.0, 2.0].
     * Amount 0.0 (0%): exact base image parameters
     * Amount 1.0 (100%): exact preset values
     * Amount 0.5 (50%): subtle half-strength effect
     * Amount 1.5 (150%): amplified intense preset effect
     */
    fun applyPresetWithAmount(
        base: DevelopmentParams,
        presetParams: DevelopmentParams,
        amountPercent: Float
    ): DevelopmentParams {
        val t = amountPercent / 100.0f
        val res = base.deepCopy()

        res.exposureEV = base.exposureEV + (presetParams.exposureEV - base.exposureEV) * t
        res.contrast = base.contrast + (presetParams.contrast - base.contrast) * t
        res.highlights = base.highlights + (presetParams.highlights - base.highlights) * t
        res.shadows = base.shadows + (presetParams.shadows - base.shadows) * t
        res.whites = base.whites + (presetParams.whites - base.whites) * t
        res.blacks = base.blacks + (presetParams.blacks - base.blacks) * t
        res.vibrance = base.vibrance + (presetParams.vibrance - base.vibrance) * t
        res.saturation = base.saturation + (presetParams.saturation - base.saturation) * t
        res.clarity = base.clarity + (presetParams.clarity - base.clarity) * t
        res.dehaze = base.dehaze + (presetParams.dehaze - base.dehaze) * t
        res.kelvin = base.kelvin + (presetParams.kelvin - base.kelvin) * t
        res.tint = base.tint + (presetParams.tint - base.tint) * t
        res.sharpeningAmount = base.sharpeningAmount + (presetParams.sharpeningAmount - base.sharpeningAmount) * t
        res.luminanceNR = base.luminanceNR + (presetParams.luminanceNR - base.luminanceNR) * t
        res.chromaNR = base.chromaNR + (presetParams.chromaNR - base.chromaNR) * t

        if (t >= 0.5f) {
            res.isMonochrome = presetParams.isMonochrome
        }

        return res
    }

    /**
     * Parses standard Adobe Camera Raw / Lightroom XMP XML into an XmpPreset.
     */
    fun parseXmpString(xmpContent: String, defaultName: String = "Imported Preset"): XmpPreset {
        val params = DevelopmentParams()

        // Name tag or title
        val titleMatch = Regex("<(crs:PresetName|dc:title|xmp:Name)[^>]*>([^<]+)</(crs:PresetName|dc:title|xmp:Name)>").find(xmpContent)
        val name = titleMatch?.groupValues?.get(2)?.trim() ?: defaultName

        // Category or Group
        val groupMatch = Regex("<(crs:Group|crs:Cluster)[^>]*>([^<]+)</(crs:Group|crs:Cluster)>").find(xmpContent)
        val category = groupMatch?.groupValues?.get(2)?.trim() ?: "USER"

        // Helper regex extractor
        fun extractFloat(attrName: String): Float? {
            val attrRegex = Regex("$attrName=\"([+-]?\\d*\\.?\\d+)\"").find(xmpContent)
            if (attrRegex != null) {
                return attrRegex.groupValues[1].toFloatOrNull()
            }
            val tagRegex = Regex("<$attrName>([+-]?\\d*\\.?\\d+)</$attrName>").find(xmpContent)
            return tagRegex?.groupValues?.get(1)?.toFloatOrNull()
        }

        extractFloat("crs:Temperature")?.let { params.kelvin = it }
        extractFloat("crs:Tint")?.let { params.tint = it }
        extractFloat("crs:Exposure2012")?.let { params.exposureEV = it }
        extractFloat("crs:Contrast2012")?.let { params.contrast = it }
        extractFloat("crs:Highlights2012")?.let { params.highlights = it }
        extractFloat("crs:Shadows2012")?.let { params.shadows = it }
        extractFloat("crs:Whites2012")?.let { params.whites = it }
        extractFloat("crs:Blacks2012")?.let { params.blacks = it }
        extractFloat("crs:Clarity2012")?.let { params.clarity = it }
        extractFloat("crs:Dehaze")?.let { params.dehaze = it }
        extractFloat("crs:Vibrance")?.let { params.vibrance = it }
        extractFloat("crs:Saturation")?.let { params.saturation = it }
        extractFloat("crs:Sharpness")?.let { params.sharpeningAmount = it }
        extractFloat("crs:LuminanceSmoothing")?.let { params.luminanceNR = it }
        extractFloat("crs:ColorNoiseReduction")?.let { params.chromaNR = it }

        if (xmpContent.contains("crs:ConvertToGrayscale=\"True\"") ||
            xmpContent.contains("<crs:ConvertToGrayscale>True</crs:ConvertToGrayscale>")) {
            params.isMonochrome = true
        }

        return XmpPreset(
            name = name,
            category = category.uppercase(),
            description = "Lightroom XMP Preset: $name",
            params = params
        )
    }

    /**
     * Serializes an XmpPreset to standard Adobe Camera Raw XMP XML packet format.
     */
    fun serializeToXmp(preset: XmpPreset): String {
        val p = preset.params
        return buildString {
            append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
            append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
            append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
            append("  <rdf:Description rdf:about=\"\"\n")
            append("    xmlns:crs=\"http://ns.adobe.com/camera-raw-settings/1.0/\"\n")
            append("   crs:PresetType=\"Normal\"\n")
            append("   crs:PresetName=\"${preset.name}\"\n")
            append("   crs:Group=\"${preset.category}\"\n")
            append("   crs:Temperature=\"${p.kelvin.toInt()}\"\n")
            append("   crs:Tint=\"${"%.1f".format(p.tint)}\"\n")
            append("   crs:Exposure2012=\"${"%+.2f".format(p.exposureEV)}\"\n")
            append("   crs:Contrast2012=\"${p.contrast.toInt()}\"\n")
            append("   crs:Highlights2012=\"${p.highlights.toInt()}\"\n")
            append("   crs:Shadows2012=\"${p.shadows.toInt()}\"\n")
            append("   crs:Whites2012=\"${p.whites.toInt()}\"\n")
            append("   crs:Blacks2012=\"${p.blacks.toInt()}\"\n")
            append("   crs:Clarity2012=\"${p.clarity.toInt()}\"\n")
            append("   crs:Dehaze=\"${p.dehaze.toInt()}\"\n")
            append("   crs:Vibrance=\"${p.vibrance.toInt()}\"\n")
            append("   crs:Saturation=\"${p.saturation.toInt()}\"\n")
            append("   crs:Sharpness=\"${p.sharpeningAmount.toInt()}\"\n")
            append("   crs:LuminanceSmoothing=\"${p.luminanceNR.toInt()}\"\n")
            append("   crs:ColorNoiseReduction=\"${p.chromaNR.toInt()}\"\n")
            append("   crs:ConvertToGrayscale=\"${if (p.isMonochrome) "True" else "False"}\">\n")
            append("  </rdf:Description>\n")
            append(" </rdf:RDF>\n")
            append("</x:xmpmeta>\n")
            append("<?xpacket end=\"w\"?>\n")
        }
    }

    /**
     * Built-in professional preset library.
     */
    fun getBuiltinPresets(): List<XmpPreset> = listOf(
        // LANDSCAPE
        XmpPreset(
            id = "preset_velvia_50",
            name = "Velvia 50",
            category = "LANDSCAPE",
            description = "High-saturation landscape classic with punchy greens and deep blues.",
            params = DevelopmentParams(
                exposureEV = 0.0f,
                contrast = 25.0f,
                highlights = -20.0f,
                shadows = 15.0f,
                whites = 20.0f,
                blacks = -15.0f,
                vibrance = 35.0f,
                saturation = 15.0f,
                clarity = 15.0f,
                dehaze = 10.0f,
                sharpeningAmount = 45.0f
            )
        ),
        XmpPreset(
            id = "preset_alpine_mist",
            name = "Alpine Mist",
            category = "LANDSCAPE",
            description = "Atmospheric mountain look with cool balance, lifted shadows, and soft contrast.",
            params = DevelopmentParams(
                kelvin = 5100.0f,
                tint = -5.0f,
                exposureEV = +0.20f,
                contrast = -15.0f,
                highlights = -10.0f,
                shadows = 35.0f,
                whites = 5.0f,
                blacks = 10.0f,
                vibrance = 10.0f,
                saturation = -10.0f,
                dehaze = -12.0f
            )
        ),
        XmpPreset(
            id = "preset_golden_coast",
            name = "Golden Coast",
            category = "LANDSCAPE",
            description = "Warm Pacific sunset with rich golden tones and gentle highlights.",
            params = DevelopmentParams(
                kelvin = 6300.0f,
                tint = 12.0f,
                exposureEV = +0.10f,
                contrast = 15.0f,
                highlights = -35.0f,
                shadows = 20.0f,
                whites = 10.0f,
                blacks = -5.0f,
                vibrance = 28.0f,
                saturation = 8.0f
            )
        ),

        // PORTRAIT
        XmpPreset(
            id = "preset_portra_400",
            name = "Portra 400",
            category = "PORTRAIT",
            description = "Natural warm skin tones, gentle contrast curve, and protected saturation.",
            params = DevelopmentParams(
                kelvin = 5650.0f,
                tint = 4.0f,
                exposureEV = +0.15f,
                contrast = -12.0f,
                highlights = -18.0f,
                shadows = 22.0f,
                whites = 8.0f,
                blacks = -4.0f,
                vibrance = 16.0f,
                saturation = -5.0f,
                clarity = -8.0f
            )
        ),
        XmpPreset(
            id = "preset_pro_neg_hi",
            name = "Pro Neg Hi",
            category = "PORTRAIT",
            description = "Controlled studio contrast with precise skin tone separation and sharp detail.",
            params = DevelopmentParams(
                kelvin = 5400.0f,
                tint = 0.0f,
                exposureEV = 0.0f,
                contrast = 18.0f,
                highlights = -15.0f,
                shadows = 10.0f,
                whites = 12.0f,
                blacks = -8.0f,
                vibrance = 12.0f,
                saturation = 0.0f,
                clarity = 10.0f,
                sharpeningAmount = 35.0f
            )
        ),

        // FILM
        XmpPreset(
            id = "preset_kodachrome_64",
            name = "Kodachrome 64",
            category = "FILM",
            description = "Legendary warm documentary slide film with saturated primaries and bold contrast.",
            params = DevelopmentParams(
                kelvin = 5800.0f,
                tint = 6.0f,
                exposureEV = -0.10f,
                contrast = 30.0f,
                highlights = -10.0f,
                shadows = -10.0f,
                whites = 15.0f,
                blacks = -25.0f,
                vibrance = 24.0f,
                saturation = 12.0f,
                clarity = 15.0f
            )
        ),
        XmpPreset(
            id = "preset_classic_chrome",
            name = "Classic Chrome",
            category = "FILM",
            description = "Subdued color saturation, hard documentary shadow contrast, and cool cast.",
            params = DevelopmentParams(
                kelvin = 5200.0f,
                tint = -4.0f,
                exposureEV = 0.0f,
                contrast = 20.0f,
                highlights = -15.0f,
                shadows = -15.0f,
                whites = 5.0f,
                blacks = -18.0f,
                vibrance = -10.0f,
                saturation = -22.0f,
                clarity = 12.0f
            )
        ),

        // MONOCHROME
        XmpPreset(
            id = "preset_ilford_hp5",
            name = "Ilford HP5 Plus",
            category = "MONOCHROME",
            description = "Quintessential versatile British black and white film with rich midtones.",
            params = DevelopmentParams(
                isMonochrome = true,
                contrast = 28.0f,
                highlights = -10.0f,
                shadows = 15.0f,
                whites = 18.0f,
                blacks = -20.0f,
                clarity = 20.0f,
                sharpeningAmount = 40.0f
            )
        ),
        XmpPreset(
            id = "preset_acros_100",
            name = "Acros 100",
            category = "MONOCHROME",
            description = "Ultra-fine tonal gradation, crisp micro-contrast, and deep lustrous shadows.",
            params = DevelopmentParams(
                isMonochrome = true,
                contrast = 35.0f,
                highlights = -20.0f,
                shadows = 8.0f,
                whites = 25.0f,
                blacks = -30.0f,
                clarity = 25.0f,
                sharpeningAmount = 50.0f
            )
        ),

        // URBAN
        XmpPreset(
            id = "preset_teal_orange",
            name = "Cine Teal Orange",
            category = "URBAN",
            description = "Cinematic complementary color contrast with moody shadows and warm highlights.",
            params = DevelopmentParams(
                kelvin = 5900.0f,
                tint = 8.0f,
                exposureEV = 0.0f,
                contrast = 22.0f,
                highlights = -20.0f,
                shadows = 18.0f,
                whites = 10.0f,
                blacks = -15.0f,
                vibrance = 30.0f,
                saturation = 5.0f,
                clarity = 18.0f
            )
        ),
        XmpPreset(
            id = "preset_tokyo_neon",
            name = "Tokyo Neon",
            category = "URBAN",
            description = "Night street photography look with deep blacks, high clarity, and vivid lighting.",
            params = DevelopmentParams(
                kelvin = 4900.0f,
                tint = 15.0f,
                exposureEV = -0.15f,
                contrast = 30.0f,
                highlights = -30.0f,
                shadows = 20.0f,
                whites = 15.0f,
                blacks = -25.0f,
                vibrance = 40.0f,
                saturation = 15.0f,
                clarity = 25.0f,
                dehaze = 15.0f
            )
        )
    )
}

