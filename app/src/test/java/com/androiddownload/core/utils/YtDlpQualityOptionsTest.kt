package com.androiddownload.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpQualityOptionsTest {
    @Test
    fun nullQualitySelectorReturnsCustomFormatLabel() {
        val label = labelFor(qualitySelector = null)

        assertEquals("Formato personalizado", label)
    }

    @Test
    fun blankQualitySelectorReturnsCustomFormatLabel() {
        listOf("", "   ").forEach { selector ->
            val label = labelFor(qualitySelector = selector)

            assertEquals("Formato personalizado", label)
        }
    }

    @Test
    fun directHttpDownloadWithoutSelectorReturnsDirectDownloadLabel() {
        val label = labelFor(
            qualitySelector = null,
            sourceUrl = "https://example.com/file.mp4"
        )

        assertEquals("Download direto", label)
    }

    @Test
    fun knownMp3SelectorReturnsExpectedMp3Label() {
        val label = labelFor(qualitySelector = YtDlpQualityOptions.SELECTOR_MP3_320K)

        assertEquals("MP3 - 320k", label)
        assertTrue("MP3" in label)
    }

    @Test
    fun knownMp4SelectorReturnsExpectedMp4Label() {
        val label = labelFor(qualitySelector = YtDlpQualityOptions.SELECTOR_MP4_720P)

        assertEquals("MP4 - 720p", label)
        assertTrue("MP4" in label)
    }

    @Test
    fun allKnownMp3SelectorLabelsContainMp3() {
        val selectors = listOf(
            YtDlpQualityOptions.SELECTOR_MP3_320K,
            YtDlpQualityOptions.SELECTOR_MP3_256K,
            YtDlpQualityOptions.SELECTOR_MP3_192K,
            YtDlpQualityOptions.SELECTOR_MP3_128K
        )

        selectors.forEach { selector ->
            val label = labelFor(qualitySelector = selector)

            assertTrue("MP3 label must remain distinguishable for player/router", "MP3" in label)
        }
    }

    @Test
    fun allKnownMp4SelectorLabelsContainMp4() {
        val selectors = listOf(
            YtDlpQualityOptions.SELECTOR_MP4_1440P,
            YtDlpQualityOptions.SELECTOR_MP4_1080P,
            YtDlpQualityOptions.SELECTOR_MP4_720P,
            YtDlpQualityOptions.SELECTOR_MP4_480P
        )

        selectors.forEach { selector ->
            val label = labelFor(qualitySelector = selector)

            assertTrue("MP4 label must remain distinguishable for player/router", "MP4" in label)
        }
    }

    @Test
    fun knownLegacySelectorReturnsLegacyFormatLabel() {
        val label = labelFor(qualitySelector = "best")

        assertEquals("Formato antigo", label)
    }

    @Test
    fun unknownSelectorReturnsCustomFormatLabel() {
        val label = labelFor(qualitySelector = "unknown-selector")

        assertEquals("Formato personalizado", label)
    }

    @Test
    fun knownFallbacksDoNotReturnBlankLabels() {
        val labels = listOf(
            labelFor(qualitySelector = null),
            labelFor(qualitySelector = ""),
            labelFor(qualitySelector = null, sourceUrl = "https://example.com/file.mp4"),
            labelFor(qualitySelector = YtDlpQualityOptions.SELECTOR_MP3_128K),
            labelFor(qualitySelector = YtDlpQualityOptions.SELECTOR_MP4_480P),
            labelFor(qualitySelector = "best"),
            labelFor(qualitySelector = "unknown-selector")
        )

        labels.forEach { label ->
            assertFalse(label.isBlank())
        }
    }

    private fun labelFor(
        qualitySelector: String?,
        sourceUrl: String = "https://youtu.be/example"
    ): String {
        return YtDlpQualityOptions.labelForDownload(
            qualitySelector = qualitySelector,
            sourceUrl = sourceUrl,
            labels = labels
        )
    }

    private val labels = YtDlpQualityLabelTexts(
        direct = "Download direto",
        custom = "Formato personalizado",
        legacy = "Formato antigo",
        mp4_1440p = "MP4 - 1440p",
        mp4_1080p = "MP4 - 1080p",
        mp4_720p = "MP4 - 720p",
        mp4_480p = "MP4 - 480p",
        mp3_320k = "MP3 - 320k",
        mp3_256k = "MP3 - 256k",
        mp3_192k = "MP3 - 192k",
        mp3_128k = "MP3 - 128k"
    )
}
