package com.lightrumor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightrumor.*

/**
 * Obsidian Black Minimalist Landing & Quick-Open Screen.
 * Zero emojis - Pure professional instrument styling.
 */
@Composable
fun QuickOpenScreen(
    onOpenSinglePhoto: () -> Unit,
    onOpenMultiplePhotos: () -> Unit,
    onOpenDesktopFileDialog: () -> Unit,
    recentItems: List<PhotoItem> = emptyList(),
    onSelectRecentItem: (PhotoItem) -> Unit = {}
) {
    val bgDark = Color(0xFF0A0A0A)
    val cardDark = Color(0xFF141414)
    val borderDark = Color(0xFF262626)
    val textPrimary = Color(0xFFE5E5E5)
    val textSecondary = Color(0xFF8C8C8C)
    val accentAmber = Color(0xFFD4A373)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Studio branding & instrument indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = "light_rumor",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Monospace,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "プロフェッショナル RAW 現像スタジオ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace,
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentAmber, shape = RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "権限不要・写真アクセス準備完了",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = accentAmber,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Center Action Cards: One-tap Open Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Primary Action: Open Multiple / Batch Culling
                Button(
                    onClick = onOpenMultiplePhotos,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cardDark),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentAmber)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "写真を選択 / バースト連写",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp,
                            color = textPrimary
                        )
                        Text(
                            text = "標準フォトピッカー (1〜100枚 RAW / JPEG / HEIC)",
                            fontSize = 10.sp,
                            color = textSecondary
                        )
                    }
                }

                // Secondary Action: Single Photo Direct Edit
                OutlinedButton(
                    onClick = onOpenSinglePhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderDark),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                ) {
                    Text(
                        text = "単写を開く",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = textPrimary
                    )
                }

                // Desktop / Folder Import
                OutlinedButton(
                    onClick = onOpenDesktopFileDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderDark),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                ) {
                    Text(
                        text = "ファイルダイアログ / ローカル",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = textSecondary
                    )
                }
            }

            // Bottom Section: Recent Session Items or Drop Target
            if (recentItems.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "最近のファイル",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textSecondary,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentItems.take(4).forEach { item ->
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(cardDark, shape = RoundedCornerShape(2.dp))
                                    .border(1.dp, borderDark, RoundedCornerShape(2.dp))
                                    .clickable { onSelectRecentItem(item) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = item.fileName.ifEmpty { "写真" }.takeLast(12),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = textSecondary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.metadata.captureDate.ifEmpty { "Unknown" },
                                        fontSize = 7.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = textSecondary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Drop Hint
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .border(1.dp, borderDark, RoundedCornerShape(4.dp))
                        .background(Color(0xFF0F0F0F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RAW / 画像ファイルをここにドラッグ&ドロップ",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        color = textSecondary
                    )
                }
            }
        }
    }
}

