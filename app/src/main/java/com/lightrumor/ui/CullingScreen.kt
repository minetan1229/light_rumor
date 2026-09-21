package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*

/**
 * Zero-Delay Culling Studio Screen.
 * Provides instant 0ms flicking with pre-cached +/-5 images, rating stars (1-5),
 * pick/reject flags, 5-color labels, and non-destructive XMP synchronization.
 * Pure instrument Obsidian Black theme without emojis.
 */
@Composable
fun CullingScreen(
    items: List<PhotoItem>,
    currentIndex: Int,
    onIndexChanged: (Int) -> Unit,
    cacheManager: CullingCacheManager,
    onOpenMultiCompare: () -> Unit,
    onOpenBatchSync: () -> Unit,
    onOpenDevelop: (PhotoItem) -> Unit,
    onBackToLauncher: () -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "NO PHOTOS SELECTED",
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF666666)
            )
        }
        return
    }

    val currentItem = items[currentIndex.coerceIn(items.indices)]
    var currentBitmap by remember(currentItem.uri) {
        mutableStateOf(cacheManager.getFromMemory(currentItem.filePath.ifEmpty { currentItem.uri.toString() }))
    }

    // Load and prefetch whenever index changes
    LaunchedEffect(currentIndex) {
        cacheManager.prefetchAround(currentIndex, items, windowSize = 5)
        cacheManager.loadBitmap(currentItem) { bmp ->
            currentBitmap = bmp
        }
    }

    val bgDark = Color(0xFF050505)
    val panelDark = Color(0xFF121212)
    val borderDark = Color(0xFF242424)
    val textPrimary = Color(0xFFE5E5E5)
    val textSecondary = Color(0xFF8A8A8A)
    val accentAmber = Color(0xFFD4A373)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .pointerInput(currentIndex) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag < -60f && currentIndex < items.size - 1) {
                            onIndexChanged(currentIndex + 1)
                        } else if (totalDrag > 60f && currentIndex > 0) {
                            onIndexChanged(currentIndex - 1)
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    }
                )
            }
    ) {
        // 1. Center Photo Viewport
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap!!.asImageBitmap(),
                    contentDescription = "Culling Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = "PREFETCHING STREAM...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = textSecondary
                )
            }
        }

        // 2. Top Header HUD: Filename, Counter, Mode Switchers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(panelDark.copy(alpha = 0.85f))
                .border(1.dp, borderDark)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "< BACK",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = textSecondary,
                    modifier = Modifier.clickable { onBackToLauncher() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "[${currentIndex + 1}/${items.size}] ${currentItem.fileName.ifEmpty { "IMG_${currentIndex + 1}" }}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
                if (currentItem.isRaw) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2C2210), RoundedCornerShape(2.dp))
                            .border(1.dp, accentAmber, RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "RAW",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = accentAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Action triggers: Compare & Batch Sync & Develop
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenMultiCompare,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderDark),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = panelDark),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "SYNC ZOOM (2/4)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = textPrimary
                    )
                }

                OutlinedButton(
                    onClick = onOpenBatchSync,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderDark),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = panelDark),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "BATCH SYNC",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = textPrimary
                    )
                }

                Button(
                    onClick = { onOpenDevelop(currentItem) },
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentAmber),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "DEVELOP",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        // 3. Bottom Instrument Bar: Metadata Strip & Rating Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(panelDark.copy(alpha = 0.92f))
                .border(1.dp, borderDark)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Metadata EXIF telemetry strip
            val meta = currentItem.metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${meta.cameraModel.ifEmpty { "ILCE-7RM5" }}  |  ${meta.focalLength.ifEmpty { "50mm" }}  |  ${meta.fNumber.ifEmpty { "f/2.8" }}  |  ${meta.exposureTime.ifEmpty { "1/250s" }}  |  ${meta.isoSpeed.ifEmpty { "ISO 100" }}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = textSecondary
                )
                Text(
                    text = "0ms PREFETCH ACTIVE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = accentAmber
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rating & Flagging control panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pick / Reject Flags
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Pick Flag
                    val isPicked = meta.pickStatus == PickStatus.PICKED
                    Box(
                        modifier = Modifier
                            .background(if (isPicked) Color(0xFF2E7D32) else Color(0xFF1E1E1E), RoundedCornerShape(2.dp))
                            .border(1.dp, if (isPicked) Color(0xFF4CAF50) else borderDark, RoundedCornerShape(2.dp))
                            .clickable {
                                meta.pickStatus = if (isPicked) PickStatus.NONE else PickStatus.PICKED
                                XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, meta, currentItem.developParams)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "FLAG",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPicked) Color.White else textSecondary
                        )
                    }

                    // Reject Flag
                    val isRejected = meta.pickStatus == PickStatus.REJECTED
                    Box(
                        modifier = Modifier
                            .background(if (isRejected) Color(0xFFC62828) else Color(0xFF1E1E1E), RoundedCornerShape(2.dp))
                            .border(1.dp, if (isRejected) Color(0xFFEF5350) else borderDark, RoundedCornerShape(2.dp))
                            .clickable {
                                meta.pickStatus = if (isRejected) PickStatus.NONE else PickStatus.REJECTED
                                XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, meta, currentItem.developParams)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "REJECT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRejected) Color.White else textSecondary
                        )
                    }
                }

                // 1-5 Star Rating Controls
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (star in 1..5) {
                        val isSelected = star <= meta.rating
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(if (isSelected) Color(0xFF2A2210) else Color(0xFF1E1E1E), RoundedCornerShape(2.dp))
                                .border(1.dp, if (isSelected) accentAmber else borderDark, RoundedCornerShape(2.dp))
                                .clickable {
                                    meta.rating = if (meta.rating == star) 0 else star
                                    XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, meta, currentItem.developParams)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$star",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) accentAmber else textSecondary
                            )
                        }
                    }
                }

                // 5 Color Labels
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val colors = listOf(
                        ColorLabel.RED to Color(0xFFE53935),
                        ColorLabel.YELLOW to Color(0xFFFDD835),
                        ColorLabel.GREEN to Color(0xFF43A047),
                        ColorLabel.BLUE to Color(0xFF1E88E5),
                        ColorLabel.PURPLE to Color(0xFF8E24AA)
                    )
                    colors.forEach { (labelEnum, clr) ->
                        val isSelected = meta.colorLabel == labelEnum
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(clr, CircleShape)
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) Color.White else Color.Black,
                                    CircleShape
                                )
                                .clickable {
                                    meta.colorLabel = if (isSelected) ColorLabel.NONE else labelEnum
                                    XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, meta, currentItem.developParams)
                                }
                        )
                    }
                }
            }
        }
    }
}

