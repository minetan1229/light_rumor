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

    val CATEGORIES = listOf("すべて", "風景", "ポートレート", "フィルム", "モノクロ", "アーバン")

    /**
     * Linearly blends development parameters between base state and preset state by amount [0.0, 2.0].
     *
     * Photographic Preserving Logic:
     * - Exposure (exposureEV) is applied as a relative offset rather than an absolute override,
     *   faithfully preserving the user's calibrated base exposure.
     * - White Balance (kelvin, tint) is applied relative to the 5500K daylight neutral baseline,
     *   preventing severe color distortion when applied across photos of different lighting (daylight, tungsten, golden hour).
     * - Stylistic tone parameters (contrast, highlights, shadows, whites, blacks, vibrance, saturation, clarity, dehaze, texture)
     *   are blended smoothly as relative adjustments and clamped to operational ranges.
     * - Detail and noise reduction parameters scale proportionally with preset amount.
     * - Monochrome mode transitions cleanly when preset amount >= 30%.
     */
    fun applyPresetWithAmount(
        base: DevelopmentParams,
        presetParams: DevelopmentParams,
        amountPercent: Float
    ): DevelopmentParams {
        val t = (amountPercent / 100.0f).coerceAtLeast(0.0f)
        val res = base.deepCopy()

        // 1. Exposure: Relative offset blend (preserving user's exposure calibration)
        val exposureOffset = presetParams.exposureEV * t
        res.exposureEV = (base.exposureEV + exposureOffset).coerceIn(-5.0f, 5.0f)

        // 2. White Balance: Relative offset from daylight neutral baseline (5500K / 0 tint)
        val kelvinOffset = (presetParams.kelvin - 5500.0f) * t
        res.kelvin = (base.kelvin + kelvinOffset).coerceIn(2000.0f, 12000.0f)

        val tintOffset = presetParams.tint * t
        res.tint = (base.tint + tintOffset).coerceIn(-100.0f, 100.0f)

        // 3. Tonal & Stylistic Parameters: Relative additive offsets clamped to [-100, +100]
        res.contrast = (base.contrast + presetParams.contrast * t).coerceIn(-100.0f, 100.0f)
        res.highlights = (base.highlights + presetParams.highlights * t).coerceIn(-100.0f, 100.0f)
        res.shadows = (base.shadows + presetParams.shadows * t).coerceIn(-100.0f, 100.0f)
        res.whites = (base.whites + presetParams.whites * t).coerceIn(-100.0f, 100.0f)
        res.blacks = (base.blacks + presetParams.blacks * t).coerceIn(-100.0f, 100.0f)
        res.vibrance = (base.vibrance + presetParams.vibrance * t).coerceIn(-100.0f, 100.0f)
        res.saturation = (base.saturation + presetParams.saturation * t).coerceIn(-100.0f, 100.0f)
        res.clarity = (base.clarity + presetParams.clarity * t).coerceIn(-100.0f, 100.0f)
        res.dehaze = (base.dehaze + presetParams.dehaze * t).coerceIn(-100.0f, 100.0f)
        res.texture = (base.texture + presetParams.texture * t).coerceIn(-100.0f, 100.0f)

        // 4. Detail / Sharpness / Noise Reduction
        res.sharpeningAmount = (base.sharpeningAmount + presetParams.sharpeningAmount * t).coerceIn(0.0f, 150.0f)
        res.luminanceNR = (base.luminanceNR + presetParams.luminanceNR * t).coerceIn(0.0f, 100.0f)
        res.chromaNR = (base.chromaNR + presetParams.chromaNR * t).coerceIn(0.0f, 100.0f)

        // 5. Monochrome mode & Channel mixing
        if (presetParams.isMonochrome) {
            if (t >= 0.3f) {
                res.isMonochrome = true
                res.monochromeWeights = presetParams.monochromeWeights.clone()
            }
        } else if (base.isMonochrome && t >= 0.5f) {
            res.isMonochrome = false
        }

        return res
    }

    /**
     * Parses standard Adobe Camera Raw / Lightroom XMP XML into an XmpPreset.
     */
    fun parseXmpString(xmpContent: String, defaultName: String = "Imported Preset"): XmpPreset {
        val params = DevelopmentParams()

        // Name tag or title (element format or attribute format)
        val titleMatch = Regex("<(crs:PresetName|dc:title|xmp:Name)[^>]*>([^<]+)</(crs:PresetName|dc:title|xmp:Name)>").find(xmpContent)
        val attrTitleMatch = Regex("""crs:PresetName="([^"]+)"""").find(xmpContent)
        val name = titleMatch?.groupValues?.get(2)?.trim()
            ?: attrTitleMatch?.groupValues?.get(1)?.trim()
            ?: defaultName

        // Category or Group
        val groupMatch = Regex("<(crs:Group|crs:Cluster)[^>]*>([^<]+)</(crs:Group|crs:Cluster)>").find(xmpContent)
        val attrGroupMatch = Regex("""crs:Group="([^"]+)"""").find(xmpContent)
        val category = groupMatch?.groupValues?.get(2)?.trim()
            ?: attrGroupMatch?.groupValues?.get(1)?.trim()
            ?: "USER"

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
            append("   crs:PresetName=\"${escapeXml(preset.name)}\"\n")
            append("   crs:Group=\"${escapeXml(preset.category)}\"\n")
            append("   crs:Temperature=\"${p.kelvin.toInt()}\"\n")
            append("   crs:Tint=\"${String.format(java.util.Locale.US, "%.1f", p.tint)}\"\n")
            append("   crs:Exposure2012=\"${String.format(java.util.Locale.US, "%+.2f", p.exposureEV)}\"\n")
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
            description = "High-saturation landscape slide classic with punchy greens and deep blues.",
            params = DevelopmentParams(
                kelvin = 5550.0f,
                tint = -2.0f,
                exposureEV = 0.0f,
                contrast = 28.0f,
                highlights = -18.0f,
                shadows = -8.0f,
                whites = 16.0f,
                blacks = -18.0f,
                vibrance = 32.0f,
                saturation = 18.0f,
                clarity = 14.0f,
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
                exposureEV = +0.10f,
                contrast = -12.0f,
                highlights = -10.0f,
                shadows = 28.0f,
                whites = 6.0f,
                blacks = 8.0f,
                vibrance = 12.0f,
                saturation = -8.0f,
                dehaze = -10.0f
            )
        ),
        XmpPreset(
            id = "preset_golden_coast",
            name = "Golden Coast",
            category = "LANDSCAPE",
            description = "Warm Pacific sunset with rich golden tones and gentle highlights.",
            params = DevelopmentParams(
                kelvin = 6250.0f,
                tint = 10.0f,
                exposureEV = +0.10f,
                contrast = 16.0f,
                highlights = -30.0f,
                shadows = 18.0f,
                whites = 10.0f,
                blacks = -6.0f,
                vibrance = 26.0f,
                saturation = 8.0f
            )
        ),

        // PORTRAIT
        XmpPreset(
            id = "preset_portra_400",
            name = "Portra 400",
            category = "PORTRAIT",
            description = "Natural warm skin tones, gentle contrast curve, protected highlights, and lifted matte blacks.",
            params = DevelopmentParams(
                kelvin = 5700.0f,
                tint = 4.0f,
                exposureEV = +0.15f,
                contrast = -8.0f,
                highlights = -22.0f,
                shadows = 18.0f,
                whites = 6.0f,
                blacks = 10.0f,
                vibrance = 14.0f,
                saturation = -6.0f,
                clarity = -6.0f,
                texture = -4.0f
            )
        ),
        XmpPreset(
            id = "preset_pro_neg_hi",
            name = "Pro Neg Hi",
            category = "PORTRAIT",
            description = "Controlled studio contrast with precise skin tone separation and sharp detail.",
            params = DevelopmentParams(
                kelvin = 5450.0f,
                tint = 1.0f,
                exposureEV = 0.0f,
                contrast = 14.0f,
                highlights = -16.0f,
                shadows = 12.0f,
                whites = 10.0f,
                blacks = -6.0f,
                vibrance = 10.0f,
                saturation = -2.0f,
                clarity = 8.0f,
                sharpeningAmount = 35.0f
            )
        ),

        // FILM
        XmpPreset(
            id = "preset_classic_chrome",
            name = "Classic Chrome",
            category = "FILM",
            description = "Subdued color saturation, hard documentary shadow contrast, and cool cast.",
            params = DevelopmentParams(
                kelvin = 5350.0f,
                tint = -3.0f,
                exposureEV = -0.05f,
                contrast = 22.0f,
                highlights = -15.0f,
                shadows = -12.0f,
                whites = 8.0f,
                blacks = -16.0f,
                vibrance = -12.0f,
                saturation = -20.0f,
                clarity = 15.0f,
                dehaze = 6.0f
            )
        ),
        XmpPreset(
            id = "preset_kodachrome_64",
            name = "Kodachrome 64",
            category = "FILM",
            description = "Legendary warm documentary slide film with saturated primaries and bold contrast.",
            params = DevelopmentParams(
                kelvin = 5750.0f,
                tint = 6.0f,
                exposureEV = -0.08f,
                contrast = 26.0f,
                highlights = -14.0f,
                shadows = -8.0f,
                whites = 14.0f,
                blacks = -20.0f,
                vibrance = 26.0f,
                saturation = 10.0f,
                clarity = 14.0f
            )
        ),

        // MONOCHROME
        XmpPreset(
            id = "preset_trix_400",
            name = "Tri-X 400",
            category = "MONOCHROME",
            description = "Iconic photojournalism monochrome with punchy contrast, deep blacks, and gritty silver grain.",
            params = DevelopmentParams(
                isMonochrome = true,
                contrast = 36.0f,
                highlights = -12.0f,
                shadows = 8.0f,
                whites = 22.0f,
                blacks = -28.0f,
                clarity = 22.0f,
                sharpeningAmount = 48.0f,
                monochromeWeights = floatArrayOf(0.25f, 0.35f, 0.20f, 0.10f, 0.05f, 0.03f, 0.01f, 0.01f)
            )
        ),
        XmpPreset(
            id = "preset_ilford_hp5",
            name = "Ilford HP5 Plus",
            category = "MONOCHROME",
            description = "Quintessential versatile British black and white film with rich midtones.",
            params = DevelopmentParams(
                isMonochrome = true,
                contrast = 18.0f,
                highlights = -10.0f,
                shadows = 20.0f,
                whites = 12.0f,
                blacks = -12.0f,
                clarity = 12.0f,
                sharpeningAmount = 35.0f
            )
        ),
        XmpPreset(
            id = "preset_acros_100",
            name = "Acros 100",
            category = "MONOCHROME",
            description = "Ultra-fine tonal gradation, crisp micro-contrast, and deep lustrous shadows.",
            params = DevelopmentParams(
                isMonochrome = true,
                contrast = 30.0f,
                highlights = -18.0f,
                shadows = 6.0f,
                whites = 26.0f,
                blacks = -22.0f,
                clarity = 20.0f,
                sharpeningAmount = 55.0f
            )
        ),

        // URBAN
        XmpPreset(
            id = "preset_teal_orange",
            name = "Cine Teal Orange",
            category = "URBAN",
            description = "Cinematic complementary color contrast with moody shadows and warm highlights.",
            params = DevelopmentParams(
                kelvin = 5850.0f,
                tint = 7.0f,
                exposureEV = 0.0f,
                contrast = 24.0f,
                highlights = -18.0f,
                shadows = 14.0f,
                whites = 12.0f,
                blacks = -16.0f,
                vibrance = 28.0f,
                saturation = 6.0f,
                clarity = 16.0f
            )
        ),
        XmpPreset(
            id = "preset_tokyo_neon",
            name = "Tokyo Neon",
            category = "URBAN",
            description = "Night street photography look with deep blacks, high clarity, and vivid lighting.",
            params = DevelopmentParams(
                kelvin = 4850.0f,
                tint = 14.0f,
                exposureEV = -0.10f,
                contrast = 28.0f,
                highlights = -25.0f,
                shadows = 18.0f,
                whites = 16.0f,
                blacks = -22.0f,
                vibrance = 35.0f,
                saturation = 12.0f,
                clarity = 22.0f,
                dehaze = 12.0f
            )
        )
    )

    private fun escapeXml(str: String): String = str
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
