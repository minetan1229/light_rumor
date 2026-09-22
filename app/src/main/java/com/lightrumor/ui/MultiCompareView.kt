package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clipToBounds
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
    BackHandler {
        onCloseCompare()
    }

    var isFourScreenMode by remember { mutableStateOf(items.size >= 4) }
    val displayedItems = remember(items, isFourScreenMode) {
        if (isFourScreenMode) items.take(4) else items.take(2)
    }

    val syncZoomState = remember { SynchronizedZoomState() }

    val colors = LightRumorTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Top HUD Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated)
                .border(1.dp, colors.borderStrong)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Vector Back Chevron
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable { onCloseCompare() },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
                        val strokeW = 2.dp.toPx()
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.65f, size.height * 0.15f)
                            lineTo(size.width * 0.25f, size.height * 0.5f)
                            lineTo(size.width * 0.65f, size.height * 0.85f)
                        }
                        drawPath(
                            path = path,
                            color = colors.textPrimary,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeW,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }

                Text(
                    text = "比較ズーム",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )

                Text(
                    text = "${"%.1f".format(syncZoomState.scale)}x ${if (syncZoomState.scale > 1.5f) "(等倍固定)" else "(全体表示)"}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentAmber
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 100% Zoom Toggle
                Box(
                    modifier = Modifier
                        .background(colors.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, if (syncZoomState.scale > 1.5f) colors.accentAmber else colors.borderSubtle, RoundedCornerShape(2.dp))
                        .clickable { syncZoomState.zoomTo100() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (syncZoomState.scale > 1.5f) "全体表示" else "100% 等倍",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (syncZoomState.scale > 1.5f) colors.accentAmber else colors.textPrimary
                    )
                }

                // 2-Screen vs 4-Screen Toggle
                if (items.size >= 4) {
                    Box(
                        modifier = Modifier
                            .background(colors.surface, RoundedCornerShape(2.dp))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                            .clickable { isFourScreenMode = !isFourScreenMode }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isFourScreenMode) "2分割" else "4分割",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
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
                            modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, colors.borderSubtle),
                            onFlag = { onSelectWinner(displayedItems[0]) }
                        )
                        ComparePane(
                            item = displayedItems[1],
                            syncState = syncZoomState,
                            cacheManager = cacheManager,
                            modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, colors.borderSubtle),
                            onFlag = { onSelectWinner(displayedItems[1]) }
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ComparePane(
                            item = displayedItems[2],
                            syncState = syncZoomState,
                            cacheManager = cacheManager,
                            modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, colors.borderSubtle),
                            onFlag = { onSelectWinner(displayedItems[2]) }
                        )
                        ComparePane(
                            item = displayedItems[3],
                            syncState = syncZoomState,
                            cacheManager = cacheManager,
                            modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, colors.borderSubtle),
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
                            modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, colors.borderSubtle),
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
    val colors = LightRumorTheme.colors
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
            .clipToBounds()
            .background(colors.background)
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
                text = "同期中...",
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }

        // HUD overlay per pane
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(colors.surface.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.fileName.ifEmpty { "PHOTO" }.takeLast(12),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = colors.textPrimary
            )
            if (isPicked) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "採用",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.statusPick
                )
            } else if (isRejected) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "不採用",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.statusReject
                )
            }
        }

        // Direct pick flag button in corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(if (isPicked) colors.statusPick else colors.surfaceElevated, RoundedCornerShape(2.dp))
                .border(1.dp, if (isPicked) colors.statusPick else colors.borderStrong, RoundedCornerShape(2.dp))
                .clickable {
                    item.metadata.pickStatus = if (isPicked) PickStatus.NONE else PickStatus.PICKED
                    XmpSidecarManager.scheduleSaveSidecar(item.filePath, item.metadata, item.developParams)
                    if (item.metadata.pickStatus == PickStatus.PICKED) onFlag()
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isPicked) "採用中" else "採用",
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPicked) colors.background else colors.textPrimary
            )
        }
    }
}

