package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*

/**
 * DevelopStudioScreen: Phase 3 Master Photographic Development Studio.
 * Integrates:
 * - Upper 65% unobstructed preview area
 * - Real-time Cinema RGB Waveform Monitor / Parade
 * - 4-Way Before/After Comparison (Hold, Split, 2-Up)
 * - Lower 35% Thumb-Zone with Lightroom-style horizontal precision sliders
 * - Virtual Knurled Rotary Dial for ±0.01 micro adjustments
 * - Dual Theme: Obsidian Black (Sony/Sigma) & Technical Arctic (Canon/Minolta)
 * - Precision Mechanical Haptic Feedback
 */
@Composable
fun DevelopStudioScreen(
    photoItem: PhotoItem,
    cacheManager: CullingCacheManager,
    onBack: (DevelopmentParams) -> Unit
) {
    var isDarkTheme by remember { mutableStateOf(true) }
    var devParams by remember(photoItem.uri) { mutableStateOf(photoItem.developParams.deepCopy()) }
    var hoverPreviewParams by remember { mutableStateOf<DevelopmentParams?>(null) }
    var compareMode by remember { mutableStateOf(CompareMode.Off) }
    var waveformSize by remember { mutableStateOf(WaveformSize.NORMAL) }

    // Phase 5 State
    var studioMode by remember { mutableStateOf(StudioMode.Develop) }
    val historyManager = remember(photoItem.uri) { HistoryManager(devParams) }
    var maskLayers by remember { mutableStateOf<List<MaskLayerState>>(emptyList()) }
    var selectedMaskIndex by remember { mutableStateOf(0) }

    // Active Precision Dial state
    var dialState by remember {
        mutableStateOf<DialConfig?>(null)
    }

    val context = LocalContext.current
    val hapticManager = remember { HapticManager(context) }

    // Active displayed parameters (with hover preview support)
    val activeParams = hoverPreviewParams ?: devParams

    // Original and simulated developed bitmap
    var originalBitmap by remember(photoItem.uri) {
        mutableStateOf(cacheManager.getFromMemory(photoItem.filePath.ifEmpty { photoItem.uri.toString() }))
    }

    LaunchedEffect(photoItem) {
        cacheManager.loadBitmap(photoItem) { bmp ->
            originalBitmap = bmp
        }
    }

    LightRumorTheme(isDark = isDarkTheme) {
        val colors = LightRumorTheme.colors

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // -------------------------------------------------------------
                // 1. TOP STATUS & EXIF BAR (5% height)
                // -------------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(colors.surface)
                        .border(1.dp, colors.borderStrong)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back & Photo Metadata
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "< 選別",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.accentAmber,
                            modifier = Modifier
                                .clickable { onBack(devParams) }
                                .padding(end = 12.dp, top = 4.dp, bottom = 4.dp)
                        )

                        Text(
                            text = photoItem.fileName,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                    }

                    // Camera EXIF Readout & Theme Switcher
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Camera EXIF Readout
                        val meta = photoItem.metadata
                        val exifParts = listOfNotNull(
                            meta.cameraModel.takeIf { it.isNotBlank() },
                            meta.isoSpeed.takeIf { it.isNotBlank() },
                            meta.exposureTime.takeIf { it.isNotBlank() },
                            meta.fNumber.takeIf { it.isNotBlank() },
                            meta.focalLength.takeIf { it.isNotBlank() }
                        )
                        if (exifParts.isNotEmpty()) {
                            Text(
                                text = exifParts.joinToString(" "),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }

                        // Studio Mode Selector Chips
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            StudioMode.values().forEach { mode ->
                                val isSel = (studioMode == mode)
                                val modeLabel = when (mode) {
                                    StudioMode.Develop -> "現像"
                                    StudioMode.Masks -> "マスク(${maskLayers.size})"
                                    StudioMode.Presets -> "プリセット"
                                    StudioMode.Reel -> "リール"
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (isSel) colors.accentAmber.copy(alpha = 0.2f) else colors.surfaceElevated, RoundedCornerShape(3.dp))
                                        .border(1.dp, if (isSel) colors.accentAmber else colors.borderSubtle, RoundedCornerShape(3.dp))
                                        .clickable {
                                            hapticManager.performDialTick()
                                            studioMode = mode
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = modeLabel,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isSel) colors.accentAmber else colors.textSecondary
                                    )
                                }
                            }
                        }

                        // Theme Toggle: Obsidian / Arctic
                        Box(
                            modifier = Modifier
                                .background(colors.surfaceElevated, RoundedCornerShape(3.dp))
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                                .clickable {
                                    hapticManager.performDialTick()
                                    isDarkTheme = !isDarkTheme
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isDarkTheme) "OBSIDIAN" else "ARCTIC",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = colors.accentAmber
                            )
                        }
                    }
                }

                // -------------------------------------------------------------
                // 2. UPPER 60% PREVIEW & INSTRUMENT AREA (60% height)
                // -------------------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.60f)
                        .background(colors.background)
                ) {
                    // 4-Way Before/After Photographic Display
                    BeforeAfterOverlay(
                        originalBitmap = originalBitmap,
                        developedBitmap = originalBitmap, // Reactively modified in real time via ColorFilter
                        compareMode = compareMode,
                        onCompareModeChange = { compareMode = it },
                        geometry = devParams.geometry,
                        params = activeParams,
                        maskLayers = maskLayers,
                        selectedMaskIndex = selectedMaskIndex,
                        isMaskMode = (studioMode == StudioMode.Masks),
                        onMaskLayersChange = { maskLayers = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Real-Time RGB Waveform Monitor HUD (Top-End when SideBySide to prevent covering left photo)
                    val effectiveWaveformAlignment = if (compareMode == CompareMode.SideBySide) Alignment.TopEnd else Alignment.TopStart
                    ColorWaveformView(
                        previewBitmap = originalBitmap,
                        params = activeParams,
                        waveformSize = if (compareMode == CompareMode.SideBySide && waveformSize == WaveformSize.EXPANDED) WaveformSize.NORMAL else waveformSize,
                        onSizeChange = { waveformSize = it },
                        modifier = Modifier
                            .align(effectiveWaveformAlignment)
                            .padding(10.dp)
                            .then(
                                when (waveformSize) {
                                    WaveformSize.EXPANDED -> Modifier.width(340.dp)
                                    WaveformSize.NORMAL -> Modifier.width(180.dp)
                                    WaveformSize.MINIMIZED -> Modifier
                                }
                            )
                    )
                }

                // -------------------------------------------------------------
                // 3. LOWER 35% HUMAN-ENGINEERED THUMB ZONE (35% height)
                // -------------------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.35f)
                ) {
                    when (studioMode) {
                        StudioMode.Develop -> {
                            ThumbZoneBottomBar(
                                params = devParams,
                                onParamsChange = { devParams = it },
                                onParamsChangeFinished = {
                                    historyManager.recordState("現像調整", devParams, maskLayers)
                                },
                                previewBitmap = originalBitmap,
                                hapticManager = hapticManager,
                                onOpenPrecisionDial = { label, valInit, range, unit, updateCb ->
                                    dialState = DialConfig(label, valInit, range, unit, updateCb)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        StudioMode.Masks -> {
                            MaskLayerManager(
                                maskLayers = maskLayers,
                                onLayersChange = {
                                    maskLayers = it
                                    historyManager.recordState("マスク編集", devParams, it)
                                },
                                selectedLayerIndex = selectedMaskIndex,
                                onSelectLayer = { selectedMaskIndex = it },
                                hapticManager = hapticManager,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        StudioMode.Presets -> {
                            PresetBrowser(
                                currentParams = devParams,
                                onApplyPreset = { preset, amount ->
                                    val blended = XmpPresetParser.applyPresetWithAmount(devParams, preset.params, amount)
                                    devParams = blended
                                    historyManager.recordState("${preset.name} (${amount.toInt()}%)", blended, maskLayers)
                                    hoverPreviewParams = null
                                },
                                onHoverPreview = { preset, amount ->
                                    hoverPreviewParams = preset?.let { XmpPresetParser.applyPresetWithAmount(devParams, it.params, amount) }
                                },
                                hapticManager = hapticManager,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        StudioMode.Reel -> {
                            AnalogReelHistoryBar(
                                historyManager = historyManager,
                                onStateRestored = { node ->
                                    devParams = node.params.deepCopy()
                                    maskLayers = node.masks.map { it.deepCopy() }
                                },
                                hapticManager = hapticManager,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // 4. FLOATING PRECISION ROTARY DIAL OVERLAY (When triggered)
            // -----------------------------------------------------------------
            dialState?.let { cfg ->
                PrecisionDial(
                    label = cfg.label,
                    value = cfg.value,
                    onValueChange = { newVal ->
                        cfg.value = newVal
                        cfg.onUpdate(newVal)
                    },
                    range = cfg.range,
                    unit = cfg.unit,
                    hapticManager = hapticManager,
                    onDismiss = { dialState = null }
                )
            }
        }
    }
}

enum class StudioMode {
    Develop,
    Masks,
    Presets,
    Reel
}

private class DialConfig(
    val label: String,
    var value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val unit: String,
    val onUpdate: (Float) -> Unit
)

