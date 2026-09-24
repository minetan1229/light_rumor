package com.lightrumor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ExportService : Service() {

    companion object {
        const val CHANNEL_ID = "light_rumor_export_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_EXPORT = "com.lightrumor.action.START_EXPORT"
        const val ACTION_CANCEL_EXPORT = "com.lightrumor.action.CANCEL_EXPORT"

        const val EXTRA_INPUT_PATH = "extra_input_path"
        const val EXTRA_OUTPUT_PATH = "extra_output_path"
        const val EXTRA_PARAMS_JSON = "extra_params_json"

        fun startExport(context: Context, inputPath: String, outputPath: String, params: DevelopmentParams? = null) {
            val intent = Intent(context, ExportService::class.java).apply {
                action = ACTION_START_EXPORT
                putExtra(EXTRA_INPUT_PATH, inputPath)
                putExtra(EXTRA_OUTPUT_PATH, outputPath)
                if (params != null) {
                    putExtra(EXTRA_PARAMS_JSON, EditHistoryCatalog.paramsToJson(params))
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val activeTasks = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var isDestroyed = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()

        when (intent?.action) {
            ACTION_START_EXPORT -> {
                val inputPath = intent.getStringExtra(EXTRA_INPUT_PATH) ?: ""
                val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH) ?: ""
                val paramsJson = intent.getStringExtra(EXTRA_PARAMS_JSON)
                val params = if (!paramsJson.isNullOrEmpty()) {
                    EditHistoryCatalog.jsonToParams(paramsJson)
                } else {
                    DevelopmentParams()
                }
                if (inputPath.isNotEmpty() && outputPath.isNotEmpty()) {
                    acquireWakeLock()
                    executeExport(inputPath, outputPath, params, startId)
                } else {
                    stopForegroundCompat()
                    stopSelf(startId)
                }
            }
            ACTION_CANCEL_EXPORT -> {
                stopForegroundCompat()
                stopSelf(startId)
            }
            else -> {
                stopForegroundCompat()
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    @Synchronized
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LightRumor::ExportWakeLock")?.apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(60 * 60 * 1000L) // 1 hour max timeout
        }
    }

    @Synchronized
    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "light_rumor RAW エクスポートエンジン",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "高解像度 RAW 現像と写真エクスポートの進捗を表示"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = buildProgressNotification(0, "RAW 現像パイプライン準備中...")
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildProgressNotification(progress: Int, statusText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("light_rumor: 写真をエクスポート中")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateProgress(progress: Int, statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildProgressNotification(progress, statusText))
    }

    private fun executeExport(inputPath: String, outputPath: String, params: DevelopmentParams, startId: Int) {
        activeTasks.incrementAndGet()

        executor.execute {
            try {
                val format = when {
                    outputPath.endsWith(".tif", ignoreCase = true) || outputPath.endsWith(".tiff", ignoreCase = true) -> ExportFormat.TIFF16
                    outputPath.endsWith(".dng", ignoreCase = true) -> ExportFormat.LinearDNG
                    outputPath.endsWith(".webp", ignoreCase = true) -> ExportFormat.WebP
                    else -> ExportFormat.JPEG
                }
                val config = ExportConfig(
                    format = format,
                    jpegQuality = 98,
                    chromaSubsampling = ChromaSubsampling.YUV444,
                    tileSize = 2048,
                    tilePadding = 16
                )

                val success = LightRumorNativeEngine.exportPhoto(
                    inputPath = inputPath,
                    outputPath = outputPath,
                    config = config,
                    params = params,
                    callback = object : ProgressCallback {
                        override fun onProgress(progressPercent: Float, statusMessage: String) {
                            updateProgress(progressPercent.toInt(), statusMessage)
                        }
                    }
                )

                if (success) {
                    try {
                        val mimeType = when {
                            outputPath.endsWith(".jpg", ignoreCase = true) || outputPath.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                            outputPath.endsWith(".tif", ignoreCase = true) || outputPath.endsWith(".tiff", ignoreCase = true) -> "image/tiff"
                            outputPath.endsWith(".dng", ignoreCase = true) -> "image/x-adobe-dng"
                            outputPath.endsWith(".webp", ignoreCase = true) -> "image/webp"
                            else -> null
                        }
                        if (!isDestroyed) {
                            android.media.MediaScannerConnection.scanFile(
                                this@ExportService,
                                arrayOf(outputPath),
                                if (mimeType != null) arrayOf(mimeType) else null,
                                null
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (!isDestroyed) {
                        showCompletionNotification(outputPath)
                    }
                } else {
                    try {
                        val file = File(outputPath)
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    val file = File(outputPath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            } finally {
                if (activeTasks.decrementAndGet() <= 0) {
                    releaseWakeLock()
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 33) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun showCompletionNotification(outputPath: String) {
        val fileName = File(outputPath).name
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("エクスポート完了")
            .setContentText("$fileName に保存しました")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onDestroy() {
        isDestroyed = true
        super.onDestroy()
        releaseWakeLock()
        executor.shutdownNow()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

