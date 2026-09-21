package com.lightrumor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/**
 * High-performance, zero-permission Photo Picker integration for Android 13/14+.
 * In accordance with privacy standards, MANAGE_EXTERNAL_STORAGE is strictly avoided.
 * Persistable URI permissions are taken automatically to allow seamless offline editing across sessions.
 */
class PhotoPickerLauncher(
    private val activity: ComponentActivity,
    private val onPhotosSelected: (List<Uri>) -> Unit
) {
    // Single photo picker contract
    private val singlePickerLauncher: ActivityResultLauncher<PickVisualMediaRequest> =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) {
                persistUriAccess(activity, uri)
                onPhotosSelected(listOf(uri))
            }
        }

    // Multiple photos picker contract (up to 100 images per batch)
    private val multiPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest> =
        activity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                uris.forEach { persistUriAccess(activity, it) }
                onPhotosSelected(uris)
            }
        }

    /**
     * Launch single image selection with standard Android Photo Picker.
     */
    fun openSinglePhoto() {
        singlePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /**
     * Launch multi-image selection with standard Android Photo Picker.
     */
    fun openMultiplePhotos() {
        multiPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    companion object {
        /**
         * Persist read permissions so the app retains access across process restarts and backgrounding.
         */
        fun persistUriAccess(context: Context, uri: Uri) {
            if (uri.scheme == "content") {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: SecurityException) {
                    // System already granted temporary read flag; persistable flag optional for transient intents
                }
            }
        }

        /**
         * Desktop Fallback: Opens system native file dialog for Windows / macOS / Linux.
         * Used when running Compose Desktop build.
         */
        fun openDesktopFileDialog(
            title: String = "Select Photos",
            allowMultiple: Boolean = true,
            callback: (List<File>) -> Unit
        ) {
            try {
                val fileDialogClass = Class.forName("java.awt.FileDialog")
                val frameClass = Class.forName("java.awt.Frame")
                val nullFrame: Any? = null
                val dialog = fileDialogClass.getConstructor(frameClass, String::class.java, Int::class.javaPrimitiveType)
                    .newInstance(nullFrame, title, 0) // 0 = FileDialog.LOAD

                // Set multiple mode if supported (Java 7+)
                val setMultipleMode = fileDialogClass.getMethod("setMultipleMode", Boolean::class.javaPrimitiveType)
                setMultipleMode.invoke(dialog, allowMultiple)

                val setVisible = fileDialogClass.getMethod("setVisible", Boolean::class.javaPrimitiveType)
                setVisible.invoke(dialog, true)

                val getFiles = fileDialogClass.getMethod("getFiles")
                val files = getFiles.invoke(dialog) as? Array<File>
                if (files != null && files.isNotEmpty()) {
                    callback(files.toList())
                }
            } catch (e: Throwable) {
                // Fallback for non-AWT headless or test environments
                callback(emptyList())
            }
        }
    }
}

