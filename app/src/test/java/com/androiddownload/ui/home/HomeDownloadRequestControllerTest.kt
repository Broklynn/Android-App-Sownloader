package com.androiddownload.ui.home

import com.androiddownload.download.request.DownloadRequestPlanner
import com.androiddownload.ui.downloads.QualityOptionUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDownloadRequestControllerTest {
    @Test
    fun invalidUrlShowsErrorAndDoesNotStartDownload() {
        val events = mutableListOf<String>()
        val controller = controller(
            isValidUrl = { false },
            shouldUseHttpDownloader = { error("should not classify invalid URLs") },
            events = events
        )

        controller.handleDownloadRequest("abc")

        assertEquals(listOf("error:invalid url"), events)
    }

    @Test
    fun directHttpAddsRecentUrlAndStartsDownloadWithoutSelector() {
        val events = mutableListOf<String>()
        val controller = controller(
            isValidUrl = { true },
            shouldUseHttpDownloader = { true },
            events = events
        )

        controller.handleDownloadRequest("https://example.com/video.mp4")

        assertEquals(
            listOf(
                "recent:https://example.com/video.mp4",
                "start:https://example.com/video.mp4:null"
            ),
            events
        )
    }

    @Test
    fun ytdlpWithAskPreferenceAddsRecentUrlAndOpensQualityPicker() {
        val events = mutableListOf<String>()
        val controller = controller(
            selectedQuality = QualityOptionUi("Perguntar sempre", "ask", "best"),
            isValidUrl = { true },
            shouldUseHttpDownloader = { false },
            events = events
        )

        controller.handleDownloadRequest("https://youtube.com/watch?v=123")

        assertEquals(
            listOf(
                "recent:https://youtube.com/watch?v=123",
                "picker:https://youtube.com/watch?v=123"
            ),
            events
        )
    }

    @Test
    fun ytdlpWithFixedQualityAddsRecentUrlAndStartsDownloadWithSelector() {
        val events = mutableListOf<String>()
        val selector = "bestvideo[height<=720]+bestaudio/best[height<=720]"
        val controller = controller(
            selectedQuality = QualityOptionUi("MP4 720p", selector, selector),
            isValidUrl = { true },
            shouldUseHttpDownloader = { false },
            events = events
        )

        controller.handleDownloadRequest("https://youtube.com/watch?v=123")

        assertEquals(
            listOf(
                "recent:https://youtube.com/watch?v=123",
                "start:https://youtube.com/watch?v=123:$selector"
            ),
            events
        )
    }

    @Test
    fun invalidUrlDoesNotAddRecentUrlOpenPickerOrStartDownload() {
        val events = mutableListOf<String>()
        val controller = controller(
            isValidUrl = { false },
            shouldUseHttpDownloader = { error("should not classify invalid URLs") },
            events = events
        )

        controller.handleDownloadRequest("not a url")

        assertTrue(events.none { it.startsWith("recent:") })
        assertTrue(events.none { it.startsWith("picker:") })
        assertTrue(events.none { it.startsWith("start:") })
    }

    private fun controller(
        selectedQuality: QualityOptionUi = QualityOptionUi("Perguntar sempre", "ask", null),
        isValidUrl: (String) -> Boolean,
        shouldUseHttpDownloader: (String) -> Boolean,
        events: MutableList<String>
    ): HomeDownloadRequestController {
        return HomeDownloadRequestController(
            selectedDefaultQualityProvider = { selectedQuality },
            showInvalidUrl = { message -> events += "error:$message" },
            invalidUrlMessageProvider = { "invalid url" },
            addRecentDownloadUrl = { url -> events += "recent:$url" },
            openQualityPicker = { url, _ -> events += "picker:$url" },
            startDownload = { url, qualitySelector, _ -> events += "start:$url:$qualitySelector" },
            planner = DownloadRequestPlanner(
                isValidUrl = isValidUrl,
                shouldUseHttpDownloader = shouldUseHttpDownloader
            )
        )
    }
}
