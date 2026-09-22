package com.lightrumor

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 端末情報・EXIFメタデータを実際の画像ファイルおよびXMPサイドカーに直接書き込み保存するライター。
 * - 写真ファイル自体のEXIFタグ (TAG_MODEL, TAG_MAKE, TAG_LENS_MODEL, TAG_F_NUMBER, TAG_EXPOSURE_TIME 等) を直接書き換え
 * - Adobe XMPサイドカー (.xmp) にも完全同期書き出し
 */
object DirectExifWriter {

    suspend fun writeMetadata(
        context: Context,
        item: PhotoItem,
        meta: CullingItemMetadata
    ): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false

        // 1. XMP サイドカーへの書き込み (ローカルファイルパスがある場合)
        if (item.filePath.isNotEmpty()) {
            try {
                XmpSidecarManager.writeSidecarDirect(
                    imageFilePath = item.filePath,
                    meta = meta,
                    params = item.developParams,
                    modifiedFields = setOf("cameraModel", "cameraMake", "lensModel", "fNumber", "exposureTime", "isoSpeed", "focalLength", "captureDate")
                )
                anySuccess = true
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        // 2. 実画像ファイル (JPEG / DNG / TIFF 等) の EXIF タグへの直接バイナリ書き込み
        // ネイティブRAW（ARW, CR2, CR3, NEF等）は ExifInterface の直接書き換えに対応しておらず例外となるため、
        // XMPサイドカー保存のみを行い、直接書き込みは非RAW（JPEG/TIFF/WebP/DNG等）のみに限定する。
        val isRaw = ThumbnailLoader.isRawFile(item.filePath.ifEmpty { item.fileName })
        if (isRaw) {
            return@withContext anySuccess
        }

        try {
            val isSupportedFormat = isWritableExifFormat(item.filePath, item.uri, context)
            if (isSupportedFormat) {
                var exifToSave: ExifInterface? = null

                if (item.filePath.isNotEmpty()) {
                    val f = File(item.filePath)
                    if (f.exists() && f.canWrite()) {
                        exifToSave = ExifInterface(f.absolutePath)
                    }
                }

                // content:// URI の場合、ParcelFileDescriptor を "rw" で開いて直接 EXIF を上書き保存
                if (exifToSave == null && item.uri.scheme == "content") {
                    try {
                        context.contentResolver.openFileDescriptor(item.uri, "rw")?.use { pfd ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                val fdExif = ExifInterface(pfd.fileDescriptor)
                                applyMetadataToExif(fdExif, meta)
                                fdExif.saveAttributes()
                                return@withContext true
                            }
                        }
                    } catch (e: Throwable) {
                        // content URI が読み取り専用の場合は XMP サイドカーのみ保存
                    }
                }

                if (exifToSave != null) {
                    applyMetadataToExif(exifToSave, meta)
                    exifToSave.saveAttributes()
                    anySuccess = true
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        anySuccess
    }

    private fun isWritableExifFormat(filePath: String, uri: Uri, context: Context): Boolean {
        if (filePath.isNotEmpty()) {
            val ext = filePath.substringAfterLast('.', "").lowercase()
            return ext in setOf("jpg", "jpeg", "dng", "webp")
        }
        val type = context.contentResolver.getType(uri)?.lowercase() ?: ""
        if (type.contains("jpeg") || type.contains("jpg") || type.contains("webp") || type.contains("dng")) {
            return true
        }
        val path = uri.path?.lowercase() ?: ""
        val ext = path.substringAfterLast('.', "")
        return ext in setOf("jpg", "jpeg", "dng", "webp")
    }

    private fun applyMetadataToExif(exif: ExifInterface, meta: CullingItemMetadata) {
        // 1. 端末情報 (カメラ機種名・メーカー・レンズ名)
        if (meta.cameraModel.isNotEmpty()) {
            exif.setAttribute(ExifInterface.TAG_MODEL, meta.cameraModel)
        }
        if (meta.cameraMake.isNotEmpty()) {
            exif.setAttribute(ExifInterface.TAG_MAKE, meta.cameraMake)
        } else if (meta.cameraModel.isNotEmpty()) {
            val autoMake = meta.cameraModel.split(" ").firstOrNull() ?: ""
            if (autoMake.isNotEmpty()) {
                exif.setAttribute(ExifInterface.TAG_MAKE, autoMake)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && meta.lensModel.isNotEmpty()) {
            exif.setAttribute(ExifInterface.TAG_LENS_MODEL, meta.lensModel)
        }

        // 2. 露出・光学パラメータ
        if (meta.fNumber.isNotEmpty()) {
            val fn = meta.fNumber.removePrefix("f/").trim().toDoubleOrNull()
            if (fn != null) {
                exif.setAttribute(ExifInterface.TAG_F_NUMBER, fn.toString())
            }
        }

        if (meta.exposureTime.isNotEmpty()) {
            val rawSec = parseExposureTimeToSeconds(meta.exposureTime)
            if (rawSec != null) {
                exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, rawSec.toString())
            }
        }

        if (meta.isoSpeed.isNotEmpty()) {
            val iso = meta.isoSpeed.replace(Regex("[^0-9]"), "")
            if (iso.isNotEmpty()) {
                exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, iso)
            }
        }

        if (meta.focalLength.isNotEmpty()) {
            val fl = meta.focalLength.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            if (fl != null) {
                val flInt = kotlin.math.round((fl * 10.0)).toLong()
                exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "$flInt/10")
            }
        }

        // 3. 撮影日時
        if (meta.captureDate.isNotEmpty()) {
            val formattedDate = meta.captureDate.replace("/", ":")
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formattedDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME, formattedDate)
        }
    }

    private fun parseExposureTimeToSeconds(text: String): Double? {
        val clean = text.removeSuffix("s").trim()
        if (clean.contains("/")) {
            val parts = clean.split("/")
            if (parts.size == 2) {
                val num = parts[0].toDoubleOrNull() ?: return null
                val den = parts[1].toDoubleOrNull() ?: return null
                if (den != 0.0) return num / den
            }
        }
        return clean.toDoubleOrNull()
    }
}
