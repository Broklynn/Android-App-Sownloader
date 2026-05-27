package com.androiddownload.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerEngineStateTest {
    @Test
    fun `defaults describe idle state without media or metadata`() {
        val state = PlayerEngineState()

        assertEquals(PlayerPlaybackStatus.IDLE, state.status)
        assertNull(state.mediaKind)
        assertNull(state.positionSnapshot)
        assertNull(state.errorMessage)
        assertNull(state.downloadId)
        assertNull(state.title)
    }

    @Test
    fun `explicit fields are preserved`() {
        val positionSnapshot = PlayerPositionSnapshot.from(
            positionMs = 7_000,
            durationMs = 65_000,
            maxProgress = 1_000
        )
        val state = PlayerEngineState(
            status = PlayerPlaybackStatus.PLAYING,
            mediaKind = PlayerMediaKind.VIDEO,
            positionSnapshot = positionSnapshot,
            errorMessage = "playback failed",
            downloadId = 42L,
            title = "video.mp4"
        )

        assertEquals(PlayerPlaybackStatus.PLAYING, state.status)
        assertEquals(PlayerMediaKind.VIDEO, state.mediaKind)
        assertEquals(positionSnapshot, state.positionSnapshot)
        assertEquals("playback failed", state.errorMessage)
        assertEquals(42L, state.downloadId)
        assertEquals("video.mp4", state.title)
    }
}
