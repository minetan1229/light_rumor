package com.lightrumor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*
import kotlin.math.atan2

/**
 * CropRotateScreen: Professional Geometry, Transform, Crop & Horizon Auto-Leveling Instrument.
 * Features:
 * - Free & Fixed Aspect Ratios (including 65:24 Hasselblad XPan, Golden Ratio, 1:1, 4:5, 3:2, 16:9, 2:1)
 * - Composition Guide Overlays (Rule of Thirds, Golden Ratio, Spiral, Diagonals, Grid)
 * - 90-degree discrete rotations and horizontal/vertical flips
 * - ±45° fine straighten slider with 0.1° resolution
 * - Interactive Horizon Ruler Drag Auto-Leveling: dragging along a tilted horizon automatically
 *   computes the tilt vector and applies exact counter-rotation.
 * - Keystone perspective upright correction (Vertical, Horizontal, Barrel/Pincushion)
 * - Strict Anti-AI styling: Obsidian black chassis, titanium hairline accents, zero emojis
 */
@Composable
fun CropRotateScreen(
    params: DevelopmentParams,
    onParamsChange: (DevelopmentParams) -> Unit,
    onParamsChangeFinished: (() -> Unit)? = null,
    hapticManager: HapticManager? = null,
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    val geom = params.geometry

    // Interactive Horizon Ruler dragging state
    var isRulerActive by remember { mutableStateOf(false) }
    var rulerStart by remember { mutableStateOf(Offset.Zero) }
    var rulerCurrent by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // ---------------------------------------------------------------------
        // SECTION 1: ASPECT RATIO SELECTION
        // ---------------------------------------------------------------------
        Text(
            text = "アスペクト比",
            style = LightRumorTheme.typography.Header,
            color = colors.accentAmber,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AspectRatioMode.entries.forEach { mode ->
                val isSelected = geom.aspectRatio == mode
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
                            onParamsChange(params.copy(geometry = geom.copy(aspectRatio = mode)))
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label.uppercase(),
                        fontFamily = FontFamily.SansSerif,
                        color = if (isSelected) colors.accentAmber else colors.textSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ---------------------------------------------------------------------
        // SECTION 2: COMPOSITION GUIDE OVERLAYS
        // ---------------------------------------------------------------------
        Text(
            text = "構図ガイド",
            style = LightRumorTheme.typography.Header,
            color = colors.accentAmber,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompositionGuide.entries.forEach { guide ->
                val isSelected = geom.guide == guide
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) colors.surfaceElevated else colors.surface,
                            RoundedCornerShape(3.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) colors.accentCyan else colors.borderSubtle,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable {
                            hapticManager?.performDialTick()
                            onParamsChange(params.copy(geometry = geom.copy(guide = guide)))
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = guide.label.uppercase(),
                        fontFamily = FontFamily.SansSerif,
                        color = if (isSelected) colors.accentCyan else colors.textSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ---------------------------------------------------------------------
        // SECTION 3: ROTATE & FLIP CONTROLS
        // ---------------------------------------------------------------------
        Text(
            text = "回転・反転",
            style = LightRumorTheme.typography.Header,
            color = colors.accentAmber,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 90 CCW
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(colors.surfaceElevated, RoundedCornerShape(3.dp))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                    .clickable {
                        hapticManager?.performDialTick()
                        val newSteps = (geom.rotationSteps + 3) % 4
                        onParamsChange(params.copy(geometry = geom.copy(rotationSteps = newSteps)))
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "-90°",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 13.sp
                )
            }

            // 90 CW
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(colors.surfaceElevated, RoundedCornerShape(3.dp))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                    .clickable {
                        hapticManager?.performDialTick()
                        val newSteps = (geom.rotationSteps + 1) % 4
                        onParamsChange(params.copy(geometry = geom.copy(rotationSteps = newSteps)))
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+90°",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 13.sp
                )
            }

            // Flip H
            val isFlipH = geom.flipHorizontal
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isFlipH) colors.surfacePressed else colors.surfaceElevated,
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        1.dp,
                        if (isFlipH) colors.accentAmber else colors.borderSubtle,
                        RoundedCornerShape(3.dp)
                    )
                    .clickable {
                        hapticManager?.performDialTick()
                        onParamsChange(params.copy(geometry = geom.copy(flipHorizontal = !isFlipH)))
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "水平反転",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        color = if (isFlipH) colors.accentAmber else colors.textPrimary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 16.dp, height = 2.dp)
                            .background(if (isFlipH) colors.accentAmber else Color.Transparent)
                    )
                }
            }

            // Flip V
            val isFlipV = geom.flipVertical
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isFlipV) colors.surfacePressed else colors.surfaceElevated,
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        1.dp,
                        if (isFlipV) colors.accentAmber else colors.borderSubtle,
                        RoundedCornerShape(3.dp)
                    )
                    .clickable {
                        hapticManager?.performDialTick()
                        onParamsChange(params.copy(geometry = geom.copy(flipVertical = !isFlipV)))
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "垂直反転",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        color = if (isFlipV) colors.accentAmber else colors.textPrimary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 16.dp, height = 2.dp)
                            .background(if (isFlipV) colors.accentAmber else Color.Transparent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ---------------------------------------------------------------------
        // SECTION 4: STRAIGHTEN & HORIZON RULER DRAG AUTO-LEVELING
        // ---------------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "傾き補正 & 自動水平",
                style = LightRumorTheme.typography.Header,
                color = colors.accentAmber
            )

            // Horizon Ruler Drag Tool Button
            Box(
                modifier = Modifier
                    .background(
                        if (isRulerActive) colors.accentAmber else colors.surfaceElevated,
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        1.dp,
                        if (isRulerActive) colors.accentAmber else colors.borderStrong,
                        RoundedCornerShape(3.dp)
                    )
                    .clickable {
                        hapticManager?.performDialTick()
                        isRulerActive = !isRulerActive
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isRulerActive) "ルーラー待機中" else "水平ルーラー",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isRulerActive) Color.Black else colors.textPrimary
                )
            }
        }

        LightroomSlider(
            label = "傾き角度",
            value = geom.rotationDegrees,
            onValueChange = { onParamsChange(params.copy(geometry = geom.copy(rotationDegrees = it))) },
            onValueChangeFinished = onParamsChangeFinished,
            range = -45.0f..45.0f,
            defaultValue = 0f,
            unit = "deg",
            displayDecimals = 1,
            step = 0.1f,
            hapticManager = hapticManager
        )

        // Interactive Horizon Drag Gesture Surface (Visible when Ruler Tool is Active)
        if (isRulerActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .background(colors.surfaceElevated, RoundedCornerShape(3.dp))
                    .border(1.dp, colors.accentAmber, RoundedCornerShape(3.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                rulerStart = offset
                                rulerCurrent = offset
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                rulerCurrent += dragAmount
                            },
                            onDragEnd = {
                                var normDx = rulerCurrent.x - rulerStart.x
                                var normDy = rulerCurrent.y - rulerStart.y
                                if (normDx < 0f) {
                                    normDx = -normDx
                                    normDy = -normDy
                                }
                                if (normDx * normDx + normDy * normDy > 400f) { // Min drag length 20px
                                    val measuredTilt = Math.toDegrees(atan2(normDy.toDouble(), normDx.toDouble())).toFloat()
                                    val counterAngle = (-measuredTilt).coerceIn(-45.0f, 45.0f)
                                    hapticManager?.performZeroSnap()
                                    onParamsChange(params.copy(geometry = geom.copy(rotationDegrees = counterAngle)))
                                    onParamsChangeFinished?.invoke()
                                }
                                isRulerActive = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    if (rulerStart != Offset.Zero || rulerCurrent != Offset.Zero) {
                        drawLine(
                            color = colors.accentAmber,
                            start = rulerStart,
                            end = rulerCurrent,
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "傾いた水平線に沿ってドラッグ",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = colors.accentAmber
                    )
                    Text(
                        text = "指を離すと自動で水平角度に補正されます",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ---------------------------------------------------------------------
        // SECTION 5: PERSPECTIVE UPRIGHT & DISTORTION
        // ---------------------------------------------------------------------
        Text(
            text = "パース補正 & 歪み",
            style = LightRumorTheme.typography.Header,
            color = colors.accentAmber,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        LightroomSlider(
            label = "垂直台形補正",
            value = geom.perspectiveVertical,
            onValueChange = { onParamsChange(params.copy(geometry = geom.copy(perspectiveVertical = it))) },
            onValueChangeFinished = onParamsChangeFinished,
            range = -100f..100f,
            defaultValue = 0f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "水平台形補正",
            value = geom.perspectiveHorizontal,
            onValueChange = { onParamsChange(params.copy(geometry = geom.copy(perspectiveHorizontal = it))) },
            onValueChangeFinished = onParamsChangeFinished,
            range = -100f..100f,
            defaultValue = 0f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "レンズ歪み",
            value = geom.distortion,
            onValueChange = { onParamsChange(params.copy(geometry = geom.copy(distortion = it))) },
            onValueChangeFinished = onParamsChangeFinished,
            range = -100f..100f,
            defaultValue = 0f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Reset All Geometry Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .background(colors.surfaceElevated, RoundedCornerShape(3.dp))
                .border(1.dp, colors.borderStrong, RoundedCornerShape(3.dp))
                .clickable {
                    hapticManager?.performZeroSnap()
                    onParamsChange(params.copy(geometry = CropTransformParams()))
                    onParamsChangeFinished?.invoke()
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "クロップ & 変形をリセット",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                fontSize = 13.sp
            )
        }
    }
}

