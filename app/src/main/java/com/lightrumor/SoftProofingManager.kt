package com.lightrumor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ProofingIntent(val id: Int, val displayName: String) {
    PERCEPTUAL(0, "Perceptual (知覚的)"),
    RELATIVE_COLORIMETRIC(1, "Relative Colorimetric (相対的色度)"),
    SATURATION(2, "Saturation (彩度)"),
    ABSOLUTE_COLORIMETRIC(3, "Absolute Colorimetric (絶対的色度)")
}

data class PaperProfile(
    val id: String,
    val manufacturer: String,
    val name: String,
    val surfaceType: String,
    val dMax: Float,
    val profilePath: String
)

data class SoftProofingState(
    val isEnabled: Boolean = false,
    val selectedProfile: PaperProfile = PaperProfile(
        id = "canon_pt101",
        manufacturer = "Canon",
        name = "Photo Paper Pro Platinum (PT-101)",
        surfaceType = "Glossy Fine Art",
        dMax = 2.45f,
        profilePath = "profiles/canon_pt101.icc"
    ),
    val intent: ProofingIntent = ProofingIntent.RELATIVE_COLORIMETRIC,
    val simulatePaperWhite: Boolean = true,
    val simulateBlackInk: Boolean = true,
    val showGamutWarning: Boolean = false,
    val gamutWarningColor: Int = 0xFF50E3C2.toInt() // Vivid mint overlay
)

object SoftProofingManager {

    val BUILTIN_PROFILES = listOf(
        PaperProfile("canon_pt101", "Canon", "Photo Paper Pro Platinum (PT-101)", "Glossy Fine Art", 2.45f, "profiles/canon_pt101.icc"),
        PaperProfile("epson_traditional", "Epson", "Traditional Photo Paper (Baryta)", "Luster Fine Art", 2.38f, "profiles/epson_traditional.icc"),
        PaperProfile("hahnemuhle_photorag", "Hahnemühle", "Photo Rag 308 (Matte Cotton)", "Smooth Matte Cotton", 1.72f, "profiles/hahnemuhle_photorag.icc"),
        PaperProfile("ilford_gold_fibre", "Ilford", "Galerie Gold Fibre Gloss", "Baryta Warm Tone", 2.35f, "profiles/ilford_gold_fibre.icc")
    )

    private val _state = MutableStateFlow(SoftProofingState())
    val state: StateFlow<SoftProofingState> = _state.asStateFlow()

    fun toggleSoftProofing(enabled: Boolean? = null) {
        val next = enabled ?: !_state.value.isEnabled
        _state.value = _state.value.copy(isEnabled = next)
    }

    fun selectProfile(profile: PaperProfile) {
        _state.value = _state.value.copy(selectedProfile = profile)
    }

    fun setIntent(intent: ProofingIntent) {
        _state.value = _state.value.copy(intent = intent)
    }

    fun setSimulatePaperWhite(enabled: Boolean) {
        _state.value = _state.value.copy(simulatePaperWhite = enabled)
    }

    fun setSimulateBlackInk(enabled: Boolean) {
        _state.value = _state.value.copy(simulateBlackInk = enabled)
    }

    fun toggleGamutWarning(enabled: Boolean? = null) {
        val next = enabled ?: !_state.value.showGamutWarning
        _state.value = _state.value.copy(showGamutWarning = next)
    }

    fun applySoftProofNative(
        rgbaPixels: IntArray,
        width: Int,
        height: Int
    ): IntArray {
        if (!_state.value.isEnabled) return rgbaPixels
        // Direct invocation via native JNI or engine
        return LightRumorNativeEngine.nativeApplySoftProof(
            pixels = rgbaPixels,
            width = width,
            height = height,
            profilePath = _state.value.selectedProfile.profilePath,
            intent = _state.value.intent.id,
            simulatePaperWhite = _state.value.simulatePaperWhite,
            simulateBlackInk = _state.value.simulateBlackInk,
            showGamutWarning = _state.value.showGamutWarning,
            gamutWarningColor = _state.value.gamutWarningColor
        ) ?: rgbaPixels
    }
}

