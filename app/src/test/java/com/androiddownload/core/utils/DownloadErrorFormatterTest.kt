package com.androiddownload.core.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadErrorFormatterTest {
    @Test
    fun youtubeBotVerificationStillTriggersAutoUpdate() {
        assertTrue(
            DownloadErrorFormatter.isYtDlpAutoUpdateRecoverable(
                "ERROR: [youtube] Sign in to confirm you're not a bot"
            )
        )
    }

    @Test
    fun tiktokWebpageVideoDataErrorTriggersAutoUpdate() {
        assertTrue(
            DownloadErrorFormatter.isYtDlpAutoUpdateRecoverable(
                "ERROR: [TikTok] 7642080643165261076: Unable to extract webpage video data"
            )
        )
    }

    @Test
    fun oldYtDlpVersionWarningTriggersAutoUpdate() {
        assertTrue(
            DownloadErrorFormatter.isYtDlpAutoUpdateRecoverable(
                "WARNING: Your yt-dlp version (2025.11.12) is older than 90 days!"
            )
        )
    }

    @Test
    fun genericErrorDoesNotTriggerAutoUpdate() {
        assertFalse(
            DownloadErrorFormatter.isYtDlpAutoUpdateRecoverable(
                "Unexpected failure while saving file"
            )
        )
    }
}
