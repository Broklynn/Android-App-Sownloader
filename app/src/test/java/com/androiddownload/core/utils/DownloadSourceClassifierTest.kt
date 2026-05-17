package com.androiddownload.core.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSourceClassifierTest {
    @Test
    fun rawGithubContentUsesHttpDownloader() {
        assertTrue(
            DownloadSourceClassifier.shouldUseHttpDownloader(
                "https://raw.githubusercontent.com/github/gitignore/main/Android.gitignore"
            )
        )
    }

    @Test
    fun youtubeDoesNotUseHttpDownloader() {
        assertFalse(
            DownloadSourceClassifier.shouldUseHttpDownloader(
                "https://www.youtube.com/watch?v=abc"
            )
        )
    }

    @Test
    fun directFileExtensionUsesHttpDownloader() {
        assertTrue(
            DownloadSourceClassifier.shouldUseHttpDownloader(
                "https://example.com/video.mp4"
            )
        )
    }

    @Test
    fun genericUrlWithoutKnownExtensionDoesNotUseHttpDownloader() {
        assertFalse(
            DownloadSourceClassifier.shouldUseHttpDownloader(
                "https://example.com/watch/abc"
            )
        )
    }
}
