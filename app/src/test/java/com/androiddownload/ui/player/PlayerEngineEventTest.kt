package com.androiddownload.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerEngineEventTest {
    @Test
    fun `state changed preserves state`() {
        val state = playingState()
        val event = PlayerEngineEvent.StateChanged(state)

        assertEquals(state, event.state)
    }

    @Test
    fun `prepared preserves state`() {
        val state = playingState(status = PlayerPlaybackStatus.READY)
        val event = PlayerEngineEvent.Prepared(state)

        assertEquals(state, event.state)
    }

    @Test
    fun `position changed preserves snapshot`() {
        val snapshot = positionSnapshot()
        val event = PlayerEngineEvent.PositionChanged(snapshot)

        assertEquals(snapshot, event.snapshot)
    }

    @Test
    fun `completed preserves state`() {
        val state = playingState(status = PlayerPlaybackStatus.COMPLETED)
        val event = PlayerEngineEvent.Completed(state)

        assertEquals(state, event.state)
    }

    @Test
    fun `error preserves state and message`() {
        val state = playingState(status = PlayerPlaybackStatus.ERROR)
        val event = PlayerEngineEvent.Error(
            state = state,
            message = "playback failed"
        )

        assertEquals(state, event.state)
        assertEquals("playback failed", event.message)
    }

    @Test
    fun `error accepts null message`() {
        val state = playingState(status = PlayerPlaybackStatus.ERROR)
        val event = PlayerEngineEvent.Error(
            state = state,
            message = null
        )

        assertEquals(state, event.state)
        assertNull(event.message)
    }

    private fun playingState(
        status: PlayerPlaybackStatus = PlayerPlaybackStatus.PLAYING
    ): PlayerEngineState {
        return PlayerEngineState(
            status = status,
            mediaKind = PlayerMediaKind.AUDIO,
            positionSnapshot = positionSnapshot(),
            downloadId = 42L,
            title = "audio.mp3"
        )
    }

    private fun positionSnapshot(): PlayerPositionSnapshot {
        return PlayerPositionSnapshot.from(
            positionMs = 7_000,
            durationMs = 65_000,
            maxProgress = 1_000
        )
    }
}
