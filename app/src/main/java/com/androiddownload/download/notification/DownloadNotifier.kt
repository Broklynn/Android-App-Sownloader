package com.androiddownload.download.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.androiddownload.MainActivity
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.download.service.DownloadForegroundService
import com.androiddownload.core.utils.DownloadSourceClassifier

class DownloadNotifier(
    private val context: Context
) {
    private val manager: NotificationManager?
        get() = context.getSystemService()

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progresso dos downloads"
        }
        manager?.createNotificationChannel(channel)
    }

    fun buildInitial(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Preparando download")
            .setContentIntent(downloadsPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
            .build()
    }

    fun buildProgress(download: DownloadEntity): Notification {
        ensureChannel()
        val running = download.status == DownloadStatus.RUNNING ||
            download.status == DownloadStatus.PREPARING ||
            download.status == DownloadStatus.QUEUED
        val isHttpDownload = DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(download.fileName)
            .setContentText(download.status.name)
            .setContentIntent(downloadsPendingIntent())
            .setAutoCancel(!running)
            .setOngoing(running)
            .setOnlyAlertOnce(true)
            .setProgress(100, download.progress, download.totalBytes <= 0 && running)

        when {
            download.status == DownloadStatus.RUNNING && isHttpDownload -> {
                builder.addAction(pauseAction(download.id))
                builder.addAction(cancelAction(download.id))
            }
            download.status == DownloadStatus.PAUSED && isHttpDownload -> {
                builder.addAction(resumeAction(download.id))
                builder.addAction(cancelAction(download.id))
            }
            download.status == DownloadStatus.RUNNING && !isHttpDownload -> {
                builder.addAction(cancelAction(download.id))
            }
            download.status == DownloadStatus.COMPLETED -> {
                builder.addAction(openAction(download.id))
            }
        }

        return builder.build()
    }

    private fun downloadsPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openDownloadPendingIntent(downloadId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_DOWNLOAD_ID, downloadId)
        return PendingIntent.getActivity(
            context,
            notificationRequestCode(downloadId, ACTION_OPEN_REQUEST_OFFSET),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pauseAction(downloadId: Long): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_pause,
            context.getString(R.string.pause),
            servicePendingIntent(downloadId, DownloadForegroundService.ACTION_PAUSE, ACTION_PAUSE_REQUEST_OFFSET)
        ).build()
    }

    private fun resumeAction(downloadId: Long): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_play,
            context.getString(R.string.resume),
            servicePendingIntent(downloadId, DownloadForegroundService.ACTION_RESUME, ACTION_RESUME_REQUEST_OFFSET)
        ).build()
    }

    private fun cancelAction(downloadId: Long): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            context.getString(R.string.cancel),
            servicePendingIntent(downloadId, DownloadForegroundService.ACTION_CANCEL, ACTION_CANCEL_REQUEST_OFFSET)
        ).build()
    }

    private fun openAction(downloadId: Long): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            context.getString(R.string.open),
            openDownloadPendingIntent(downloadId)
        ).build()
    }

    private fun servicePendingIntent(
        downloadId: Long,
        action: String,
        requestCodeOffset: Int
    ): PendingIntent {
        val intent = Intent(context, DownloadForegroundService::class.java)
            .setAction(action)
            .putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, downloadId)
        return PendingIntent.getService(
            context,
            notificationRequestCode(downloadId, requestCodeOffset),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notificationRequestCode(downloadId: Long, offset: Int): Int {
        return (downloadId xor offset.toLong()).toInt()
    }

    fun notify(notificationId: Int, notification: Notification) {
        manager?.notify(notificationId, notification)
    }

    fun cancel(notificationId: Int) {
        manager?.cancel(notificationId)
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        private const val ACTION_PAUSE_REQUEST_OFFSET = 1
        private const val ACTION_RESUME_REQUEST_OFFSET = 2
        private const val ACTION_CANCEL_REQUEST_OFFSET = 3
        private const val ACTION_OPEN_REQUEST_OFFSET = 4
    }
}
