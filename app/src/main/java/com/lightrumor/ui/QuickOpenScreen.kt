package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.CullingCacheManager
import com.lightrumor.PhotoItem

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

    val bgDark = Color(0xFF0A0A0A)
    val cardDark = Color(0xFF141414)
    val borderDark = Color(0xFF262626)
    val textPrimary = Color(0xFFE5E5E5)
    val textSecondary = Color(0xFF8C8C8C)
    val accentAmber = Color(0xFFD4A373)

    // 現在のタブに応じた表示対象リスト
    val currentList = when (selectedTab) {
        CollectionTab.DevicePhotos -> devicePhotos
        CollectionTab.RecentEdits -> recentItems
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
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
                    .background(Color(0xFF111111))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "light_rumor",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textPrimary
                    )
                    Text(
                        text = "COLLECTION",
                        fontSize = 9.sp,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = accentAmber,
                        modifier = Modifier
                            .background(Color(0x33D4A373), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // フォトピッカーからインポート
                OutlinedButton(
                    onClick = onOpenMultiplePhotos,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentAmber),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "+ 外部から追加",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = accentAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // -------------------------------------------------------------
            // 2. TAB SELECTOR (端末写真 / 最近の編集)
            // -------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161616))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 端末写真タブ
                TabChip(
                    label = "端末の写真 (${devicePhotos.size})",
                    isSelected = (selectedTab == CollectionTab.DevicePhotos),
                    onClick = { selectedTab = CollectionTab.DevicePhotos },
                    accentColor = accentAmber,
                    textColor = textPrimary,
                    subTextColor = textSecondary
                )

                // 最近の編集タブ
                TabChip(
                    label = "最近の編集 (${recentItems.size})",
                    isSelected = (selectedTab == CollectionTab.RecentEdits),
                    onClick = { selectedTab = CollectionTab.RecentEdits },
                    accentColor = accentAmber,
                    textColor = textPrimary,
                    subTextColor = textSecondary
                )

                Spacer(modifier = Modifier.weight(1f))

                // 全選択 / 解除
                if (currentList.isNotEmpty()) {
                    Text(
                        text = if (selectedItemUris.size == currentList.size) "全解除" else "すべて選択",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = accentAmber,
                        modifier = Modifier
                            .clickable {
                                selectedItemUris = if (selectedItemUris.size == currentList.size) {
                                    emptySet()
                                } else {
                                    currentList.map { it.uri.toString() }.toSet()
                                }
                            }
                            .padding(4.dp)
                    )
                }
            }

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
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "許可すると最新の写真やRAWファイルをコレクションで即座にサムネイル確認できます。",
                                fontSize = 10.sp,
                                color = textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRequestPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = accentAmber),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "写真へのアクセスを許可",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
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
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = onOpenMultiplePhotos,
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accentAmber)
                            ) {
                                Text(
                                    text = "写真を選択して読み込む (RAW / JPEG)",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = accentAmber
                                )
                            }
                        }
                    }

                    // 写真サムネイルのグリッド表示
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 105.dp),
                            contentPadding = PaddingValues(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                Surface(
                    color = Color(0xFF161616),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${selectedItemUris.size} 枚の写真を選択中",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = textPrimary
                            )
                            Text(
                                text = "RAW現像・選別スタジオで開きます",
                                fontSize = 9.sp,
                                color = textSecondary
                            )
                        }

                        Button(
                            onClick = {
                                val selectedPhotos = currentList.filter { selectedItemUris.contains(it.uri.toString()) }
                                if (selectedPhotos.isNotEmpty()) {
                                    onOpenBatch(selectedPhotos)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentAmber),
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text(
                                text = "まとめて開く (${selectedItemUris.size})",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
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
    onClick: () -> Unit,
    accentColor: Color,
    textColor: Color,
    subTextColor: Color
) {
    Box(
        modifier = Modifier
            .background(
                if (isSelected) Color(0xFF242424) else Color.Transparent,
                RoundedCornerShape(3.dp)
            )
            .border(
                1.dp,
                if (isSelected) accentColor else Color(0xFF333333),
                RoundedCornerShape(3.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) accentColor else subTextColor
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

    val borderColor = if (isSelected) Color(0xFFD4A373) else Color(0xFF262626)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF141414))
            .border(borderWidth, borderColor, RoundedCornerShape(2.dp))
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
                    text = item.fileName.takeLast(10),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        // RAW バッジ
        if (item.isRaw) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(2.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "RAW",
                    color = Color(0xFFD4A373),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // チェックボックス選択インジケーター
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(18.dp)
                .background(
                    if (isSelected) Color(0xFFD4A373) else Color(0x88000000),
                    RoundedCornerShape(2.dp)
                )
                .border(1.dp, if (isSelected) Color(0xFFD4A373) else Color(0xFF888888), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Text(
                    text = "✓",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 下部ファイル名バー（半透明）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xAA000000))
                .padding(horizontal = 3.dp, vertical = 2.dp)
        ) {
            Text(
                text = item.fileName,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFCCCCCC),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
