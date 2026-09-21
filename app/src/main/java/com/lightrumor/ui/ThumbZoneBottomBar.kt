package com.lightrumor.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

enum class ThumbTab(val title: String) {
    Light("ライト"),
    Color("カラー"),
    Mixer("ミキサー"),
    Detail("ディテール"),
    Optics("レンズ"),
    Geometry("変形")
}

/**
 * ThumbZoneBottomBar: Human-Engineered Bottom 35% Control Center.
 * - 縦スクロールと干渉しない水平ドラッグ対応
 * - カラー／モノクロの完全統合切替
 * - 現在の写真プレビューに即応したリアル写真カラープロファイルサムネイル
 */
@Composable
fun ThumbZoneBottomBar(
    params: DevelopmentParams,
    onParamsChange: (DevelopmentParams) -> Unit,
    onParamsChangeFinished: (() -> Unit)? = null,
    previewBitmap: Bitmap? = null,
    hapticManager: HapticManager?,
    onOpenPrecisionDial: (label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onUpdate: (Float) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    var activeTab by remember { mutableStateOf(ThumbTab.Light) }

    // 現在の写真の縮小サムネイルを元にしたカラープロファイルアイテムの生成
    val profileItems = remember(previewBitmap) {
        createLiveProfileItems(previewBitmap)
    }

    val filterItems = remember(previewBitmap) {
        createLiveFilterItems(previewBitmap)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(colors.surface)
            .border(width = 1.dp, color = colors.borderStrong)
    ) {
        // 1. Primary Tool Tabs (Scrollable horizontal thumb strip)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(vertical = 6.dp, horizontal = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThumbTab.values().forEach { tab ->
                val isSelected = tab == activeTab
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .background(
                            if (isSelected) colors.surfaceElevated else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) colors.accentAmber else colors.borderSubtle,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable {
                            if (activeTab != tab) {
                                hapticManager?.performDialTick()
                                activeTab = tab
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.title,
                        style = LightRumorTheme.typography.Tab,
                        color = if (isSelected) colors.accentAmber else colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderSubtle)
        )

        // 2. Active Tab Content (Lightroom Precision Sliders & Real Photo Thumbnails)
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(LightRumorMotionSpecs.DURATION_SNAP_MS, easing = LightRumorMotionSpecs.PanelSlideEasing)) togetherWith
                fadeOut(animationSpec = tween(LightRumorMotionSpecs.DURATION_FADE_MS))
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "ThumbTabContent"
        ) { tab ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 16.dp)
            ) {
                when (tab) {
                    ThumbTab.Light -> {
                        LightroomSlider(
                            label = "露出",
                            value = params.exposureEV,
                            onValueChange = { onParamsChange(params.copy(exposureEV = it)) },
                            onValueChangeFinished = onParamsChangeFinished,
                            range = -5.0f..5.0f,
                            defaultValue = 0.0f,
                            unit = "EV",
                            step = 0.05f,
                            hapticManager = hapticManager,
                            onLongPressDial = {
                                onOpenPrecisionDial("露出", params.exposureEV, -5.0f..5.0f, "EV") {
                                    onParamsChange(params.copy(exposureEV = it))
                                    onParamsChangeFinished?.invoke()
                                }
                            }
                        )

                        LightroomSlider(
                            label = "コントラスト",
                            value = params.contrast,
                            onValueChange = { onParamsChange(params.copy(contrast = it)) },
                            onValueChangeFinished = onParamsChangeFinished,
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager,
                            onLongPressDial = {
                                onOpenPrecisionDial("コントラスト", params.contrast, -100f..100f, "%") {
                                    onParamsChange(params.copy(contrast = it))
                                    onParamsChangeFinished?.invoke()
                                }
                            }
                        )

                        LightroomSlider(
                            label = "ハイライト",
                            value = params.highlights,
                            onValueChange = { onParamsChange(params.copy(highlights = it)) },
                            onValueChangeFinished = onParamsChangeFinished,
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )

                        LightroomSlider(
                            label = "シャドウ",
                            value = params.shadows,
                            onValueChange = { onParamsChange(params.copy(shadows = it)) },
                            onValueChangeFinished = onParamsChangeFinished,
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )

                        LightroomSlider(
                            label = "白レベル",
                            value = params.whites,
                            onValueChange = { onParamsChange(params.copy(whites = it)) },
                            onValueChangeFinished = onParamsChangeFinished,
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )

                        LightroomSlider(
                            label = "黒レベル",
                            value = params.blacks,
                            onValueChange = { onParamsChange(params.copy(blacks = it)) },
                            onValueChangeFinished = onParamsChangeFinished,
                            range = -100f..100f,
                            defaultValue = 0f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )
                    }

                    ThumbTab.Color -> {
                        // モノクロ / カラー 切替トグル
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .background(colors.surfaceElevated, RoundedCornerShape(3.dp))
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val isMono = params.isMonochrome || params.colorProfile == "monochrome"

                            // カラー
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .background(if (!isMono) colors.accentAmber else Color.Transparent, RoundedCornerShape(2.dp))
                                    .clickable {
                                        hapticManager?.performDialTick()
                                        val newProfile = if (params.colorProfile == "monochrome") "cinetone" else params.colorProfile
                                        onParamsChange(params.copy(isMonochrome = false, colorProfile = newProfile))
                                        onParamsChangeFinished?.invoke()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "カラー現像",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (!isMono) Color.Black else colors.textSecondary
                                )
                            }

                            // モノクロ
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .background(if (isMono) colors.accentAmber else Color.Transparent, RoundedCornerShape(2.dp))
                                    .clickable {
                                        hapticManager?.performDialTick()
                                        onParamsChange(params.copy(isMonochrome = true, colorProfile = "monochrome"))
                                        onParamsChangeFinished?.invoke()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "モノクロ現像",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isMono) Color.Black else colors.textSecondary
                                )
                            }
                        }

                        val isMono = params.isMonochrome || params.colorProfile == "monochrome"

                        if (!isMono) {
                            // --- カラー現像モード ---
                            Text(
                                text = "カラープロファイル (タップして適用)",
                                style = LightRumorTheme.typography.Label,
                                color = colors.accentAmber,
                                modifier = Modifier.padding(start = 12.dp, top = 6.dp)
                            )
                            PhotoThumbnailSelector(
                                items = profileItems,
                                selectedId = params.colorProfile,
                                onItemSelected = {
                                    hapticManager?.performDialTick()
                                    onParamsChange(params.copy(
                                        colorProfile = it.id,
                                        isMonochrome = (it.id == "monochrome")
                                    ))
                                    onParamsChangeFinished?.invoke()
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            LightroomSlider(
                                label = "色温度",
                                value = params.kelvin,
                                onValueChange = { onParamsChange(params.copy(kelvin = it)) },
                                onValueChangeFinished = onParamsChangeFinished,
                                range = 2000f..12000f,
                                defaultValue = 5500f,
                                unit = "K",
                                displayDecimals = 0,
                                step = 50f,
                                hapticManager = hapticManager,
                                onLongPressDial = {
                                    onOpenPrecisionDial("色温度", params.kelvin, 2000f..12000f, "K") {
                                        onParamsChange(params.copy(kelvin = it))
                                        onParamsChangeFinished?.invoke()
                                    }
                                }
                            )

                            LightroomSlider(
                                label = "色かぶり",
                                value = params.tint,
                                onValueChange = { onParamsChange(params.copy(tint = it)) },
                                onValueChangeFinished = onParamsChangeFinished,
                                range = -100f..100f,
                                defaultValue = 0f,
                                unit = "",
                                displayDecimals = 1,
                                step = 0.5f,
                                hapticManager = hapticManager
                            )

                            LightroomSlider(
                                label = "明瞭度",
                                value = params.clarity,
                                onValueChange = { onParamsChange(params.copy(clarity = it)) },
                                onValueChangeFinished = onParamsChangeFinished,
                                range = -100f..100f,
                                defaultValue = 0f,
                                unit = "%",
                                displayDecimals = 0,
                                step = 1f,
                                hapticManager = hapticManager,
                                onLongPressDial = {
                                    onOpenPrecisionDial("明瞭度", params.clarity, -100f..100f, "%") {
                                        onParamsChange(params.copy(clarity = it))
                                        onParamsChangeFinished?.invoke()
                                    }
                                }
                            )

                            LightroomSlider(
                                label = "自然な彩度",
                                value = params.vibrance,
                                onValueChange = { onParamsChange(params.copy(vibrance = it)) },
                                onValueChangeFinished = onParamsChangeFinished,
                                range = -100f..100f,
                                defaultValue = 0f,
                                unit = "%",
                                displayDecimals = 0,
                                step = 1f,
                                hapticManager = hapticManager
                            )

                            LightroomSlider(
                                label = "彩度",
                                value = params.saturation,
                                onValueChange = { onParamsChange(params.copy(saturation = it)) },
                                onValueChangeFinished = onParamsChangeFinished,
                                range = -100f..100f,
                                defaultValue = 0f,
                                unit = "%",
                                displayDecimals = 0,
                                step = 1f,
                                hapticManager = hapticManager
                            )
                        } else {
                            // --- モノクロ現像モード ---
                            Text(
                                text = "白黒光学フィルター (コントラスト調子)",
                                style = LightRumorTheme.typography.Label,
                                color = colors.accentAmber,
                                modifier = Modifier.padding(start = 12.dp, top = 6.dp)
                            )
                            PhotoThumbnailSelector(
                                items = filterItems,
                                selectedId = params.colorProfile.takeIf { it != "monochrome" } ?: "std",
                                onItemSelected = {
                                    hapticManager?.performDialTick()
                                    val weights = when (it.id) {
                                        "red25a" -> floatArrayOf(0.50f, 0.25f, 0.10f, 0.05f, 0.03f, 0.03f, 0.02f, 0.02f)
                                        "orange_o2" -> floatArrayOf(0.35f, 0.35f, 0.15f, 0.05f, 0.03f, 0.03f, 0.02f, 0.02f)
                                        "yellow_y2" -> floatArrayOf(0.20f, 0.35f, 0.25f, 0.10f, 0.04f, 0.03f, 0.02f, 0.01f)
                                        "green_x0" -> floatArrayOf(0.08f, 0.12f, 0.25f, 0.35f, 0.10f, 0.05f, 0.03f, 0.02f)
                                        "ir720" -> floatArrayOf(0.60f, 0.25f, 0.05f, 0.02f, 0.02f, 0.02f, 0.02f, 0.02f)
                                        else -> floatArrayOf(0.18f, 0.24f, 0.22f, 0.16f, 0.08f, 0.06f, 0.03f, 0.03f)
                                    }
                                    onParamsChange(params.copy(
                                        isMonochrome = true,
                                        monochromeWeights = weights
                                    ))
                                    onParamsChangeFinished?.invoke()
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            LightroomSlider(
                                label = "明瞭度 (テクスチャ)",
                                value = params.clarity,
                                onValueChange = { onParamsChange(params.copy(clarity = it)) },
                                onValueChangeFinished = onParamsChangeFinished,
                                range = -100f..100f,
                                defaultValue = 0f,
                                unit = "%",
                                displayDecimals = 0,
                                step = 1f,
                                hapticManager = hapticManager
                            )

                            LightroomSlider(
                                label = "コントラスト",
                                value = params.contrast,
                                onValueChange = { onParamsChange(params.copy(contrast = it)) },
                                onValueChangeFinished = onParamsChangeFinished,
                                range = -100f..100f,
                                defaultValue = 0f,
                                unit = "%",
                                displayDecimals = 0,
                                step = 1f,
                                hapticManager = hapticManager
                            )

                            LightroomSlider(
                                label = "シャドウの階調",
                                value = params.shadows,
                                onValueChange = { onParamsChange(params.copy(shadows = it)) },
                                onValueChangeFinished = onParamsChangeFinished,
                                range = -100f..100f,
                                defaultValue = 0f,
                                unit = "%",
                                displayDecimals = 0,
                                step = 1f,
                                hapticManager = hapticManager
                            )

                            LightroomSlider(
                                label = "ハイライトの階調",
                                value = params.highlights,
                                onValueChange = { onParamsChange(params.copy(highlights = it)) },
                                onValueChangeFinished = onParamsChangeFinished,
                                range = -100f..100f,
                                defaultValue = 0f,
                                unit = "%",
                                displayDecimals = 0,
                                step = 1f,
                                hapticManager = hapticManager
                            )
                        }
                    }

                    ThumbTab.Mixer -> {
                        ColorMixerScreen(
                            params = params,
                            onParamsChange = onParamsChange,
                            hapticManager = hapticManager
                        )
                    }

                    ThumbTab.Detail -> {
                        DetailDenoiseScreen(
                            params = params,
                            onParamsChange = onParamsChange,
                            hapticManager = hapticManager
                        )
                    }

                    ThumbTab.Optics -> {
                        Text(
                            text = "レンズ歪曲・周辺減光補正",
                            style = LightRumorTheme.typography.Label,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                        LightroomSlider(
                            label = "歪曲収差補正",
                            value = params.lensCorrection.distortionCorrection,
                            onValueChange = { 
                                val updated = params.copy(lensCorrection = params.lensCorrection.copy(distortionCorrection = it))
                                onParamsChange(updated)
                            },
                            onValueChangeFinished = onParamsChangeFinished,
                            range = 0f..200f,
                            defaultValue = 100f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )
                        LightroomSlider(
                            label = "周辺光量補正",
                            value = params.lensCorrection.vignettingCorrection,
                            onValueChange = {
                                val updated = params.copy(lensCorrection = params.lensCorrection.copy(vignettingCorrection = it))
                                onParamsChange(updated)
                            },
                            onValueChangeFinished = onParamsChangeFinished,
                            range = 0f..200f,
                            defaultValue = 100f,
                            unit = "%",
                            displayDecimals = 0,
                            step = 1f,
                            hapticManager = hapticManager
                        )
                    }

                    ThumbTab.Geometry -> {
                        CropRotateScreen(
                            params = params,
                            onParamsChange = onParamsChange,
                            hapticManager = hapticManager
                        )
                    }
                }
            }
        }
    }
}

/**
 * 現在のビットマップの縮小版に各カラープロファイルを適用したリアル写真サムネイルリスト
 */
private fun createLiveProfileItems(sourceBitmap: Bitmap?): List<PhotoThumbnailItem> {
    val mini = sourceBitmap?.let { bmp ->
        try {
            val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
            val targetH = 64
            val targetW = (targetH * aspect).toInt().coerceIn(48, 120)
            Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
        } catch (_: Throwable) { null }
    }

    return listOf(
        PhotoThumbnailItem("cinetone", "S-CINETONE", "フィルムソフト", mini?.let { applyProfileToBitmap(it, "cinetone", false) } ?: PhotoThumbnailPresets.generateProfileSample(1.0f, 0.95f, 0.9f)),
        PhotoThumbnailItem("std_color", "スタンダード", "ニュートラル", mini?.let { applyProfileToBitmap(it, "std_color", false) } ?: PhotoThumbnailPresets.generateProfileSample(1.0f, 1.0f, 1.0f)),
        PhotoThumbnailItem("landscape", "風景", "鮮明な青/緑", mini?.let { applyProfileToBitmap(it, "landscape", false) } ?: PhotoThumbnailPresets.generateProfileSample(0.9f, 1.1f, 1.2f)),
        PhotoThumbnailItem("portrait", "ポートレート", "なめらか肌", mini?.let { applyProfileToBitmap(it, "portrait", false) } ?: PhotoThumbnailPresets.generateProfileSample(1.15f, 1.0f, 0.95f)),
        PhotoThumbnailItem("monochrome", "モノクロ HC", "深い階調", mini?.let { applyProfileToBitmap(it, "monochrome", true) } ?: PhotoThumbnailPresets.generateProfileSample(0.7f, 0.7f, 0.7f))
    )
}

/**
 * 現在のビットマップの縮小版に各白黒光学フィルターを適用したリアル写真サムネイルリスト
 */
private fun createLiveFilterItems(sourceBitmap: Bitmap?): List<PhotoThumbnailItem> {
    val mini = sourceBitmap?.let { bmp ->
        try {
            val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
            val targetH = 64
            val targetW = (targetH * aspect).toInt().coerceIn(48, 120)
            Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
        } catch (_: Throwable) { null }
    }

    return listOf(
        PhotoThumbnailItem("std", "通常パンクロ", "標準モノクロ", mini?.let { applyFilterToBitmap(it, listOf(0.299f, 0.587f, 0.114f)) } ?: PhotoThumbnailPresets.createOpticalFilterItems()[0].sampleBitmap),
        PhotoThumbnailItem("red25a", "赤 25A", "高コントラスト空", mini?.let { applyFilterToBitmap(it, listOf(0.70f, 0.20f, 0.10f)) } ?: PhotoThumbnailPresets.createOpticalFilterItems()[1].sampleBitmap),
        PhotoThumbnailItem("orange_o2", "オレンジ", "ドラマチック雲", mini?.let { applyFilterToBitmap(it, listOf(0.55f, 0.35f, 0.10f)) } ?: PhotoThumbnailPresets.createOpticalFilterItems()[2].sampleBitmap),
        PhotoThumbnailItem("yellow_y2", "イエロー", "自然な風景", mini?.let { applyFilterToBitmap(it, listOf(0.40f, 0.50f, 0.10f)) } ?: PhotoThumbnailPresets.createOpticalFilterItems()[3].sampleBitmap),
        PhotoThumbnailItem("green_x0", "グリーン", "豊かな木々/肌", mini?.let { applyFilterToBitmap(it, listOf(0.15f, 0.70f, 0.15f)) } ?: PhotoThumbnailPresets.createOpticalFilterItems()[4].sampleBitmap),
        PhotoThumbnailItem("ir720", "IR 720nm", "白銀ウッド効果", mini?.let { applyFilterToBitmap(it, listOf(0.85f, 0.10f, 0.05f)) } ?: PhotoThumbnailPresets.createOpticalFilterItems()[5].sampleBitmap)
    )
}

private fun applyProfileToBitmap(src: Bitmap, profileId: String, isMono: Boolean): Bitmap {
    return try {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = Paint()
        val dummyParams = DevelopmentParams(colorProfile = profileId, isMonochrome = isMono)
        val cm = buildPhotoDevelopColorMatrix(dummyParams)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm.values)
        canvas.drawBitmap(src, 0f, 0f, paint)
        out
    } catch (_: Throwable) {
        src
    }
}

private fun applyFilterToBitmap(src: Bitmap, weights: List<Float>): Bitmap {
    return try {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = Paint()
        val dummyParams = DevelopmentParams(isMonochrome = true, monochromeWeights = weights.toFloatArray())
        val cm = buildPhotoDevelopColorMatrix(dummyParams)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm.values)
        canvas.drawBitmap(src, 0f, 0f, paint)
        out
    } catch (_: Throwable) {
        src
    }
}
