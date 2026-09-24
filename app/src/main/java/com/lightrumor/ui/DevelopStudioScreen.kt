package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
    onBack: (DevelopmentParams, List<MaskLayerState>) -> Unit
) {
    var isDarkTheme by remember { mutableStateOf(true) }
    var devParams by remember(photoItem.uri) { mutableStateOf(photoItem.developParams.deepCopy()) }
    var maskLayers by remember(photoItem.uri) { mutableStateOf(photoItem.maskLayers.map { it.deepCopy() }) }

    BackHandler {
        photoItem.maskLayers = maskLayers
        onBack(devParams, maskLayers)
    }
    var hoverPreviewParams by remember { mutableStateOf<DevelopmentParams?>(null) }
    var compareMode by remember { mutableStateOf(CompareMode.Off) }
    var waveformSize by remember { mutableStateOf(WaveformSize.NORMAL) }

    // Phase 5 State
    var studioMode by remember { mutableStateOf(StudioMode.Develop) }
    val historyManager = remember(photoItem.uri) { HistoryManager(devParams, maskLayers) }
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
    val activeUri by rememberUpdatedState(photoItem.uri)

    LaunchedEffect(photoItem.uri) {
        val targetUri = photoItem.uri
        cacheManager.loadBitmap(photoItem) { bmp ->
            if (activeUri == targetUri) {
                originalBitmap = bmp
            }
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
                // 1. TOP STATUS & EXIF BAR (Header HUD)
                // -------------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(colors.surface)
                        .border(1.dp, colors.borderStrong)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button & Photo Metadata HUD
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Minimalist Vector Chevron Back Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    photoItem.maskLayers = maskLayers
                                    onBack(devParams, maskLayers)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp, 14.dp)) {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(size.width, 0f)
                                    lineTo(0f, size.height / 2f)
                                    lineTo(size.width, size.height)
                                }
                                drawPath(
                                    path = path,
                                    color = colors.accentAmber,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 2.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Square,
                                        join = androidx.compose.ui.graphics.StrokeJoin.Miter
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "選別",
                                style = LightRumorTheme.typography.Button,
                                color = colors.accentAmber
                            )
                        }

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .height(20.dp)
                                .width(1.dp)
                                .background(colors.borderSubtle)
                        )

                        // 2-Line Filename & EXIF Readout
                        val meta = photoItem.metadata
                        val exifParts = listOfNotNull(
                            meta.fNumber.takeIf { it.isNotBlank() },
                            meta.exposureTime.takeIf { it.isNotBlank() },
                            meta.isoSpeed.takeIf { it.isNotBlank() },
                            meta.focalLength.takeIf { it.isNotBlank() }
                        )

                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = photoItem.fileName,
                                style = LightRumorTheme.typography.Header,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (exifParts.isNotEmpty()) {
                                Text(
                                    text = exifParts.joinToString("  "),
                                    style = LightRumorTheme.typography.MicroIndex,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    // Studio Mode Selector Chips & Theme Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Studio Mode Selector Chips
                        StudioMode.entries.forEach { mode ->
                            val isSel = (studioMode == mode)
                            val modeLabel = when (mode) {
                                StudioMode.Develop -> "現像"
                                StudioMode.Masks -> if (maskLayers.isNotEmpty()) "マスク(${maskLayers.size})" else "マスク"
                                StudioMode.Presets -> "プリセット"
                                StudioMode.Reel -> "履歴"
                            }
                            Box(
                                modifier = Modifier
                                    .background(if (isSel) colors.accentAmber.copy(alpha = 0.15f) else colors.surfaceElevated, LightRumorShapes.Panel)
                                    .border(1.dp, if (isSel) colors.accentAmber else colors.borderSubtle, LightRumorShapes.Panel)
                                    .clickable {
                                        hapticManager.performDialTick()
                                        studioMode = mode
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = modeLabel,
                                    style = LightRumorTheme.typography.Button,
                                    color = if (isSel) colors.accentAmber else colors.textSecondary
                                )
                            }
                        }

                        // Compact Theme Toggle Icon
                        Box(
                            modifier = Modifier
                                .background(colors.surfaceElevated, LightRumorShapes.Panel)
                                .border(1.dp, colors.borderSubtle, LightRumorShapes.Panel)
                                .clickable {
                                    hapticManager.performDialTick()
                                    isDarkTheme = !isDarkTheme
                                }
                                .padding(horizontal = 7.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isDarkTheme) "DARK" else "LIGHT",
                                style = LightRumorTheme.typography.Badge,
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
                        dialState = cfg.copy(value = newVal)
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

private data class DialConfig(
    val label: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val unit: String,
    val onUpdate: (Float) -> Unit
)

