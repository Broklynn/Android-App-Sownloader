package com.androiddownload.download.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.androiddownload.AndroidDownloadApp
import com.androiddownload.core.model.DownloadStatus
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
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        app.container.notifier.ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        if (downloadId <= 0L) return START_NOT_STICKY

        return when (intent?.action) {
            ACTION_CANCEL -> {
                cancelDownload(downloadId)
                START_NOT_STICKY
            }
            else -> {
                startDownload(downloadId)
                START_NOT_STICKY
            }
        }
    }

    private fun startDownload(downloadId: Long) {
        startForeground(FOREGROUND_NOTIFICATION_ID, app.container.notifier.buildInitial())
        foregroundStarted = true
        if (runningJobs.containsKey(downloadId)) return

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                app.container.downloader.download(downloadId) {
                    val download = app.container.repository.getById(downloadId) ?: return@download
                    app.container.notifier.notify(
                        FOREGROUND_NOTIFICATION_ID,
                        app.container.notifier.buildProgress(download)
                    )
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
        runningJobs.remove(downloadId)?.cancel()

        scope.launch {
            app.container.repository.markCanceled(downloadId)
            finishIfIdle(downloadId)
        }
    }

    private suspend fun finishIfIdle(downloadId: Long) {
        val download = app.container.repository.getById(downloadId)
        if (runningJobs.isEmpty()) {
            if (download?.status == DownloadStatus.CANCELED) {
                stopForegroundIfStarted(STOP_FOREGROUND_REMOVE)
                app.container.notifier.cancel(FOREGROUND_NOTIFICATION_ID)
            } else {
                stopForegroundIfStarted(STOP_FOREGROUND_DETACH)
            }
            stopSelf()
        } else {
            notifyFirstRunningDownload()
        }
    }

    private suspend fun notifyFirstRunningDownload() {
        val runningId = runningJobs.keys.firstOrNull() ?: return
        val download = app.container.repository.getById(runningId) ?: return
        app.container.notifier.notify(
            FOREGROUND_NOTIFICATION_ID,
            app.container.notifier.buildProgress(download)
        )
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

    companion object {
        private const val EXTRA_DOWNLOAD_ID = "download_id"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        private const val ACTION_START = "com.androiddownload.action.START_DOWNLOAD"
        private const val ACTION_CANCEL = "com.androiddownload.action.CANCEL_DOWNLOAD"

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
    }
}
