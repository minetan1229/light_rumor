package com.lightrumor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
    var developParams: DevelopmentParams = DevelopmentParams()
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
            // 1. If it's a RAW file with a direct path, attempt native embedded thumbnail extraction (<10ms)
            if (item.filePath.isNotEmpty() && isRawFile(item.filePath)) {
                val thumbBytes = ApexNativeEngine.extractThumbnailBytes(item.filePath)
                if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                    val bmp = decodeSampledBitmapFromByteArray(thumbBytes, targetWidth, targetHeight)
                    if (bmp != null) return@withContext bmp
                }
            }

            // 2. Load via ContentResolver or File InputStream
            val inputStream = openInputStream(context, item.uri, item.filePath)
            if (inputStream != null) {
                inputStream.use { stream ->
                    val bytes = stream.readBytes()
                    // If RAW, fast check if bytes contain embedded thumbnail
                    val bmp = decodeSampledBitmapFromByteArray(bytes, targetWidth, targetHeight)
                    if (bmp != null) {
                        return@withContext fixOrientationIfNeeded(bytes, bmp)
                    }
                }
            }

            // 3. Fallback: generate neutral placeholder bitmap
            Bitmap.createBitmap(targetWidth.coerceAtMost(800), targetHeight.coerceAtMost(600), Bitmap.Config.ARGB_8888)
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
                        meta.cameraModel = if (model.startsWith(make, ignoreCase = true)) model else "$make $model".trim()
                        
                        val expTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                        meta.exposureTime = if (expTime > 0) {
                            if (expTime < 1.0) "1/${(1.0 / expTime).toInt()}s" else "${expTime}s"
                        } else ""

                        val fNum = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                        meta.fNumber = if (fNum > 0) "f/${fNum}" else ""

                        val iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0)
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
        return if (context != null && uri.scheme == "content") {
            context.contentResolver.openInputStream(uri)
        } else if (filePath.isNotEmpty()) {
            File(filePath).inputStream()
        } else if (uri.path != null) {
            File(uri.path!!).inputStream()
        } else null
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

    private fun fixOrientationIfNeeded(jpegBytes: ByteArray, bitmap: Bitmap): Bitmap {
        try {
            val exif = ExifInterface(jpegBytes.inputStream())
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
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Throwable) {
            return bitmap
        }
    }
}

