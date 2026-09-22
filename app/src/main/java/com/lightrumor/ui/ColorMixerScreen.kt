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
    ColorBandInfo(0, "レッド", "RED", Color(0xFFE53935)),
    ColorBandInfo(1, "オレンジ", "ORG", Color(0xFFFF9800)),
    ColorBandInfo(2, "イエロー", "YEL", Color(0xFFFFEB3B)),
    ColorBandInfo(3, "グリーン", "GRN", Color(0xFF4CAF50)),
    ColorBandInfo(4, "アクア", "AQU", Color(0xFF00BCD4)),
    ColorBandInfo(5, "ブルー", "BLU", Color(0xFF2196F3)),
    ColorBandInfo(6, "パープル", "PUR", Color(0xFF9C27B0)),
    ColorBandInfo(7, "マゼンタ", "MAG", Color(0xFFE91E63))
)

data class OpticalFilterPreset(
    val id: String,
    val label: String,
    val weights: FloatArray
)

val OPTICAL_FILTERS = listOf(
    OpticalFilterPreset("pan", "パンクロ", floatArrayOf(0.18f, 0.24f, 0.22f, 0.16f, 0.08f, 0.06f, 0.03f, 0.03f)),
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
    onParamsChangeFinished: (() -> Unit)? = null,
    hapticManager: HapticManager? = null,
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    var selectedBandIndex by remember { mutableIntStateOf(0) }
    var activeFilterId by remember { mutableStateOf("pan") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
    ) {
        // 1. Mode Switcher: HSL COLOR vs MONOCHROME MIXER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .background(colors.surfaceElevated, LightRumorShapes.Panel)
                .border(1.dp, colors.borderSubtle, LightRumorShapes.Panel)
                .padding(2.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .background(
                        if (!params.isMonochrome) colors.accentAmber.copy(alpha = 0.2f) else Color.Transparent,
                        LightRumorShapes.SharpSquare
                    )
                    .clickable {
                        hapticManager?.performDialTick()
                        onParamsChange(params.copy(isMonochrome = false))
                        onParamsChangeFinished?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "HSL カラー",
                    style = LightRumorTheme.typography.Button,
                    color = if (!params.isMonochrome) colors.accentAmber else colors.textSecondary
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .background(
                        if (params.isMonochrome) colors.accentAmber.copy(alpha = 0.2f) else Color.Transparent,
                        LightRumorShapes.SharpSquare
                    )
                    .clickable {
                        hapticManager?.performDialTick()
                        onParamsChange(params.copy(isMonochrome = true))
                        onParamsChangeFinished?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "モノクロ ミキサー",
                    style = LightRumorTheme.typography.Button,
                    color = if (params.isMonochrome) colors.accentAmber else colors.textSecondary
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
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                COLOR_BANDS.forEach { band ->
                    val isSelected = band.index == selectedBandIndex
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) colors.accentAmber.copy(alpha = 0.15f) else colors.surfaceElevated,
                                LightRumorShapes.Panel
                            )
                            .border(
                                1.dp,
                                if (isSelected) colors.accentAmber else colors.borderSubtle,
                                LightRumorShapes.Panel
                            )
                            .clickable {
                                hapticManager?.performDialTick()
                                selectedBandIndex = band.index
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(band.previewColor, LightRumorShapes.SharpSquare)
                            )
                            Text(
                                text = band.name,
                                style = LightRumorTheme.typography.Tab,
                                color = if (isSelected) colors.accentAmber else colors.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Band Sliders (Hue, Saturation, Luminance)
            val currentBand = params.hslBands.getOrElse(selectedBandIndex) { HSLBandAdjust() }
            val currentBandInfo = COLOR_BANDS[selectedBandIndex]

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${currentBandInfo.name} バンド調整",
                    style = LightRumorTheme.typography.Header,
                    color = colors.accentAmber,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )

                LightroomSlider(
                    label = "${currentBandInfo.name} 色相",
                    value = currentBand.hueShift,
                    onValueChange = { newVal ->
                        val updated = params.hslBands.copyOf()
                        updated[selectedBandIndex] = updated[selectedBandIndex].copy(hueShift = newVal)
                        onParamsChange(params.copy(hslBands = updated))
                    },
                    onValueChangeFinished = onParamsChangeFinished,
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "${currentBandInfo.name} 彩度",
                    value = currentBand.saturation,
                    onValueChange = { newVal ->
                        val updated = params.hslBands.copyOf()
                        updated[selectedBandIndex] = updated[selectedBandIndex].copy(saturation = newVal)
                        onParamsChange(params.copy(hslBands = updated))
                    },
                    onValueChangeFinished = onParamsChangeFinished,
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "${currentBandInfo.name} 輝度",
                    value = currentBand.luminance,
                    onValueChange = { newVal ->
                        val updated = params.hslBands.copyOf()
                        updated[selectedBandIndex] = updated[selectedBandIndex].copy(luminance = newVal)
                        onParamsChange(params.copy(hslBands = updated))
                    },
                    onValueChangeFinished = onParamsChangeFinished,
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                // Primary Calibration Sub-panel
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "カメラプライマリキャリブレーション",
                    style = LightRumorTheme.typography.Header,
                    color = colors.accentAmber,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )

                LightroomSlider(
                    label = "レッド 色相",
                    value = params.primaryRed.hueShift,
                    onValueChange = { onParamsChange(params.copy(primaryRed = params.primaryRed.copy(hueShift = it))) },
                    onValueChangeFinished = onParamsChangeFinished,
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "レッド 彩度",
                    value = params.primaryRed.saturationShift,
                    onValueChange = { onParamsChange(params.copy(primaryRed = params.primaryRed.copy(saturationShift = it))) },
                    onValueChangeFinished = onParamsChangeFinished,
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "グリーン 色相",
                    value = params.primaryGreen.hueShift,
                    onValueChange = { onParamsChange(params.copy(primaryGreen = params.primaryGreen.copy(hueShift = it))) },
                    onValueChangeFinished = onParamsChangeFinished,
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "グリーン 彩度",
                    value = params.primaryGreen.saturationShift,
                    onValueChange = { onParamsChange(params.copy(primaryGreen = params.primaryGreen.copy(saturationShift = it))) },
                    onValueChangeFinished = onParamsChangeFinished,
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "ブルー 色相",
                    value = params.primaryBlue.hueShift,
                    onValueChange = { onParamsChange(params.copy(primaryBlue = params.primaryBlue.copy(hueShift = it))) },
                    onValueChangeFinished = onParamsChangeFinished,
                    range = -100f..100f,
                    defaultValue = 0f,
                    displayDecimals = 0,
                    step = 1f,
                    hapticManager = hapticManager
                )

                LightroomSlider(
                    label = "ブルー 彩度",
                    value = params.primaryBlue.saturationShift,
                    onValueChange = { onParamsChange(params.copy(primaryBlue = params.primaryBlue.copy(saturationShift = it))) },
                    onValueChangeFinished = onParamsChangeFinished,
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
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OPTICAL_FILTERS.forEach { filter ->
                    val isSelected = filter.id == activeFilterId
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) colors.accentAmber.copy(alpha = 0.15f) else colors.surfaceElevated,
                                LightRumorShapes.Panel
                            )
                            .border(
                                1.dp,
                                if (isSelected) colors.accentAmber else colors.borderSubtle,
                                LightRumorShapes.Panel
                            )
                            .clickable {
                                hapticManager?.performDialTick()
                                activeFilterId = filter.id
                                onParamsChange(params.copy(monochromeWeights = filter.weights.clone()))
                                onParamsChangeFinished?.invoke()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            style = LightRumorTheme.typography.Tab,
                            color = if (isSelected) colors.accentAmber else colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 8-Channel Monochrome Luminance Sliders
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                COLOR_BANDS.forEach { band ->
                    val currentWeight = params.monochromeWeights.getOrElse(band.index) { 0.125f }
                    LightroomSlider(
                        label = "${band.name} 輝度",
                        value = currentWeight * 100f,
                        onValueChange = { newVal ->
                            val updatedWeights = if (params.monochromeWeights.size == 8) {
                                params.monochromeWeights.clone()
                            } else {
                                FloatArray(8) { idx -> params.monochromeWeights.getOrElse(idx) { 0.125f } }
                            }
                            updatedWeights[band.index] = newVal * 0.01f
                            activeFilterId = "custom"
                            onParamsChange(params.copy(monochromeWeights = updatedWeights))
                        },
                        onValueChangeFinished = onParamsChangeFinished,
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

