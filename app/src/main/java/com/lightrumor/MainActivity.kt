package com.lightrumor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.lightrumor.ui.BatchSyncDialog
import com.lightrumor.ui.CullingScreen
import com.lightrumor.ui.DevelopStudioScreen
import com.lightrumor.ui.MultiCompareView
import com.lightrumor.ui.QuickOpenScreen
import kotlinx.coroutines.launch
import java.io.File

/**
 * Main Activity of light_rumor.
 * Seamlessly transitions from Collection to Culling Screen or Develop Studio.
 */
class MainActivity : ComponentActivity() {

    private lateinit var pickerLauncher: PhotoPickerLauncher
    private lateinit var cacheManager: CullingCacheManager
    private lateinit var editHistoryCatalog: EditHistoryCatalog

    // Mutable state hoisted to Activity level so PhotoPickerLauncher callback can update it
    private val _photoItems = mutableStateOf<List<PhotoItem>>(emptyList())
    private val _currentIndex = mutableStateOf(0)

    private suspend fun resolvePhotoItem(uri: Uri): PhotoItem {
        var fileName = ""
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex) ?: ""
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (fileName.isEmpty()) {
            val p = uri.path ?: ""
            fileName = File(p).name.ifEmpty { "PHOTO_${System.currentTimeMillis() % 10000}.jpg" }
        }

        var resolvedPath = if (uri.scheme == "file") (uri.path ?: "") else ""
        if (resolvedPath.isEmpty() && ThumbnailLoader.isRawFile(fileName)) {
            try {
                val tempDir = File(cacheDir, "raw_cache").apply { if (!exists()) mkdirs() }
                trimRawCache(tempDir)
                val safeRawName = "${uri.toString().hashCode()}_$fileName"
                val tempFile = File(tempDir, safeRawName)
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0L) {
                    resolvedPath = tempFile.absolutePath
                }
            } catch (_: Exception) {}
        }

        val existingParams = editHistoryCatalog.getParamsForUri(uri.toString()) ?: DevelopmentParams()
        val existingMasks = editHistoryCatalog.getMasksForUri(uri.toString())

        val item = PhotoItem(
            uri = uri,
            filePath = resolvedPath,
            fileName = fileName,
            isRaw = ThumbnailLoader.isRawFile(fileName),
            developParams = existingParams,
            maskLayers = existingMasks
        )
        editHistoryCatalog.applyMetadataFromCatalog(uri.toString(), item.metadata)
        if (item.metadata.cameraModel.isEmpty() && item.metadata.fNumber.isEmpty() && item.metadata.isoSpeed.isEmpty()) {
            ThumbnailLoader.extractExif(this, item)
        }
        return item
    }

    private fun trimRawCache(cacheDir: File, maxSizeBytes: Long = 500L * 1024 * 1024) {
        try {
            val files = cacheDir.listFiles()?.filter { it.isFile } ?: return
            var totalSize = files.sumOf { it.length() }
            if (totalSize > maxSizeBytes) {
                val sortedFiles = files.sortedBy { it.lastModified() }
                for (f in sortedFiles) {
                    val len = f.length()
                    if (f.delete()) {
                        totalSize -= len
                        if (totalSize <= (maxSizeBytes * 0.8).toLong()) {
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val uris = mutableListOf<Uri>()

        @Suppress("DEPRECATION")
        val intentUris = intent.getParcelableArrayListExtra<Uri>(IntentHandlerActivity.EXTRA_PHOTO_URIS)
        if (intentUris != null) {
            uris.addAll(intentUris)
        }

        intent.data?.let { dataUri ->
            if (!uris.contains(dataUri)) uris.add(dataUri)
        }

        if (intent.action == android.content.Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM))?.let {
                if (!uris.contains(it)) uris.add(it)
            }
        } else if (intent.action == android.content.Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(android.content.Intent.EXTRA_STREAM)?.let { list ->
                for (u in list) {
                    if (!uris.contains(u)) uris.add(u)
                }
            }
        }

        if (uris.isNotEmpty()) {
            lifecycleScope.launch {
                val newItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    uris.map { resolvePhotoItem(it) }
                }
                _photoItems.value = newItems
                _currentIndex.value = 0
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cacheManager = CullingCacheManager(this)
        editHistoryCatalog = EditHistoryCatalog(this)

        // Register ActivityResult launchers BEFORE setContent (must be in CREATED state)
        pickerLauncher = PhotoPickerLauncher(this) { selectedUris ->
            lifecycleScope.launch {
                val newItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    selectedUris.map { resolvePhotoItem(it) }
                }
                if (_photoItems.value.isEmpty()) {
                    _photoItems.value = newItems
                    _currentIndex.value = 0
                } else {
                    val existingUris = _photoItems.value.map { it.uri.toString() }.toSet()
                    val toAppend = newItems.filter { !existingUris.contains(it.uri.toString()) }
                    _photoItems.value = _photoItems.value + toAppend
                }
            }
        }

        // Handle incoming URIs from IntentHandlerActivity or external share
        handleIncomingIntent(intent)

        enableEdgeToEdge()
        setContent {
            LightRumorTheme(isDark = true) {
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()

                val photoItems by _photoItems
                var currentIndex by _currentIndex
                var isCompareMode by remember { mutableStateOf(false) }
                var isBatchSyncOpen by remember { mutableStateOf(false) }
                var activeDevelopItem by remember { mutableStateOf<PhotoItem?>(null) }

                // 権限管理＆コレクション写真リスト
                var hasStoragePermission by remember { mutableStateOf(MediaCollectionManager.hasPermission(context)) }
                var devicePhotos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasStoragePermission = isGranted
                    if (isGranted) {
                        coroutineScope.launch {
                            devicePhotos = MediaCollectionManager.queryDevicePhotos(context)
                        }
                    }
                }

                LaunchedEffect(hasStoragePermission) {
                    if (hasStoragePermission) {
                        devicePhotos = MediaCollectionManager.queryDevicePhotos(context)
                    }
                }

                var recentItems by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
                LaunchedEffect(photoItems.isEmpty()) {
                    if (photoItems.isEmpty()) {
                        val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val recentEntries = editHistoryCatalog.getRecentEntries()
                            recentEntries.map { entry ->
                                PhotoItem(
                                    uri = Uri.parse(entry.uri),
                                    filePath = entry.filePath,
                                    fileName = entry.fileName,
                                    isRaw = ThumbnailLoader.isRawFile(entry.fileName.ifEmpty { entry.filePath }),
                                    developParams = editHistoryCatalog.getParamsForUri(entry.uri) ?: DevelopmentParams(),
                                    maskLayers = editHistoryCatalog.getMasksForUri(entry.uri)
                                ).apply {
                                    editHistoryCatalog.applyMetadataFromCatalog(entry.uri, metadata)
                                    if (entry.lastEditedAt > 0) {
                                        metadata.captureDate = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.lastEditedAt))
                                    }
                                }
                            }
                        }
                        recentItems = items
                    }
                }

                when {
                    // 1. コレクション画面 (写真未選択時)
                    photoItems.isEmpty() -> {
                        QuickOpenScreen(
                            cacheManager = cacheManager,
                            hasPermission = hasStoragePermission,
                            onRequestPermission = {
                                permissionLauncher.launch(MediaCollectionManager.getRequiredPermission())
                            },
                            onOpenMultiplePhotos = { pickerLauncher.openMultiplePhotos() },
                            devicePhotos = devicePhotos,
                            recentItems = recentItems,
                            onOpenBatch = { selectedList ->
                                coroutineScope.launch {
                                    val resolvedList = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        selectedList.map { item ->
                                            resolvePhotoItem(item.uri)
                                        }
                                    }
                                    _photoItems.value = resolvedList
                                    _currentIndex.value = 0
                                }
                            }
                        )
                    }

                    // 2. マスター現像スタジオ (Phase 3)
                    activeDevelopItem != null -> {
                        DevelopStudioScreen(
                            photoItem = activeDevelopItem!!,
                            cacheManager = cacheManager,
                            onBack = { updatedParams, updatedMasks ->
                                activeDevelopItem?.let { item ->
                                    item.developParams = updatedParams
                                    item.maskLayers = updatedMasks
                                    editHistoryCatalog.saveEntry(
                                        uri = item.uri.toString(),
                                        fileName = item.fileName,
                                        params = updatedParams,
                                        filePath = item.filePath,
                                        metadata = item.metadata,
                                        masks = updatedMasks
                                    )
                                }
                                activeDevelopItem = null
                            }
                        )
                    }

                    // 3. 複数画面比較モード (2画面/4画面)
                    isCompareMode -> {
                        MultiCompareView(
                            items = photoItems,
                            cacheManager = cacheManager,
                            onCloseCompare = { isCompareMode = false },
                            onSelectWinner = { winner ->
                                val idx = photoItems.indexOf(winner)
                                if (idx >= 0) currentIndex = idx
                                isCompareMode = false
                            }
                        )
                    }

                    // 4. 高速選別画面 (CullingScreen)
                    else -> {
                        CullingScreen(
                            items = photoItems,
                            currentIndex = currentIndex,
                            onIndexChanged = { newIdx -> currentIndex = newIdx },
                            cacheManager = cacheManager,
                            onOpenMultiCompare = { isCompareMode = true },
                            onOpenBatchSync = { isBatchSyncOpen = true },
                            onOpenDevelop = { item -> activeDevelopItem = item },
                            onAddPhotos = { pickerLauncher.openMultiplePhotos() },
                            editHistoryCatalog = editHistoryCatalog,
                            onBackToLauncher = { _photoItems.value = emptyList() }
                        )

                        // 一括同期ダイアログ
                        if (isBatchSyncOpen && photoItems.isNotEmpty()) {
                            val safeIndex = currentIndex.coerceIn(photoItems.indices)
                            val currentItem = photoItems[safeIndex]
                            val targetItems = photoItems.filterIndexed { idx, _ -> idx != safeIndex }
                            BatchSyncDialog(
                                sourceItem = currentItem,
                                targetItems = targetItems,
                                onDismiss = { isBatchSyncOpen = false },
                                onSyncApplied = {
                                    isBatchSyncOpen = false
                                    targetItems.forEach { target ->
                                        editHistoryCatalog.saveEntry(
                                            uri = target.uri.toString(),
                                            fileName = target.fileName,
                                            params = target.developParams,
                                            filePath = target.filePath,
                                            metadata = target.metadata
                                        )
                                    }
                                    _photoItems.value = _photoItems.value.map { it.copy() }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cacheManager.cancel()
    }
}
