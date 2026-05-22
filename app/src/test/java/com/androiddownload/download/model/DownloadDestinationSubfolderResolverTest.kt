package com.androiddownload.download.model

import com.androiddownload.core.model.DownloadEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadDestinationSubfolderResolverTest {
    @Test
    fun youtubeReturnsYoutubeSubfolder() {
        assertSubfolder(
            sourceUrl = "https://www.youtube.com/watch?v=abc",
            expected = "Youtube"
        )
    }

    @Test
    fun instagramReturnsInstagramSubfolder() {
        assertSubfolder(
            sourceUrl = "https://www.instagram.com/p/DYcw_oliWHF/",
            expected = "Instagram"
        )
    }

    @Test
    fun tiktokReturnsTikTokSubfolder() {
        assertSubfolder(
            sourceUrl = "https://vt.tiktok.com/ZSxDVKFum/",
            expected = "TikTok"
        )
    }

    @Test
    fun directHttpReturnsArquivosSubfolder() {
        assertSubfolder(
            sourceUrl = "https://raw.githubusercontent.com/github/gitignore/main/Android.gitignore",
            expected = "Arquivos"
        )
    }

    @Test
    fun facebookCdnWithInstagramRefererReturnsInstagramSubfolder() {
        assertSubfolder(
            sourceUrl = "https://video.xx.fbcdn.net/v/t42.1790-2/example.mp4",
            httpHeadersJson = """{"Referer":"https://www.instagram.com/p/example/"}""",
            expected = "Instagram"
        )
    }

    @Test
    fun invalidUrlReturnsArquivosSubfolder() {
        assertSubfolder(
            sourceUrl = "not a url",
            expected = "Arquivos"
        )
    }

    private fun assertSubfolder(
        sourceUrl: String,
        expected: String,
        httpHeadersJson: String? = null
    ) {
        val download = DownloadEntity(
            sourceUrl = sourceUrl,
            fileName = "file.mp4",
            httpHeadersJson = httpHeadersJson
        )

        assertEquals(expected, DownloadDestinationSubfolderResolver.resolve(download))
    }
}
