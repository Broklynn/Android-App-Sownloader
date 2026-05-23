package com.androiddownload.ui.downloads

import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.FileSizeFormatter

object DownloadItemSizeFormatter {
    fun sizeText(download: DownloadEntity): String {
        return when {
            download.totalBytes > 0 -> {
                val downloaded = FileSizeFormatter.formatBytes(download.downloadedBytes)
                val total = FileSizeFormatter.formatBytes(download.totalBytes)
                "$downloaded / $total"
            }
            download.status == DownloadStatus.RUNNING ||
                download.status == DownloadStatus.PREPARING ||
                download.status == DownloadStatus.QUEUED -> {
                ""
            }
            download.progress > 0 -> "${download.progress.coerceIn(0, 100)}%"
            download.downloadedBytes > 0 -> FileSizeFormatter.formatBytes(download.downloadedBytes)
            else -> ""
        }
    }
}
