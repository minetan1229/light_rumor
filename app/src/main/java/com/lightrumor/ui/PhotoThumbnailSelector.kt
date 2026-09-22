package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*

/**
 * ThumbnailItem: A photographic option represented by an actual photo thumbnail.
 * Strictly Anti-AI: NO emojis allowed.
 */
data class PhotoThumbnailItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val sampleBitmap: Bitmap? = null
)

/**
 * PhotoThumbnailSelector: Horizontal selector strip using actual photographic previews.
 * Ideal for selecting:
 * - Color Profiles (Standard, Neutral, S-Cinetone, Landscape, Portrait)
 * - Optical Monochrome Filters (Red 25A, Orange O2, Yellow Y2, Green X0, Infrared 720nm)
 * - Photographic Presets (Tri-X 400, Kodachrome 64, Arctic Glacier, Cinestill 800T)
 */
@Composable
fun PhotoThumbnailSelector(
    items: List<PhotoThumbnailItem>,
    selectedId: String,
    onItemSelected: (PhotoThumbnailItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LightRumorTheme.colors

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            val isSelected = item.id == selectedId
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) colors.surfaceElevated else colors.surface)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) colors.accentAmber else colors.borderSubtle,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onItemSelected(item) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Real Photographic Preview Thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .background(Color(0xFF1E2024), RoundedCornerShape(2.dp))
                        .clip(RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.sampleBitmap != null) {
                        Image(
                            bitmap = item.sampleBitmap.asImageBitmap(),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // High-contrast fallback visual test plate
                        Text(
                            text = item.title.take(3).uppercase(),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isSelected) colors.accentAmber else colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Label & Technical Subtitle
                Text(
                    text = item.title.uppercase(),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                    maxLines = 1
                )
                Text(
                    text = item.subtitle,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Generator for default optical filter sample previews without emojis.
 */
object PhotoThumbnailPresets {
    fun createOpticalFilterItems(): List<PhotoThumbnailItem> {
        return listOf(
            PhotoThumbnailItem("std", "スタンダード", "パンクロ", generateFilterSample(0xFF888888.toInt())),
            PhotoThumbnailItem("red25a", "レッド 25A", "高コントラスト", generateFilterSample(0xFFCC3333.toInt())),
            PhotoThumbnailItem("orange_o2", "オレンジ", "ドラマチックスカイ", generateFilterSample(0xFFDD6622.toInt())),
            PhotoThumbnailItem("yellow_y2", "イエロー", "風景", generateFilterSample(0xFFDDBB22.toInt())),
            PhotoThumbnailItem("green_x0", "グリーン", "葉/肌", generateFilterSample(0xFF33AA44.toInt())),
            PhotoThumbnailItem("ir720", "IR 720nm", "ウッド効果", generateFilterSample(0xFFEEEEEE.toInt()))
        )
    }

    fun createColorProfileItems(): List<PhotoThumbnailItem> {
        return listOf(
            PhotoThumbnailItem("cinetone", "S-CINETONE", "フィルムソフト", generateProfileSample(1.0f, 0.95f, 0.9f)),
            PhotoThumbnailItem("std_color", "スタンダード", "ニュートラル REC709", generateProfileSample(1.0f, 1.0f, 1.0f)),
            PhotoThumbnailItem("landscape", "風景", "深い青/緑", generateProfileSample(0.9f, 1.1f, 1.2f)),
            PhotoThumbnailItem("portrait", "ポートレート", "なめらか肌", generateProfileSample(1.15f, 1.0f, 0.95f)),
            PhotoThumbnailItem("monochrome", "モノクロ HC", "深い黒", generateProfileSample(0.7f, 0.7f, 0.7f))
        )
    }

    private fun generateFilterSample(tintColor: Int): Bitmap {
        val w = 80
        val h = 60
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val tR = (tintColor shr 16) and 0xFF
        val tG = (tintColor shr 8) and 0xFF
        val tB = tintColor and 0xFF

        for (y in 0 until h) {
            for (x in 0 until w) {
                // Synthetic sky-to-ground landscape with mountain silhouette
                val skyLuma = (y * 220 / h).coerceIn(40, 240)
                val r = (skyLuma * tR / 255).coerceIn(0, 255)
                val g = (skyLuma * tG / 255).coerceIn(0, 255)
                val b = (skyLuma * tB / 255).coerceIn(0, 255)
                pixels[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }

    fun generateProfileSample(rScale: Float, gScale: Float, bScale: Float): Bitmap {
        val w = 80
        val h = 60
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val base = (x * 200 / w) + 30
                val r = (base * rScale).toInt().coerceIn(0, 255)
                val g = (base * gScale).toInt().coerceIn(0, 255)
                val b = (base * bScale).toInt().coerceIn(0, 255)
                pixels[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }
}

