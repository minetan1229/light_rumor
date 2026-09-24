package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class CompareMode {
    Off,
    SplitVertical,   // Left = Before, Right = After
    SplitHorizontal, // Top = Before, Bottom = After
    SideBySide       // 2-Up Side by Side
}

enum class MaskOverlayColor(val label: String, val color: Color) {
    RUBY_RED("赤", Color(0xE6E53935)),
    EMERALD_GREEN("緑", Color(0xE643A047)),
    OCEAN_CYAN("シアン", Color(0xE600ACC1)),
    POLAR_WHITE("白", Color(0xE6ECEFF1))
}

enum class MaskDisplayMode(val label: String) {
    APPLIED("適用部"),       // マスク内をルビ色表示
    EXCLUDED("欠けた部"),     // マスク外（非対象）をルビ色表示
    DUAL_COLOR("両方色分け")  // 適用＝赤、欠けた部＝シアンで2色同時表示
}

fun calculateFittedRect(containerW: Float, containerH: Float, imgW: Float, imgH: Float): Rect {
    if (containerW <= 0f || containerH <= 0f || imgW <= 0f || imgH <= 0f) {
        return Rect(0f, 0f, containerW, containerH)
    }
    val containerAspect = containerW / containerH
    val imgAspect = imgW / imgH
    return if (imgAspect > containerAspect) {
        val fh = containerW / imgAspect
        val top = (containerH - fh) / 2f
        Rect(0f, top, containerW, top + fh)
    } else {
        val fw = containerH * imgAspect
        val left = (containerW - fw) / 2f
        Rect(left, 0f, left + fw, containerH)
    }
}

fun clampOffset(offset: Offset, scale: Float, containerW: Float, containerH: Float): Offset {
    if (scale <= 1.0f) return Offset.Zero
    val maxOffsetX = (containerW * (scale - 1f)) / 2f
    val maxOffsetY = (containerH * (scale - 1f)) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
    )
}

fun buildPhotoDevelopColorMatrix(params: DevelopmentParams): ColorMatrix {
    val evScale = Math.pow(2.0, params.exposureEV.toDouble()).toFloat().coerceIn(0.05f, 20.0f)
    val cFactor = (params.contrast / 100f).coerceIn(-0.95f, 3.0f)
    val cScale = 1.0f + cFactor
    val cOffset = (-0.5f * cScale + 0.5f) * 255f
    val shadowBoost = (params.shadows * 0.35f + params.blacks * 0.25f)
    val highlightTweak = (params.highlights * 0.35f + params.whites * 0.25f)
    val totalLumaOffset = cOffset + (shadowBoost + highlightTweak) * 0.4f

    val kDiff = (params.kelvin - 5500f) / 5500f
    val rWb = (1.0f + kDiff * 0.45f).coerceIn(0.2f, 2.5f)
    val bWb = (1.0f - kDiff * 0.45f).coerceIn(0.2f, 2.5f)
    val gWb = (1.0f - (params.tint / 200f)).coerceIn(0.2f, 2.0f)
    val mWb = (1.0f + (params.tint / 200f)).coerceIn(0.2f, 2.0f)

    val clarityBonus = (params.clarity / 250f)
    val effectiveScale = (evScale * cScale * (1f + clarityBonus)).coerceAtLeast(0.01f)
    val sat = (((params.saturation + params.vibrance * 0.75f) / 100f) + 1.0f).coerceAtLeast(0.0f)

    var pR = 1.0f
    var pG = 1.0f
    var pB = 1.0f
    when (params.colorProfile) {
        "cinetone" -> { pR = 1.04f; pG = 0.98f; pB = 0.94f }
        "landscape" -> { pR = 0.96f; pG = 1.06f; pB = 1.12f }
        "portrait" -> { pR = 1.08f; pG = 1.02f; pB = 0.96f }
    }

    val isMono = params.isMonochrome || params.colorProfile == "monochrome"

    return if (isMono) {
        val wR = params.monochromeWeights.getOrElse(0) { 0.2126f }
        val wG = params.monochromeWeights.getOrElse(3) { 0.7152f }
        val wB = params.monochromeWeights.getOrElse(5) { 0.0722f }
        val sum = (wR + wG + wB).coerceAtLeast(0.001f)
        val nR = (wR / sum) * effectiveScale
        val nG = (wG / sum) * effectiveScale
        val nB = (wB / sum) * effectiveScale

        ColorMatrix(
            floatArrayOf(
                nR, nG, nB, 0f, totalLumaOffset,
                nR, nG, nB, 0f, totalLumaOffset,
                nR, nG, nB, 0f, totalLumaOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )
    } else {
        val lumR = 0.2126f
        val lumG = 0.7152f
        val lumB = 0.0722f
        val invSat = 1.0f - sat
        val mR = (invSat * lumR + sat) * effectiveScale * rWb * pR * mWb
        val mG = (invSat * lumG + sat) * effectiveScale * gWb * pG
        val mB = (invSat * lumB + sat) * effectiveScale * bWb * pB
        val offR = (invSat * lumR) * effectiveScale
        val offG = (invSat * lumG) * effectiveScale
        val offB = (invSat * lumB) * effectiveScale

        ColorMatrix(
            floatArrayOf(
                mR, offG, offB, 0f, totalLumaOffset,
                offR, mG, offB, 0f, totalLumaOffset,
                offR, offG, mB, 0f, totalLumaOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
}

/**
 * BeforeAfterOverlay: 4-Way Photographic Comparison & In-Viewport Local Masking Engine.
 */
@Composable
fun BeforeAfterOverlay(
    originalBitmap: Bitmap?,
    developedBitmap: Bitmap?,
    compareMode: CompareMode,
    onCompareModeChange: (CompareMode) -> Unit,
    params: DevelopmentParams = DevelopmentParams(),
    maskLayers: List<MaskLayerState> = emptyList(),
    selectedMaskIndex: Int = 0,
    isMaskMode: Boolean = false,
    onMaskLayersChange: ((List<MaskLayerState>) -> Unit)? = null,
    geometry: CropTransformParams = CropTransformParams(),
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    var isHoldBefore by remember { mutableStateOf(false) }
    var splitFractionX by remember { mutableFloatStateOf(0.5f) }
    var splitFractionY by remember { mutableFloatStateOf(0.5f) }
    var overlayColor by remember { mutableStateOf(MaskOverlayColor.RUBY_RED) }
    var maskDisplayMode by remember { mutableStateOf(MaskDisplayMode.APPLIED) }
    var showMaskOverlay by remember { mutableStateOf(true) }

    // ズーム・パン状態
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // マスクレイヤー状態
    var localMaskLayers by remember(maskLayers) { mutableStateOf(maskLayers) }
    val currentActiveLayer = localMaskLayers.getOrNull(selectedMaskIndex)

    // ブラシ（ペン）描画用リアルタイムストロークバッファ
    val inProgressStrokes = remember { mutableStateListOf<BrushStrokePoint>() }
    var isEraserMode by remember { mutableStateOf(false) }
    var brushRadius by remember { mutableFloatStateOf(28.0f) }

    val developColorMatrix = remember(params) { buildPhotoDevelopColorMatrix(params) }
    val developColorFilter = remember(developColorMatrix) { ColorFilter.colorMatrix(developColorMatrix) }

    // 非マスクモードかつ比較Off時のジェスチャー: ダブルタップ切替、ピンチズーム、拡大時パン、等倍時長押しRAW比較
    val outerModifier = if (!isMaskMode && compareMode == CompareMode.Off) {
        modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (zoomScale > 1.1f) {
                            zoomScale = 1.0f
                            panOffset = Offset.Zero
                        } else {
                            zoomScale = 2.5f
                        }
                    },
                    onPress = {
                        if (zoomScale <= 1.05f) {
                            isHoldBefore = true
                            tryAwaitRelease()
                            isHoldBefore = false
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var prevCentroid = Offset.Zero
                    var prevSpan = 0f
                    val totalW = size.width.toFloat()
                    val totalH = size.height.toFloat()

                    do {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }

                        if (pressedPointers.size >= 2) {
                            val p1 = pressedPointers[0].position
                            val p2 = pressedPointers[1].position
                            val centroid = (p1 + p2) / 2f
                            val span = (p1 - p2).getDistance()

                            if (prevSpan > 0f) {
                                val zoomFactor = span / prevSpan
                                val newScale = (zoomScale * zoomFactor).coerceIn(1.0f, 6.0f)
                                val panDelta = centroid - prevCentroid
                                zoomScale = newScale
                                panOffset = clampOffset(panOffset + panDelta, newScale, totalW, totalH)
                            }
                            prevCentroid = centroid
                            prevSpan = span
                            event.changes.forEach { it.consume() }

                        } else if (pressedPointers.size == 1 && zoomScale > 1.05f) {
                            val p = pressedPointers[0]
                            val dragDelta = p.position - p.previousPosition
                            panOffset = clampOffset(panOffset + dragDelta, zoomScale, totalW, totalH)
                            p.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    } else {
        modifier
            .fillMaxSize()
            .clipToBounds()
    }

    Box(modifier = outerModifier) {
        val activeBitmap = if (isHoldBefore) originalBitmap else (developedBitmap ?: originalBitmap)
        val activeFilter = if (isHoldBefore) null else developColorFilter

        // ズーム・パンが適用されるコンテンツレイヤー
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        ) {

        when (compareMode) {
            CompareMode.Off -> {
                activeBitmap?.let { bmp ->
                    val totalRotation = (geometry.rotationSteps * 90f) + geometry.rotationDegrees
                    val scaleX = if (geometry.flipHorizontal) -1f else 1f
                    val scaleY = if (geometry.flipVertical) -1f else 1f

                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "写真プレビュー",
                        contentScale = ContentScale.Fit,
                        colorFilter = activeFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (!isHoldBefore) {
                                    Modifier.graphicsLayer {
                                        rotationZ = totalRotation
                                        this.scaleX = scaleX
                                        this.scaleY = scaleY
                                        rotationX = geometry.perspectiveVertical * 0.3f
                                        rotationY = geometry.perspectiveHorizontal * 0.3f
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }

            CompareMode.SplitVertical -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val totalW = size.width.toFloat()
                                if (totalW > 0f) {
                                    splitFractionX = (splitFractionX + dragAmount.x / totalW).coerceIn(0.02f, 0.98f)
                                }
                            }
                        }
                ) {
                    (developedBitmap ?: originalBitmap)?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "補正後",
                            contentScale = ContentScale.Fit,
                            colorFilter = developColorFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    originalBitmap?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(FractionalRectShape(rightFraction = splitFractionX, bottomFraction = 1f))
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "補正前",
                                contentScale = ContentScale.Fit,
                                colorFilter = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dividerX = size.width * splitFractionX
                        drawLine(
                            color = colors.borderStrong,
                            start = Offset(dividerX, 0f),
                            end = Offset(dividerX, size.height),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        // Precision Reticle Handle
                        drawCircle(color = colors.surface, radius = 9.dp.toPx(), center = Offset(dividerX, size.height / 2f))
                        drawCircle(color = colors.accentAmber, radius = 9.dp.toPx(), center = Offset(dividerX, size.height / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                        drawCircle(color = colors.accentAmber, radius = 2.dp.toPx(), center = Offset(dividerX, size.height / 2f))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("補正前 (ORIGINAL)", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = colors.textSecondary)
                        Text("補正後 (DEVELOPED)", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = colors.accentAmber)
                    }
                }
            }

            CompareMode.SplitHorizontal -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val totalH = size.height.toFloat()
                                if (totalH > 0f) {
                                    splitFractionY = (splitFractionY + dragAmount.y / totalH).coerceIn(0.02f, 0.98f)
                                }
                            }
                        }
                ) {
                    (developedBitmap ?: originalBitmap)?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "補正後",
                            contentScale = ContentScale.Fit,
                            colorFilter = developColorFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    originalBitmap?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(FractionalRectShape(rightFraction = 1f, bottomFraction = splitFractionY))
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "補正前",
                                contentScale = ContentScale.Fit,
                                colorFilter = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dividerY = size.height * splitFractionY
                        drawLine(
                            color = colors.borderStrong,
                            start = Offset(0f, dividerY),
                            end = Offset(size.width, dividerY),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        // Precision Reticle Handle
                        drawCircle(color = colors.surface, radius = 9.dp.toPx(), center = Offset(size.width / 2f, dividerY))
                        drawCircle(color = colors.accentAmber, radius = 9.dp.toPx(), center = Offset(size.width / 2f, dividerY), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                        drawCircle(color = colors.accentAmber, radius = 2.dp.toPx(), center = Offset(size.width / 2f, dividerY))
                    }
                }
            }

            CompareMode.SideBySide -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(1.dp, colors.borderSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        originalBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "補正前",
                                contentScale = ContentScale.Fit,
                                colorFilter = null
                            )
                        }
                        Text(
                            text = "補正前",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(1.dp, colors.borderSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        (developedBitmap ?: originalBitmap)?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "補正後",
                                contentScale = ContentScale.Fit,
                                colorFilter = developColorFilter
                            )
                        }
                        Text(
                            text = "補正後",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = colors.accentAmber,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // IN-VIEWPORT MASK OVERLAY CANVAS (ズーム・パンレイヤー内に描画)
        // ---------------------------------------------------------------------
        if (isMaskMode && currentActiveLayer != null && showMaskOverlay && compareMode == CompareMode.Off) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val imgW = (activeBitmap?.width ?: 1).toFloat()
                val imgH = (activeBitmap?.height ?: 1).toFloat()
                val fitted = calculateFittedRect(size.width, size.height, imgW, imgH)
                val w = fitted.width
                val h = fitted.height
                val ox = fitted.left
                val oy = fitted.top

                val colApplied = overlayColor.color
                val colExcluded = Color(0x9900B0FF)

                if (maskDisplayMode == MaskDisplayMode.DUAL_COLOR) {
                    drawRect(
                        color = colExcluded.copy(alpha = 0.28f),
                        topLeft = Offset(ox, oy),
                        size = Size(w, h)
                    )
                } else if (maskDisplayMode == MaskDisplayMode.EXCLUDED) {
                    drawRect(
                        color = colApplied.copy(alpha = 0.35f),
                        topLeft = Offset(ox, oy),
                        size = Size(w, h)
                    )
                }

                when (currentActiveLayer.type) {
                    MaskType.RADIAL_GRADIENT -> {
                        val cx = ox + currentActiveLayer.radialCenterX * w
                        val cy = oy + currentActiveLayer.radialCenterY * h
                        val rx = currentActiveLayer.radialRadiusX * w
                        val ry = currentActiveLayer.radialRadiusY * h

                        val fillAlpha = when (maskDisplayMode) {
                            MaskDisplayMode.APPLIED -> 0.42f
                            MaskDisplayMode.EXCLUDED -> 0.05f
                            MaskDisplayMode.DUAL_COLOR -> 0.48f
                        }

                        drawOval(
                            color = colApplied.copy(alpha = fillAlpha),
                            topLeft = Offset(cx - rx, cy - ry),
                            size = Size(rx * 2f, ry * 2f)
                        )
                        drawOval(
                            color = colApplied,
                            topLeft = Offset(cx - rx, cy - ry),
                            size = Size(rx * 2f, ry * 2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx() / zoomScale.coerceAtLeast(1f))
                        )

                        // 中心移動ピン（拡大率に応じて画面上での見た目を調整）
                        val pinRadius1 = 12.dp.toPx() / zoomScale.coerceAtLeast(1f)
                        val pinRadius2 = 5.dp.toPx() / zoomScale.coerceAtLeast(1f)
                        drawCircle(color = colors.accentAmber, radius = pinRadius1, center = Offset(cx, cy))
                        drawCircle(color = Color.Black, radius = pinRadius2, center = Offset(cx, cy))

                        // 外周半径変更ピン
                        val pinRadius3 = 9.dp.toPx() / zoomScale.coerceAtLeast(1f)
                        val pinRadius4 = 4.dp.toPx() / zoomScale.coerceAtLeast(1f)
                        drawCircle(color = Color.White, radius = pinRadius3, center = Offset(cx + rx, cy))
                        drawCircle(color = colors.accentAmber, radius = pinRadius4, center = Offset(cx + rx, cy))
                    }

                    MaskType.LINEAR_GRADIENT -> {
                        val sx = ox + currentActiveLayer.linearStartX * w
                        val sy = oy + currentActiveLayer.linearStartY * h
                        val ex = ox + currentActiveLayer.linearEndX * w
                        val ey = oy + currentActiveLayer.linearEndY * h

                        drawLine(
                            color = colApplied,
                            start = Offset(sx, sy),
                            end = Offset(ex, ey),
                            strokeWidth = 3.dp.toPx() / zoomScale.coerceAtLeast(1f)
                        )
                        val pinRadius1 = 12.dp.toPx() / zoomScale.coerceAtLeast(1f)
                        val pinRadius2 = 5.dp.toPx() / zoomScale.coerceAtLeast(1f)
                        val pinRadius3 = 10.dp.toPx() / zoomScale.coerceAtLeast(1f)
                        drawCircle(color = colors.accentAmber, radius = pinRadius1, center = Offset(sx, sy))
                        drawCircle(color = Color.Black, radius = pinRadius2, center = Offset(sx, sy))
                        drawCircle(color = Color.White, radius = pinRadius3, center = Offset(ex, ey))
                        drawCircle(color = colors.accentAmber, radius = pinRadius2, center = Offset(ex, ey))
                    }

                    MaskType.BRUSH -> {
                        // 確定ストロークの描画
                        currentActiveLayer.brushStrokes.forEach { pt ->
                            val strokeColor = if (pt.isEraser) Color(0xFF101012).copy(alpha = 0.85f) else colApplied.copy(alpha = 0.50f)
                            drawCircle(
                                color = strokeColor,
                                radius = pt.radius,
                                center = Offset(ox + pt.x * w, oy + pt.y * h)
                            )
                        }
                        // ドラッグ中のリアルタイムストローク描画
                        inProgressStrokes.forEach { pt ->
                            val strokeColor = if (pt.isEraser) Color(0xFF101012).copy(alpha = 0.85f) else colApplied.copy(alpha = 0.50f)
                            drawCircle(
                                color = strokeColor,
                                radius = pt.radius,
                                center = Offset(ox + pt.x * w, oy + pt.y * h)
                            )
                        }
                    }

                    else -> {}
                }
            }
        }

        } // photoZoomModifier Box の閉じ括弧

        // マスクが0件の時のクイック追加案内
        if (isMaskMode && maskLayers.isEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .background(colors.surfaceElevated.copy(alpha = 0.92f), RoundedCornerShape(3.dp))
                    .border(1.dp, colors.accentAmber, RoundedCornerShape(3.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "マスクを追加:",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .background(colors.accentAmber, RoundedCornerShape(2.dp))
                        .clickable {
                            val newLayer = MaskLayerState(
                                name = "円形マスク 1",
                                type = MaskType.RADIAL_GRADIENT,
                                radialCenterX = 0.5f,
                                radialCenterY = 0.5f,
                                radialRadiusX = 0.25f,
                                radialRadiusY = 0.25f
                            )
                            onMaskLayersChange?.invoke(listOf(newLayer))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("+ 円形マスク", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.background, fontFamily = FontFamily.SansSerif)
                }
                Box(
                    modifier = Modifier
                        .background(colors.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable {
                            val newLayer = MaskLayerState(
                                name = "線形グラデ 1",
                                type = MaskType.LINEAR_GRADIENT,
                                linearStartX = 0.5f,
                                linearStartY = 0.2f,
                                linearEndX = 0.5f,
                                linearEndY = 0.8f
                            )
                            onMaskLayersChange?.invoke(listOf(newLayer))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("+ 線形グラデ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontFamily = FontFamily.SansSerif)
                }
                Box(
                    modifier = Modifier
                        .background(colors.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable {
                            val newLayer = MaskLayerState(
                                name = "ブラシ 1",
                                type = MaskType.BRUSH,
                                brushRadius = brushRadius
                            )
                            onMaskLayersChange?.invoke(listOf(newLayer))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("+ ブラシ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontFamily = FontFamily.SansSerif)
                }
            }
        }

        // マスクモード時のインタラクティブ入力（2本指ピンチズーム・パン ＆ 1本指ペン描画/ピン移動）
        if (isMaskMode && currentActiveLayer != null && showMaskOverlay && compareMode == CompareMode.Off) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentActiveLayer.id, currentActiveLayer.type, isEraserMode, brushRadius) {
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            var isMultiTouch = false
                            var prevCentroid = Offset.Zero
                            var prevSpan = 0f

                            val totalW = size.width.toFloat()
                            val totalH = size.height.toFloat()
                            val imgW = (activeBitmap?.width ?: 1).toFloat()
                            val imgH = (activeBitmap?.height ?: 1).toFloat()
                            val fitted = calculateFittedRect(totalW, totalH, imgW, imgH)

                            // スクリーン座標から写真正規化座標 (0..1) への変換
                            fun screenToNorm(pos: Offset): Offset {
                                val cx = totalW / 2f
                                val cy = totalH / 2f
                                val lx = (pos.x - cx - panOffset.x) / zoomScale + cx
                                val ly = (pos.y - cy - panOffset.y) / zoomScale + cy
                                val nx = if (fitted.width > 0f) ((lx - fitted.left) / fitted.width).coerceIn(0f, 1f) else 0.5f
                                val ny = if (fitted.height > 0f) ((ly - fitted.top) / fitted.height).coerceIn(0f, 1f) else 0.5f
                                return Offset(nx, ny)
                            }

                            val layer = localMaskLayers.getOrNull(selectedMaskIndex) ?: return@awaitEachGesture
                            var draggingTarget = 0 // 0: None, 1: Center/Start, 2: Radius/End
                            val initialNorm = screenToNorm(firstDown.position)

                            when (layer.type) {
                                MaskType.RADIAL_GRADIENT -> {
                                    val distCenter = sqrt((initialNorm.x - layer.radialCenterX).pow(2) + (initialNorm.y - layer.radialCenterY).pow(2))
                                    val distEdge = abs(distCenter - layer.radialRadiusX)
                                    val tolCenter = 0.14f / zoomScale.coerceAtLeast(1f)
                                    val tolEdge = 0.12f / zoomScale.coerceAtLeast(1f)
                                    draggingTarget = if (distCenter < tolCenter) 1 else if (distEdge < tolEdge) 2 else 1
                                }
                                MaskType.LINEAR_GRADIENT -> {
                                    val distStart = sqrt((initialNorm.x - layer.linearStartX).pow(2) + (initialNorm.y - layer.linearStartY).pow(2))
                                    val distEnd = sqrt((initialNorm.x - layer.linearEndX).pow(2) + (initialNorm.y - layer.linearEndY).pow(2))
                                    draggingTarget = if (distStart < distEnd) 1 else 2
                                }
                                MaskType.BRUSH -> {
                                    inProgressStrokes.clear()
                                    inProgressStrokes.add(
                                        BrushStrokePoint(
                                            x = initialNorm.x,
                                            y = initialNorm.y,
                                            radius = brushRadius,
                                            isEraser = isEraserMode
                                        )
                                    )
                                }
                                else -> {}
                            }

                            var prevSinglePos = firstDown.position

                            do {
                                val event = awaitPointerEvent()
                                val pressedPointers = event.changes.filter { it.pressed }

                                if (pressedPointers.size >= 2) {
                                    if (!isMultiTouch) {
                                        isMultiTouch = true
                                        inProgressStrokes.clear()
                                    }
                                    val p1 = pressedPointers[0].position
                                    val p2 = pressedPointers[1].position
                                    val centroid = (p1 + p2) / 2f
                                    val span = (p1 - p2).getDistance()

                                    if (prevSpan > 0f) {
                                        val zoomFactor = span / prevSpan
                                        val newScale = (zoomScale * zoomFactor).coerceIn(1.0f, 6.0f)
                                        val panDelta = centroid - prevCentroid
                                        zoomScale = newScale
                                        panOffset = clampOffset(panOffset + panDelta, newScale, totalW, totalH)
                                    }
                                    prevCentroid = centroid
                                    prevSpan = span
                                    event.changes.forEach { it.consume() }

                                } else if (pressedPointers.size == 1 && !isMultiTouch) {
                                    val pointer = pressedPointers[0]
                                    val currentPos = pointer.position
                                    pointer.consume()

                                    val norm = screenToNorm(currentPos)
                                    val currentLayer = localMaskLayers.getOrNull(selectedMaskIndex)

                                    if (currentLayer != null) {
                                        when (currentLayer.type) {
                                            MaskType.BRUSH -> {
                                                val lastPt = inProgressStrokes.lastOrNull()
                                                if (lastPt == null || abs(lastPt.x - norm.x) > 0.001f || abs(lastPt.y - norm.y) > 0.001f) {
                                                    if (lastPt != null) {
                                                        val dist = sqrt((norm.x - lastPt.x).pow(2) + (norm.y - lastPt.y).pow(2))
                                                        val step = 0.008f / zoomScale.coerceAtLeast(1f)
                                                        if (dist > step) {
                                                            val steps = (dist / step).toInt().coerceAtMost(25)
                                                            for (s in 1 until steps) {
                                                                val t = s.toFloat() / steps
                                                                inProgressStrokes.add(
                                                                    BrushStrokePoint(
                                                                        x = lastPt.x + (norm.x - lastPt.x) * t,
                                                                        y = lastPt.y + (norm.y - lastPt.y) * t,
                                                                        radius = brushRadius,
                                                                        isEraser = isEraserMode
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                    inProgressStrokes.add(
                                                        BrushStrokePoint(
                                                            x = norm.x,
                                                            y = norm.y,
                                                            radius = brushRadius,
                                                            isEraser = isEraserMode
                                                        )
                                                    )
                                                }
                                            }
                                            MaskType.RADIAL_GRADIENT -> {
                                                val prevNorm = screenToNorm(prevSinglePos)
                                                val dnx = norm.x - prevNorm.x
                                                val dny = norm.y - prevNorm.y
                                                localMaskLayers = localMaskLayers.mapIndexed { idx, l ->
                                                    if (idx == selectedMaskIndex) {
                                                        if (draggingTarget == 1) {
                                                            l.copy(
                                                                radialCenterX = (l.radialCenterX + dnx).coerceIn(0.02f, 0.98f),
                                                                radialCenterY = (l.radialCenterY + dny).coerceIn(0.02f, 0.98f)
                                                            )
                                                        } else {
                                                            l.copy(
                                                                radialRadiusX = (l.radialRadiusX + dnx).coerceIn(0.05f, 0.85f),
                                                                radialRadiusY = (l.radialRadiusY + dny).coerceIn(0.05f, 0.85f)
                                                            )
                                                        }
                                                    } else l
                                                }
                                            }
                                            MaskType.LINEAR_GRADIENT -> {
                                                val prevNorm = screenToNorm(prevSinglePos)
                                                val dnx = norm.x - prevNorm.x
                                                val dny = norm.y - prevNorm.y
                                                localMaskLayers = localMaskLayers.mapIndexed { idx, l ->
                                                    if (idx == selectedMaskIndex) {
                                                        if (draggingTarget == 1) {
                                                            l.copy(
                                                                linearStartX = (l.linearStartX + dnx).coerceIn(0.02f, 0.98f),
                                                                linearStartY = (l.linearStartY + dny).coerceIn(0.02f, 0.98f)
                                                            )
                                                        } else {
                                                            l.copy(
                                                                linearEndX = (l.linearEndX + dnx).coerceIn(0.02f, 0.98f),
                                                                linearEndY = (l.linearEndY + dny).coerceIn(0.02f, 0.98f)
                                                            )
                                                        }
                                                    } else l
                                                }
                                            }
                                            else -> {}
                                        }
                                    }
                                    prevSinglePos = currentPos
                                }
                            } while (event.changes.any { it.pressed })

                            // 指を離した時の確定処理
                            if (!isMultiTouch && inProgressStrokes.isNotEmpty()) {
                                val currentLayer = localMaskLayers.getOrNull(selectedMaskIndex)
                                if (currentLayer != null && currentLayer.type == MaskType.BRUSH) {
                                    val mergedStrokes = currentLayer.brushStrokes + inProgressStrokes.toList()
                                    val updatedLayers = localMaskLayers.mapIndexed { idx, l ->
                                        if (idx == selectedMaskIndex) l.copy(brushStrokes = mergedStrokes) else l
                                    }
                                    localMaskLayers = updatedLayers
                                    onMaskLayersChange?.invoke(updatedLayers)
                                }
                                inProgressStrokes.clear()
                            } else if (!isMultiTouch && layer.type != MaskType.BRUSH) {
                                onMaskLayersChange?.invoke(localMaskLayers)
                            }
                        }
                    }
            )
        }

        // Top-left Mask Overlay Control HUD (表示モード・ルビ色・ブラシ設定・全消去)
        if (isMaskMode && currentActiveLayer != null && showMaskOverlay && compareMode == CompareMode.Off) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(colors.surface.copy(alpha = 0.90f), RoundedCornerShape(3.dp))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 表示モード切替: 適用エリア / 欠けたところ / 両方色分け
                Box(
                    modifier = Modifier
                        .background(colors.surfacePressed, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.accentAmber, RoundedCornerShape(2.dp))
                        .clickable {
                            maskDisplayMode = when (maskDisplayMode) {
                                MaskDisplayMode.APPLIED -> MaskDisplayMode.EXCLUDED
                                MaskDisplayMode.EXCLUDED -> MaskDisplayMode.DUAL_COLOR
                                MaskDisplayMode.DUAL_COLOR -> MaskDisplayMode.APPLIED
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "表示: ${maskDisplayMode.label}",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = colors.accentAmber,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 4色ルビカラーセレクター
                MaskOverlayColor.entries.forEach { mc ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(mc.color, CircleShape)
                            .border(if (overlayColor == mc) 2.dp else 0.5.dp, Color.White, CircleShape)
                            .clickable { overlayColor = mc }
                    )
                }

                val currentBrushLayer = localMaskLayers.getOrNull(selectedMaskIndex)
                if (currentBrushLayer?.type == MaskType.BRUSH) {
                    // ペン / 消しゴム 切替
                    Box(
                        modifier = Modifier
                            .background(if (isEraserMode) colors.accentAmber else colors.surfacePressed, RoundedCornerShape(2.dp))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                            .clickable { isEraserMode = !isEraserMode }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isEraserMode) "消しゴム" else "ペン",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = if (isEraserMode) Color.Black else colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // ブラシサイズ切替 (15 -> 28 -> 45 -> 70 -> 15)
                    Box(
                        modifier = Modifier
                            .background(colors.surfacePressed, RoundedCornerShape(2.dp))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                            .clickable {
                                brushRadius = when (brushRadius.toInt()) {
                                    15 -> 28.0f
                                    28 -> 45.0f
                                    45 -> 70.0f
                                    else -> 15.0f
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "太さ ${brushRadius.toInt()}",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = colors.accentAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // ストローク消去ボタン
                    if (currentBrushLayer.brushStrokes.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF882222), RoundedCornerShape(2.dp))
                                .clickable {
                                    val updatedLayers = localMaskLayers.mapIndexed { idx, l ->
                                        if (idx == selectedMaskIndex) l.copy(brushStrokes = emptyList()) else l
                                    }
                                    localMaskLayers = updatedLayers
                                    onMaskLayersChange?.invoke(updatedLayers)
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "クリア",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Hold-to-compare banner
        if (isHoldBefore) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(3.dp))
                    .border(1.dp, colors.accentAmber, RoundedCornerShape(3.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "原画 RAW（長押し中）",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.accentAmber
                )
            }
        }

        // Compare Mode Controller Bar & Zoom Toggle (Top Right)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ズーム倍率ボタン (タップで 1.0x 全体 ⇔ 2.5x 等倍 を切替)
            Box(
                modifier = Modifier
                    .background(
                        if (zoomScale > 1.1f) colors.accentAmber else colors.surface.copy(alpha = 0.85f),
                        RoundedCornerShape(3.dp)
                    )
                    .border(1.dp, if (zoomScale > 1.1f) colors.accentAmber else colors.borderSubtle, RoundedCornerShape(3.dp))
                    .clickable {
                        if (zoomScale > 1.1f) {
                            zoomScale = 1.0f
                            panOffset = Offset.Zero
                        } else {
                            zoomScale = 2.5f
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${"%.1f".format(zoomScale)}x ${if (zoomScale > 1.1f) "等倍" else "全体"}",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (zoomScale > 1.1f) Color.Black else colors.textPrimary
                )
            }

            Row(
                modifier = Modifier
                    .background(colors.surface.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompareTabButton("オフ", active = compareMode == CompareMode.Off) {
                    onCompareModeChange(CompareMode.Off)
                }
                CompareTabButton("左右分割", active = compareMode == CompareMode.SplitVertical) {
                    onCompareModeChange(CompareMode.SplitVertical)
                }
                CompareTabButton("上下分割", active = compareMode == CompareMode.SplitHorizontal) {
                    onCompareModeChange(CompareMode.SplitHorizontal)
                }
                CompareTabButton("並列", active = compareMode == CompareMode.SideBySide) {
                    onCompareModeChange(CompareMode.SideBySide)
                }
            }
        }
    }
}

@Composable
private fun CompareTabButton(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = LightRumorTheme.colors
    Box(
        modifier = Modifier
            .background(if (active) colors.accentAmber else colors.surfacePressed, RoundedCornerShape(2.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (active) Color.Black else colors.textPrimary
        )
    }
}

private class FractionalRectShape(
    val rightFraction: Float,
    val bottomFraction: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Rectangle(
            Rect(
                left = 0f,
                top = 0f,
                right = size.width * rightFraction,
                bottom = size.height * bottomFraction
            )
        )
    }
}

