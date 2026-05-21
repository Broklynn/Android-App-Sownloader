package com.androiddownload.download.model

import com.androiddownload.core.model.DownloadEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadOriginResolverTest {
    @Test
    fun youtubeWatchReturnsYoutube() {
        assertOrigin(
            "https://www.youtube.com/watch?v=abc",
            DownloadOrigin.YOUTUBE
        )
    }

    @Test
    fun youtubeShortLinkReturnsYoutube() {
        assertOrigin(
            "https://youtu.be/abc",
            DownloadOrigin.YOUTUBE
        )
    }

    @Test
    fun tiktokShortLinkReturnsTikTok() {
        assertOrigin(
            "https://vt.tiktok.com/ZSxDVKFum/",
            DownloadOrigin.TIKTOK
        )
    }

    @Test
    fun tiktokVideoLinkReturnsTikTok() {
        assertOrigin(
            "https://www.tiktok.com/@user/video/1234567890",
            DownloadOrigin.TIKTOK
        )
    }

    @Test
    fun instagramPostReturnsInstagram() {
        assertOrigin(
            "https://www.instagram.com/p/DYcw_oliWHF/",
            DownloadOrigin.INSTAGRAM
        )
    }

    @Test
    fun facebookCdnWithInstagramRefererReturnsInstagram() {
        assertOrigin(
            sourceUrl = "https://scontent.cdninstagram.com/v/t50.2886-16/example.mp4",
            expected = DownloadOrigin.INSTAGRAM,
            httpHeadersJson = """{"Referer":"https://www.instagram.com/p/example/"}"""
        )
        assertOrigin(
            sourceUrl = "https://video.xx.fbcdn.net/v/t42.1790-2/example.mp4",
            expected = DownloadOrigin.INSTAGRAM,
            httpHeadersJson = """{"Referer":"https://www.instagram.com/reel/example/"}"""
        )
    }

    @Test
    fun rawGithubContentReturnsFiles() {
        assertOrigin(
            "https://raw.githubusercontent.com/github/gitignore/main/Android.gitignore",
            DownloadOrigin.FILES
        )
    }

    @Test
    fun genericUrlReturnsFiles() {
        assertOrigin(
            "https://example.com/watch/abc",
            DownloadOrigin.FILES
        )
    }

    @Test
    fun invalidUrlDoesNotCrashAndReturnsFiles() {
        assertOrigin(
            "not a url",
            DownloadOrigin.FILES
        )
    }

    private fun assertOrigin(
        sourceUrl: String,
        expected: DownloadOrigin,
        httpHeadersJson: String? = null
    ) {
        val download = DownloadEntity(
            sourceUrl = sourceUrl,
            fileName = "file.mp4",
            httpHeadersJson = httpHeadersJson
        )

        assertEquals(expected, DownloadOriginResolver.resolve(download))
    }
}
