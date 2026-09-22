package com.lightrumor

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Zero-Delay Two-Way LRU Caching and Prefetching Engine.
 * Combines an in-memory Bitmap LRU cache with an SSD disk cache, prefetching +/- 5 images
 * ahead of the current viewport to guarantee 0-millisecond flick latency with no loading spinners.
 */
class CullingCacheManager(
    private val context: Context?,
    private val diskCacheDir: File? = context?.cacheDir?.resolve("culling_cache")
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentPrefetchJob: Job? = null

    // 1. In-Memory LRU Cache (sized to 25% of available heap memory, in KB)
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val memoryCacheSizeKb = (maxMemoryKb / 4).coerceAtLeast(32 * 1024)

    private val memoryCache = object : LruCache<String, Bitmap>(memoryCacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    init {
        diskCacheDir?.mkdirs()
    }

    /**
     * Instantly returns cached Bitmap if present in memory (0ms access).
     */
    fun getFromMemory(keyOrRaw: String): Bitmap? {
        val direct = memoryCache.get(keyOrRaw)
        if (direct != null) return direct
        return memoryCache.get(md5(keyOrRaw))
    }

    fun getFromMemory(item: PhotoItem): Bitmap? {
        return memoryCache.get(getCacheKey(item))
    }

    /**
     * Asynchronously retrieves Bitmap from Memory -> Disk -> Loader,
     * placing decoded results in memory and notifying onLoaded callback.
     */
    fun loadBitmap(
        item: PhotoItem,
        targetWidth: Int = 1440,
        targetHeight: Int = 1440,
        onLoaded: (Bitmap) -> Unit
    ) {
        val key = getCacheKey(item)
        val memCached = memoryCache.get(key)
        if (memCached != null) {
            onLoaded(memCached)
            return
        }

        scope.launch {
            // Check SSD disk cache first
            val diskBmp = loadFromDisk(key)
            if (diskBmp != null) {
                memoryCache.put(key, diskBmp)
                withContext(Dispatchers.Main) {
                    onLoaded(diskBmp)
                }
                return@launch
            }

            // Extract from source file
            val loadedBmp = ThumbnailLoader.loadPreviewBitmap(context, item, targetWidth, targetHeight)
            if (loadedBmp != null) {
                memoryCache.put(key, loadedBmp)
                saveToDisk(key, loadedBmp)
                withContext(Dispatchers.Main) {
                    onLoaded(loadedBmp)
                }
            }
        }
    }

    /**
     * Predictive Prefetching: Prefetches +/- 5 images centered at currentIndex.
     * Prioritizes immediate neighbors (+1, -1, +2, -2...) to ensure 0ms flicking.
     */
    fun prefetchAround(currentIndex: Int, items: List<PhotoItem>, windowSize: Int = 5) {
        if (items.isEmpty()) return

        currentPrefetchJob?.cancel()

        // Interleaved priority sequence: +1, -1, +2, -2, +3, -3, +4, -4, +5, -5
        val offsets = mutableListOf<Int>()
        for (w in 1..windowSize) {
            offsets.add(w)
            offsets.add(-w)
        }

        currentPrefetchJob = scope.launch {
            for (offset in offsets) {
                if (!isActive) break
                val idx = currentIndex + offset
                if (idx in items.indices) {
                    val targetItem = items[idx]
                    val key = getCacheKey(targetItem)
                    if (memoryCache.get(key) == null) {
                        val bmp = loadFromDisk(key) ?: ThumbnailLoader.loadPreviewBitmap(context, targetItem)
                        if (bmp != null) {
                            memoryCache.put(key, bmp)
                            saveToDisk(key, bmp)
                        }
                    }
                }
            }
        }
    }

    /**
     * Clears all memory and disk caches.
     */
    suspend fun clearCache() = kotlinx.coroutines.withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        diskCacheDir?.listFiles()?.forEach { it.delete() }
    }

    /**
     * Cancels active background prefetch jobs and releases CoroutineScope resources.
     */
    fun cancel() {
        currentPrefetchJob?.cancel()
        scope.cancel()
    }

    private fun getCacheKey(item: PhotoItem): String {
        val raw = item.filePath.ifEmpty { item.uri.toString() }
        return md5(raw)
    }

    private fun loadFromDisk(key: String): Bitmap? {
        val file = File(diskCacheDir, "$key.cache")
        if (!file.exists()) return null
        return try {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Throwable) {
            null
        }
    }

    private fun saveToDisk(key: String, bitmap: Bitmap) {
        val dir = diskCacheDir ?: return
        try {
            val file = File(dir, "$key.cache")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            trimDiskCacheIfNeeded(dir, maxBytes = 250L * 1024L * 1024L)
        } catch (e: Throwable) {
            // Disk caching non-fatal
        }
    }

    private fun trimDiskCacheIfNeeded(dir: File, maxBytes: Long) {
        val files = dir.listFiles { f -> f.name.endsWith(".cache") } ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize > maxBytes) {
            val sorted = files.sortedBy { it.lastModified() }
            for (f in sorted) {
                val len = f.length()
                if (f.delete()) {
                    totalSize -= len
                    if (totalSize <= (maxBytes * 0.8).toLong()) break
                }
            }
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

