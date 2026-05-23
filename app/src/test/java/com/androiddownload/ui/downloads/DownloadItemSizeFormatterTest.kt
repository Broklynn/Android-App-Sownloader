package com.androiddownload.ui.downloads

import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadItemSizeFormatterTest {
    @Test
    fun sizeTextWithKnownTotalShowsDownloadedAndTotal() {
        val download = download(downloadedBytes = 1024, totalBytes = 2048)

        assertEquals("1.0 KB / 2.0 KB", DownloadItemSizeFormatter.sizeText(download))
    }

    @Test
    fun runningWithoutTotalAndWithProgressIsEmpty() {
        val download = download(status = DownloadStatus.RUNNING, totalBytes = -1, progress = 42)

        assertEquals("", DownloadItemSizeFormatter.sizeText(download))
    }

    @Test
    fun runningWithoutTotalAndWithDownloadedBytesIsEmpty() {
        val download = download(status = DownloadStatus.RUNNING, totalBytes = -1, downloadedBytes = 1536)

        assertEquals("", DownloadItemSizeFormatter.sizeText(download))
    }

    @Test
    fun preparingWithoutTotalAndWithProgressOrDownloadedBytesIsEmpty() {
        val download = download(
            status = DownloadStatus.PREPARING,
            totalBytes = -1,
            downloadedBytes = 1536,
            progress = 42
        )

        assertEquals("", DownloadItemSizeFormatter.sizeText(download))
    }

    @Test
    fun queuedWithoutTotalAndWithProgressOrDownloadedBytesIsEmpty() {
        val download = download(
            status = DownloadStatus.QUEUED,
            totalBytes = -1,
            downloadedBytes = 1536,
            progress = 42
        )

        assertEquals("", DownloadItemSizeFormatter.sizeText(download))
    }

    @Test
    fun pausedWithoutTotalAndWithProgressShowsProgress() {
        val download = download(status = DownloadStatus.PAUSED, totalBytes = -1, progress = 42)

        assertEquals("42%", DownloadItemSizeFormatter.sizeText(download))
    }

    @Test
    fun failedWithoutTotalAndWithProgressShowsProgress() {
        val download = download(status = DownloadStatus.FAILED, totalBytes = -1, progress = 42)

        assertEquals("42%", DownloadItemSizeFormatter.sizeText(download))
    }

    @Test
    fun canceledWithoutTotalAndWithProgressShowsProgress() {
        val download = download(status = DownloadStatus.CANCELED, totalBytes = -1, progress = 42)

        assertEquals("42%", DownloadItemSizeFormatter.sizeText(download))
    }

    @Test
    fun completedWithoutTotalAndWithProgressShowsProgress() {
        val download = download(status = DownloadStatus.COMPLETED, totalBytes = -1, progress = 42)

        assertEquals("42%", DownloadItemSizeFormatter.sizeText(download))
    }

    @Test
    fun progressHasPriorityOverDownloadedBytesWhenTotalIsMissing() {
        val download = download(
            status = DownloadStatus.PAUSED,
            totalBytes = -1,
            downloadedBytes = 1536,
            progress = 42
        )

        assertEquals("42%", DownloadItemSizeFormatter.sizeText(download))
    }

    @Test
    fun downloadedBytesAreShownWhenTotalAndProgressAreMissing() {
        val download = download(
            status = DownloadStatus.PAUSED,
            totalBytes = -1,
            downloadedBytes = 1536,
            progress = 0
        )

        assertEquals("1.5 KB", DownloadItemSizeFormatter.sizeText(download))
    }

    @Test
    fun sizeTextWithNoTotalProgressOrDownloadedBytesIsEmpty() {
        val download = download(status = DownloadStatus.PAUSED, totalBytes = -1, downloadedBytes = 0, progress = 0)

        assertEquals("", DownloadItemSizeFormatter.sizeText(download))
    }

    private fun download(
        status: DownloadStatus = DownloadStatus.RUNNING,
        downloadedBytes: Long = 0,
        totalBytes: Long = -1,
        progress: Int = 0
    ): DownloadEntity {
        return DownloadEntity(
            sourceUrl = "https://example.com/file.bin",
            fileName = "file.bin",
            status = status,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            progress = progress
        )
    }
}
