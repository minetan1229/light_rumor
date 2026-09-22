package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ColorWaveformView: Real-Time Cinema RGB Waveform Monitor & RGB Parade.
 * Inspired by Sony Venice and DaVinci Resolve scopes.
 * Real-time 60fps tracking responding to exposure, white balance, and tone curve adjustments.
 */
enum class WaveformSize { EXPANDED, NORMAL, MINIMIZED }

@Composable
fun ColorWaveformView(
    previewBitmap: Bitmap?,
    params: DevelopmentParams,
    waveformSize: WaveformSize = WaveformSize.NORMAL,
    onSizeChange: (WaveformSize) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors
    var mode by remember { mutableIntStateOf(0) } // 0: RGB Overlay, 1: RGB Parade, 2: Histogram
    var waveformBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderTrigger by remember { mutableIntStateOf(0) }

    // Recompute waveform texture whenever previewBitmap or development params change
    LaunchedEffect(previewBitmap, params, mode) {
        if (previewBitmap == null || previewBitmap.isRecycled) return@LaunchedEffect

        withContext(Dispatchers.Default) {
            try {
                val w = 512
                val h = 256
                val aspect = (previewBitmap.width.toFloat() / previewBitmap.height.coerceAtLeast(1).toFloat()).coerceIn(0.2f, 5.0f)
                val sampleW = 256
                val sampleH = (sampleW / aspect).toInt().coerceIn(128, 256)
                val reqSize = sampleW * sampleH

                val softBmp = Bitmap.createScaledBitmap(previewBitmap, sampleW, sampleH, true)
                val pixels = IntArray(reqSize)
                val byteBuf = ByteArray(reqSize * 4)

                softBmp.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
                if (softBmp != previewBitmap) {
                    softBmp.recycle()
                }

                // Simulate development exposure / kelvin shifts on preview buffer
                val expGain = Math.pow(2.0, params.exposureEV.toDouble()).toFloat()
                val rGain = if (params.kelvin > 5500f) 1.0f + (params.kelvin - 5500f) / 10000f else 1.0f
                val bGain = if (params.kelvin < 5500f) 1.0f + (5500f - params.kelvin) / 7000f else 1.0f

                for (i in 0 until reqSize) {
                    val p = pixels[i]
                    val r = (((p shr 16) and 0xFF) * expGain * rGain).toInt().coerceIn(0, 255)
                    val g = (((p shr 8) and 0xFF) * expGain).toInt().coerceIn(0, 255)
                    val b = ((p and 0xFF) * expGain * bGain).toInt().coerceIn(0, 255)

                    byteBuf[i * 4 + 0] = r.toByte()
                    byteBuf[i * 4 + 1] = g.toByte()
                    byteBuf[i * 4 + 2] = b.toByte()
                    byteBuf[i * 4 + 3] = (0xFF).toByte()
                }

                val wavePixels = LightRumorNativeEngine.computeWaveform(
                    rgbaBytes = byteBuf,
                    width = sampleW,
                    height = sampleH,
                    mode = mode,
                    waveW = w,
                    waveH = h
                )

                val existing = waveformBitmap
                val outBmp = if (existing != null && !existing.isRecycled && existing.width == w && existing.height == h && existing.isMutable) {
                    existing
                } else {
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                }
                outBmp.setPixels(wavePixels, 0, w, 0, 0, w, h)
                withContext(Dispatchers.Main) {
                    waveformBitmap = outBmp
                    renderTrigger++
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            waveformBitmap?.let { bmp ->
                if (!bmp.isRecycled) {
                    bmp.recycle()
                }
            }
            waveformBitmap = null
        }
    }

    // Minimized mode: just a small icon button
    if (waveformSize == WaveformSize.MINIMIZED) {
        Box(
            modifier = modifier
                .size(32.dp)
                .background(colors.surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
                .clickable { onSizeChange(WaveformSize.NORMAL) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "W",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colors.accentAmber
            )
        }
        return
    }

    val isExpanded = waveformSize == WaveformSize.EXPANDED

    Box(
        modifier = modifier
            .background(colors.surface.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
            .border(1.dp, colors.borderStrong, RoundedCornerShape(4.dp))
            .padding(6.dp)
    ) {
        Column {
            // Header Bar: Mode Toggles & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "WAVEFORM",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (mode == 0) "RGB OVERLAY" else if (mode == 1) "RGB PARADE" else "HISTOGRAM",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = colors.accentAmber
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScopeModeButton("OVR", active = mode == 0, onClick = { mode = 0 })
                    ScopeModeButton("PRD", active = mode == 1, onClick = { mode = 1 })
                    ScopeModeButton("HST", active = mode == 2, onClick = { mode = 2 })
                    // Expand: NORMAL -> EXPANDED
                    ScopeModeButton(
                        text = if (isExpanded) "[-]" else "[+]",
                        active = false,
                        onClick = {
                            onSizeChange(if (isExpanded) WaveformSize.NORMAL else WaveformSize.EXPANDED)
                        }
                    )
                    // Minimize: -> MINIMIZED
                    ScopeModeButton(
                        text = "[x]",
                        active = false,
                        onClick = { onSizeChange(WaveformSize.MINIMIZED) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Waveform Canvas / Image with IRE scale
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isExpanded) 200.dp else 100.dp)
                    .background(Color(0xFF0C0A0A), RoundedCornerShape(2.dp))
                    .clickable {
                        onSizeChange(if (isExpanded) WaveformSize.NORMAL else WaveformSize.EXPANDED)
                    }
            ) {
                val currentBmp = waveformBitmap
                if (currentBmp != null && !currentBmp.isRecycled) {
                    key(renderTrigger) {
                        Image(
                            bitmap = currentBmp.asImageBitmap(),
                            contentDescription = "RGB Waveform",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // IRE Scale HUD Readouts (Left side)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 6.dp, top = 3.dp, bottom = 3.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("100%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colors.textTertiary)
                    Text("70%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colors.accentAmber) // Skin
                    Text("50%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colors.textTertiary)
                    Text("18%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colors.textSecondary) // 18% Gray
                    Text("0%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun ScopeModeButton(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = LightRumorTheme.colors
    Box(
        modifier = Modifier
            .background(
                if (active) colors.accentAmber else colors.surfaceElevated,
                RoundedCornerShape(3.dp)
            )
            .border(
                1.dp,
                if (active) colors.accentAmber else colors.borderSubtle,
                RoundedCornerShape(3.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (active) Color.Black else colors.textPrimary
        )
    }
}

