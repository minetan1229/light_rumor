package com.lightrumor

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log

/**
 * OS Instant Launch Intent Router.
 * Handles incoming SEND, SEND_MULTIPLE, EDIT, and VIEW intents from Pixel Camera,
 * Google Photos, and 3rd-party camera apps, transitioning directly into light_rumor in < 0.2s.
 */
class IntentHandlerActivity : Activity() {

    companion object {
        private const val TAG = "IntentHandlerActivity"
        const val EXTRA_PHOTO_URIS = "com.lightrumor.extra.PHOTO_URIS"
        const val EXTRA_LAUNCH_ACTION = "com.lightrumor.extra.LAUNCH_ACTION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startTime = System.nanoTime()

        val incomingIntent = intent
        if (incomingIntent == null) {
            finish()
            return
        }

        val resolvedUris = extractUrisFromIntent(incomingIntent)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        Log.d(TAG, "Extracted ${resolvedUris.size} URIs in ${elapsedMs}ms from action: ${incomingIntent.action}")

        if (resolvedUris.isNotEmpty()) {
            // Persist read access for all extracted content URIs
            resolvedUris.forEach { uri ->
                PhotoPickerLauncher.persistUriAccess(this, uri)
            }

            // Route to MainActivity / Culling Screen
            val targetIntent = Intent(this, MainActivity::class.java).apply {
                action = incomingIntent.action
                putParcelableArrayListExtra(EXTRA_PHOTO_URIS, ArrayList<Parcelable>(resolvedUris))
                putExtra(EXTRA_LAUNCH_ACTION, incomingIntent.action)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(targetIntent)
        }

        finish()
    }

    private fun extractUrisFromIntent(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()

        when (intent.action) {
            Intent.ACTION_SEND -> {
                // Single image share (e.g. Pixel Camera "Share" button)
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
                if (uri != null) {
                    uris.add(uri)
                } else if (intent.data != null) {
                    uris.add(intent.data!!)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                // Multiple images share
                val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (streamUris != null) {
                    uris.addAll(streamUris)
                }

                // Also inspect ClipData if present
                val clipData = intent.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val itemUri = clipData.getItemAt(i).uri
                        if (itemUri != null && !uris.contains(itemUri)) {
                            uris.add(itemUri)
                        }
                    }
                }
            }

            Intent.ACTION_EDIT, Intent.ACTION_VIEW -> {
                // Direct edit or view from Camera app
                if (intent.data != null) {
                    uris.add(intent.data!!)
                }
            }
        }

        return uris
    }
}

