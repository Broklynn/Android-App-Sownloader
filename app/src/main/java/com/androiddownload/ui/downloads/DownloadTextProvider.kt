package com.androiddownload.ui.downloads

import android.content.Context
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus

class DownloadTextProvider(
    private val context: Context
) {
    fun summarySizeText(download: DownloadEntity): String {
        return DownloadSummaryFormatter.summarySizeText(download)
    }

    fun typeBadgeLabel(download: DownloadEntity, formatLabel: String): String {
        return DownloadSummaryFormatter.typeBadgeLabel(download, formatLabel)
    }

    fun formatLabel(download: DownloadEntity): String {
        return DownloadFormatLabelFormatter.labelForDownload(context, download)
    }

    fun statusLabel(status: DownloadStatus): String {
        return DownloadStatusTextFormatter.statusLabel(
            status = status,
            labels = statusTextLabels()
        )
    }

    fun progressLabelForDetails(download: DownloadEntity): String {
        return DownloadStatusTextFormatter.progressLabel(
            status = download.status,
            progress = DownloadSummaryFormatter.normalizedProgress(download),
            indeterminate = DownloadSummaryFormatter.isIndeterminate(download),
            labels = statusTextLabels()
        )
    }

    private fun statusTextLabels(): DownloadStatusTextLabels {
        return DownloadStatusTextLabels(
            queued = context.getString(R.string.status_queued),
            preparing = context.getString(R.string.status_preparing),
            preparingProgress = context.getString(R.string.status_preparing_progress),
            running = context.getString(R.string.status_running),
            paused = context.getString(R.string.status_paused),
            completed = context.getString(R.string.status_completed),
            failed = context.getString(R.string.status_failed),
            canceled = context.getString(R.string.status_canceled),
            downloadingUnknown = context.getString(R.string.download_progress_unknown)
        )
    }
}
