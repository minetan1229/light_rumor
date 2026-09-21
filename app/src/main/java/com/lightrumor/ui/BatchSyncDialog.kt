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

    val bgDark = Color(0xFF141414)
    val cardDark = Color(0xFF1C1C1C)
    val borderDark = Color(0xFF2C2C2C)
    val textPrimary = Color(0xFFEDEDED)
    val textSecondary = Color(0xFF8E8E8E)
    val accentAmber = Color(0xFFD4A373)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = bgDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderDark),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Text(
                    text = "BATCH PARAMETER SYNCHRONIZE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Source: ${sourceItem.fileName.ifEmpty { "Current Photo" }} -> ${targetItems.size} Target Photos",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = accentAmber
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Preset Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton("ALL BASIC", onClick = {
                        options = BatchSyncOptions(
                            syncWhiteBalance = true,
                            syncBasicTone = true,
                            syncColorMixer = true,
                            syncDetailNR = true,
                            syncToneCurve = false,
                            syncRatingAndLabel = false
                        )
                    })
                    PresetButton("WB ONLY", onClick = {
                        options = BatchSyncOptions(
                            syncWhiteBalance = true,
                            syncBasicTone = false,
                            syncColorMixer = false,
                            syncDetailNR = false,
                            syncToneCurve = false,
                            syncRatingAndLabel = false
                        )
                    })
                    PresetButton("SELECT ALL", onClick = {
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

                Spacer(modifier = Modifier.height(16.dp))

                // Category Checkboxes
                SyncCheckboxRow(
                    title = "WHITE BALANCE & TINT",
                    description = "Temperature (${sourceItem.developParams.kelvin.toInt()}K), Tint (${"%.1f".format(sourceItem.developParams.tint)})",
                    checked = options.syncWhiteBalance,
                    onCheckedChange = { options = options.copy(syncWhiteBalance = it) }
                )

                SyncCheckboxRow(
                    title = "BASIC TONE & CONTRAST",
                    description = "Exposure (${"%+.2f".format(sourceItem.developParams.exposureEV)} EV), Highlights, Shadows, Whites, Blacks",
                    checked = options.syncBasicTone,
                    onCheckedChange = { options = options.copy(syncBasicTone = it) }
                )

                SyncCheckboxRow(
                    title = "COLOR & VIBRANCE",
                    description = "Vibrance, Global Saturation, Monochrome state",
                    checked = options.syncColorMixer,
                    onCheckedChange = { options = options.copy(syncColorMixer = it) }
                )

                SyncCheckboxRow(
                    title = "DETAIL & NOISE REDUCTION",
                    description = "Luminance NR, Chroma NR, Edge Sharpening",
                    checked = options.syncDetailNR,
                    onCheckedChange = { options = options.copy(syncDetailNR = it) }
                )

                SyncCheckboxRow(
                    title = "TONE CURVE",
                    description = "1D Spline parametric tone curve",
                    checked = options.syncToneCurve,
                    onCheckedChange = { options = options.copy(syncToneCurve = it) }
                )

                SyncCheckboxRow(
                    title = "RATING & CULLING FLAGS",
                    description = "Rating (${sourceItem.metadata.rating}/5), Flag (${sourceItem.metadata.pickStatus}), Label (${sourceItem.metadata.colorLabel.labelName})",
                    checked = options.syncRatingAndLabel,
                    onCheckedChange = { options = options.copy(syncRatingAndLabel = it) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Cancel & Execute
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "CANCEL",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            // Apply to all target items in memory and asynchronously write to XMP
                            targetItems.forEach { target ->
                                options.merge(sourceItem.developParams, target.developParams)
                                if (options.syncRatingAndLabel) {
                                    target.metadata.rating = sourceItem.metadata.rating
                                    target.metadata.pickStatus = sourceItem.metadata.pickStatus
                                    target.metadata.colorLabel = sourceItem.metadata.colorLabel
                                }
                                XmpSidecarManager.scheduleSaveSidecar(target.filePath, target.metadata, target.developParams)
                            }
                            onSyncApplied(options)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentAmber),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text(
                            text = "APPLY SYNC",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xFF222222), RoundedCornerShape(2.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFCCCCCC)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFFD4A373),
                uncheckedColor = Color(0xFF555555),
                checkmarkColor = Color.Black
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFE0E0E0)
            )
            Text(
                text = description,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF777777)
            )
        }
    }
}

