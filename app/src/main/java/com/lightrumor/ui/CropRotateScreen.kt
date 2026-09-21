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
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // ---------------------------------------------------------------------
        // SECTION 1: ASPECT RATIO SELECTION
        // ---------------------------------------------------------------------
        Text(
            text = "ASPECT RATIO",
            style = LightRumorTheme.typography.Header,
            color = colors.accentAmber,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AspectRatioMode.values().forEach { mode ->
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
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label.uppercase(),
                        style = LightRumorTheme.typography.Tab,
                        color = if (isSelected) colors.accentAmber else colors.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ---------------------------------------------------------------------
        // SECTION 2: COMPOSITION GUIDE OVERLAYS
        // ---------------------------------------------------------------------
        Text(
            text = "COMPOSITION GUIDE OVERLAYS",
            style = LightRumorTheme.typography.Header,
            color = colors.accentAmber,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompositionGuide.values().forEach { guide ->
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
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = guide.label.uppercase(),
                        style = LightRumorTheme.typography.Tab,
                        color = if (isSelected) colors.accentCyan else colors.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ---------------------------------------------------------------------
        // SECTION 3: ROTATE & FLIP CONTROLS
        // ---------------------------------------------------------------------
        Text(
            text = "DISCRETE ORIENTATION",
            style = LightRumorTheme.typography.Header,
            color = colors.accentAmber,
            fontSize = 11.sp,
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
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ROTATE -90 DEG",
                    style = LightRumorTheme.typography.Tab,
                    color = colors.textPrimary,
                    fontSize = 10.sp
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
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ROTATE +90 DEG",
                    style = LightRumorTheme.typography.Tab,
                    color = colors.textPrimary,
                    fontSize = 10.sp
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
                Text(
                    text = if (isFlipH) "FLIP H [ON]" else "FLIP H",
                    style = LightRumorTheme.typography.Tab,
                    color = if (isFlipH) colors.accentAmber else colors.textPrimary,
                    fontSize = 10.sp
                )
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
                Text(
                    text = if (isFlipV) "FLIP V [ON]" else "FLIP V",
                    style = LightRumorTheme.typography.Tab,
                    color = if (isFlipV) colors.accentAmber else colors.textPrimary,
                    fontSize = 10.sp
                )
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
                text = "STRAIGHTEN & AUTO-HORIZON",
                style = LightRumorTheme.typography.Header,
                color = colors.accentAmber,
                fontSize = 11.sp
            )

            // Horizon Ruler Drag Tool Button
            Box(
                modifier = Modifier
                    .background(
                        if (isRulerActive) colors.accentAmber else colors.surfaceElevated,
                        RoundedCornerShape(2.dp)
                    )
                    .border(
                        1.dp,
                        if (isRulerActive) colors.accentAmber else colors.borderStrong,
                        RoundedCornerShape(2.dp)
                    )
                    .clickable {
                        hapticManager?.performDialTick()
                        isRulerActive = !isRulerActive
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isRulerActive) "RULER ACTIVE [DRAG HORIZON]" else "HORIZON RULER",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = if (isRulerActive) Color.Black else colors.textPrimary
                )
            }
        }

        LightroomSlider(
            label = "Straighten Angle",
            value = geom.rotationDegrees,
            onValueChange = { onParamsChange(params.copy(geometry = geom.copy(rotationDegrees = it))) },
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
                                val dx = rulerCurrent.x - rulerStart.x
                                val dy = rulerCurrent.y - rulerStart.y
                                if (dx * dx + dy * dy > 400f) { // Min drag length 20px
                                    val measuredTilt = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    val counterAngle = -measuredTilt
                                    hapticManager?.performZeroSnap()
                                    onParamsChange(params.copy(geometry = geom.copy(rotationDegrees = counterAngle)))
                                }
                                isRulerActive = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "DRAG A LINE ALONG THE TILTED HORIZON",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = colors.accentAmber
                    )
                    Text(
                        text = "RELEASE TO AUTO-COUNTER-ROTATE AND LEVEL IMAGE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
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
            text = "PERSPECTIVE UPRIGHT & DISTORTION",
            style = LightRumorTheme.typography.Header,
            color = colors.accentAmber,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        LightroomSlider(
            label = "Vertical Keystone",
            value = geom.perspectiveVertical,
            onValueChange = { onParamsChange(params.copy(geometry = geom.copy(perspectiveVertical = it))) },
            range = -100f..100f,
            defaultValue = 0f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "Horizontal Keystone",
            value = geom.perspectiveHorizontal,
            onValueChange = { onParamsChange(params.copy(geometry = geom.copy(perspectiveHorizontal = it))) },
            range = -100f..100f,
            defaultValue = 0f,
            unit = "",
            displayDecimals = 0,
            step = 1f,
            hapticManager = hapticManager
        )

        LightroomSlider(
            label = "Lens Distortion",
            value = geom.distortion,
            onValueChange = { onParamsChange(params.copy(geometry = geom.copy(distortion = it))) },
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
                }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "RESET CROP & GEOMETRY TO DEFAULT",
                style = LightRumorTheme.typography.Tab,
                color = colors.textSecondary,
                fontSize = 10.sp
            )
        }
    }
}

