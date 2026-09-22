package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*

enum class CollectionTab {
    DevicePhotos,
    RecentEdits
}

/**
 * プロフェッショナル コレクション＆クイックオープン画面。
 * - 端末内の写真と最近の編集をタブで切り替え
 * - 実際の写真サムネイルをゼロ遅延グリッド表示
 * - 複数枚の「まとめて選択」と一括現像・選別開始
 * - メディア権限のリクエスト対応
 */
@Composable
fun QuickOpenScreen(
    cacheManager: CullingCacheManager,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenMultiplePhotos: () -> Unit,
    devicePhotos: List<PhotoItem>,
    recentItems: List<PhotoItem>,
    onOpenBatch: (List<PhotoItem>) -> Unit
) {
    var selectedTab by remember { mutableStateOf(if (hasPermission && devicePhotos.isNotEmpty()) CollectionTab.DevicePhotos else CollectionTab.RecentEdits) }
    var selectedItemUris by remember { mutableStateOf<Set<String>>(emptySet()) }

    LightRumorTheme(isDark = true) {
        val colors = LightRumorTheme.colors

        // 現在のタブに応じた表示対象リスト
        val currentList = when (selectedTab) {
            CollectionTab.DevicePhotos -> devicePhotos
            CollectionTab.RecentEdits -> recentItems
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // -------------------------------------------------------------
                // 1. TOP HEADER & STUDIO BRANDING
                // -------------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(colors.surface)
                        .border(1.dp, colors.borderStrong)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "LIGHT RUMOR",
                            style = LightRumorTheme.typography.TitleLarge,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "STUDIO",
                            style = LightRumorTheme.typography.Badge,
                            color = colors.accentAmber,
                            modifier = Modifier
                                .background(colors.accentAmber.copy(alpha = 0.15f), LightRumorShapes.SharpSquare)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // フォトピッカーからインポート
                    Box(
                        modifier = Modifier
                            .background(colors.surfaceElevated, LightRumorShapes.Button)
                            .border(1.dp, colors.accentAmber, LightRumorShapes.Button)
                            .clickable { onOpenMultiplePhotos() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+ 外部から追加",
                            style = LightRumorTheme.typography.Button,
                            color = colors.accentAmber
                        )
                    }
                }

                // -------------------------------------------------------------
                // 2. TAB SELECTOR (端末写真 / 最近の編集)
                // -------------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 端末写真タブ
                    TabChip(
                        label = "端末写真 (${devicePhotos.size})",
                        isSelected = (selectedTab == CollectionTab.DevicePhotos),
                        onClick = { selectedTab = CollectionTab.DevicePhotos }
                    )

                    // 最近の編集タブ
                    TabChip(
                        label = "最近の編集 (${recentItems.size})",
                        isSelected = (selectedTab == CollectionTab.RecentEdits),
                        onClick = { selectedTab = CollectionTab.RecentEdits }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 全選択 / 解除
                    if (currentList.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(colors.surfaceElevated, LightRumorShapes.Panel)
                                .border(1.dp, colors.borderSubtle, LightRumorShapes.Panel)
                                .clickable {
                                    selectedItemUris = if (selectedItemUris.size == currentList.size) {
                                        emptySet()
                                    } else {
                                        currentList.map { it.uri.toString() }.toSet()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (selectedItemUris.size == currentList.size) "全解除" else "すべて選択",
                                style = LightRumorTheme.typography.Button,
                                color = colors.accentAmber
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.borderSubtle)
                )

                // -------------------------------------------------------------
                // 3. MAIN THUMBNAIL GRID CONTENT
                // -------------------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        // 端末写真タブで権限がない場合
                        selectedTab == CollectionTab.DevicePhotos && !hasPermission -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "端末内の写真を表示するにはアクセス権限が必要です",
                                    style = LightRumorTheme.typography.Header,
                                    color = colors.textPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "許可すると最新の写真やRAWファイルをコレクションで即座にサムネイル確認できます。",
                                    style = LightRumorTheme.typography.Body,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .background(colors.accentAmber, LightRumorShapes.Button)
                                        .clickable { onRequestPermission() }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "写真へのアクセスを許可",
                                        style = LightRumorTheme.typography.Button,
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        // リストが空の場合
                        currentList.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (selectedTab == CollectionTab.DevicePhotos) "写真が見つかりませんでした" else "最近の編集履歴がありません",
                                    style = LightRumorTheme.typography.Header,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .background(colors.surfaceElevated, LightRumorShapes.Button)
                                        .border(1.dp, colors.accentAmber, LightRumorShapes.Button)
                                        .clickable { onOpenMultiplePhotos() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "写真を選択して読み込む (RAW / JPEG)",
                                        style = LightRumorTheme.typography.Button,
                                        color = colors.accentAmber
                                    )
                                }
                            }
                        }

                        // 写真サムネイルのグリッド表示
                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 110.dp),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(currentList, key = { it.uri.toString() }) { item ->
                                    val isSelected = selectedItemUris.contains(item.uri.toString())
                                    PhotoThumbnailGridCell(
                                        item = item,
                                        cacheManager = cacheManager,
                                        isSelected = isSelected,
                                        onToggleSelect = {
                                            selectedItemUris = if (isSelected) {
                                                selectedItemUris - item.uri.toString()
                                            } else {
                                                selectedItemUris + item.uri.toString()
                                            }
                                        },
                                        onDirectOpen = {
                                            onOpenBatch(listOf(item))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // 4. BOTTOM ACTION BAR (まとめて開く / 選別開始)
                // -------------------------------------------------------------
                if (selectedItemUris.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .border(1.dp, colors.borderStrong)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${selectedItemUris.size} 枚の写真を選択中",
                                    style = LightRumorTheme.typography.Header,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "RAW現像・選別スタジオで開きます",
                                    style = LightRumorTheme.typography.Caption,
                                    color = colors.textSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(colors.accentAmber, LightRumorShapes.Button)
                                    .clickable {
                                        val selectedPhotos = currentList.filter { selectedItemUris.contains(it.uri.toString()) }
                                        if (selectedPhotos.isNotEmpty()) {
                                            onOpenBatch(selectedPhotos)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "まとめて開く (${selectedItemUris.size})",
                                    style = LightRumorTheme.typography.Button,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LightRumorTheme.colors
    Box(
        modifier = Modifier
            .background(
                if (isSelected) colors.accentAmber.copy(alpha = 0.15f) else colors.surfaceElevated,
                LightRumorShapes.Panel
            )
            .border(
                1.dp,
                if (isSelected) colors.accentAmber else colors.borderSubtle,
                LightRumorShapes.Panel
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = LightRumorTheme.typography.Tab,
            color = if (isSelected) colors.accentAmber else colors.textSecondary
        )
    }
}

/**
 * 実際の写真サムネイルを非同期に取得して表示するセル。
 */
@Composable
private fun PhotoThumbnailGridCell(
    item: PhotoItem,
    cacheManager: CullingCacheManager,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onDirectOpen: () -> Unit
) {
    val colors = LightRumorTheme.colors
    var bitmap by remember(item.uri) {
        mutableStateOf<Bitmap?>(cacheManager.getFromMemory(item))
    }

    LaunchedEffect(item.uri) {
        if (bitmap == null) {
            cacheManager.loadBitmap(item, targetWidth = 360, targetHeight = 360) { bmp ->
                bitmap = bmp
            }
        }
    }

    val borderColor = if (isSelected) colors.accentAmber else colors.borderSubtle
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(LightRumorShapes.SharpSquare)
            .background(colors.surface)
            .border(borderWidth, borderColor, LightRumorShapes.SharpSquare)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleSelect() },
                    onDoubleTap = { onDirectOpen() }
                )
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // ローディング・プレースホルダー
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.fileName.takeLast(12),
                    style = LightRumorTheme.typography.MicroIndex,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        // 上部バッジ行 (RAW, 採用/不採用)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // RAW バッジ
            if (item.isRaw) {
                Box(
                    modifier = Modifier
                        .background(Color(0xCC000000), LightRumorShapes.SharpSquare)
                        .border(1.dp, colors.accentAmber, LightRumorShapes.SharpSquare)
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "RAW",
                        color = colors.accentAmber,
                        style = LightRumorTheme.typography.Badge
                    )
                }
            }

            // 採用バッジ (緑LED) / 不採用バッジ (赤LED)
            if (item.metadata.pickStatus == com.lightrumor.PickStatus.PICKED) {
                Box(
                    modifier = Modifier
                        .background(Color(0xCC000000), LightRumorShapes.SharpSquare)
                        .border(1.dp, Color(0xFF4CAF50), LightRumorShapes.SharpSquare)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "採用",
                        color = Color(0xFF4CAF50),
                        style = LightRumorTheme.typography.Badge
                    )
                }
            } else if (item.metadata.pickStatus == com.lightrumor.PickStatus.REJECTED) {
                Box(
                    modifier = Modifier
                        .background(Color(0xCC000000), LightRumorShapes.SharpSquare)
                        .border(1.dp, colors.accentRed, LightRumorShapes.SharpSquare)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "不採用",
                        color = colors.accentRed,
                        style = LightRumorTheme.typography.Badge
                    )
                }
            }
        }

        // カラーラベルインジケーター (ドット)
        if (item.metadata.colorLabel != com.lightrumor.ColorLabel.NONE) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 20.dp)
                    .size(8.dp)
                    .background(Color(item.metadata.colorLabel.hexColor), CircleShape)
                    .border(1.dp, Color.Black, CircleShape)
            )
        }

        // チェックボックス選択インジケーター (精密ベクターチェック)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(18.dp)
                .background(
                    if (isSelected) colors.accentAmber else Color(0x99000000),
                    LightRumorShapes.SharpSquare
                )
                .border(1.dp, if (isSelected) colors.accentAmber else colors.borderSubtle, LightRumorShapes.SharpSquare),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    val path = Path().apply {
                        moveTo(2.dp.toPx(), 5.dp.toPx())
                        lineTo(4.dp.toPx(), 7.5.dp.toPx())
                        lineTo(8.dp.toPx(), 2.5.dp.toPx())
                    }
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }

        // 下部ファイル名＆レーティングバー（滑らかなグラデーション scrim）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xCC000000))
                    )
                )
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.fileName,
                    style = LightRumorTheme.typography.MicroIndex,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.metadata.rating > 0) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "★${item.metadata.rating}",
                        style = LightRumorTheme.typography.Badge,
                        color = colors.accentAmber
                    )
                }
            }
        }
    }
}
