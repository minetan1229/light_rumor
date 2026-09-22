package com.lightrumor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.lightrumor.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lightroom-Style Full-Width Horizontal Precision Slider.
 * - Full-width high-resolution track for maximum touch fidelity.
 * - Zero-delay real-time 120fps feedback without conflicting with parent vertical scroll.
 * - Double-tap header/value or zero-tick to reset to default.
 * - Long-press to open virtual precision dial.
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
            .padding(horizontal = 14.dp, vertical = 3.dp)
    ) {
        // 1. Header: Parameter Name (Left) and Value Readout (Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(defaultValue) {
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
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Non-zero LED status indicator
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(if (isNonZero) colors.accentAmber else colors.surfaceElevated, LightRumorShapes.SharpSquare)
                )

                Text(
                    text = label.uppercase(),
                    style = LightRumorTheme.typography.Label,
                    color = if (isNonZero) colors.textPrimary else colors.textSecondary
                )
            }

            Text(
                text = formattedValue,
                style = LightRumorTheme.typography.ValueReadout,
                color = if (isNonZero) colors.accentAmber else colors.textSecondary,
                modifier = Modifier.clickable {
                    if (isNonZero) {
                        currentHapticManager?.performZeroSnap()
                        currentOnValueChange(defaultValue)
                        currentOnFinished?.invoke()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 2. Full-Width Precision Slider Track
        val totalSpan = range.endInclusive - range.start
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
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
                        internalValue = currentValue
                    },
                    onDragStopped = {
                        currentOnFinished?.invoke()
                    }
                )
                .pointerInput(range, defaultValue) {
                    detectTapGestures(
                        onDoubleTap = {
                            currentHapticManager?.performZeroSnap()
                            internalValue = defaultValue
                            currentOnValueChange(defaultValue)
                            currentOnFinished?.invoke()
                        },
                        onLongPress = {
                            currentHapticManager?.performZeroSnap()
                            currentOnLongPressDial?.invoke()
                        },
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
                val trackHeight = 3.dp.toPx()

                // 1. Base background track
                drawRoundRect(
                    color = colors.sliderTrack,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(w, trackHeight),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                )

                // Normalized positions
                val zeroNorm = if (totalSpan > 0f) ((defaultValue - range.start) / totalSpan).coerceIn(0f, 1f) else 0.5f
                val zeroX = zeroNorm * w
                val valNorm = if (totalSpan > 0f) ((value - range.start) / totalSpan).coerceIn(0f, 1f) else 0.5f
                val valX = valNorm * w

                // 2. Active fill (from zero/default to current value)
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

                // 3. Center zero tick line (hairline titanium marker)
                drawLine(
                    color = colors.sliderZeroTick,
                    start = Offset(zeroX, centerY - 5.dp.toPx()),
                    end = Offset(zeroX, centerY + 5.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )

                // 4. Thumb indicator (sharp titanium knurled fader pin)
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
    }
}
