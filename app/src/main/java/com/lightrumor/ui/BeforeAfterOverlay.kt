package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

    val developColorMatrix = remember(params) { buildPhotoDevelopColorMatrix(params) }
    val developColorFilter = remember(developColorMatrix) { ColorFilter.colorMatrix(developColorMatrix) }

    // マスクモード時は長押し比較を無効化し、写真上でのマスク操作にポインターを100%解放
    val outerModifier = if (!isMaskMode) {
        modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHoldBefore = true
                        tryAwaitRelease()
                        isHoldBefore = false
                    }
                )
            }
    } else {
        modifier
            .fillMaxSize()
            .clipToBounds()
    }

    Box(modifier = outerModifier) {
        val activeBitmap = if (isHoldBefore) originalBitmap else (developedBitmap ?: originalBitmap)
        val activeFilter = if (isHoldBefore) null else developColorFilter

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
                            strokeWidth = 2.dp.toPx()
                        )
                        drawCircle(color = colors.accentAmber, radius = 8.dp.toPx(), center = Offset(dividerX, size.height / 2f))
                        drawCircle(color = Color.Black, radius = 3.dp.toPx(), center = Offset(dividerX, size.height / 2f))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("補正前 (ORIGINAL)", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = colors.textSecondary)
                        Text("補正後 (DEVELOPED)", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = colors.accentAmber)
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
                            strokeWidth = 2.dp.toPx()
                        )
                        drawCircle(color = colors.accentAmber, radius = 8.dp.toPx(), center = Offset(size.width / 2f, dividerY))
                        drawCircle(color = Color.Black, radius = 3.dp.toPx(), center = Offset(size.width / 2f, dividerY))
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
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
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
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = colors.accentAmber,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // IN-VIEWPORT MASK OVERLAY & INTERACTIVE PIN ADJUSTMENT
        // ---------------------------------------------------------------------
        val activeLayer = maskLayers.getOrNull(selectedMaskIndex)

        // マスクが0件の時のクイック追加案内
        if (isMaskMode && maskLayers.isEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .background(Color(0xEE141414), RoundedCornerShape(4.dp))
                    .border(1.dp, colors.accentAmber, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "マスクを追加:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color.White,
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
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ 円形マスク", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = FontFamily.Monospace)
                }
                Box(
                    modifier = Modifier
                        .background(colors.surfaceElevated, RoundedCornerShape(2.dp))
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
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ 線形グラデ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                }
                Box(
                    modifier = Modifier
                        .background(colors.surfaceElevated, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable {
                            val newLayer = MaskLayerState(
                                name = "ブラシ 1",
                                type = MaskType.BRUSH
                            )
                            onMaskLayersChange?.invoke(listOf(newLayer))
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ ブラシ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (isMaskMode && activeLayer != null && showMaskOverlay && compareMode == CompareMode.Off) {
            var localMaskLayers by remember(maskLayers) { mutableStateOf(maskLayers) }
            val currentActiveLayer = localMaskLayers.getOrNull(selectedMaskIndex)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentActiveLayer?.id, currentActiveLayer?.type) {
                        var draggingTarget = 0 // 0: None, 1: Center/Start, 2: Radius/End
                        detectDragGestures(
                            onDragStart = { offset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w <= 0f || h <= 0f) return@detectDragGestures
                                val nx = offset.x / w
                                val ny = offset.y / h
                                
                                val layer = currentActiveLayer ?: return@detectDragGestures

                                when (layer.type) {
                                    MaskType.RADIAL_GRADIENT -> {
                                        val distCenter = sqrt((nx - layer.radialCenterX) * (nx - layer.radialCenterX) + (ny - layer.radialCenterY) * (ny - layer.radialCenterY))
                                        val distEdge = abs(distCenter - layer.radialRadiusX)
                                        draggingTarget = if (distCenter < 0.14f) 1 else if (distEdge < 0.12f) 2 else 1
                                    }
                                    MaskType.LINEAR_GRADIENT -> {
                                        val distStart = sqrt((nx - layer.linearStartX) * (nx - layer.linearStartX) + (ny - layer.linearStartY) * (ny - layer.linearStartY))
                                        val distEnd = sqrt((nx - layer.linearEndX) * (nx - layer.linearEndX) + (ny - layer.linearEndY) * (ny - layer.linearEndY))
                                        draggingTarget = if (distStart < distEnd) 1 else 2
                                    }
                                    MaskType.BRUSH -> {
                                        val updatedList = layer.brushStrokes.toMutableList()
                                        updatedList.add(BrushStrokePoint(x = nx, y = ny, radius = layer.brushRadius))
                                        layer.brushStrokes = updatedList
                                        localMaskLayers = localMaskLayers.toList()
                                    }
                                    else -> {}
                                }
                            },
                            onDragEnd = {
                                onMaskLayersChange?.invoke(localMaskLayers.toList())
                            },
                            onDragCancel = {
                                onMaskLayersChange?.invoke(localMaskLayers.toList())
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w <= 0f || h <= 0f) return@detectDragGestures
                                val dnx = dragAmount.x / w
                                val dny = dragAmount.y / h
                                
                                val layer = currentActiveLayer ?: return@detectDragGestures

                                when (layer.type) {
                                    MaskType.RADIAL_GRADIENT -> {
                                        if (draggingTarget == 1) {
                                            layer.radialCenterX = (layer.radialCenterX + dnx).coerceIn(0.02f, 0.98f)
                                            layer.radialCenterY = (layer.radialCenterY + dny).coerceIn(0.02f, 0.98f)
                                        } else if (draggingTarget == 2) {
                                            layer.radialRadiusX = (layer.radialRadiusX + dnx).coerceIn(0.05f, 0.85f)
                                            layer.radialRadiusY = (layer.radialRadiusY + dny).coerceIn(0.05f, 0.85f)
                                        }
                                        localMaskLayers = localMaskLayers.toList()
                                    }
                                    MaskType.LINEAR_GRADIENT -> {
                                        if (draggingTarget == 1) {
                                            layer.linearStartX = (layer.linearStartX + dnx).coerceIn(0.02f, 0.98f)
                                            layer.linearStartY = (layer.linearStartY + dny).coerceIn(0.02f, 0.98f)
                                        } else {
                                            layer.linearEndX = (layer.linearEndX + dnx).coerceIn(0.02f, 0.98f)
                                            layer.linearEndY = (layer.linearEndY + dny).coerceIn(0.02f, 0.98f)
                                        }
                                        localMaskLayers = localMaskLayers.toList()
                                    }
                                    MaskType.BRUSH -> {
                                        val nx = (change.position.x / w).coerceIn(0f, 1f)
                                        val ny = (change.position.y / h).coerceIn(0f, 1f)
                                        val updatedList = layer.brushStrokes.toMutableList()
                                        updatedList.add(BrushStrokePoint(x = nx, y = ny, radius = layer.brushRadius))
                                        layer.brushStrokes = updatedList
                                        localMaskLayers = localMaskLayers.toList()
                                    }
                                    else -> {}
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val colApplied = overlayColor.color
                    val colExcluded = Color(0x9900B0FF) // 欠けたところ（非対象エリア）は鮮明なシアン/ブルー

                    // 両方色分けモードの場合、まず全面を「欠けたところの色」で塗る
                    if (maskDisplayMode == MaskDisplayMode.DUAL_COLOR) {
                        drawRect(color = colExcluded.copy(alpha = 0.28f))
                    } else if (maskDisplayMode == MaskDisplayMode.EXCLUDED) {
                        drawRect(color = colApplied.copy(alpha = 0.35f))
                    }
                    
                    val layer = currentActiveLayer ?: return@Canvas

                    when (layer.type) {
                        MaskType.RADIAL_GRADIENT -> {
                            val cx = layer.radialCenterX * w
                            val cy = layer.radialCenterY * h
                            val rx = layer.radialRadiusX * w
                            val ry = layer.radialRadiusY * h

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
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )

                            // 中心移動ピン（大型化）
                            drawCircle(color = colors.accentAmber, radius = 12.dp.toPx(), center = Offset(cx, cy))
                            drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = Offset(cx, cy))

                            // 外周半径変更ピン（大型化）
                            drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(cx + rx, cy))
                            drawCircle(color = colors.accentAmber, radius = 4.dp.toPx(), center = Offset(cx + rx, cy))
                        }

                        MaskType.LINEAR_GRADIENT -> {
                            val sx = layer.linearStartX * w
                            val sy = layer.linearStartY * h
                            val ex = layer.linearEndX * w
                            val ey = layer.linearEndY * h

                            drawLine(
                                color = colApplied,
                                start = Offset(sx, sy),
                                end = Offset(ex, ey),
                                strokeWidth = 3.dp.toPx()
                            )
                            // 開始ピン
                            drawCircle(color = colors.accentAmber, radius = 12.dp.toPx(), center = Offset(sx, sy))
                            drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = Offset(sx, sy))

                            // 終了ピン
                            drawCircle(color = Color.White, radius = 10.dp.toPx(), center = Offset(ex, ey))
                            drawCircle(color = colors.accentAmber, radius = 5.dp.toPx(), center = Offset(ex, ey))
                        }

                        MaskType.BRUSH -> {
                            layer.brushStrokes.forEach { pt ->
                                drawCircle(
                                    color = colApplied.copy(alpha = 0.50f),
                                    radius = pt.radius,
                                    center = Offset(pt.x * w, pt.y * h)
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }

            // Top-left Mask Overlay Control HUD (ルビ色・欠けたところ色分け・全消去)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(colors.surface.copy(alpha = 0.90f), RoundedCornerShape(3.dp))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "表示: ${maskDisplayMode.label}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = colors.accentAmber,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 4色ルビカラーセレクター
                MaskOverlayColor.values().forEach { mc ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(mc.color, CircleShape)
                            .border(if (overlayColor == mc) 2.dp else 0.5.dp, Color.White, CircleShape)
                            .clickable { overlayColor = mc }
                    )
                }

                // ブラシのときのストローク消去
                if (activeLayer.type == MaskType.BRUSH && activeLayer.brushStrokes.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF882222), RoundedCornerShape(2.dp))
                            .clickable {
                                activeLayer.brushStrokes = emptyList()
                                onMaskLayersChange?.invoke(maskLayers.toList())
                            }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "クリア",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
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
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "原画 RAW（長押し中）",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = colors.accentAmber
                )
            }
        }

        // Compare Mode Controller Bar (Top Right)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .background(colors.surface.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompareTabButton("オフ", active = compareMode == CompareMode.Off) {
                onCompareModeChange(CompareMode.Off)
            }
            CompareTabButton("分割", active = compareMode == CompareMode.SplitVertical) {
                onCompareModeChange(CompareMode.SplitVertical)
            }
            CompareTabButton("並列", active = compareMode == CompareMode.SideBySide) {
                onCompareModeChange(CompareMode.SideBySide)
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
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
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

