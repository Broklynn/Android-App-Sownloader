package com.androiddownload.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadDestinationResolverTest {
    @Test
    fun publicRelativePathUsesYoutubeSubfolder() {
        assertEquals(
            "Download/DarkWave/Youtube",
            DownloadDestinationResolver.publicRelativePath("Youtube")
        )
    }

    @Test
    fun publicRelativePathUsesInstagramSubfolder() {
        assertEquals(
            "Download/DarkWave/Instagram",
            DownloadDestinationResolver.publicRelativePath("Instagram")
        )
    }

    @Test
    fun publicRelativePathUsesTikTokSubfolder() {
        assertEquals(
            "Download/DarkWave/TikTok",
            DownloadDestinationResolver.publicRelativePath("TikTok")
        )
    }

    @Test
    fun publicRelativePathUsesArquivosForBlankSubfolder() {
        assertEquals(
            "Download/DarkWave/Arquivos",
            DownloadDestinationResolver.publicRelativePath(" ")
        )
    }

    @Test
    fun mediaStoreQueryRelativePathEndsWithSlash() {
        assertEquals(
            "Download/DarkWave/Youtube/",
            DownloadDestinationResolver.mediaStoreQueryRelativePath("Youtube")
        )
    }
}
