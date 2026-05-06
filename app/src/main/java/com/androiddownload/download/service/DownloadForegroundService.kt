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
                val download = app.container.repository.getById(downloadId) ?: return@launch
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
