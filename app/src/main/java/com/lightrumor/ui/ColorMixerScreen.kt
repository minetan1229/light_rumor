package com.lightrumor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*

/**
 * Optical Color Bands for 8-channel independent HSL and Monochrome adjustment.
 */
data class ColorBandInfo(
    val index: Int,
    val name: String,
    val code: String,
    val previewColor: Color
)

val COLOR_BANDS = listOf(
    ColorBandInfo(0, "RED", "RED", Color(0xFFE53935)),
    ColorBandInfo(1, "ORANGE", "ORG", Color(0xFFFF9800)),
    ColorBandInfo(2, "YELLOW", "YEL", Color(0xFFFFEB3B)),
    ColorBandInfo(3, "GREEN", "GRN", Color(0xFF4CAF50)),
    ColorBandInfo(4, "AQUA", "AQU", Color(0xFF00BCD4)),
    ColorBandInfo(5, "BLUE", "BLU", Color(0xFF2196F3)),
    ColorBandInfo(6, "PURPLE", "PUR", Color(0xFF9C27B0)),
    ColorBandInfo(7, "MAGENTA", "MAG", Color(0xFFE91E63))
)

data class OpticalFilterPreset(
    val id: String,
    val label: String,
    val weights: FloatArray
)

val OPTICAL_FILTERS = listOf(
    OpticalFilterPreset("pan", "PANCHROMATIC", floatArrayOf(0.18f, 0.24f, 0.22f, 0.16f, 0.08f, 0.06f, 0.03f, 0.03f)),
    OpticalFilterPreset("red25a", "RED 25A", floatArrayOf(0.60f, 0.25f, 0.10f, 0.03f, 0.01f, 0.001f, 0.004f, 0.005f)),
    OpticalFilterPreset("orange", "ORANGE", floatArrayOf(0.35f, 0.40f, 0.15f, 0.06f, 0.02f, 0.01f, 0.005f, 0.005f)),
    OpticalFilterPreset("yellow", "YELLOW", floatArrayOf(0.15f, 0.30f, 0.35f, 0.12f, 0.04f, 0.02f, 0.01f, 0.01f)),
    OpticalFilterPreset("green", "GREEN", floatArrayOf(0.05f, 0.10f, 0.25f, 0.45f, 0.10f, 0.03f, 0.01f, 0.01f))
)

/**
 * ColorMixerScreen: Professional 8-Color HSL & Advanced Monochrome Instrument.
 * Features:
 * - 8-color independent HSL band control (Hue, Saturation, Luminance)
 * - True optical color chips with high-contrast tactile feedback
 * - Advanced B&W Monochrome Luminance Mixer
 * - Classic physical optical filter presets (Red 25A, Orange, Yellow, Green)
 * - Strict Anti-AI styling: Obsidian chassis, titanium hairline accents, zero emojis
 */
@Composable
fun ColorMixerScreen(
    params: DevelopmentParams,
    onParamsChange: (DevelopmentParams) -> Unit,
    hapticManager: HapticManager? = null,
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    var selectedBandIndex by remember { mutableIntStateOf(0) }
    var activeFilterId by remember { mutableStateOf("pan") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(vertical = 6.dp)
    ) {
        // 1. Mode Switcher: HSL COLOR vs MONOCHROME MIXER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (!params.isMonochrome) colors.surfaceElevated else colors.surface,
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        1.dp,
                        if (!params.isMonochrome) colors.accentAmber else colors.borderSubtle,
                        RoundedCornerShape(3.dp)
                    )
                    .clickable {
                        hapticManager?.performDialTick()
                        onParamsChange(params.copy(isMonochrome = false))
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "8-COLOR HSL MIXER",
                    style = LightRumorTheme.typography.Tab,
                    color = if (!params.isMonochrome) colors.accentAmber else colors.textSecondary,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (params.isMonochrome) colors.surfaceElevated else colors.surface,
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        1.dp,
                        if (params.isMonochrome) colors.accentAmber else colors.borderSubtle,
                        RoundedCornerShape(3.dp)
                    )
                    .clickable {
                        hapticManager?.performDialTick()
                        onParamsChange(params.copy(isMonochrome = true))
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "B&W MONOCHROME MIXER",
                    style = LightRumorTheme.typography.Tab,
                    color = if (params.isMonochrome) colors.accentAmber else colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (!params.isMonochrome) {
            // -----------------------------------------------------------------
            // MODE A: 8-COLOR HSL MIXER
            // -----------------------------------------------------------------
            // Color band chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                COLOR_BANDS.forEach { band ->
                    val isSelected = band.index == selectedBandIndex
                    Row(
                        modifier = Modifier
                            .background(
                                if (isSelected) colors.surfaceElevated else colors.surface,
                                RoundedCornerShape(3.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) colors.accentAmber else colors.borderSubtle,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable {
                                hapticManager?.performDialTick()
                                selectedBandIndex = band.index
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(band.previewColor, CircleShape)
                        )
                        Text(
                            text = band.code,
                            style = LightRumorTheme.typography.Header,
                            color = if (isSelected) colors.textPrimary else colors.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Band Sliders (Hue, Saturation, Luminance)
            val currentBand = params.hslBands.getOrElse(selectedBandIndex) { HSLBandAdjust() }
            val currentBandInfo = COLOR_BANDS[selectedBandIndex]

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${currentBandInfo.name} BAND ADJUSTMENT",
                    style = LightRumorTheme.typography.Label,
                    color = colors.accentAmber,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    fontSize = 10.sp
                )

                LightroomSlider(
                    label = "${currentBandInfo.name} Hue",
                    value = currentBand.hueShift,
                    onValueChange = { newVal ->
                        val updated = params.hslBands.copyOf()
                        updated[selectedBandIndex] = updated[selectedBandIndex].copy(hueShift = newVal)
                        onParamsChange(params.copy(hslBands = updated))
                    },
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "${currentBandInfo.name} Saturation",
                    value = currentBand.saturation,
                    onValueChange = { newVal ->
                        val updated = params.hslBands.copyOf()
                        updated[selectedBandIndex] = updated[selectedBandIndex].copy(saturation = newVal)
                        onParamsChange(params.copy(hslBands = updated))
                    },
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "${currentBandInfo.name} Luminance",
                    value = currentBand.luminance,
                    onValueChange = { newVal ->
                        val updated = params.hslBands.copyOf()
                        updated[selectedBandIndex] = updated[selectedBandIndex].copy(luminance = newVal)
                        onParamsChange(params.copy(hslBands = updated))
                    },
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                // Primary Calibration Sub-panel
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "CAMERA PRIMARY CALIBRATION",
                    style = LightRumorTheme.typography.Label,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    fontSize = 10.sp
                )

                LightroomSlider(
                    label = "Red Primary Hue",
                    value = params.primaryRed.hueShift,
                    onValueChange = { onParamsChange(params.copy(primaryRed = params.primaryRed.copy(hueShift = it))) },
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "Red Primary Sat",
                    value = params.primaryRed.saturationShift,
                    onValueChange = { onParamsChange(params.copy(primaryRed = params.primaryRed.copy(saturationShift = it))) },
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "Green Primary Hue",
                    value = params.primaryGreen.hueShift,
                    onValueChange = { onParamsChange(params.copy(primaryGreen = params.primaryGreen.copy(hueShift = it))) },
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "Green Primary Sat",
                    value = params.primaryGreen.saturationShift,
                    onValueChange = { onParamsChange(params.copy(primaryGreen = params.primaryGreen.copy(saturationShift = it))) },
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "Blue Primary Hue",
                    value = params.primaryBlue.hueShift,
                    onValueChange = { onParamsChange(params.copy(primaryBlue = params.primaryBlue.copy(hueShift = it))) },
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "Blue Primary Sat",
                    value = params.primaryBlue.saturationShift,
                    onValueChange = { onParamsChange(params.copy(primaryBlue = params.primaryBlue.copy(saturationShift = it))) },
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )
            }
        } else {
            // -----------------------------------------------------------------
            // MODE B: B&W MONOCHROME MIXER & OPTICAL FILTERS
            // -----------------------------------------------------------------
            // Optical Filter Preset Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OPTICAL_FILTERS.forEach { filter ->
                    val isSelected = filter.id == activeFilterId
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) colors.surfaceElevated else colors.surface,
                                RoundedCornerShape(3.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) colors.accentAmber else colors.borderSubtle,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable {
                                hapticManager?.performDialTick()
                                activeFilterId = filter.id
                                onParamsChange(params.copy(monochromeWeights = filter.weights.clone()))
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            style = LightRumorTheme.typography.Tab,
                            color = if (isSelected) colors.accentAmber else colors.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 8-Channel Monochrome Luminance Sliders
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                COLOR_BANDS.forEach { band ->
                    val currentWeight = params.monochromeWeights.getOrElse(band.index) { 0.125f }
                    LightroomSlider(
                        label = "${band.name} Luma",
                        value = currentWeight * 100f,
                        onValueChange = { newVal ->
                            val updatedWeights = params.monochromeWeights.clone()
                            updatedWeights[band.index] = newVal * 0.01f
                            activeFilterId = "custom"
                            onParamsChange(params.copy(monochromeWeights = updatedWeights))
                        },
                        range = 0f..100f,
                        defaultValue = 12.5f,
                        unit = "%",
                        displayDecimals = 1,
                        step = 1f,
                        hapticManager = hapticManager
                    )
                }
            }
        }
    }
}

