package com.sxsbsxs.dog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            runCatching { YoutubeDL.destroyProcessById(PROCESS_ID) }
            downloadJob?.cancel()
            DownloadState.canceled()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL)?.trim()
        if (url.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (downloadJob?.isActive == true) return START_NOT_STICKY

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("正在准备下载", 0, true))
        DownloadState.started()

        downloadJob = serviceScope.launch {
            val directory = downloadDirectory()
            val beforeDownload = fileSnapshot(directory)

            try {
                YtDlpEngine.initialize(applicationContext)

                val request = YoutubeDLRequest(url).apply {
                    addOption("-o", File(directory, "%(title).200B.%(ext)s").absolutePath)
                    addOption("-f", "bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                    addOption("--newline")
                    addOption("--progress")
                    addOption("--no-mtime")
                    addOption("--concurrent-fragments", "4")
                    addOption("--no-warnings")

                    // Native aria2c (C++) is used by yt-dlp for HTTP segment transfers.
                    addOption("--downloader", "libaria2c.so")
                }

                val cookie = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(COOKIE_KEY, "")
                    .orEmpty()
                    .removePrefix("Cookie:")
                    .trim()
                if (cookie.isNotEmpty()) {
                    request.addOption("--add-header", "Cookie:$cookie")
                }

                var lastProgress = -1
                YoutubeDL.getInstance().execute(request, PROCESS_ID) { progress, eta, _ ->
                    val percent = progress.toInt().coerceIn(0, 100)
                    val message = if (eta > 0) {
                        "正在下载 · $percent% · 剩余约 ${eta} 秒"
                    } else {
                        "正在下载 · $percent%"
                    }
                    DownloadState.updateProgress(percent, message)

                    if (percent != lastProgress) {
                        lastProgress = percent
                        runCatching {
                            getSystemService(NotificationManager::class.java)
                                .notify(NOTIFICATION_ID, notification(message, percent, true))
                        }
                    }
                }

                // Java MediaStore bridge publishes completed files into public Downloads/Dog.
                val finishedFiles = changedFiles(directory, beforeDownload)
                var publishedCount = 0
                finishedFiles.forEach { file ->
                    runCatching { MediaStorePublisher.publish(this@DownloadService, file) }
                        .onSuccess { publishedCount++ }
                }

                val savedLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && publishedCount > 0) {
                    "公共下载目录：Download/Dog（$publishedCount 个文件）"
                } else {
                    directory.absolutePath
                }

                DownloadState.completed(savedLocation)
                val doneMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && publishedCount > 0) {
                    "下载完成，已保存到 Download/Dog"
                } else {
                    "下载完成"
                }
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification(doneMessage, 100, false))
                stopForeground(STOP_FOREGROUND_DETACH)
            } catch (cancelled: CancellationException) {
                DownloadState.canceled()
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (error: Throwable) {
                val detail = error.message?.takeIf { it.isNotBlank() } ?: "未知错误"
                DownloadState.failed("下载失败：$detail")
                runCatching {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, notification("下载失败：$detail", 0, false))
                }
                stopForeground(STOP_FOREGROUND_DETACH)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun downloadDirectory(): File {
        val base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        return File(base, "Dog").apply { mkdirs() }
    }

    private fun fileSnapshot(root: File): Map<String, String> =
        root.walkTopDown()
            .filter { it.isFile && it.length() > 0L }
            .associate { it.absolutePath to "${it.length()}:${it.lastModified()}" }

    private fun changedFiles(root: File, before: Map<String, String>): List<File> =
        root.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.length() > 0L &&
                    !file.name.endsWith(".part", ignoreCase = true) &&
                    !file.name.endsWith(".ytdl", ignoreCase = true) &&
                    before[file.absolutePath] != "${file.length()}:${file.lastModified()}"
            }
            .toList()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "下载任务", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(message: String, progress: Int, ongoing: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Dog 下载器")
            .setContentText(message)
            .setContentIntent(openApp)
            .setProgress(100, progress.coerceIn(0, 100), ongoing && progress == 0)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .build()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val ACTION_CANCEL = "com.sxsbsxs.dog.CANCEL_DOWNLOAD"
        private const val CHANNEL_ID = "dog_downloads"
        private const val NOTIFICATION_ID = 7421
        private const val PROCESS_ID = "dog-download-process"
        private const val PREFERENCES = "dog_settings"
        private const val COOKIE_KEY = "cookie"

        fun start(context: Context, url: String) {
            val intent = Intent(context, DownloadService::class.java).putExtra(EXTRA_URL, url)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }
}
