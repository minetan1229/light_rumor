package com.lightrumor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lightrumor.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Selective Batch Parameter Synchronization Dialog.
 * Allows the photographer to choose exactly which parameter groups to synchronize across photos
 * (e.g. WB and basic tone only, while keeping crop and ratings untouched).
 * Automatically updates in-memory states and non-destructive XMP sidecars.
 */
@Composable
fun BatchSyncDialog(
    sourceItem: PhotoItem,
    targetItems: List<PhotoItem>,
    onDismiss: () -> Unit,
    onSyncApplied: (BatchSyncOptions) -> Unit
) {
    var options by remember { mutableStateOf(BatchSyncOptions()) }
    val colors = LightRumorTheme.colors
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Text(
                    text = "一括パラメータ同期",
                    style = LightRumorTheme.typography.Header,
                    fontSize = 15.sp,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "同期元: ${sourceItem.fileName.ifEmpty { "現在の写真" }} → ${targetItems.size}枚の対象写真",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    color = colors.accentAmber
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Preset Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton("基本全部", onClick = {
                        options = BatchSyncOptions(
                            syncWhiteBalance = true,
                            syncBasicTone = true,
                            syncColorMixer = true,
                            syncDetailNR = true,
                            syncToneCurve = false,
                            syncRatingAndLabel = false
                        )
                    })
                    PresetButton("WBのみ", onClick = {
                        options = BatchSyncOptions(
                            syncWhiteBalance = true,
                            syncBasicTone = false,
                            syncColorMixer = false,
                            syncDetailNR = false,
                            syncToneCurve = false,
                            syncRatingAndLabel = false
                        )
                    })
                    PresetButton("全選択", onClick = {
                        options = BatchSyncOptions(
                            syncWhiteBalance = true,
                            syncBasicTone = true,
                            syncColorMixer = true,
                            syncDetailNR = true,
                            syncToneCurve = true,
                            syncRatingAndLabel = true
                        )
                    })
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Category Checkboxes
                SyncCheckboxRow(
                    title = "ホワイトバランス & 色かぶり",
                    description = "色温度 (${sourceItem.developParams.kelvin.toInt()}K), 色かぶり補正 (${"%.1f".format(sourceItem.developParams.tint)})",
                    checked = options.syncWhiteBalance,
                    onCheckedChange = { options = options.copy(syncWhiteBalance = it) }
                )

                SyncCheckboxRow(
                    title = "基本トーン & コントラスト",
                    description = "露出 (${"%+.2f".format(sourceItem.developParams.exposureEV)} EV), ハイライト, シャドウ, 白レベル, 黒レベル",
                    checked = options.syncBasicTone,
                    onCheckedChange = { options = options.copy(syncBasicTone = it) }
                )

                SyncCheckboxRow(
                    title = "カラー & 彩度",
                    description = "自然な彩度, 彩度, モノクロ状態",
                    checked = options.syncColorMixer,
                    onCheckedChange = { options = options.copy(syncColorMixer = it) }
                )

                SyncCheckboxRow(
                    title = "ディテール & ノイズ低減",
                    description = "輝度NR, カラーNR, 輪郭強調",
                    checked = options.syncDetailNR,
                    onCheckedChange = { options = options.copy(syncDetailNR = it) }
                )

                SyncCheckboxRow(
                    title = "トーンカーブ",
                    description = "パラメトリックトーンカーブ",
                    checked = options.syncToneCurve,
                    onCheckedChange = { options = options.copy(syncToneCurve = it) }
                )

                SyncCheckboxRow(
                    title = "レーティング & 選別フラグ",
                    description = "レーティング (${sourceItem.metadata.rating}/5), フラグ, カラーラベル",
                    checked = options.syncRatingAndLabel,
                    onCheckedChange = { options = options.copy(syncRatingAndLabel = it) }
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: Cancel & Execute
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(colors.surfaceElevated, RoundedCornerShape(2.dp))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "キャンセル",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .background(colors.accentAmber, RoundedCornerShape(2.dp))
                            .clickable {
                                // Apply to all target items in memory and asynchronously write to XMP
                                val currentOptions = options
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        targetItems.forEach { target ->
                                            currentOptions.merge(sourceItem.developParams, target.developParams)
                                            if (currentOptions.syncRatingAndLabel) {
                                                target.metadata.rating = sourceItem.metadata.rating
                                                target.metadata.pickStatus = sourceItem.metadata.pickStatus
                                                target.metadata.colorLabel = sourceItem.metadata.colorLabel
                                            }
                                            XmpSidecarManager.scheduleSaveSidecar(target.filePath, target.metadata, target.developParams)
                                        }
                                    }
                                }
                                onSyncApplied(options)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "同期実行",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.background
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetButton(label: String, onClick: () -> Unit) {
    val colors = LightRumorTheme.colors
    Box(
        modifier = Modifier
            .background(colors.surfaceElevated, RoundedCornerShape(2.dp))
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(2.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun SyncCheckboxRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LightRumorTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Square precision checkbox
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(if (checked) colors.accentAmber else colors.surfaceElevated, RoundedCornerShape(2.dp))
                .border(1.dp, if (checked) colors.accentAmber else colors.borderStrong, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                    val strokeW = 1.8.dp.toPx()
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.15f, size.height * 0.5f)
                        lineTo(size.width * 0.42f, size.height * 0.8f)
                        lineTo(size.width * 0.85f, size.height * 0.2f)
                    }
                    drawPath(
                        path = path,
                        color = colors.background,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeW,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                color = colors.textPrimary
            )
            Text(
                text = description,
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif,
                color = colors.textSecondary
            )
        }
    }
}

