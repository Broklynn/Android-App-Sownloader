package com.androiddownload.download.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.IBinder
import com.androiddownload.AndroidDownloadApp
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.DownloadSourceClassifier
import com.androiddownload.core.utils.YtDlpDiagnostics
import com.androiddownload.download.http.DownloadMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningJobs = ConcurrentHashMap<Long, Job>()
    private val notificationStates = ConcurrentHashMap<Long, NotificationState>()
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        app.container.notifier.ensureChannel()
        YtDlpDiagnostics.record(
            context = this,
            url = "app",
            option = "notificacao",
            attempt = "progresso",
            result = "throttle notificacao aplicado: 500ms/1s",
            type = "desempenho"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        if (downloadId <= 0L) return START_NOT_STICKY

        return when (intent?.action) {
            ACTION_CANCEL -> {
                cancelDownload(downloadId)
                START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseDownload(downloadId)
                START_NOT_STICKY
            }
            ACTION_RESUME -> {
                startDownload(downloadId, DownloadMode.RESUME)
                START_NOT_STICKY
            }
            ACTION_RETRY -> {
                startDownload(downloadId, DownloadMode.RETRY)
                START_NOT_STICKY
            }
            else -> {
                startDownload(downloadId, DownloadMode.NORMAL)
                START_NOT_STICKY
            }
        }
    }

    private fun startDownload(downloadId: Long, mode: DownloadMode = DownloadMode.NORMAL) {
        startForeground(FOREGROUND_NOTIFICATION_ID, app.container.notifier.buildInitial())
        foregroundStarted = true
        if (runningJobs.containsKey(downloadId)) return

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val original = app.container.repository.getById(downloadId) ?: return@launch
                val download = if (mode == DownloadMode.RETRY) {
                    prepareManualRetry(original)
                } else {
                    original
                }
                if (DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl)) {
                    app.container.downloader.download(downloadId, mode) {
                        app.container.repository.getById(downloadId)?.let { current ->
                            notifyProgressIfNeeded(current)
                        }
                    }
                } else {
                    app.container.ytDlpDownloader.download(
                        downloadId = downloadId,
                        formatSelector = download.qualitySelector ?: "best"
                    ) {
                        app.container.repository.getById(downloadId)?.let { current ->
                            notifyProgressIfNeeded(current)
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    runningJobs.remove(downloadId)
                    if (mode == DownloadMode.RETRY) {
                        app.container.repository.getById(downloadId)?.let { finished ->
                            recordManualRetry(
                                download = finished,
                                kind = if (DownloadSourceClassifier.shouldUseHttpDownloader(finished.sourceUrl)) {
                                    "HTTP direto"
                                } else {
                                    "yt-dlp"
                                },
                                result = "retry manual finalizado: ${finished.status.name.lowercase()}"
                            )
                        }
                    }
                    finishIfIdle(downloadId)
                }
            }
        }

        val existing = runningJobs.putIfAbsent(downloadId, job)
        if (existing == null) {
            job.start()
        } else {
            job.cancel()
        }
    }

    private fun cancelDownload(downloadId: Long) {
        app.container.downloader.cancel(downloadId)
        app.container.ytDlpDownloader.cancel(downloadId)
        runningJobs.remove(downloadId)?.cancel()

        scope.launch {
            app.container.repository.markCanceled(downloadId)
            notificationStates.remove(downloadId)
            finishIfIdle(downloadId)
        }
    }

    private suspend fun prepareManualRetry(download: DownloadEntity): DownloadEntity {
        val isHttp = DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl)
        return if (isHttp) {
            prepareHttpManualRetry(download)
        } else {
            prepareYtDlpManualRetry(download)
        }
    }

    private suspend fun prepareHttpManualRetry(download: DownloadEntity): DownloadEntity {
        val tempFile = File(cacheDir, "downloads/${download.id}.part")
        val canReuseTemp = tempFile.isFile && tempFile.length() > 0L && download.downloadedBytes > 0L
        val downloadedBytes = if (canReuseTemp) {
            minOf(download.downloadedBytes, tempFile.length())
        } else {
            cleanupTempPath(download.tempPath)
            cleanupTempPath(tempFile.absolutePath)
            0L
        }
        val totalBytes = if (canReuseTemp && download.totalBytes > downloadedBytes) {
            download.totalBytes
        } else {
            -1L
        }
        if (canReuseTemp && download.tempPath != tempFile.absolutePath) {
            cleanupTempPath(download.tempPath)
        }
        val retryDownload = download.copy(
            destinationUri = null,
            tempPath = if (canReuseTemp) tempFile.absolutePath else null,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            progress = calculateProgress(downloadedBytes, totalBytes),
            speed = 0,
            status = DownloadStatus.QUEUED,
            errorMessage = null
        )
        app.container.repository.update(retryDownload)
        recordManualRetry(
            download = retryDownload,
            kind = "HTTP direto",
            result = if (canReuseTemp) {
                "retry manual solicitado; reaproveitou tempPath"
            } else {
                "retry manual solicitado; reiniciou do zero"
            }
        )
        notifyProgressIfNeeded(retryDownload, force = true)
        return retryDownload
    }

    private suspend fun prepareYtDlpManualRetry(download: DownloadEntity): DownloadEntity {
        val tempDir = File(cacheDir, "ytdlp/${download.id}")
        val cleanedOldTemp = cleanupTempPath(download.tempPath) || cleanupTempPath(tempDir.absolutePath)
        val retryDownload = download.copy(
            destinationUri = null,
            tempPath = null,
            totalBytes = -1,
            downloadedBytes = 0,
            progress = 0,
            speed = 0,
            status = DownloadStatus.QUEUED,
            errorMessage = null
        )
        app.container.repository.update(retryDownload)
        recordManualRetry(
            download = retryDownload,
            kind = "yt-dlp",
            result = if (cleanedOldTemp) {
                "retry manual solicitado; limpou temp antigo; reiniciou do zero"
            } else {
                "retry manual solicitado; reiniciou do zero"
            }
        )
        notifyProgressIfNeeded(retryDownload, force = true)
        return retryDownload
    }

    private fun cleanupTempPath(path: String?): Boolean {
        val cleanPath = path?.trim().orEmpty()
        if (cleanPath.isBlank()) return false
        return runCatching {
            val file = File(cleanPath)
            if (!file.exists()) return@runCatching false
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }.getOrDefault(false)
    }

    private fun calculateProgress(downloadedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        return ((downloadedBytes * 100) / totalBytes).coerceIn(0, 100).toInt()
    }

    private fun recordManualRetry(
        download: DownloadEntity,
        kind: String,
        result: String
    ) {
        YtDlpDiagnostics.record(
            context = this,
            url = download.sourceUrl,
            option = kind,
            attempt = "retry manual",
            result = result,
            type = "retry"
        )
    }

    private fun pauseDownload(downloadId: Long) {
        app.container.downloader.pause(downloadId)
        runningJobs.remove(downloadId)?.cancel()

        scope.launch {
            app.container.repository.markPaused(downloadId)
            app.container.repository.getById(downloadId)?.let { current ->
                notifyProgressIfNeeded(current, force = true)
            }
            finishIfIdle(downloadId)
        }
    }

    private suspend fun finishIfIdle(downloadId: Long) {
        val download = app.container.repository.getById(downloadId)
        if (runningJobs.isEmpty()) {
            when (download?.status) {
                DownloadStatus.CANCELED,
                DownloadStatus.FAILED -> {
                    download?.let { notifyProgressIfNeeded(it, force = true) }
                    stopForegroundIfStarted(STOP_FOREGROUND_REMOVE)
                    app.container.notifier.cancel(FOREGROUND_NOTIFICATION_ID)
                    notificationStates.remove(downloadId)
                }
                DownloadStatus.PAUSED,
                DownloadStatus.COMPLETED -> {
                    download?.let {
                        notifyProgressIfNeeded(it, force = true)
                    }
                    stopForegroundIfStarted(STOP_FOREGROUND_DETACH)
                    if (download.status == DownloadStatus.COMPLETED) {
                        notificationStates.remove(downloadId)
                    }
                }
                else -> {
                    stopForegroundIfStarted(STOP_FOREGROUND_DETACH)
                }
            }
            stopSelf()
        } else {
            notifyFirstRunningDownload()
        }
    }

    private suspend fun notifyFirstRunningDownload() {
        val runningId = runningJobs.keys.firstOrNull() ?: return
        val download = app.container.repository.getById(runningId) ?: return
        notifyProgressIfNeeded(download, force = true)
    }

    private fun notifyProgressIfNeeded(download: DownloadEntity, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val previous = notificationStates[download.id]
        if (!force && !shouldNotify(download, previous, now)) return

        val progressSnapshot = app.container.ytDlpDownloader.getProgressSnapshot(download.id)
        app.container.notifier.notify(
            FOREGROUND_NOTIFICATION_ID,
            app.container.notifier.buildProgress(
                download,
                progressSnapshot
            )
        )
        notificationStates[download.id] = NotificationState(
            status = download.status,
            progress = download.progress,
            downloadedBytes = download.downloadedBytes,
            totalBytes = download.totalBytes,
            updatedAt = now
        )
    }

    private fun shouldNotify(
        download: DownloadEntity,
        previous: NotificationState?,
        now: Long
    ): Boolean {
        if (previous == null) return true
        if (download.status != previous.status) return true
        if (download.status in IMPORTANT_STATUSES) return true
        if ((download.progress == 0 || download.progress == 100) && download.progress != previous.progress) return true
        val elapsedMs = now - previous.updatedAt
        if (download.progress != previous.progress && elapsedMs >= NOTIFICATION_MIN_INTERVAL_MS) return true
        return elapsedMs >= NOTIFICATION_HEARTBEAT_INTERVAL_MS &&
            (download.downloadedBytes != previous.downloadedBytes || download.totalBytes != previous.totalBytes)
    }

    private fun stopForegroundIfStarted(option: Int) {
        if (!foregroundStarted) return
        stopForeground(option)
        foregroundStarted = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private val app: AndroidDownloadApp
        get() = application as AndroidDownloadApp

    private data class NotificationState(
        val status: DownloadStatus,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val updatedAt: Long
    )

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        const val ACTION_START = "com.androiddownload.action.START_DOWNLOAD"
        const val ACTION_CANCEL = "com.androiddownload.action.CANCEL_DOWNLOAD"
        const val ACTION_PAUSE = "com.androiddownload.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.androiddownload.action.RESUME_DOWNLOAD"
        const val ACTION_RETRY = "com.androiddownload.action.RETRY_DOWNLOAD"
        private const val NOTIFICATION_MIN_INTERVAL_MS = 500L
        private const val NOTIFICATION_HEARTBEAT_INTERVAL_MS = 1000L
        private val IMPORTANT_STATUSES = setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.PREPARING,
            DownloadStatus.PAUSED,
            DownloadStatus.COMPLETED,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELED
        )

        fun start(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadForegroundService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            context.startService(intent)
        }

        fun pause(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadForegroundService::class.java)
                .setAction(ACTION_PAUSE)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            context.startService(intent)
        }

        fun resume(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadForegroundService::class.java)
                .setAction(ACTION_RESUME)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun retry(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadForegroundService::class.java)
                .setAction(ACTION_RETRY)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
