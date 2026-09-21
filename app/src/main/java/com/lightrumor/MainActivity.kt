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

    private fun resolvePhotoItem(uri: Uri): PhotoItem {
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
                val tempFile = File(tempDir, fileName)
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

        return PhotoItem(
            uri = uri,
            filePath = resolvedPath,
            fileName = fileName,
            isRaw = ThumbnailLoader.isRawFile(fileName),
            developParams = existingParams
        )
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
                _photoItems.value = newItems
                _currentIndex.value = 0
            }
        }

        // Handle incoming URIs from IntentHandlerActivity
        @Suppress("DEPRECATION")
        val intentUris = intent.getParcelableArrayListExtra<Uri>(IntentHandlerActivity.EXTRA_PHOTO_URIS)
        if (intentUris != null && intentUris.isNotEmpty()) {
            lifecycleScope.launch {
                val newItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    intentUris.map { resolvePhotoItem(it) }
                }
                _photoItems.value = newItems
                _currentIndex.value = 0
            }
        }

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

                when {
                    // 1. コレクション画面 (写真未選択時)
                    photoItems.isEmpty() -> {
                        val recentEntries = editHistoryCatalog.getRecentEntries()
                        val recentItems = recentEntries.map { entry ->
                            PhotoItem(
                                uri = Uri.parse(entry.uri),
                                fileName = entry.fileName,
                                isRaw = ThumbnailLoader.isRawFile(entry.fileName),
                                developParams = editHistoryCatalog.getParamsForUri(entry.uri) ?: DevelopmentParams()
                            ).apply {
                                metadata.captureDate = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.lastEditedAt))
                            }
                        }

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
                                _photoItems.value = selectedList
                                _currentIndex.value = 0
                            }
                        )
                    }

                    // 2. マスター現像スタジオ (Phase 3)
                    activeDevelopItem != null -> {
                        DevelopStudioScreen(
                            photoItem = activeDevelopItem!!,
                            cacheManager = cacheManager,
                            onBack = { updatedParams ->
                                activeDevelopItem?.let { item ->
                                    item.developParams = updatedParams
                                    editHistoryCatalog.saveEntry(
                                        uri = item.uri.toString(),
                                        fileName = item.fileName,
                                        params = updatedParams
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
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
