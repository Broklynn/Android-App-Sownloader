package com.androiddownload.ui.downloads

import com.androiddownload.core.model.DownloadStatus

data class DownloadStatusTextLabels(
    val queued: String,
    val preparing: String,
    val preparingProgress: String,
    val running: String,
    val paused: String,
    val completed: String,
    val failed: String,
    val canceled: String,
    val downloadingUnknown: String
)

object DownloadStatusTextFormatter {
    fun statusLabel(
        status: DownloadStatus,
        labels: DownloadStatusTextLabels
    ): String {
        return when (status) {
            DownloadStatus.QUEUED -> labels.queued
            DownloadStatus.PREPARING -> labels.preparing
            DownloadStatus.RUNNING -> labels.running
            DownloadStatus.PAUSED -> labels.paused
            DownloadStatus.FAILED -> labels.failed
            DownloadStatus.COMPLETED -> labels.completed
            DownloadStatus.CANCELED -> labels.canceled
        }
    }

    fun progressLabel(
        status: DownloadStatus,
        progress: Int,
        indeterminate: Boolean,
        labels: DownloadStatusTextLabels
    ): String {
        return when (status) {
            DownloadStatus.QUEUED -> labels.queued
            DownloadStatus.PREPARING -> labels.preparingProgress
            DownloadStatus.RUNNING -> {
                val prefix = if (indeterminate) "" else "$progress% "
                prefix + labels.downloadingUnknown
            }
            DownloadStatus.PAUSED -> if (progress > 0) {
                "$progress% ${labels.paused}"
            } else {
                labels.paused
            }
            DownloadStatus.COMPLETED -> "100% ${labels.completed}"
            DownloadStatus.FAILED -> labels.failed
            DownloadStatus.CANCELED -> labels.canceled
        }
    }
}
