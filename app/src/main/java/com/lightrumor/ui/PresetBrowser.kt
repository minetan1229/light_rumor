package com.lightrumor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*

val categoryTranslations = mapOf(
    "ALL" to "すべて",
    "LANDSCAPE" to "風景",
    "PORTRAIT" to "ポートレート",
    "FILM" to "フィルム",
    "MONOCHROME" to "モノクロ",
    "URBAN" to "アーバン"
)

/**
 * PresetBrowser: Lightroom XMP Preset Selector & Real-Time Hover Engine.
 * Features:
 * - Category Tabs: ALL, LANDSCAPE, PORTRAIT, FILM, MONOCHROME, URBAN
 * - Real-Time Hover Preview: instantaneous preview on touch-down/hover before committing
 * - Preset Amount Slider (0% to 200%) with 100% center detent and double-tap reset
 * - XMP Import & Export actions
 * - Zero AI dependencies, zero emojis, industrial camera chassis styling.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PresetBrowser(
    currentParams: DevelopmentParams,
    onApplyPreset: (XmpPreset, Float) -> Unit,
    onHoverPreview: (XmpPreset?, Float) -> Unit,
    hapticManager: HapticManager?,
    onImportXmp: () -> Unit = {},
    onExportXmp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    var selectedCategory by remember { mutableStateOf("ALL") }
    var selectedPreset by remember { mutableStateOf<XmpPreset?>(null) }
    var presetAmount by remember { mutableStateOf(100.0f) } // 0% to 200%
    val allPresets = remember { XmpPresetParser.getBuiltinPresets() }

    val filteredPresets = remember(selectedCategory) {
        if (selectedCategory == "ALL") allPresets
        else allPresets.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .border(1.dp, colors.borderStrong)
    ) {
        // 1. TOP TOOLBAR: Category Chips & Import/Export
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1.0f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                XmpPresetParser.CATEGORIES.forEach { cat ->
                    val isSel = (cat == selectedCategory)
                    Box(
                        modifier = Modifier
                            .background(if (isSel) colors.surfacePressed else Color.Transparent, RoundedCornerShape(2.dp))
                            .border(1.dp, if (isSel) colors.accentAmber else colors.borderSubtle, RoundedCornerShape(2.dp))
                            .clickable {
                                hapticManager?.performDialTick()
                                selectedCategory = cat
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = categoryTranslations[cat] ?: cat,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = if (isSel) colors.accentAmber else colors.textSecondary
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "[読込]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentAmber,
                    modifier = Modifier
                        .background(colors.surfacePressed, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable { onImportXmp() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
                Text(
                    text = "[書出]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .background(colors.surfacePressed, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable { onExportXmp() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        // 2. PRESET AMOUNT SLIDER (0% to 200%)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "プリセット適用量:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = "${presetAmount.toInt()}%",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = colors.accentAmber,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    hapticManager?.performDialTick()
                                    presetAmount = 100.0f
                                    selectedPreset?.let { onApplyPreset(it, 100.0f) }
                                }
                            )
                        }
                )
            }

            Slider(
                value = presetAmount,
                onValueChange = { newVal ->
                    presetAmount = newVal
                    selectedPreset?.let { p ->
                        onHoverPreview(p, newVal)
                    }
                },
                onValueChangeFinished = {
                    hapticManager?.performDialTick()
                    selectedPreset?.let { p ->
                        onApplyPreset(p, presetAmount)
                    }
                },
                valueRange = 0.0f..200.0f,
                modifier = Modifier.weight(1.0f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = colors.accentAmber,
                    activeTrackColor = colors.accentAmber,
                    inactiveTrackColor = colors.borderSubtle
                )
            )

            Text(
                text = "[100%にリセット]",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = colors.textSecondary,
                modifier = Modifier
                    .background(colors.surfacePressed, RoundedCornerShape(2.dp))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                    .clickable {
                        hapticManager?.performDialTick()
                        presetAmount = 100.0f
                        selectedPreset?.let { onApplyPreset(it, 100.0f) }
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // 3. PRESET CARDS GRID (with Real-Time Hover Preview)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            modifier = Modifier.weight(1.0f).fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredPresets) { preset ->
                val isSelected = (selectedPreset?.id == preset.id)
                PresetCard(
                    preset = preset,
                    isSelected = isSelected,
                    onHover = { isHovering ->
                        if (isHovering) {
                            onHoverPreview(preset, presetAmount)
                        } else {
                            onHoverPreview(null, presetAmount)
                        }
                    },
                    onClick = {
                        hapticManager?.performDialTick()
                        selectedPreset = preset
                        onApplyPreset(preset, presetAmount)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PresetCard(
    preset: XmpPreset,
    isSelected: Boolean,
    onHover: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val colors = LightRumorTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(
                if (isSelected) colors.surfacePressed else colors.surfaceElevated,
                RoundedCornerShape(3.dp)
            )
            .border(
                1.dp,
                if (isSelected) colors.accentAmber else colors.borderSubtle,
                RoundedCornerShape(3.dp)
            )
            .pointerInput(preset.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> onHover(true)
                            PointerEventType.Exit -> onHover(false)
                            PointerEventType.Press -> onHover(true)
                            PointerEventType.Release -> onHover(false)
                        }
                    }
                }
            }
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preset.name,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = if (isSelected) colors.accentAmber else colors.textPrimary,
                    maxLines = 1
                )

                Box(
                    modifier = Modifier
                        .background(colors.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = (categoryTranslations[preset.category] ?: preset.category).take(4),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Text(
                text = preset.description,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = colors.textSecondary,
                maxLines = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (preset.params.isMonochrome) "モノクロ" else "カラー RAW",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.sp,
                    color = colors.accentAmber
                )
                Text(
                    text = if (isSelected) "[適用中]" else "[タップ]",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    color = if (isSelected) colors.accentAmber else colors.textSecondary
                )
            }
        }
    }
}

