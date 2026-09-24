package com.lightrumor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max

data class PhotoItem(
    val uri: Uri,
    val filePath: String = "",
    val fileName: String = "",
    val isRaw: Boolean = false,
    var metadata: CullingItemMetadata = CullingItemMetadata(id = uri.toString(), filePath = filePath),
    var developParams: DevelopmentParams = DevelopmentParams(),
    var maskLayers: List<MaskLayerState> = emptyList()
)

data class CullingItemMetadata(
    val id: String,
    val filePath: String,
    var rating: Int = 0,               // 0 to 5 stars
    var pickStatus: PickStatus = PickStatus.NONE,
    var colorLabel: ColorLabel = ColorLabel.NONE,
    var width: Int = 0,
    var height: Int = 0,
    var cameraModel: String = "",
    var cameraMake: String = "",
    var lensModel: String = "",
    var exposureTime: String = "",     // e.g. "1/250s"
    var fNumber: String = "",          // e.g. "f/2.8"
    var isoSpeed: String = "",         // e.g. "ISO 100"
    var focalLength: String = "",      // e.g. "50mm"
    var captureDate: String = "",
    var isRaw: Boolean = false
)

/**
 * High-speed thumbnail extractor for RAW and non-RAW images.
 * Extracts embedded JPEG thumbnails in < 10ms for instant previewing and zero-delay culling.
 */
object ThumbnailLoader {

    private val RAW_EXTENSIONS = setOf("arw", "cr2", "cr3", "nef", "dng", "raf", "orf", "rw2", "pef")

    fun isRawFile(fileNameOrPath: String): Boolean {
        val ext = fileNameOrPath.substringAfterLast('.', "").lowercase()
        return RAW_EXTENSIONS.contains(ext)
    }

    /**
     * Loads high-performance preview bitmap for culling display.
     * Target size defaults to 1440px (sufficient for 100% sharp culling on mobile and desktop).
     */
    suspend fun loadPreviewBitmap(
        context: Context?,
        item: PhotoItem,
        targetWidth: Int = 1440,
        targetHeight: Int = 1440
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 1. Android 10+ (API 29+) System Native Fast Thumbnail API (<5ms)
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.uri.scheme == "content") {
                try {
                    val size = android.util.Size(targetWidth.coerceAtMost(1080), targetHeight.coerceAtMost(1080))
                    val nativeThumb = context.contentResolver.loadThumbnail(item.uri, size, null)
                    val softThumb = if (nativeThumb.config == Bitmap.Config.HARDWARE) {
                        val copied = nativeThumb.copy(Bitmap.Config.ARGB_8888, false)
                        if (copied != null) {
                            nativeThumb.recycle()
                            copied
                        } else {
                            nativeThumb
                        }
                    } else {
                        nativeThumb
                    }
                    return@withContext softThumb
                } catch (_: Throwable) {}
            }

            // 2. If it's a RAW file with a direct path, attempt native embedded thumbnail extraction (<10ms)
            if (item.filePath.isNotEmpty() && isRawFile(item.filePath)) {
                val thumbBytes = LightRumorNativeEngine.extractThumbnailBytes(item.filePath)
                if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                    val bmp = decodeSampledBitmapFromByteArray(thumbBytes, targetWidth, targetHeight)
                    if (bmp != null) {
                        return@withContext fixOrientationFromSource(context, item.uri, item.filePath, bmp)
                    }
                }
            }

            // 3. Load via ContentResolver or File with streaming downsampling (OOM safe)
            val bmp = decodeSampledBitmapFromSource(context, item.uri, item.filePath, targetWidth, targetHeight)
            if (bmp != null) {
                return@withContext fixOrientationFromSource(context, item.uri, item.filePath, bmp)
            }

            null
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extract EXIF metadata (camera model, exposure, aperture, ISO, focal length).
     */
    suspend fun extractExif(context: Context?, item: PhotoItem): CullingItemMetadata = withContext(Dispatchers.IO) {
        val meta = item.metadata
        try {
            val inputStream = openInputStream(context, item.uri, item.filePath)
            if (inputStream != null) {
                inputStream.use { stream ->
                    val exif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        ExifInterface(stream)
                    } else {
                        if (item.filePath.isNotEmpty()) ExifInterface(item.filePath) else null
                    }
                    if (exif != null) {
                        val make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: ""
                        val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: ""
                        meta.cameraMake = make
                        meta.cameraModel = if (model.startsWith(make, ignoreCase = true)) model else "$make $model".trim()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            meta.lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL) ?: ""
                        }
                        
                        val expTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                        meta.exposureTime = if (expTime > 0) {
                            if (expTime < 1.0) "1/${(1.0 / expTime).toInt()}s" else "${expTime}s"
                        } else ""

                        val fNum = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                        meta.fNumber = if (fNum > 0) "f/${fNum}" else ""

                        @Suppress("DEPRECATION")
                        val iso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val sens = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                            if (sens > 0) sens else exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0)
                        } else {
                            exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0)
                        }
                        meta.isoSpeed = if (iso > 0) "ISO $iso" else ""

                        val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                        meta.focalLength = if (focal > 0) "${focal.toInt()}mm" else ""

                        meta.captureDate = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: ""
                        meta.width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                        meta.height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                        meta.isRaw = isRawFile(item.filePath.ifEmpty { item.fileName })
                    }
                }
            }
        } catch (e: Throwable) {
            // Non-critical, continue with default metadata
        }
        meta
    }

    private fun openInputStream(context: Context?, uri: Uri, filePath: String): InputStream? {
        return try {
            if (context != null && uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)
            } else if (filePath.isNotEmpty()) {
                val file = File(filePath)
                if (file.exists()) file.inputStream() else null
            } else {
                val path = uri.path
                if (!path.isNullOrEmpty()) {
                    val file = File(path)
                    if (file.exists()) file.inputStream() else null
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeSampledBitmapFromByteArray(data: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    private fun decodeSampledBitmapFromSource(context: Context?, uri: Uri, filePath: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (filePath.isNotEmpty()) {
            val f = File(filePath)
            if (!f.exists() || f.length() == 0L) return null
            BitmapFactory.decodeFile(filePath, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            return BitmapFactory.decodeFile(filePath, options)
        } else if (context != null && uri.scheme == "content") {
            try {
                // Use openFileDescriptor to decode bounds and image with a single descriptor (avoids double open)
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    pfd.use {
                        val fd = it.fileDescriptor
                        BitmapFactory.decodeFileDescriptor(fd, null, options)
                        android.system.Os.lseek(fd, 0, android.system.OsConstants.SEEK_SET)
                        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                        options.inJustDecodeBounds = false
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888
                        return BitmapFactory.decodeFileDescriptor(fd, null, options)
                    }
                }
                // Fallback for providers that do not support FileDescriptor
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                return context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (_: Throwable) {
                return null
            }
        }
        return null
    }

    private fun fixOrientationFromSource(context: Context?, uri: Uri, filePath: String, bitmap: Bitmap): Bitmap {
        try {
            val exif = if (filePath.isNotEmpty()) {
                val f = File(filePath)
                if (f.exists()) ExifInterface(filePath) else null
            } else if (context != null && uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream)
                }
            } else null

            if (exif == null) return bitmap
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            return rotated
        } catch (_: Throwable) {
            return bitmap
        }
    }
}

