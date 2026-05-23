package com.androiddownload.ui.downloads

import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSummaryFormatterTest {
    @Test
    fun completedProgressReturnsOneHundred() {
        val download = download(status = DownloadStatus.COMPLETED, progress = 12)

        assertEquals(100, DownloadSummaryFormatter.normalizedProgress(download))
    }

    @Test
    fun progressBelowZeroIsClampedToZero() {
        val download = download(progress = -10)

        assertEquals(0, DownloadSummaryFormatter.normalizedProgress(download))
    }

    @Test
    fun progressAboveOneHundredIsClampedToOneHundred() {
        val download = download(progress = 135)

        assertEquals(100, DownloadSummaryFormatter.normalizedProgress(download))
    }

    @Test
    fun normalProgressIsPreserved() {
        val download = download(progress = 42)

        assertEquals(42, DownloadSummaryFormatter.normalizedProgress(download))
    }

    @Test
    fun directHttpRunningWithoutTotalOrProgressIsIndeterminate() {
        val download = download(
            sourceUrl = "https://raw.githubusercontent.com/example/file.bin",
            status = DownloadStatus.RUNNING,
            totalBytes = -1,
            progress = 0
        )

        assertTrue(DownloadSummaryFormatter.isIndeterminate(download))
    }

    @Test
    fun directHttpPreparingWithoutTotalOrProgressIsIndeterminate() {
        val download = download(
            sourceUrl = "https://raw.githubusercontent.com/example/file.bin",
            status = DownloadStatus.PREPARING,
            totalBytes = 0,
            progress = 0
        )

        assertTrue(DownloadSummaryFormatter.isIndeterminate(download))
    }

    @Test
    fun downloadWithKnownTotalIsNotIndeterminate() {
        val download = download(
            sourceUrl = "https://raw.githubusercontent.com/example/file.bin",
            status = DownloadStatus.RUNNING,
            totalBytes = 2048,
            progress = 0
        )

        assertFalse(DownloadSummaryFormatter.isIndeterminate(download))
    }

    @Test
    fun downloadWithProgressIsNotIndeterminate() {
        val download = download(
            sourceUrl = "https://raw.githubusercontent.com/example/file.bin",
            status = DownloadStatus.RUNNING,
            totalBytes = -1,
            progress = 10
        )

        assertFalse(DownloadSummaryFormatter.isIndeterminate(download))
    }

    @Test
    fun summarySizeTextWithKnownTotalShowsDownloadedAndTotal() {
        val download = download(downloadedBytes = 1024, totalBytes = 2048)

        assertEquals("1.0 KB / 2.0 KB", DownloadSummaryFormatter.summarySizeText(download))
    }

    @Test
    fun summarySizeTextWithOnlyDownloadedBytesShowsDownloadedBytes() {
        val download = download(downloadedBytes = 1536, totalBytes = -1)

        assertEquals("1.5 KB", DownloadSummaryFormatter.summarySizeText(download))
    }

    @Test
    fun summarySizeTextWithOnlyProgressShowsProgressPercent() {
        val download = download(downloadedBytes = 0, totalBytes = -1, progress = 27)

        assertEquals("27%", DownloadSummaryFormatter.summarySizeText(download))
    }

    @Test
    fun summarySizeTextWithNoSizeOrProgressIsEmpty() {
        val download = download(downloadedBytes = 0, totalBytes = -1, progress = 0)

        assertEquals("", DownloadSummaryFormatter.summarySizeText(download))
    }

    @Test
    fun typeBadgeLabelReturnsMp3WhenFormatLabelIndicatesMp3() {
        val download = download(sourceUrl = "https://raw.githubusercontent.com/example/file.bin")

        assertEquals("MP3", DownloadSummaryFormatter.typeBadgeLabel(download, "MP3 - 320k"))
    }

    @Test
    fun typeBadgeLabelReturnsMp4WhenFormatLabelIndicatesMp4() {
        val download = download(sourceUrl = "https://raw.githubusercontent.com/example/file.bin")

        assertEquals("MP4", DownloadSummaryFormatter.typeBadgeLabel(download, "MP4 - 720p"))
    }

    @Test
    fun typeBadgeLabelReturnsHttpForDirectHttpDownload() {
        val download = download(sourceUrl = "https://raw.githubusercontent.com/example/file.bin")

        assertEquals("HTTP", DownloadSummaryFormatter.typeBadgeLabel(download, "Download direto"))
    }

    @Test
    fun typeBadgeLabelReturnsFallbackForUnclassifiedDownload() {
        val download = download(sourceUrl = "https://youtu.be/dQw4w9WgXcQ")

        assertEquals("MIDIA", DownloadSummaryFormatter.typeBadgeLabel(download, "WEBM - 720p"))
    }

    @Test
    fun typeBadgeLabelPreservesFormatLabelPriorityOverDirectHttp() {
        val download = download(sourceUrl = "https://raw.githubusercontent.com/example/file.bin")

        assertEquals("MP3", DownloadSummaryFormatter.typeBadgeLabel(download, "mp3"))
    }

    private fun download(
        sourceUrl: String = "https://example.com/file.bin",
        status: DownloadStatus = DownloadStatus.RUNNING,
        downloadedBytes: Long = 0,
        totalBytes: Long = -1,
        progress: Int = 0
    ): DownloadEntity {
        return DownloadEntity(
            sourceUrl = sourceUrl,
            fileName = "file.bin",
            status = status,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            progress = progress
        )
    }
}
