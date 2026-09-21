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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class CompareMode {
    Off,
    SplitVertical,   // Left = Before, Right = After
    SplitHorizontal, // Top = Before, Bottom = After
    SideBySide       // 2-Up Side by Side
}

/**
 * BeforeAfterOverlay: 4-Way Professional Comparison Engine.
 * 1. Long-press hold toggle (hold to reveal original)
 * 2. Draggable Vertical Split bar
 * 3. Draggable Horizontal Split bar
 * 4. Side-by-Side dual view
 */
@Composable
fun BeforeAfterOverlay(
    originalBitmap: Bitmap?,
    developedBitmap: Bitmap?,
    compareMode: CompareMode,
    onCompareModeChange: (CompareMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    var isHoldBefore by remember { mutableStateOf(false) }
    var splitFractionX by remember { mutableFloatStateOf(0.5f) }
    var splitFractionY by remember { mutableFloatStateOf(0.5f) }

    Box(
        modifier = modifier
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
    ) {
        val activeBitmap = if (isHoldBefore) originalBitmap else developedBitmap

        when (compareMode) {
            CompareMode.Off -> {
                // Standard single view with hold-to-compare support
                activeBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Photo Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            CompareMode.SplitVertical -> {
                // Vertical Split: Left = Before, Right = After
                Box(modifier = Modifier.fillMaxSize()) {
                    // Base: After (Developed)
                    developedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "After",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top layer: Before (clipped to left of splitFractionX)
                    originalBitmap?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(FractionalRectShape(rightFraction = splitFractionX, bottomFraction = 1f))
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Before",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Draggable Vertical Divider Bar
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.08f)
                            .align(Alignment.CenterStart)
                            .offset(x = 0.dp) // adjusted via pointerInput
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // Update splitFractionX based on drag
                                    val deltaFrac = dragAmount.x / 1000f
                                    splitFractionX = (splitFractionX + deltaFrac).coerceIn(0.05f, 0.95f)
                                }
                            }
                    )

                    // Draw vertical divider line & HUD
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dividerX = size.width * splitFractionX
                        drawLine(
                            color = colors.borderStrong,
                            start = Offset(dividerX, 0f),
                            end = Offset(dividerX, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                        // Grip handle in center
                        drawCircle(
                            color = colors.accentAmber,
                            radius = 6.dp.toPx(),
                            center = Offset(dividerX, size.height / 2f)
                        )
                    }

                    // Micro labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("BEFORE (ORIGINAL)", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = colors.textSecondary)
                        Text("AFTER (DEVELOPED)", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = colors.accentAmber)
                    }
                }
            }

            CompareMode.SplitHorizontal -> {
                // Horizontal Split: Top = Before, Bottom = After
                Box(modifier = Modifier.fillMaxSize()) {
                    developedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "After",
                            contentScale = ContentScale.Fit,
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
                                contentDescription = "Before",
                                contentScale = ContentScale.Fit,
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
                        drawCircle(
                            color = colors.accentAmber,
                            radius = 6.dp.toPx(),
                            center = Offset(size.width / 2f, dividerY)
                        )
                    }
                }
            }

            CompareMode.SideBySide -> {
                // 2-Up Side by Side
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(1.dp, colors.borderSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        originalBitmap?.let { bmp ->
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Before", contentScale = ContentScale.Fit)
                        }
                        Text(
                            text = "BEFORE",
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
                        developedBitmap?.let { bmp ->
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "After", contentScale = ContentScale.Fit)
                        }
                        Text(
                            text = "AFTER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = colors.accentAmber,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
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
                    text = "ORIGINAL RAW (HOLDING)",
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
            CompareTabButton("OFF", active = compareMode == CompareMode.Off) {
                onCompareModeChange(CompareMode.Off)
            }
            CompareTabButton("SPLIT", active = compareMode == CompareMode.SplitVertical) {
                onCompareModeChange(CompareMode.SplitVertical)
            }
            CompareTabButton("2-UP", active = compareMode == CompareMode.SideBySide) {
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

