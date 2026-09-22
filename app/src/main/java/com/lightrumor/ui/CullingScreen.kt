package com.lightrumor.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
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
    onAddPhotos: () -> Unit = {},
    editHistoryCatalog: EditHistoryCatalog? = null,
    onBackToLauncher: () -> Unit
) {
    BackHandler {
        onBackToLauncher()
    }

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

    LightRumorTheme(isDark = true) {
        val colors = LightRumorTheme.colors

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
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
                val bmp = currentBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "選別写真",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = "写真読み込み中...",
                        style = LightRumorTheme.typography.Body,
                        color = colors.textSecondary
                    )
                }
            }

            // 2. Top Header HUD: Filename, Counter, Mode Switchers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(colors.surface.copy(alpha = 0.95f))
                    .border(1.dp, colors.borderStrong)
                    .padding(horizontal = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back button & Photo info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onBackToLauncher() }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp, 12.dp)) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(size.width, 0f)
                                lineTo(0f, size.height / 2f)
                                lineTo(size.width, size.height)
                            }
                            drawPath(
                                path = path,
                                color = colors.accentAmber,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.8.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Square
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "一覧",
                            style = LightRumorTheme.typography.Button,
                            color = colors.accentAmber
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[${currentIndex + 1}/${items.size}] ${currentItem.fileName.ifEmpty { "IMG_${currentIndex + 1}" }}",
                        style = LightRumorTheme.typography.Header,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (currentItem.isRaw) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(colors.accentAmber.copy(alpha = 0.15f), LightRumorShapes.SharpSquare)
                                .border(1.dp, colors.accentAmber, LightRumorShapes.SharpSquare)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "RAW",
                                style = LightRumorTheme.typography.Badge,
                                color = colors.accentAmber
                            )
                        }
                    }
                }

                // Right Action triggers: MultiCompare & Batch Sync & Load Photo & Develop
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .background(colors.surfaceElevated, LightRumorShapes.Panel)
                            .border(1.dp, colors.borderSubtle, LightRumorShapes.Panel)
                            .clickable { onOpenMultiCompare() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "比較",
                            style = LightRumorTheme.typography.Button,
                            color = colors.textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(colors.surfaceElevated, LightRumorShapes.Panel)
                            .border(1.dp, colors.borderSubtle, LightRumorShapes.Panel)
                            .clickable { onOpenBatchSync() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "同期",
                            style = LightRumorTheme.typography.Button,
                            color = colors.textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(colors.surfaceElevated, LightRumorShapes.Panel)
                            .border(1.dp, colors.accentAmber, LightRumorShapes.Panel)
                            .clickable { onAddPhotos() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+ 追加",
                            style = LightRumorTheme.typography.Button,
                            color = colors.accentAmber
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(colors.accentAmber, LightRumorShapes.Button)
                            .clickable { onOpenDevelop(currentItem) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "現像スタジオ",
                            style = LightRumorTheme.typography.Button,
                            color = Color.Black
                        )
                    }
                }
            }

        // 3. Bottom Instrument Bar: Metadata Strip & Rating Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface.copy(alpha = 0.95f))
                .border(1.dp, colors.borderStrong)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Metadata EXIF telemetry strip
            val meta = currentItem.metadata
            var editingField by remember { mutableStateOf<String?>(null) }
            var editValue by remember { mutableStateOf("") }
            
            var modifiedMetaFields by remember(currentItem.uri) {
                mutableStateOf<Set<String>>(emptySet())
            }

            editingField?.let { currentField ->
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
                                    style = LightRumorTheme.typography.Header,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = currentFieldDef.label,
                                    style = LightRumorTheme.typography.Badge,
                                    color = colors.accentAmber
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // タブ切り替えバー (LazyRow)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(CULLING_META_FIELDS) { fieldConfig ->
                                    val isSelected = fieldConfig.key == currentField
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
                                            style = LightRumorTheme.typography.Tab,
                                            color = if (isSelected) colors.accentAmber else colors.textSecondary
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
                                    style = LightRumorTheme.typography.Caption,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    currentFieldDef.candidates.forEach { candidate ->
                                        val isChosen = editValue == candidate
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isChosen) colors.accentAmber.copy(alpha = 0.2f) else colors.surfaceElevated,
                                                    LightRumorShapes.Panel
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isChosen) colors.accentAmber else colors.borderSubtle,
                                                    LightRumorShapes.Panel
                                                )
                                                .clickable {
                                                    editValue = candidate
                                                }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = candidate,
                                                style = LightRumorTheme.typography.MicroIndex,
                                                color = if (isChosen) colors.accentAmber else colors.textPrimary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            OutlinedTextField(
                                value = editValue,
                                onValueChange = { editValue = it },
                                label = { Text("${currentFieldDef.label} (直接入力)", style = LightRumorTheme.typography.Caption) },
                                textStyle = LightRumorTheme.typography.ValueReadout.copy(color = colors.textPrimary),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentAmber,
                                    unfocusedBorderColor = colors.borderSubtle,
                                    focusedLabelColor = colors.accentAmber,
                                    unfocusedLabelColor = colors.textSecondary,
                                    cursorColor = colors.accentAmber
                                ),
                                trailingIcon = {
                                    if (editValue.isNotEmpty()) {
                                        Text(
                                            text = "✕",
                                            color = colors.textSecondary,
                                            style = LightRumorTheme.typography.Button,
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
                            
                            val updatedMeta = when(field) {
                                "captureDate" -> meta.copy(captureDate = editValue)
                                "cameraModel" -> meta.copy(
                                    cameraModel = editValue,
                                    cameraMake = editValue.split(" ").firstOrNull() ?: ""
                                )
                                "lensModel" -> meta.copy(lensModel = editValue)
                                "focalLength" -> meta.copy(focalLength = editValue)
                                "fNumber" -> meta.copy(fNumber = editValue)
                                "exposureTime" -> meta.copy(exposureTime = editValue)
                                "isoSpeed" -> meta.copy(isoSpeed = editValue)
                                else -> meta
                            }
                            currentItem.metadata = updatedMeta

                            metaUpdateTrigger++
                            coroutineScope.launch {
                                editHistoryCatalog?.saveMetadataOnly(
                                    uri = currentItem.uri.toString(),
                                    fileName = currentItem.fileName,
                                    filePath = currentItem.filePath,
                                    metadata = updatedMeta,
                                    params = currentItem.developParams
                                )
                                val success = DirectExifWriter.writeMetadata(context, currentItem, updatedMeta)
                                if (success) {
                                    android.widget.Toast.makeText(context, "EXIFをファイルに書き込み保存しました", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }

                            editingField = null
                        }) {
                            Text("保存", color = colors.accentAmber, style = LightRumorTheme.typography.Button)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingField = null }) {
                            Text("キャンセル", color = colors.textSecondary, style = LightRumorTheme.typography.Button)
                        }
                    },
                    containerColor = colors.surface
                )
            }

            // 1. EXIF Metadata Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    val openEdit = { field: String, value: String -> 
                        editingField = field
                        editValue = value
                    }
                    
                    val presentFields = buildList {
                        if (meta.fNumber.isNotBlank()) add("fNumber" to meta.fNumber)
                        if (meta.exposureTime.isNotBlank()) add("exposureTime" to meta.exposureTime)
                        if (meta.isoSpeed.isNotBlank()) add("isoSpeed" to meta.isoSpeed)
                        if (meta.focalLength.isNotBlank()) add("focalLength" to meta.focalLength)
                        if (meta.cameraModel.isNotBlank()) add("cameraModel" to meta.cameraModel)
                        if (meta.lensModel.isNotBlank()) add("lensModel" to meta.lensModel)
                    }

                    presentFields.forEach { (field, value) ->
                        Box(
                            modifier = Modifier
                                .background(colors.surfaceElevated, LightRumorShapes.SharpSquare)
                                .border(1.dp, colors.borderSubtle, LightRumorShapes.SharpSquare)
                                .clickable { openEdit(field, value) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = value,
                                style = LightRumorTheme.typography.MicroIndex,
                                color = colors.textPrimary
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(colors.surfaceElevated, LightRumorShapes.SharpSquare)
                            .border(1.dp, colors.accentAmber.copy(alpha = 0.5f), LightRumorShapes.SharpSquare)
                            .clickable { openEdit("cameraModel", meta.cameraModel) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "+ EXIF編集",
                            style = LightRumorTheme.typography.MicroIndex,
                            color = colors.accentAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Rating & Flagging control panel (LED Flags, 5-Star Bar, Color Chips)
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
                            .background(
                                if (isPicked) Color(0xFF1B3820) else colors.surfaceElevated,
                                LightRumorShapes.Panel
                            )
                            .border(
                                1.dp,
                                if (isPicked) Color(0xFF4CAF50) else colors.borderSubtle,
                                LightRumorShapes.Panel
                            )
                            .clickable {
                                val newStatus = if (isPicked) PickStatus.NONE else PickStatus.PICKED
                                val updated = currentItem.metadata.copy(pickStatus = newStatus)
                                currentItem.metadata = updated
                                metaUpdateTrigger++
                                editHistoryCatalog?.saveMetadataOnly(currentItem.uri.toString(), currentItem.fileName, currentItem.filePath, updated, currentItem.developParams)
                                XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, updated, currentItem.developParams)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (isPicked) Color(0xFF4CAF50) else colors.borderStrong, LightRumorShapes.SharpSquare)
                            )
                            Text(
                                text = "採用",
                                style = LightRumorTheme.typography.Button,
                                color = if (isPicked) Color(0xFF4CAF50) else colors.textSecondary
                            )
                        }
                    }

                    // Reject Flag
                    val isRejected = meta.pickStatus == PickStatus.REJECTED
                    Box(
                        modifier = Modifier
                            .background(
                                if (isRejected) Color(0xFF381B1B) else colors.surfaceElevated,
                                LightRumorShapes.Panel
                            )
                            .border(
                                1.dp,
                                if (isRejected) colors.accentRed else colors.borderSubtle,
                                LightRumorShapes.Panel
                            )
                            .clickable {
                                val newStatus = if (isRejected) PickStatus.NONE else PickStatus.REJECTED
                                val updated = currentItem.metadata.copy(pickStatus = newStatus)
                                currentItem.metadata = updated
                                metaUpdateTrigger++
                                editHistoryCatalog?.saveMetadataOnly(currentItem.uri.toString(), currentItem.fileName, currentItem.filePath, updated, currentItem.developParams)
                                XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, updated, currentItem.developParams)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (isRejected) colors.accentRed else colors.borderStrong, LightRumorShapes.SharpSquare)
                            )
                            Text(
                                text = "不採用",
                                style = LightRumorTheme.typography.Button,
                                color = if (isRejected) colors.accentRed else colors.textSecondary
                            )
                        }
                    }
                }

                // 1-5 Star Rating Bar
                Row(
                    modifier = Modifier
                        .background(colors.surfaceElevated, LightRumorShapes.Panel)
                        .border(1.dp, colors.borderSubtle, LightRumorShapes.Panel)
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (star in 1..5) {
                        val isSelected = star <= meta.rating
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    val newRating = if (meta.rating == star) 0 else star
                                    val updated = currentItem.metadata.copy(rating = newRating)
                                    currentItem.metadata = updated
                                    metaUpdateTrigger++
                                    editHistoryCatalog?.saveMetadataOnly(currentItem.uri.toString(), currentItem.fileName, currentItem.filePath, updated, currentItem.developParams)
                                    XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, updated, currentItem.developParams)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "★",
                                style = LightRumorTheme.typography.Header,
                                color = if (isSelected) colors.accentAmber else colors.borderStrong
                            )
                        }
                    }
                }

                // 5 Color Labels (Optical Chips)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val colorList = listOf(
                        ColorLabel.RED to Color(0xFFE53935),
                        ColorLabel.YELLOW to Color(0xFFFDD835),
                        ColorLabel.GREEN to Color(0xFF43A047),
                        ColorLabel.BLUE to Color(0xFF1E88E5),
                        ColorLabel.PURPLE to Color(0xFF8E24AA)
                    )
                    colorList.forEach { (labelEnum, clr) ->
                        val isSelected = meta.colorLabel == labelEnum
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(clr, LightRumorShapes.SharpSquare)
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) colors.textPrimary else Color.Transparent,
                                    LightRumorShapes.SharpSquare
                                )
                                .clickable {
                                    val newColor = if (isSelected) ColorLabel.NONE else labelEnum
                                    val updated = currentItem.metadata.copy(colorLabel = newColor)
                                    currentItem.metadata = updated
                                    metaUpdateTrigger++
                                    editHistoryCatalog?.saveMetadataOnly(currentItem.uri.toString(), currentItem.fileName, currentItem.filePath, updated, currentItem.developParams)
                                    XmpSidecarManager.scheduleSaveSidecar(currentItem.filePath, updated, currentItem.developParams)
                                }
                        )
                    }
                }
            }
        }
    }
}
}



