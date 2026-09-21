package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch

private data class MetaFieldConfig(
    val key: String,
    val label: String,
    val candidates: List<String> = emptyList()
)

private val CULLING_META_FIELDS = listOf(
    MetaFieldConfig(
        key = "cameraModel",
        label = "端末/カメラ",
        candidates = listOf(
            "SONY ILCE-7M4", "SONY ILCE-7RM5", "SONY ILCE-1", "SONY FX3", "SONY A7C II",
            "Canon EOS R5", "Canon EOS R6 Mark II", "Canon EOS R3",
            "NIKON Z 8", "NIKON Z 9", "NIKON Z 6III", "NIKON Z f",
            "FUJIFILM X-T5", "FUJIFILM X100VI", "FUJIFILM GFX100 II",
            "Leica M11", "Leica Q3", "Leica SL2",
            "Apple iPhone 16 Pro", "Apple iPhone 15 Pro", "Google Pixel 9 Pro"
        )
    ),
    MetaFieldConfig(
        key = "lensModel",
        label = "レンズ",
        candidates = listOf(
            "FE 24-70mm F2.8 GM II", "FE 50mm F1.2 GM", "FE 35mm F1.4 GM", "FE 70-200mm F2.8 GM OSS II",
            "RF24-70mm F2.8 L IS USM", "RF50mm F1.2 L USM",
            "NIKKOR Z 24-70mm f/2.8 S", "NIKKOR Z 50mm f/1.2 S",
            "XF35mmF1.4 R", "XF18-55mmF2.8-4 R LM OIS",
            "Summilux-M 35mm f/1.4 ASPH."
        )
    ),
    MetaFieldConfig(
        key = "fNumber",
        label = "F値",
        candidates = listOf("f/1.2", "f/1.4", "f/1.8", "f/2.0", "f/2.8", "f/3.5", "f/4.0", "f/5.6", "f/8.0", "f/11", "f/16", "f/22")
    ),
    MetaFieldConfig(
        key = "exposureTime",
        label = "SS",
        candidates = listOf("1/8000s", "1/4000s", "1/2000s", "1/1000s", "1/500s", "1/250s", "1/125s", "1/60s", "1/30s", "1/15s", "1/4s", "1s")
    ),
    MetaFieldConfig(
        key = "isoSpeed",
        label = "ISO",
        candidates = listOf("ISO 100", "ISO 200", "ISO 400", "ISO 800", "ISO 1600", "ISO 3200", "ISO 6400", "ISO 12800")
    ),
    MetaFieldConfig(
        key = "focalLength",
        label = "焦点距離",
        candidates = listOf("14mm", "20mm", "24mm", "28mm", "35mm", "50mm", "70mm", "85mm", "105mm", "135mm", "200mm")
    ),
    MetaFieldConfig(
        key = "captureDate",
        label = "日時",
        candidates = emptyList()
    )
)

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
                text = "写真が選択されていません",
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF666666)
            )
        }
        return
    }

    val currentItem = items[currentIndex.coerceIn(items.indices)]
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentBitmap by remember(currentItem.uri) {
        mutableStateOf(cacheManager.getFromMemory(currentItem))
    }
    var metaUpdateTrigger by remember(currentIndex) { mutableStateOf(0) }

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
            .statusBarsPadding()
            .navigationBarsPadding()
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
                    contentDescription = "選別写真",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = "ストリーム先読み中...",
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
                .background(panelDark.copy(alpha = 0.92f))
                .border(1.dp, borderDark)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "< コレクション",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentAmber,
                    modifier = Modifier
                        .clickable { onBackToLauncher() }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "[${currentIndex + 1}/${items.size}] ${currentItem.fileName.ifEmpty { "IMG_${currentIndex + 1}" }}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
                if (currentItem.isRaw) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2C2210), RoundedCornerShape(2.dp))
                            .border(1.dp, accentAmber, RoundedCornerShape(2.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "RAW",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
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
                    shape = RoundedCornerShape(3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF48484A)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1B1D22)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "同期ズーム",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                OutlinedButton(
                    onClick = onOpenBatchSync,
                    shape = RoundedCornerShape(3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF48484A)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1B1D22)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "一括同期",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                Button(
                    onClick = { onOpenDevelop(currentItem) },
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentAmber),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "現像",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
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
            var editingField by remember { mutableStateOf<String?>(null) }
            var editValue by remember { mutableStateOf("") }
            
            var modifiedMetaFields by remember(currentItem.uri) {
                mutableStateOf(mutableSetOf<String>())
            }

            if (editingField != null) {
                val currentField = editingField!!
                val currentFieldDef = CULLING_META_FIELDS.find { it.key == currentField }
                    ?: MetaFieldConfig(currentField, currentField)

                AlertDialog(
                    onDismissRequest = { editingField = null },
                    title = {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "メタデータ編集",
                                    color = textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = currentFieldDef.label,
                                    color = accentAmber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            // タブ切り替えバー (LazyRow)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(CULLING_META_FIELDS) { fieldConfig ->
                                    val isSelected = fieldConfig.key == currentField
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isSelected) accentAmber.copy(alpha = 0.2f) else borderDark.copy(alpha = 0.6f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) accentAmber else borderDark,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                editingField = fieldConfig.key
                                                editValue = when (fieldConfig.key) {
                                                    "captureDate" -> meta.captureDate
                                                    "cameraModel" -> meta.cameraModel
                                                    "lensModel" -> meta.lensModel
                                                    "focalLength" -> meta.focalLength
                                                    "fNumber" -> meta.fNumber
                                                    "exposureTime" -> meta.exposureTime
                                                    "isoSpeed" -> meta.isoSpeed
                                                    else -> ""
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = fieldConfig.label,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isSelected) accentAmber else textSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (currentFieldDef.candidates.isNotEmpty()) {
                                Text(
                                    text = "候補一覧（タップで選択）:",
                                    fontSize = 10.sp,
                                    color = textSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    currentFieldDef.candidates.forEach { candidate ->
                                        val isChosen = editValue == candidate
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                if (isChosen) accentAmber else panelDark,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isChosen) accentAmber else borderDark,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                editValue = candidate
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = candidate,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (isChosen) Color(0xFF121212) else textPrimary,
                                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            OutlinedTextField(
                                value = editValue,
                                onValueChange = { editValue = it },
                                label = { Text("${currentFieldDef.label} (自由入力・微調整)", fontSize = 11.sp) },
                                textStyle = LocalTextStyle.current.copy(
                                    color = textPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentAmber,
                                    unfocusedBorderColor = borderDark,
                                    focusedLabelColor = accentAmber,
                                    unfocusedLabelColor = textSecondary,
                                    cursorColor = accentAmber
                                ),
                                trailingIcon = {
                                    if (editValue.isNotEmpty()) {
                                        Text(
                                            text = "✕",
                                            color = textSecondary,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .clickable { editValue = "" }
                                                .padding(8.dp)
                                        )
                                    }
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val field = editingField ?: return@TextButton
                            val newSet = modifiedMetaFields.toMutableSet()
                            newSet.add(field)
                            modifiedMetaFields = newSet
                            
                            when(field) {
                                "captureDate" -> meta.captureDate = editValue
                                "cameraModel" -> {
                                    meta.cameraModel = editValue
                                    meta.cameraMake = editValue.split(" ").firstOrNull() ?: ""
                                }
                                "lensModel" -> meta.lensModel = editValue
                                "focalLength" -> meta.focalLength = editValue
                                "fNumber" -> meta.fNumber = editValue
                                "exposureTime" -> meta.exposureTime = editValue
                                "isoSpeed" -> meta.isoSpeed = editValue
                            }

                            // 実際の画像ファイルEXIFおよびXMPサイドカーへ直接書き込み保存
                            metaUpdateTrigger++
                            coroutineScope.launch {
                                val success = DirectExifWriter.writeMetadata(context, currentItem, meta)
                                if (success) {
                                    android.widget.Toast.makeText(context, "端末情報・EXIFをファイルに書き込み保存しました", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }

                            editingField = null
                        }) {
                            Text("Save", color = accentAmber, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingField = null }) {
                            Text("Cancel", color = textSecondary)
                        }
                    },
                    containerColor = panelDark
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val _bottomTrigger = metaUpdateTrigger
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val openEdit = { field: String, value: String -> 
                        editingField = field
                        editValue = value
                    }
                    
                    val presentFields = buildList {
                        if (meta.captureDate.isNotBlank()) add("captureDate" to meta.captureDate)
                        if (meta.cameraModel.isNotBlank()) add("cameraModel" to meta.cameraModel)
                        if (meta.lensModel.isNotBlank()) add("lensModel" to meta.lensModel)
                        if (meta.focalLength.isNotBlank()) add("focalLength" to meta.focalLength)
                        if (meta.fNumber.isNotBlank()) add("fNumber" to meta.fNumber)
                        if (meta.exposureTime.isNotBlank()) add("exposureTime" to meta.exposureTime)
                        if (meta.isoSpeed.isNotBlank()) add("isoSpeed" to meta.isoSpeed)
                    }

                    presentFields.forEachIndexed { index, (field, value) ->
                        Text(
                            text = value,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = textSecondary,
                            modifier = Modifier.clickable { openEdit(field, value) }
                        )
                        if (index < presentFields.lastIndex) {
                            Text("|", color = borderDark, fontSize = 10.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+ ADD",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = accentAmber,
                        modifier = Modifier
                            .clickable { openEdit("fNumber", meta.fNumber) }
                            .border(1.dp, borderDark)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                
                Text(
                    text = "0ms 先読み有効",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = accentAmber
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rating & Flagging control panel
            val _trigger = metaUpdateTrigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pick / Reject Flags
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Pick Flag
                    val isPicked = meta.pickStatus == PickStatus.PICKED
                    Box(
                        modifier = Modifier
                            .background(if (isPicked) Color(0xFF2E7D32) else Color(0xFF1E1E1E), RoundedCornerShape(3.dp))
                            .border(1.dp, if (isPicked) Color(0xFF4CAF50) else Color(0xFF48484A), RoundedCornerShape(3.dp))
                            .clickable {
                                meta.pickStatus = if (isPicked) PickStatus.NONE else PickStatus.PICKED
                                metaUpdateTrigger++
                                XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, meta, currentItem.developParams)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "採用",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPicked) Color.White else textPrimary
                        )
                    }

                    // Reject Flag
                    val isRejected = meta.pickStatus == PickStatus.REJECTED
                    Box(
                        modifier = Modifier
                            .background(if (isRejected) Color(0xFFC62828) else Color(0xFF1E1E1E), RoundedCornerShape(3.dp))
                            .border(1.dp, if (isRejected) Color(0xFFEF5350) else Color(0xFF48484A), RoundedCornerShape(3.dp))
                            .clickable {
                                meta.pickStatus = if (isRejected) PickStatus.NONE else PickStatus.REJECTED
                                metaUpdateTrigger++
                                XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, meta, currentItem.developParams)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "不採用",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRejected) Color.White else textPrimary
                        )
                    }
                }

                // 1-5 Star Rating Controls
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (star in 1..5) {
                        val isSelected = star <= meta.rating
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(if (isSelected) Color(0xFF332714) else Color(0xFF1B1D22), RoundedCornerShape(3.dp))
                                .border(1.dp, if (isSelected) accentAmber else Color(0xFF48484A), RoundedCornerShape(3.dp))
                            .clickable {
                                meta.rating = if (meta.rating == star) 0 else star
                                metaUpdateTrigger++
                                XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, meta, currentItem.developParams)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "★$star",
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
                                .size(22.dp)
                                .background(clr, CircleShape)
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) Color.White else Color.Black,
                                    CircleShape
                                )
                                .clickable {
                                    meta.colorLabel = if (isSelected) ColorLabel.NONE else labelEnum
                                    metaUpdateTrigger++
                                    XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, meta, currentItem.developParams)
                                }
                        )
                    }
                }
            }
        }
    }
}

