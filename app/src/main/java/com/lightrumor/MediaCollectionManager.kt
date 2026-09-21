package com.lightrumor

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 端末内の写真（MediaStore）からコレクション写真一覧を非同期に取得するマネージャー。
 * Android 13+ (READ_MEDIA_IMAGES) および Android 12以前 (READ_EXTERNAL_STORAGE) の権限状態に対応。
 */
object MediaCollectionManager {

    fun hasPermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getRequiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    suspend fun queryDevicePhotos(context: Context, limit: Int = 120): List<PhotoItem> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<PhotoItem>()
        if (!hasPermission(context)) {
            return@withContext photos
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATA
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        try {
            context.contentResolver.query(queryUri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                var count = 0

                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val name = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val dateTaken = if (dateCol >= 0) cursor.getLong(dateCol) else 0L

                    val item = PhotoItem(
                        uri = contentUri,
                        filePath = path,
                        fileName = name,
                        isRaw = ThumbnailLoader.isRawFile(name)
                    ).apply {
                        if (dateTaken > 0) {
                            metadata.captureDate = dateFormat.format(Date(dateTaken))
                        }
                    }
                    photos.add(item)
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        photos
    }
}
