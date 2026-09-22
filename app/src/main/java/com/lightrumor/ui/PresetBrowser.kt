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

val categoryEnToJa = mapOf(
    "ALL" to "すべて",
    "LANDSCAPE" to "風景",
    "PORTRAIT" to "ポートレート",
    "FILM" to "フィルム",
    "MONOCHROME" to "モノクロ",
    "URBAN" to "アーバン"
)

val categoryJaToEn = categoryEnToJa.entries.associate { (k, v) -> v to k }

val categoryTranslations = categoryEnToJa

fun getCategoryDisplayName(category: String): String {
    return categoryEnToJa[category.uppercase()] ?: category
}

fun isSameCategory(catA: String, catB: String): Boolean {
    if (catA.equals(catB, ignoreCase = true)) return true
    val enA = categoryJaToEn[catA] ?: catA.uppercase()
    val enB = categoryJaToEn[catB] ?: catB.uppercase()
    return enA.equals(enB, ignoreCase = true)
}

fun matchesCategory(presetCategory: String, targetCategory: String): Boolean {
    val normTarget = targetCategory.trim()
    val normPreset = presetCategory.trim()

    if (normTarget.equals("すべて", ignoreCase = true) || normTarget.equals("ALL", ignoreCase = true)) {
        return true
    }

    if (normPreset.equals(normTarget, ignoreCase = true)) {
        return true
    }

    val enFromTarget = categoryJaToEn[normTarget]
    if (enFromTarget != null && normPreset.equals(enFromTarget, ignoreCase = true)) {
        return true
    }

    val jaFromTarget = categoryEnToJa[normTarget.uppercase()]
    if (jaFromTarget != null && normPreset.equals(jaFromTarget, ignoreCase = true)) {
        return true
    }

    val jaFromPreset = categoryEnToJa[normPreset.uppercase()]
    if (jaFromPreset != null && jaFromPreset.equals(normTarget, ignoreCase = true)) {
        return true
    }

    val enFromPreset = categoryJaToEn[normPreset]
    if (enFromPreset != null && enFromPreset.equals(normTarget, ignoreCase = true)) {
        return true
    }

    return false
}

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
    var selectedCategory by remember { mutableStateOf("すべて") }
    var selectedPreset by remember { mutableStateOf<XmpPreset?>(null) }
    var presetAmount by remember { mutableStateOf(100.0f) } // 0% to 200%
    val allPresets = remember { XmpPresetParser.getBuiltinPresets() }

    val filteredPresets = remember(selectedCategory) {
        allPresets.filter { matchesCategory(it.category, selectedCategory) }
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
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1.0f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                XmpPresetParser.CATEGORIES.forEach { cat ->
                    val isSel = isSameCategory(cat, selectedCategory)
                    Box(
                        modifier = Modifier
                            .background(if (isSel) colors.surfacePressed else Color.Transparent, RoundedCornerShape(3.dp))
                            .border(1.dp, if (isSel) colors.accentAmber else colors.borderSubtle, RoundedCornerShape(3.dp))
                            .clickable {
                                hapticManager?.performDialTick()
                                selectedCategory = cat
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = getCategoryDisplayName(cat),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            color = if (isSel) colors.accentAmber else colors.textSecondary
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "読込",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentAmber,
                    modifier = Modifier
                        .background(colors.surfacePressed, RoundedCornerShape(3.dp))
                        .border(1.dp, colors.accentAmber.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                        .clickable { onImportXmp() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
                Text(
                    text = "書出",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .background(colors.surfacePressed, RoundedCornerShape(3.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                        .clickable { onExportXmp() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }

        // 2. PRESET AMOUNT SLIDER (0% to 200%)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "適用量",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = "${presetAmount.toInt()}%",
                    style = LightRumorTheme.typography.ValueReadout,
                    fontSize = 13.sp,
                    color = colors.accentAmber,
                    modifier = Modifier.pointerInput(Unit) {
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

            Spacer(modifier = Modifier.height(4.dp))

            // Custom Precision Amount Slider Track
            LightroomSlider(
                label = "",
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
                range = 0.0f..200.0f,
                defaultValue = 100.0f,
                unit = "%",
                displayDecimals = 0,
                step = 1.0f,
                hapticManager = hapticManager
            )
        }

        // 3. PRESET CARDS GRID (with Real-Time Hover Preview)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
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
            .height(84.dp)
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
                        if (event.type == PointerEventType.Enter) {
                            onHover(true)
                        } else if (event.type == PointerEventType.Exit) {
                            onHover(false)
                        }
                    }
                }
            }
            .pointerInput(preset.id) {
                detectTapGestures(
                    onPress = {
                        onHover(true)
                        tryAwaitRelease()
                        onHover(false)
                    },
                    onTap = {
                        onClick()
                    }
                )
            }
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preset.name,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isSelected) colors.accentAmber else colors.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier
                        .background(colors.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = getCategoryDisplayName(preset.category).take(4),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Text(
                text = preset.description,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
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
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = colors.accentAmber
                )
                Text(
                    text = if (isSelected) "適用中" else "選択",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isSelected) colors.accentAmber else colors.textSecondary
                )
            }
        }
    }
}

