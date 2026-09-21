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
import com.lightrumor.ApexNativeEngine
import com.lightrumor.ApexTheme
import com.lightrumor.DevelopmentParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ColorWaveformView: Real-Time Cinema RGB Waveform Monitor & RGB Parade.
 * Inspired by Sony Venice and DaVinci Resolve scopes.
 * Real-time 60fps tracking responding to exposure, white balance, and tone curve adjustments.
 */
@Composable
fun ColorWaveformView(
    previewBitmap: Bitmap?,
    params: DevelopmentParams,
    isExpanded: Boolean = false,
    onToggleExpanded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    var mode by remember { mutableIntStateOf(0) } // 0: RGB Overlay, 1: RGB Parade, 2: Histogram
    var waveformBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Recompute waveform texture whenever previewBitmap or development params change
    LaunchedEffect(previewBitmap, params.exposureEV, params.kelvin, params.contrast, params.highlights, params.shadows, mode) {
        if (previewBitmap == null) return@LaunchedEffect

        withContext(Dispatchers.Default) {
            val w = 512
            val h = 256
            val srcW = previewBitmap.width
            val srcH = previewBitmap.height

            // Extract RGBA bytes
            val pixels = IntArray(srcW * srcH)
            previewBitmap.getPixels(pixels, 0, srcW, 0, 0, srcW, srcH)
            val byteBuf = ByteArray(srcW * srcH * 4)

            // Simulate development exposure / kelvin shifts on preview buffer
            val expGain = Math.pow(2.0, params.exposureEV.toDouble()).toFloat()
            val rGain = if (params.kelvin > 5500f) 1.0f + (params.kelvin - 5500f) / 10000f else 1.0f
            val bGain = if (params.kelvin < 5500f) 1.0f + (5500f - params.kelvin) / 7000f else 1.0f

            for (i in 0 until (srcW * srcH)) {
                val p = pixels[i]
                var r = (((p shr 16) and 0xFF) * expGain * rGain).toInt().coerceIn(0, 255)
                var g = (((p shr 8) and 0xFF) * expGain).toInt().coerceIn(0, 255)
                var b = ((p and 0xFF) * expGain * bGain).toInt().coerceIn(0, 255)

                byteBuf[i * 4 + 0] = r.toByte()
                byteBuf[i * 4 + 1] = g.toByte()
                byteBuf[i * 4 + 2] = b.toByte()
                byteBuf[i * 4 + 3] = (0xFF).toByte()
            }

            val wavePixels = ApexNativeEngine.computeWaveform(
                rgbaBytes = byteBuf,
                width = srcW,
                height = srcH,
                mode = mode,
                waveW = w,
                waveH = h
            )

            val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            outBmp.setPixels(wavePixels, 0, w, 0, 0, w, h)
            waveformBitmap = outBmp
        }
    }

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
                        fontSize = 10.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (mode == 0) "RGB OVERLAY" else if (mode == 1) "RGB PARADE" else "HISTOGRAM",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = colors.accentAmber
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScopeModeButton("OVR", active = mode == 0, onClick = { mode = 0 })
                    ScopeModeButton("PRD", active = mode == 1, onClick = { mode = 1 })
                    ScopeModeButton("HST", active = mode == 2, onClick = { mode = 2 })
                    ScopeModeButton(
                        text = if (isExpanded) "[-] " else "[+]",
                        active = false,
                        onClick = onToggleExpanded
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Waveform Canvas / Image with IRE scale
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isExpanded) 180.dp else 88.dp)
                    .background(Color(0xFF0C0A0A), RoundedCornerShape(2.dp))
                    .clickable { onToggleExpanded() }
            ) {
                waveformBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "RGB Waveform",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // IRE Scale HUD Readouts (Left side)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("100%", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = colors.textTertiary)
                    Text("70%", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = colors.accentAmber) // Skin
                    Text("50%", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = colors.textTertiary)
                    Text("18%", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = colors.textSecondary) // 18% Gray
                    Text("0%", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun ScopeModeButton(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = ApexTheme.colors
    Box(
        modifier = Modifier
            .background(
                if (active) colors.accentAmber else colors.surfacePressed,
                RoundedCornerShape(2.dp)
            )
            .border(
                1.dp,
                if (active) colors.accentAmber else colors.borderSubtle,
                RoundedCornerShape(2.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 5.dp, vertical = 2.dp),
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

