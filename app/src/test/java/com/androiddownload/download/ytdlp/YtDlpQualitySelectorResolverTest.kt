package com.androiddownload.download.ytdlp

import com.androiddownload.core.utils.YtDlpQualityOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtDlpQualitySelectorResolverTest {
    @Test
    fun tiktokMp4720pUsesPermissiveSelector() {
        assertEquals(
            YtDlpQualitySelectorResolver.TIKTOK_MP4_SELECTOR,
            YtDlpQualitySelectorResolver.resolve(
                url = "https://vt.tiktok.com/ZSxDVKFum/",
                selectedSelector = YtDlpQualityOptions.SELECTOR_MP4_720P
            )
        )
    }

    @Test
    fun tiktokMp41440pUsesPermissiveSelector() {
        assertEquals(
            YtDlpQualitySelectorResolver.TIKTOK_MP4_SELECTOR,
            YtDlpQualitySelectorResolver.resolve(
                url = "https://www.tiktok.com/@user/video/123",
                selectedSelector = YtDlpQualityOptions.SELECTOR_MP4_1440P
            )
        )
    }

    @Test
    fun tiktokMp3KeepsSelectedSelector() {
        assertEquals(
            YtDlpQualityOptions.SELECTOR_MP3_320K,
            YtDlpQualitySelectorResolver.resolve(
                url = "https://vt.tiktok.com/ZSxDVKFum/",
                selectedSelector = YtDlpQualityOptions.SELECTOR_MP3_320K
            )
        )
    }

    @Test
    fun youtubeMp4KeepsSelectedSelector() {
        assertEquals(
            YtDlpQualityOptions.SELECTOR_MP4_720P,
            YtDlpQualitySelectorResolver.resolve(
                url = "https://www.youtube.com/watch?v=abc",
                selectedSelector = YtDlpQualityOptions.SELECTOR_MP4_720P
            )
        )
    }

    @Test
    fun instagramMp4KeepsSelectedSelector() {
        assertEquals(
            YtDlpQualityOptions.SELECTOR_MP4_720P,
            YtDlpQualitySelectorResolver.resolve(
                url = "https://www.instagram.com/reels/abc/",
                selectedSelector = YtDlpQualityOptions.SELECTOR_MP4_720P
            )
        )
    }

    @Test
    fun nullSelectorStaysNull() {
        assertNull(
            YtDlpQualitySelectorResolver.resolve(
                url = "https://vt.tiktok.com/ZSxDVKFum/",
                selectedSelector = null
            )
        )
    }
}
