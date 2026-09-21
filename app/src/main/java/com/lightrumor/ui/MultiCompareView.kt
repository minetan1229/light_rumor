package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*

/**
 * Shared transformation state for lockstep 100% pixel zoom & pan synchronization across 2 or 4 viewports.
 */
class SynchronizedZoomState(
    initialScale: Float = 1.0f,
    initialOffset: Offset = Offset.Zero
) {
    var scale by mutableStateOf(initialScale)
    var offset by mutableStateOf(initialOffset)

    fun reset() {
        scale = 1.0f
        offset = Offset.Zero
    }

    fun zoomTo100() {
        scale = if (scale >= 2.0f) 1.0f else 2.5f
        offset = Offset.Zero
    }
}

/**
 * 2-Screen and 4-Screen Synchronized Zoom Comparison Component.
 * Dragging or pinching on ANY viewport synchronously transforms ALL viewports to the exact same pixel coordinates.
 */
@Composable
fun MultiCompareView(
    items: List<PhotoItem>,
    cacheManager: CullingCacheManager,
    onCloseCompare: () -> Unit,
    onSelectWinner: (PhotoItem) -> Unit
) {
    var isFourScreenMode by remember { mutableStateOf(items.size >= 4) }
    val displayedItems = remember(items, isFourScreenMode) {
        if (isFourScreenMode) items.take(4) else items.take(2)
    }

    val syncZoomState = remember { SynchronizedZoomState() }

    val bgDark = Color(0xFF060606)
    val panelDark = Color(0xFF141414)
    val borderDark = Color(0xFF262626)
    val textPrimary = Color(0xFFE5E5E5)
    val textSecondary = Color(0xFF888888)
    val accentAmber = Color(0xFFD4A373)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
    ) {
        // Top HUD Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(panelDark)
                .border(1.dp, borderDark)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "< EXIT COMPARE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = textSecondary,
                    modifier = Modifier.clickable { onCloseCompare() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "SYNC ZOOM: ${"%.1f".format(syncZoomState.scale)}x ${if (syncZoomState.scale > 1.5f) "(100% LOCK)" else "(FIT)"}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentAmber
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 100% Zoom Toggle
                OutlinedButton(
                    onClick = { syncZoomState.zoomTo100() },
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentAmber),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = panelDark),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (syncZoomState.scale > 1.5f) "RESET FIT" else "100% ZOOM",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = accentAmber
                    )
                }

                // 2-Screen vs 4-Screen Toggle
                if (items.size >= 4) {
                    OutlinedButton(
                        onClick = { isFourScreenMode = !isFourScreenMode },
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderDark),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = panelDark),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isFourScreenMode) "2-SPLIT" else "4-SPLIT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = textPrimary
                        )
                    }
                }
            }
        }

        // Synchronized Multi-Viewport Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        syncZoomState.scale = (syncZoomState.scale * zoom).coerceIn(1.0f, 6.0f)
                        if (syncZoomState.scale > 1.0f) {
                            val maxOffset = 600f * (syncZoomState.scale - 1.0f)
                            syncZoomState.offset = Offset(
                                x = (syncZoomState.offset.x + pan.x).coerceIn(-maxOffset, maxOffset),
                                y = (syncZoomState.offset.y + pan.y).coerceIn(-maxOffset, maxOffset)
                            )
                        } else {
                            syncZoomState.offset = Offset.Zero
                        }
                    }
                }
        ) {
            if (isFourScreenMode && displayedItems.size >= 4) {
                // 4-Screen Grid (2x2)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ComparePane(
                            item = displayedItems[0],
                            syncState = syncZoomState,
                            cacheManager = cacheManager,
                            modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, borderDark),
                            onFlag = { onSelectWinner(displayedItems[0]) }
                        )
                        ComparePane(
                            item = displayedItems[1],
                            syncState = syncZoomState,
                            cacheManager = cacheManager,
                            modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, borderDark),
                            onFlag = { onSelectWinner(displayedItems[1]) }
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ComparePane(
                            item = displayedItems[2],
                            syncState = syncZoomState,
                            cacheManager = cacheManager,
                            modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, borderDark),
                            onFlag = { onSelectWinner(displayedItems[2]) }
                        )
                        ComparePane(
                            item = displayedItems[3],
                            syncState = syncZoomState,
                            cacheManager = cacheManager,
                            modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, borderDark),
                            onFlag = { onSelectWinner(displayedItems[3]) }
                        )
                    }
                }
            } else {
                // 2-Screen Split (Side-by-Side)
                Row(modifier = Modifier.fillMaxSize()) {
                    displayedItems.take(2).forEachIndexed { _, item ->
                        ComparePane(
                            item = item,
                            syncState = syncZoomState,
                            cacheManager = cacheManager,
                            modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, borderDark),
                            onFlag = { onSelectWinner(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparePane(
    item: PhotoItem,
    syncState: SynchronizedZoomState,
    cacheManager: CullingCacheManager,
    modifier: Modifier = Modifier,
    onFlag: () -> Unit
) {
    var bitmap by remember(item.uri) {
        mutableStateOf<Bitmap?>(cacheManager.getFromMemory(item.filePath.ifEmpty { item.uri.toString() }))
    }

    LaunchedEffect(item.uri) {
        cacheManager.loadBitmap(item) { bmp ->
            bitmap = bmp
        }
    }

    val isPicked = item.metadata.pickStatus == PickStatus.PICKED
    val isRejected = item.metadata.pickStatus == PickStatus.REJECTED

    Box(
        modifier = modifier
            .background(Color(0xFF090909))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.fileName,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = syncState.scale,
                        scaleY = syncState.scale,
                        translationX = syncState.offset.x,
                        translationY = syncState.offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = "SYNCING...",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color.Gray
            )
        }

        // HUD overlay per pane
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color(0xCC111111), RoundedCornerShape(2.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.fileName.ifEmpty { "PHOTO" }.takeLast(10),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Color(0xFFD0D0D0)
            )
            if (isPicked) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "[FLAG]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            } else if (isRejected) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "[REJECT]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF5350)
                )
            }
        }

        // Direct pick flag button in corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(if (isPicked) Color(0xFF2E7D32) else Color(0xCC202020), RoundedCornerShape(2.dp))
                .border(1.dp, if (isPicked) Color(0xFF4CAF50) else Color(0xFF404040), RoundedCornerShape(2.dp))
                .clickable {
                    item.metadata.pickStatus = if (isPicked) PickStatus.NONE else PickStatus.PICKED
                    XmpSidecarManager.scheduleSaveSidecar(item.filePath, item.metadata, item.developParams)
                    if (item.metadata.pickStatus == PickStatus.PICKED) onFlag()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isPicked) "WINNER" else "SELECT",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPicked) Color.White else Color(0xFFB0B0B0)
            )
        }
    }
}

