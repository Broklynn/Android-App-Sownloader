package com.androiddownload.download.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus

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

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(download.fileName)
            .setContentText(download.status.name)
            .setOngoing(running)
            .setOnlyAlertOnce(true)
            .setProgress(100, download.progress, download.totalBytes <= 0 && running)
            .build()
    }

    fun notify(notificationId: Int, notification: Notification) {
        manager?.notify(notificationId, notification)
    }

    fun cancel(notificationId: Int) {
        manager?.cancel(notificationId)
    }

    companion object {
        const val CHANNEL_ID = "downloads"
    }
}
