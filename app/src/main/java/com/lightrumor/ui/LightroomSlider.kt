package com.lightrumor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lightroom-Style Horizontal Precision Slider.
 * 縦スクロール（親スクロールコンテナ）と干渉しない draggable(Orientation.Horizontal) 設計。
 * ドラッグ中は 60/120fps リアルタイム反映、離した時に onValueChangeFinished で1回だけ履歴保存。
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
    onValueChangeFinished: (() -> Unit)? = null,
    onLongPressDial: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var internalValue by remember(value) { mutableFloatStateOf(value) }
    var dragStartVal by remember { mutableFloatStateOf(value) }

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
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
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
                        currentOnFinished?.invoke()
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
                fontSize = 12.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedValue,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isNonZero) colors.accentAmber else colors.textSecondary
                )

                // Micro reset button if non-zero
                if (isNonZero) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(colors.surfaceElevated, RoundedCornerShape(2.dp))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                            .clickable {
                                hapticManager?.performZeroSnap()
                                onValueChange(defaultValue)
                                onValueChangeFinished?.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "×",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Main Track & Steppers Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nudge button: [-]
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(colors.surfaceElevated, RoundedCornerShape(3.dp))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                    .clickable {
                        val nextVal = (value - step).coerceIn(range.start, range.endInclusive)
                        hapticManager?.evaluateMovement(value, nextVal, range.start, range.endInclusive)
                        onValueChange(nextVal)
                        onValueChangeFinished?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("-", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Bipolar Horizontal Slider Track (Draggable without conflicting with vertical scroll)
            val totalSpan = range.endInclusive - range.start
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .onGloballyPositioned { coordinates ->
                        trackWidthPx = coordinates.size.width.toFloat()
                    }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            if (trackWidthPx > 0f) {
                                val deltaVal = (delta / trackWidthPx) * totalSpan
                                val newVal = (internalValue + deltaVal).coerceIn(range.start, range.endInclusive)
                                currentHapticManager?.evaluateMovement(internalValue, newVal, range.start, range.endInclusive)
                                internalValue = newVal
                                currentOnValueChange(newVal)
                            }
                        },
                        onDragStarted = {
                            dragStartVal = currentValue
                            internalValue = currentValue
                        },
                        onDragStopped = {
                            currentOnFinished?.invoke()
                        }
                    )
                    .pointerInput(range, defaultValue) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (trackWidthPx > 0f) {
                                    val valNorm = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                                    val tapValue = range.start + valNorm * totalSpan
                                    currentHapticManager?.evaluateMovement(currentValue, tapValue, range.start, range.endInclusive)
                                    internalValue = tapValue
                                    currentOnValueChange(tapValue)
                                    currentOnFinished?.invoke()
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val centerY = h / 2f
                    val trackHeight = 6.dp.toPx()

                    // Draw base background track
                    drawRoundRect(
                        color = colors.sliderTrack,
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = Size(w, trackHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )

                    // Normalized positions
                    val zeroNorm = if (totalSpan > 0f) ((defaultValue - range.start) / totalSpan).coerceIn(0f, 1f) else 0.5f
                    val zeroX = zeroNorm * w
                    val valNorm = if (totalSpan > 0f) ((value - range.start) / totalSpan).coerceIn(0f, 1f) else 0.5f
                    val valX = valNorm * w

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

                    // Draw center zero tick line (high-contrast tick)
                    drawLine(
                        color = colors.sliderZeroTick,
                        start = Offset(zeroX, centerY - 8.dp.toPx()),
                        end = Offset(zeroX, centerY + 8.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Draw thumb indicator (sharp vertical rectangular indicator)
                    val thumbW = 6.dp.toPx()
                    val thumbH = 22.dp.toPx()
                    drawRoundRect(
                        color = if (isNonZero) colors.accentAmber else colors.textPrimary,
                        topLeft = Offset(valX - thumbW / 2f, centerY - thumbH / 2f),
                        size = Size(thumbW, thumbH),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Nudge button: [+]
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(colors.surfaceElevated, RoundedCornerShape(3.dp))
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
                    .clickable {
                        val nextVal = (value + step).coerceIn(range.start, range.endInclusive)
                        hapticManager?.evaluateMovement(value, nextVal, range.start, range.endInclusive)
                        onValueChange(nextVal)
                        onValueChangeFinished?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
