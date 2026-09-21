package com.lightrumor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lightroom-Style Horizontal Precision Slider.
 * The undisputed gold standard for professional photographer photo adjustments.
 * Features:
 * - Bipolar centered 0-point track with distinct zero indicator
 * - Direct 1-to-1 tactile drag response
 * - Double-tap anywhere on the slider row to instantly reset to default (0.0)
 * - Zero-point snap with mechanical haptic click
 * - Fine nudge [-] / [+] stepper buttons for single-handed thumb operation
 * - Long-press to launch virtual precision dial
 */
@Composable
fun LightroomSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float = 0f,
    unit: String = "",
    displayDecimals: Int = 2,
    step: Float = 0.05f,
    hapticManager: HapticManager? = null,
    onLongPressDial: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var previousValue by remember { mutableFloatStateOf(value) }

    // Format value with sign and units e.g. "+0.75 EV" or "5600 K"
    val formattedValue = remember(value, unit, displayDecimals) {
        val sign = if (value > 0.001f && defaultValue == 0f) "+" else ""
        when (displayDecimals) {
            0 -> "$sign${value.roundToInt()} $unit"
            1 -> String.format("%s%.1f %s", sign, value, unit)
            else -> String.format("%s%.2f %s", sign, value, unit)
        }.trim()
    }

    val isNonZero = abs(value - defaultValue) > 0.001f

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentHapticManager by rememberUpdatedState(hapticManager)
    val currentOnLongPressDial by rememberUpdatedState(onLongPressDial)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        currentHapticManager?.performZeroSnap()
                        currentOnValueChange(defaultValue)
                    },
                    onLongPress = {
                        currentHapticManager?.performZeroSnap()
                        currentOnLongPressDial?.invoke()
                    }
                )
            }
    ) {
        // 1. Header: Parameter Name (Left) and Value Readout (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                style = LightRumorTheme.typography.Label,
                color = if (isNonZero) colors.textPrimary else colors.textSecondary,
                fontSize = 11.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedValue,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isNonZero) colors.accentAmber else colors.textSecondary
                )

                // Micro reset button if non-zero
                if (isNonZero) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(colors.borderSubtle, RoundedCornerShape(2.dp))
                            .clickable {
                                hapticManager?.performZeroSnap()
                                onValueChange(defaultValue)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "×",
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. Main Track & Steppers Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nudge button: [-]
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(colors.surfaceElevated, RoundedCornerShape(2.dp))
                    .clickable {
                        val nextVal = (value - step).coerceIn(range.start, range.endInclusive)
                        hapticManager?.evaluateMovement(value, nextVal, range.start, range.endInclusive)
                        onValueChange(nextVal)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("-", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Bipolar Horizontal Slider Track
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .pointerInput(range, defaultValue) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (trackWidthPx > 0f) {
                                    val totalRange = range.endInclusive - range.start
                                    val valNorm = offset.x / trackWidthPx
                                    val tapValue = range.start + valNorm * totalRange
                                    val zeroSnapThreshold = totalRange * 0.02f
                                    val finalVal = if (abs(tapValue - defaultValue) < zeroSnapThreshold) {
                                        defaultValue
                                    } else {
                                        tapValue
                                    }
                                    currentHapticManager?.evaluateMovement(currentValue, finalVal, range.start, range.endInclusive)
                                    previousValue = finalVal
                                    currentOnValueChange(finalVal)
                                }
                            }
                        )
                    }
                    .pointerInput(range, defaultValue) {
                        var dragStartValue = 0f
                        var cumulativeDrag = 0f

                        detectDragGestures(
                            onDragStart = {
                                dragStartValue = currentValue
                                cumulativeDrag = 0f
                            },
                            onDragEnd = {
                                // ゼロスナップ判定はドラッグ中は無効化し、onDragEnd時のみ適用
                                val totalRange = range.endInclusive - range.start
                                val zeroSnapThreshold = totalRange * 0.02f
                                if (abs(currentValue - defaultValue) < zeroSnapThreshold) {
                                    currentHapticManager?.performZeroSnap()
                                    currentOnValueChange(defaultValue)
                                }
                            },
                            onDragCancel = {
                                val totalRange = range.endInclusive - range.start
                                val zeroSnapThreshold = totalRange * 0.02f
                                if (abs(currentValue - defaultValue) < zeroSnapThreshold) {
                                    currentOnValueChange(defaultValue)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (trackWidthPx > 0f) {
                                    cumulativeDrag += dragAmount.x
                                    val totalRange = range.endInclusive - range.start
                                    val deltaVal = (cumulativeDrag / trackWidthPx) * totalRange
                                    val candidate = (dragStartValue + deltaVal).coerceIn(range.start, range.endInclusive)

                                    currentHapticManager?.evaluateMovement(previousValue, candidate, range.start, range.endInclusive)
                                    previousValue = candidate
                                    currentOnValueChange(candidate)
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    trackWidthPx = size.width
                    val h = size.height
                    val centerY = h / 2f
                    val trackHeight = 4.dp.toPx()
                    val totalRange = range.endInclusive - range.start

                    // Draw base background track
                    drawRoundRect(
                        color = colors.sliderTrack,
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = Size(size.width, trackHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )

                    // Normalized positions
                    val zeroNorm = ((defaultValue - range.start) / totalRange).coerceIn(0f, 1f)
                    val zeroX = zeroNorm * size.width
                    val valNorm = ((value - range.start) / totalRange).coerceIn(0f, 1f)
                    val valX = valNorm * size.width

                    // Draw active fill (from center zero to current value)
                    val fillLeft = kotlin.math.min(zeroX, valX)
                    val fillWidth = abs(valX - zeroX)
                    if (fillWidth > 0.5f) {
                        drawRoundRect(
                            color = colors.accentAmber,
                            topLeft = Offset(fillLeft, centerY - trackHeight / 2f),
                            size = Size(fillWidth, trackHeight),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                        )
                    }

                    // Draw center zero tick line
                    drawLine(
                        color = colors.sliderZeroTick,
                        start = Offset(zeroX, centerY - 6.dp.toPx()),
                        end = Offset(zeroX, centerY + 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Draw thumb indicator (sharp vertical rectangular indicator - Minolta/Sony style)
                    val thumbW = 4.dp.toPx()
                    val thumbH = 16.dp.toPx()
                    drawRoundRect(
                        color = if (isNonZero) colors.accentAmber else colors.textPrimary,
                        topLeft = Offset(valX - thumbW / 2f, centerY - thumbH / 2f),
                        size = Size(thumbW, thumbH),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Nudge button: [+]
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(colors.surfaceElevated, RoundedCornerShape(2.dp))
                    .clickable {
                        val nextVal = (value + step).coerceIn(range.start, range.endInclusive)
                        hapticManager?.evaluateMovement(value, nextVal, range.start, range.endInclusive)
                        onValueChange(nextVal)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

