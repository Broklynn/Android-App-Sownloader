package com.androiddownload.ui.player

import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMediaLabelResolverTest {
    @Test
    fun returnsMp3WhenFormatLabelIndicatesMp3() {
        val download = download(fileName = "track.bin")

        val label = PlayerMediaLabelResolver.typeLabel(download, "MP3 - 320k")

        assertEquals("MP3", label)
    }

    @Test
    fun returnsMp3WhenFileNameIndicatesMp3() {
        val download = download(fileName = "track.mp3")

        val label = PlayerMediaLabelResolver.typeLabel(download, "Download direto")

        assertEquals("MP3", label)
    }

    @Test
    fun returnsMp3WhenDestinationUriIndicatesMp3() {
        val download = download(
            fileName = "track",
            destinationUri = "content://downloads/music/track.mp3"
        )

        val label = PlayerMediaLabelResolver.typeLabel(download, "Download direto")

        assertEquals("MP3", label)
    }

    @Test
    fun returnsMp4WhenFormatLabelIndicatesMp4() {
        val download = download(fileName = "video.bin")

        val label = PlayerMediaLabelResolver.typeLabel(download, "MP4 - 720p")

        assertEquals("MP4", label)
    }

    @Test
    fun returnsMp4WhenFileNameIndicatesMp4() {
        val download = download(fileName = "video.mp4")

        val label = PlayerMediaLabelResolver.typeLabel(download, "Download direto")

        assertEquals("MP4", label)
    }

    @Test
    fun returnsMp4WhenDestinationUriIndicatesMp4WithQueryAndHash() {
        val download = download(
            fileName = "video",
            destinationUri = "content://downloads/videos/video.mp4?token=abc#section"
        )

        val label = PlayerMediaLabelResolver.typeLabel(download, "Download direto")

        assertEquals("MP4", label)
    }

    @Test
    fun keepsHttpFallbackForHttpDownloads() {
        val download = download(
            sourceUrl = "https://example.com/archive.bin",
            fileName = "archive.bin"
        )

        val label = PlayerMediaLabelResolver.typeLabel(download, "Download direto")

        assertEquals("HTTP", label)
    }

    @Test
    fun keepsMidiaFallbackWhenNoBetterClassificationExists() {
        val download = download(
            sourceUrl = "https://youtu.be/example",
            fileName = "download.bin"
        )

        val label = PlayerMediaLabelResolver.typeLabel(download, "Download direto")

        assertEquals("MIDIA", label)
    }

    @Test
    fun handlesEmptyFileName() {
        val download = download(fileName = "", destinationUri = null)

        val label = PlayerMediaLabelResolver.typeLabel(download, "Download direto")

        assertEquals("MIDIA", label)
    }

    @Test
    fun handlesBlankDestinationUri() {
        val download = download(fileName = "track.mp3", destinationUri = "")

        val label = PlayerMediaLabelResolver.typeLabel(download, "Download direto")

        assertEquals("MP3", label)
    }

    @Test
    fun preservesMp3PriorityWhenFormatLabelAndExtensionDiverge() {
        val download = download(fileName = "video.mp4")

        val label = PlayerMediaLabelResolver.typeLabel(download, "MP3 - 320k")

        assertEquals("MP3", label)
    }

    private fun download(
        sourceUrl: String = "https://example.com/file",
        fileName: String,
        destinationUri: String? = "content://downloads/$fileName"
    ): DownloadEntity {
        return DownloadEntity(
            sourceUrl = sourceUrl,
            fileName = fileName,
            destinationUri = destinationUri,
            status = DownloadStatus.COMPLETED
        )
    }
}
