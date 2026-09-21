package com.lightrumor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.lightrumor.ui.BatchSyncDialog
import com.lightrumor.ui.CullingScreen
import com.lightrumor.ui.MultiCompareView
import com.lightrumor.ui.QuickOpenScreen
import java.io.File

/**
 * Main Activity of light_rumor.
 * Seamlessly transitions from QuickOpen to Culling Screen or Compare Mode in < 0.1s.
 */
class MainActivity : ComponentActivity() {

    private lateinit var pickerLauncher: PhotoPickerLauncher
    private lateinit var cacheManager: CullingCacheManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cacheManager = CullingCacheManager(this)

        setContent {
            var photoItems by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
            var currentIndex by remember { mutableStateOf(0) }
            var isCompareMode by remember { mutableStateOf(false) }
            var isBatchSyncOpen by remember { mutableStateOf(false) }
            var activeDevelopItem by remember { mutableStateOf<PhotoItem?>(null) }

            // Initialize photo picker with fast state updater
            LaunchedEffect(Unit) {
                pickerLauncher = PhotoPickerLauncher(this@MainActivity) { selectedUris ->
                    val newItems = selectedUris.map { uri ->
                        val path = uri.path ?: ""
                        val name = File(path).name.ifEmpty { "PHOTO_${System.currentTimeMillis() % 10000}" }
                        PhotoItem(
                            uri = uri,
                            filePath = path,
                            fileName = name,
                            isRaw = ThumbnailLoader.isRawFile(name)
                        )
                    }
                    photoItems = newItems
                    currentIndex = 0
                }

                // Check if launched with URIs from IntentHandlerActivity
                val intentUris = intent.getParcelableArrayListExtra<Uri>(IntentHandlerActivity.EXTRA_PHOTO_URIS)
                if (intentUris != null && intentUris.isNotEmpty()) {
                    val newItems = intentUris.map { uri ->
                        val path = uri.path ?: ""
                        val name = File(path).name.ifEmpty { "PHOTO_${System.currentTimeMillis() % 10000}" }
                        PhotoItem(
                            uri = uri,
                            filePath = path,
                            fileName = name,
                            isRaw = ThumbnailLoader.isRawFile(name)
                        )
                    }
                    photoItems = newItems
                    currentIndex = 0
                }
            }

            when {
                // 1. Landing Screen (when no photos are loaded)
                photoItems.isEmpty() -> {
                    QuickOpenScreen(
                        onOpenSinglePhoto = { pickerLauncher.openSinglePhoto() },
                        onOpenMultiplePhotos = { pickerLauncher.openMultiplePhotos() },
                        onOpenDesktopFileDialog = {
                            PhotoPickerLauncher.openDesktopFileDialog { files ->
                                val items = files.map { f ->
                                    PhotoItem(
                                        uri = Uri.fromFile(f),
                                        filePath = f.absolutePath,
                                        fileName = f.name,
                                        isRaw = ThumbnailLoader.isRawFile(f.name)
                                    )
                                }
                                photoItems = items
                                currentIndex = 0
                            }
                        }
                    )
                }

                // 2. Master Photographic Development Studio Screen (Phase 3)
                activeDevelopItem != null -> {
                    com.lightrumor.ui.DevelopStudioScreen(
                        photoItem = activeDevelopItem!!,
                        cacheManager = cacheManager,
                        onBack = { activeDevelopItem = null }
                    )
                }

                // 3. Synchronized 2-Screen / 4-Screen Compare Mode
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

                // 4. Primary Zero-Delay Culling Screen
                else -> {
                    CullingScreen(
                        items = photoItems,
                        currentIndex = currentIndex,
                        onIndexChanged = { newIdx -> currentIndex = newIdx },
                        cacheManager = cacheManager,
                        onOpenMultiCompare = { isCompareMode = true },
                        onOpenBatchSync = { isBatchSyncOpen = true },
                        onOpenDevelop = { item -> activeDevelopItem = item },
                        onBackToLauncher = { photoItems = emptyList() }
                    )

                    // Batch sync dialog overlay
                    if (isBatchSyncOpen && photoItems.isNotEmpty()) {
                        val currentItem = photoItems[currentIndex]
                        val targetItems = photoItems.filterIndexed { idx, _ -> idx != currentIndex }
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

