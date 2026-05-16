package com.androiddownload.download.request

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRequestPlannerTest {
    @Test
    fun invalidUrlReturnsInvalidUrlWithRawValue() {
        val planner = DownloadRequestPlanner(
            isValidUrl = { false },
            shouldUseHttpDownloader = { error("should not classify invalid URLs") }
        )

        val decision = planner.plan(
            rawUrl = "abc",
            defaultQualityPreferenceValue = "ask",
            defaultQualitySelector = null
        )

        assertTrue(decision is DownloadRequestDecision.InvalidUrl)
        assertEquals("abc", (decision as DownloadRequestDecision.InvalidUrl).rawUrl)
    }

    @Test
    fun directHttpDownloadReturnsTrimmedDirectDownload() {
        val planner = DownloadRequestPlanner(
            isValidUrl = { true },
            shouldUseHttpDownloader = { true }
        )

        val decision = planner.plan(
            rawUrl = "  https://example.com/video.mp4  ",
            defaultQualityPreferenceValue = "ask",
            defaultQualitySelector = null
        )

        assertEquals(
            DownloadRequestDecision.DirectDownload("https://example.com/video.mp4"),
            decision
        )
    }

    @Test
    fun ytdlpWithAskPreferenceReturnsAskQualityEvenWithSelector() {
        val planner = ytdlpPlanner()

        val decision = planner.plan(
            rawUrl = "https://youtube.com/watch?v=123",
            defaultQualityPreferenceValue = "ask",
            defaultQualitySelector = "bestvideo[height<=720]+bestaudio/best[height<=720]"
        )

        assertEquals(
            DownloadRequestDecision.YtDlpAskQuality("https://youtube.com/watch?v=123"),
            decision
        )
    }

    @Test
    fun ytdlpWithNullSelectorReturnsAskQuality() {
        val planner = ytdlpPlanner()

        val decision = planner.plan(
            rawUrl = "https://youtube.com/watch?v=123",
            defaultQualityPreferenceValue = "fixed",
            defaultQualitySelector = null
        )

        assertEquals(
            DownloadRequestDecision.YtDlpAskQuality("https://youtube.com/watch?v=123"),
            decision
        )
    }

    @Test
    fun ytdlpWithBlankSelectorReturnsAskQuality() {
        val planner = ytdlpPlanner()

        val decision = planner.plan(
            rawUrl = "https://youtube.com/watch?v=123",
            defaultQualityPreferenceValue = "fixed",
            defaultQualitySelector = ""
        )

        assertEquals(
            DownloadRequestDecision.YtDlpAskQuality("https://youtube.com/watch?v=123"),
            decision
        )
    }

    @Test
    fun ytdlpWithRealSelectorReturnsFixedQuality() {
        val planner = ytdlpPlanner()
        val selector = "bestvideo[height<=720]+bestaudio/best[height<=720]"

        val decision = planner.plan(
            rawUrl = "https://youtube.com/watch?v=123",
            defaultQualityPreferenceValue = "fixed",
            defaultQualitySelector = selector
        )

        assertEquals(
            DownloadRequestDecision.YtDlpFixedQuality(
                url = "https://youtube.com/watch?v=123",
                qualitySelector = selector
            ),
            decision
        )
    }

    @Test
    fun directHttpDownloadIgnoresQualityPreferenceAndSelector() {
        val planner = DownloadRequestPlanner(
            isValidUrl = { true },
            shouldUseHttpDownloader = { true }
        )

        val decision = planner.plan(
            rawUrl = "https://example.com/video.mp4",
            defaultQualityPreferenceValue = "ask",
            defaultQualitySelector = "bestvideo[height<=720]+bestaudio/best[height<=720]"
        )

        assertEquals(
            DownloadRequestDecision.DirectDownload("https://example.com/video.mp4"),
            decision
        )
    }

    private fun ytdlpPlanner(): DownloadRequestPlanner {
        return DownloadRequestPlanner(
            isValidUrl = { true },
            shouldUseHttpDownloader = { false }
        )
    }
}
