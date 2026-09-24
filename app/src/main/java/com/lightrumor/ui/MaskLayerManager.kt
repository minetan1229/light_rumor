package com.lightrumor.ui

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*

/**
 * MaskLayerManager: Phase 5 Multi-Layer Local Masking Studio.
 * Integrates:
 * - Layer Stack with Reorder, Visibility, Invert, Opacity, Delete
 * - 9 Mask Types: Linear, Radial, Polygon, Brush, Luma, Color, Depth, Edge, Composite
 * - Boolean Operations: SET (Replace), ADD (Union), SUB (Subtract), INT (Intersect)
 * - Local Tone & Color Adjustments (Exposure, WB, Contrast, Highlights, Saturation)
 * - Stylus Pressure-Sensitive Brush Canvas (Android MotionEvent getPressure())
 * - Zero AI dependencies, zero emojis, industrial camera chassis styling.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MaskLayerManager(
    maskLayers: List<MaskLayerState>,
    onLayersChange: (List<MaskLayerState>) -> Unit,
    selectedLayerIndex: Int,
    onSelectLayer: (Int) -> Unit,
    hapticManager: HapticManager?,
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    var showAddDialog by remember { mutableStateOf(false) }
    var activeSubTab by remember { mutableStateOf(0) } // 0: LAYERS, 1: ADJUST, 2: STYLUS

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .border(1.dp, colors.borderStrong)
    ) {
        // 1. Top Sub-Header & Mode Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("レイヤー (${maskLayers.size})", "調整", "スタイラスブラシ").forEachIndexed { idx, title ->
                    val isSel = (activeSubTab == idx)
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSel) colors.surfacePressed else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            )
                            .border(
                                1.dp,
                                if (isSel) colors.accentAmber else colors.borderSubtle,
                                RoundedCornerShape(2.dp)
                            )
                            .clickable {
                                hapticManager?.performDialTick()
                                activeSubTab = idx
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = title,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSel) colors.accentAmber else colors.textSecondary
                        )
                    }
                }
            }

            // + マスク追加 Button
            Box(
                modifier = Modifier
                    .background(colors.accentAmber, RoundedCornerShape(2.dp))
                    .clickable {
                        hapticManager?.performDialTick()
                        showAddDialog = true
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "+ マスク追加",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = colors.background
                )
            }
        }

        // 2. Main Content Area
        Box(modifier = Modifier.weight(1.0f).fillMaxWidth()) {
            when (activeSubTab) {
                0 -> {
                    // LAYERS STACK
                    if (maskLayers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "有効なマスクがありません。[+ マスク追加] で開始",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(maskLayers) { index, layer ->
                                val isSelected = (index == selectedLayerIndex)
                                MaskLayerRow(
                                    layer = layer,
                                    isSelected = isSelected,
                                    onSelect = {
                                        hapticManager?.performDialTick()
                                        onSelectLayer(index)
                                    },
                                    onToggleVisible = {
                                        val updated = maskLayers.toMutableList()
                                        updated[index] = layer.copy(enabled = !layer.enabled)
                                        onLayersChange(updated)
                                    },
                                    onToggleInvert = {
                                        val updated = maskLayers.toMutableList()
                                        updated[index] = layer.copy(inverted = !layer.inverted)
                                        onLayersChange(updated)
                                    },
                                    onBooleanOpChange = { newOp ->
                                        val updated = maskLayers.toMutableList()
                                        updated[index] = layer.copy(booleanOp = newOp)
                                        onLayersChange(updated)
                                    },
                                    onDelete = {
                                        val updated = maskLayers.toMutableList()
                                        updated.removeAt(index)
                                        onLayersChange(updated)
                                        if (selectedLayerIndex >= updated.size) {
                                            onSelectLayer(maxOf(0, updated.size - 1))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // LOCAL ADJUSTMENTS PANEL FOR SELECTED MASK
                    val selectedLayer = maskLayers.getOrNull(selectedLayerIndex)
                    if (selectedLayer == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "マスクレイヤーを選択してパラメータを調整",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        }
                    } else {
                        LocalAdjustmentsPanel(
                            layer = selectedLayer,
                            onLayerUpdated = { updatedLayer ->
                                val updated = maskLayers.toMutableList()
                                if (selectedLayerIndex in updated.indices) {
                                    updated[selectedLayerIndex] = updatedLayer
                                    onLayersChange(updated)
                                }
                            },
                            hapticManager = hapticManager
                        )
                    }
                }

                2 -> {
                    // STYLUS PRESSURE BRUSH CANVAS
                    val selectedLayer = maskLayers.getOrNull(selectedLayerIndex)
                    StylusBrushCanvas(
                        activeLayer = selectedLayer,
                        onStrokeFinished = { newStrokes ->
                            if (selectedLayer != null) {
                                val updatedStrokes = selectedLayer.brushStrokes + newStrokes
                                val updatedLayer = selectedLayer.copy(
                                    type = MaskType.BRUSH,
                                    brushStrokes = updatedStrokes
                                )
                                val updatedList = maskLayers.toMutableList()
                                if (selectedLayerIndex in updatedList.indices) {
                                    updatedList[selectedLayerIndex] = updatedLayer
                                    onLayersChange(updatedList)
                                }
                            }
                        },
                        onClearStrokes = {
                            if (selectedLayer != null) {
                                val updatedLayer = selectedLayer.copy(brushStrokes = emptyList())
                                val updatedList = maskLayers.toMutableList()
                                if (selectedLayerIndex in updatedList.indices) {
                                    updatedList[selectedLayerIndex] = updatedLayer
                                    onLayersChange(updatedList)
                                }
                            }
                        }
                    )
                }
            }
        }

        // 3. Add Mask Modal Dialog
        if (showAddDialog) {
            AddMaskModal(
                onDismiss = { showAddDialog = false },
                onAddMask = { maskType ->
                    showAddDialog = false
                    val newName = "${maskType.displayName} ${maskLayers.size + 1}"
                    val newLayer = MaskLayerState(
                        name = newName,
                        type = maskType,
                        booleanOp = if (maskLayers.isEmpty()) BooleanOp.REPLACE else BooleanOp.UNION
                    )
                    val updated = maskLayers + newLayer
                    onLayersChange(updated)
                    onSelectLayer(updated.size - 1)
                    activeSubTab = 0
                }
            )
        }
    }
}

@Composable
private fun MaskLayerRow(
    layer: MaskLayerState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleInvert: () -> Unit,
    onBooleanOpChange: (BooleanOp) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LightRumorTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) colors.surfacePressed else colors.surfaceElevated,
                RoundedCornerShape(2.dp)
            )
            .border(
                1.dp,
                if (isSelected) colors.accentAmber else colors.borderSubtle,
                RoundedCornerShape(2.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Visibility LED Indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (layer.enabled) colors.accentAmber else colors.borderStrong,
                        RoundedCornerShape(1.dp)
                    )
                    .clickable { onToggleVisible() }
            )

            Box(
                modifier = Modifier
                    .background(colors.surface, RoundedCornerShape(2.dp))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = layer.type.displayName,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            Text(
                text = layer.name,
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = colors.textPrimary,
                maxLines = 1
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(if (layer.inverted) colors.accentAmber else Color.Transparent, RoundedCornerShape(2.dp))
                    .border(1.dp, if (layer.inverted) colors.accentAmber else colors.borderSubtle, RoundedCornerShape(2.dp))
                    .clickable { onToggleInvert() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "反転",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (layer.inverted) colors.background else colors.textSecondary
                )
            }

            Box(
                modifier = Modifier
                    .background(colors.surface, RoundedCornerShape(2.dp))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                    .clickable {
                        val nextOp = when (layer.booleanOp) {
                            BooleanOp.REPLACE -> BooleanOp.UNION
                            BooleanOp.UNION -> BooleanOp.SUBTRACT
                            BooleanOp.SUBTRACT -> BooleanOp.INTERSECT
                            BooleanOp.INTERSECT -> BooleanOp.UNION
                        }
                        onBooleanOpChange(nextOp)
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = layer.booleanOp.displayName,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentAmber
                )
            }

            Box(
                modifier = Modifier
                    .background(colors.surface, RoundedCornerShape(2.dp))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                    .clickable { onDelete() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "削除",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = colors.statusReject
                )
            }
        }
    }
}

@Composable
private fun LocalAdjustmentsPanel(
    layer: MaskLayerState,
    onLayerUpdated: (MaskLayerState) -> Unit,
    hapticManager: HapticManager?
) {
    val colors = LightRumorTheme.colors
    val adj = layer.adjustments

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Text(
            text = "局所調整: ${layer.name.uppercase()} (${layer.type.displayName})",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = colors.accentAmber,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LocalSliderMini(
                label = "露出",
                value = adj.exposureEV,
                range = -3.0f..3.0f,
                unit = "EV",
                onValueChange = {
                    val updated = layer.deepCopy()
                    updated.adjustments.exposureEV = it
                    onLayerUpdated(updated)
                }
            )

            LocalSliderMini(
                label = "コントラスト",
                value = adj.contrast,
                range = -100.0f..100.0f,
                unit = "",
                onValueChange = {
                    val updated = layer.deepCopy()
                    updated.adjustments.contrast = it
                    onLayerUpdated(updated)
                }
            )

            LocalSliderMini(
                label = "ハイライト",
                value = adj.highlights,
                range = -100.0f..100.0f,
                unit = "",
                onValueChange = {
                    val updated = layer.deepCopy()
                    updated.adjustments.highlights = it
                    onLayerUpdated(updated)
                }
            )

            LocalSliderMini(
                label = "シャドウ",
                value = adj.shadows,
                range = -100.0f..100.0f,
                unit = "",
                onValueChange = {
                    val updated = layer.deepCopy()
                    updated.adjustments.shadows = it
                    onLayerUpdated(updated)
                }
            )

            LocalSliderMini(
                label = "彩度",
                value = adj.saturation,
                range = -100.0f..100.0f,
                unit = "%",
                onValueChange = {
                    val updated = layer.deepCopy()
                    updated.adjustments.saturation = it
                    onLayerUpdated(updated)
                }
            )

            LocalSliderMini(
                label = "明瞭度",
                value = adj.clarity,
                range = -100.0f..100.0f,
                unit = "",
                onValueChange = {
                    val updated = layer.deepCopy()
                    updated.adjustments.clarity = it
                    onLayerUpdated(updated)
                }
            )
        }
    }
}

@Composable
private fun LocalSliderMini(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    val colors = LightRumorTheme.colors
    Column(
        modifier = Modifier
            .width(100.dp)
            .background(colors.surfaceElevated, RoundedCornerShape(2.dp))
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = colors.textSecondary
        )
        Text(
            text = "${if (value > 0) "+" else ""}${"%.2f".format(value)}$unit",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = colors.textPrimary,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "[-]",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentAmber,
                modifier = Modifier
                    .clickable {
                        val step = (range.endInclusive - range.start) * 0.05f
                        onValueChange((value - step).coerceIn(range))
                    }
                    .padding(4.dp)
            )
            Text(
                text = "[+]",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentAmber,
                modifier = Modifier
                    .clickable {
                        val step = (range.endInclusive - range.start) * 0.05f
                        onValueChange((value + step).coerceIn(range))
                    }
                    .padding(4.dp)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StylusBrushCanvas(
    activeLayer: MaskLayerState?,
    onStrokeFinished: (List<BrushStrokePoint>) -> Unit,
    onClearStrokes: () -> Unit
) {
    val colors = LightRumorTheme.colors
    var isEraserMode by remember { mutableStateOf(false) }
    var currentPressure by remember { mutableStateOf(1.0f) }
    var currentRadius by remember { mutableStateOf(25.0f) }
    val currentStroke = remember { mutableStateListOf<BrushStrokePoint>() }

    Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "筆圧: ${"%.2f".format(currentPressure)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = colors.accentAmber
                )
                Text(
                    text = "半径: ${currentRadius.toInt()}px",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(if (isEraserMode) colors.statusReject else Color.Transparent, RoundedCornerShape(2.dp))
                        .border(1.dp, if (isEraserMode) colors.statusReject else colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable { isEraserMode = !isEraserMode }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isEraserMode) "消しゴム [ON]" else "消しゴム [OFF]",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEraserMode) colors.background else colors.textSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .background(colors.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable { onClearStrokes() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "全消去",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = colors.statusReject
                    )
                }
            }
        }

        var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
                .background(colors.background, RoundedCornerShape(2.dp))
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                .onGloballyPositioned { canvasSize = it.size }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { motionEvent ->
                        val p = motionEvent.pressure.coerceIn(0.05f, 1.0f)
                        currentPressure = p
                        val action = motionEvent.actionMasked

                        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                            if (action == MotionEvent.ACTION_DOWN) {
                                currentStroke.clear()
                            }
                            val cw = canvasSize.width.toFloat()
                            val ch = canvasSize.height.toFloat()
                            if (cw > 10f && ch > 10f) {
                                val nx = (motionEvent.x / cw).coerceIn(0f, 1f)
                                val ny = (motionEvent.y / ch).coerceIn(0f, 1f)
                                val stroke = BrushStrokePoint(
                                    x = nx,
                                    y = ny,
                                    pressure = p,
                                    radius = currentRadius,
                                    flow = 0.8f,
                                    isEraser = isEraserMode
                                )
                                currentStroke.add(stroke)
                            }
                        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                            if (currentStroke.isNotEmpty()) {
                                onStrokeFinished(currentStroke.toList())
                                currentStroke.clear()
                            }
                        }
                        true
                    }
            ) {
                val cw = size.width
                val ch = size.height
                val drawPoint: (BrushStrokePoint) -> Unit = { pt ->
                    val effRadius = pt.radius * pt.pressure
                    val effAlpha = (pt.flow * pt.pressure).coerceIn(0.1f, 1.0f)
                    drawCircle(
                        color = if (pt.isEraser) Color(0xFF101012) else Color(0xFFFF8800).copy(alpha = effAlpha),
                        radius = effRadius,
                        center = Offset(pt.x * cw, pt.y * ch)
                    )
                }
                activeLayer?.brushStrokes?.forEach(drawPoint)
                currentStroke.forEach(drawPoint)
            }
        }
    }
}

@Composable
private fun AddMaskModal(
    onDismiss: () -> Unit,
    onAddMask: (MaskType) -> Unit
) {
    val colors = LightRumorTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .background(colors.surfaceElevated, RoundedCornerShape(4.dp))
                .border(1.dp, colors.accentAmber, RoundedCornerShape(4.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
                .clickable(enabled = false) {}
        ) {
            Text(
                text = "マスク種別を選択",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colors.accentAmber,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            MaskType.entries.forEach { maskType ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable { onAddMask(maskType) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = maskType.displayName,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "選択",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = colors.accentAmber
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}


