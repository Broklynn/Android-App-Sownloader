package com.androiddownload.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPlaybackRequestTest {
    @Test
    fun `defaults preserve required fields and use playback defaults`() {
        val request = PlayerPlaybackRequest(
            playbackUri = "content://downloads/item.mp3",
            mediaKind = PlayerMediaKind.AUDIO
        )

        assertEquals("content://downloads/item.mp3", request.playbackUri)
        assertEquals(PlayerMediaKind.AUDIO, request.mediaKind)
        assertEquals(0, request.startPositionMs)
        assertTrue(request.playWhenReady)
        assertNull(request.downloadId)
        assertNull(request.title)
    }

    @Test
    fun `explicit optional values are preserved`() {
        val request = PlayerPlaybackRequest(
            playbackUri = "file:///storage/emulated/0/Download/video.mp4",
            mediaKind = PlayerMediaKind.VIDEO,
            startPositionMs = -1_000,
            playWhenReady = false,
            downloadId = 42L,
            title = "video.mp4"
        )

        assertEquals("file:///storage/emulated/0/Download/video.mp4", request.playbackUri)
        assertEquals(PlayerMediaKind.VIDEO, request.mediaKind)
        assertEquals(-1_000, request.startPositionMs)
        assertEquals(false, request.playWhenReady)
        assertEquals(42L, request.downloadId)
        assertEquals("video.mp4", request.title)
    }
}
