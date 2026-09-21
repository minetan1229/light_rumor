package com.lightrumor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.ApexMotionSpecs
import com.lightrumor.ApexTheme
import com.lightrumor.HapticManager
import kotlin.math.*

/**
 * PrecisionDial: Virtual Knurled Rotary Dial.
 * Inspired by the machined aluminum knurled dials of Sigma fp / I-series and Minolta alpha cameras.
 * Provides ±0.01 micro-adjustments with mechanical haptic detents.
 */
@Composable
fun PrecisionDial(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    unit: String = "",
    fineStep: Float = 0.01f,
    hapticManager: HapticManager? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    var accumulatedAngle by remember { mutableFloatStateOf(0f) }
    var lastAngle by remember { mutableFloatStateOf(0f) }

    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = ApexMotionSpecs.MechanicalSpring,
        label = "DialValue"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = false) {} // block clicks from dismissing
                .background(colors.surfaceElevated, RoundedCornerShape(8.dp))
                .border(1.dp, colors.borderStrong, RoundedCornerShape(8.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterVertically
        ) {
            // Dial Title & Close
            Row(
                modifier = Modifier.width(260.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FINE DETENT DIAL: $label",
                    style = ApexTheme.typography.Header,
                    color = colors.textPrimary,
                    fontSize = 12.sp
                )
                Text(
                    text = "DONE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = colors.accentAmber,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Rotary Dial Visual Surface
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val center = Offset(110.dp.toPx(), 110.dp.toPx())
                                lastAngle = atan2(offset.y - center.y, offset.x - center.x)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val center = Offset(110.dp.toPx(), 110.dp.toPx())
                                val currentAngle = atan2(change.position.y - center.y, change.position.x - center.x)
                                var deltaAngle = currentAngle - lastAngle
                                if (deltaAngle > PI) deltaAngle -= (2 * PI).toFloat()
                                if (deltaAngle < -PI) deltaAngle += (2 * PI).toFloat()
                                lastAngle = currentAngle

                                accumulatedAngle += deltaAngle

                                // 1 degree (approx 0.017 rad) = 1 fineStep (0.01)
                                val radPerStep = 0.035f
                                val steps = (deltaAngle / radPerStep).toInt()
                                if (steps != 0) {
                                    val nextVal = (value + steps * fineStep).coerceIn(range.start, range.endInclusive)
                                    if (nextVal != value) {
                                        hapticManager?.evaluateMovement(value, nextVal, range.start, range.endInclusive, zeroSnapTolerance = 0.01f)
                                        onValueChange(nextVal)
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f - 8.dp.toPx()

                    // Outer chassis ring (Minolta titanium bevel)
                    drawCircle(
                        color = colors.borderStrong,
                        radius = radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Machined knurled detents (48 radial teeth)
                    val numTicks = 48
                    for (i in 0 until numTicks) {
                        val angle = (i * 2 * PI / numTicks) + accumulatedAngle
                        val tickLen = if (i % 4 == 0) 10.dp.toPx() else 5.dp.toPx()
                        val tickColor = if (i % 4 == 0) colors.textPrimary else colors.textTertiary
                        val start = Offset(
                            center.x + (radius - tickLen) * cos(angle).toFloat(),
                            center.y + (radius - tickLen) * sin(angle).toFloat()
                        )
                        val end = Offset(
                            center.x + (radius - 2.dp.toPx()) * cos(angle).toFloat(),
                            center.y + (radius - 2.dp.toPx()) * sin(angle).toFloat()
                        )
                        drawLine(
                            color = tickColor,
                            start = start,
                            end = end,
                            strokeWidth = if (i % 4 == 0) 2.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Square
                        )
                    }

                    // Top indicator index needle (Sony Cine Amber)
                    val needleTop = Offset(center.x, center.y - radius - 4.dp.toPx())
                    val needleBottom = Offset(center.x, center.y - radius + 12.dp.toPx())
                    drawLine(
                        color = colors.accentAmber,
                        start = needleTop,
                        end = needleBottom,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Center readout display
                Column(horizontalAlignment = Alignment.CenterVertically) {
                    val sign = if (value > 0f) "+" else ""
                    Text(
                        text = String.format("%s%.2f", sign, animatedValue),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = unit.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = colors.accentAmber
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "±0.01 STEP",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Micro Steppers (+/- 0.01 and +/- 0.10)
            Row(
                modifier = Modifier.width(220.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FineStepButton("-0.10", onClick = {
                    val nv = (value - 0.10f).coerceIn(range.start, range.endInclusive)
                    hapticManager?.performDialTick()
                    onValueChange(nv)
                })
                FineStepButton("-0.01", onClick = {
                    val nv = (value - 0.01f).coerceIn(range.start, range.endInclusive)
                    hapticManager?.performDialTick()
                    onValueChange(nv)
                })
                FineStepButton("+0.01", onClick = {
                    val nv = (value + 0.01f).coerceIn(range.start, range.endInclusive)
                    hapticManager?.performDialTick()
                    onValueChange(nv)
                })
                FineStepButton("+0.10", onClick = {
                    val nv = (value + 0.10f).coerceIn(range.start, range.endInclusive)
                    hapticManager?.performDialTick()
                    onValueChange(nv)
                })
            }
        }
    }
}

@Composable
private fun FineStepButton(text: String, onClick: () -> Unit) {
    val colors = ApexTheme.colors
    Box(
        modifier = Modifier
            .background(colors.surfacePressed, RoundedCornerShape(3.dp))
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = colors.textPrimary
        )
    }
}

