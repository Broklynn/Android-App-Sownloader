package com.androiddownload.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPositionSnapshotTest {
    @Test
    fun `duration zero is not available or seekable`() {
        val snapshot = PlayerPositionSnapshot.from(
            positionMs = 7_000,
            durationMs = 0,
            maxProgress = 1_000
        )

        assertFalse(snapshot.hasDuration)
        assertFalse(snapshot.isSeekable)
    }

    @Test
    fun `positive duration is available and seekable`() {
        val snapshot = PlayerPositionSnapshot.from(
            positionMs = 7_000,
            durationMs = 65_000,
            maxProgress = 1_000
        )

        assertTrue(snapshot.hasDuration)
        assertTrue(snapshot.isSeekable)
    }

    @Test
    fun `labels use playback time format`() {
        val snapshot = PlayerPositionSnapshot.from(
            positionMs = 7_000,
            durationMs = 65_000,
            maxProgress = 1_000
        )

        assertEquals("0:07", snapshot.currentTimeLabel)
        assertEquals("1:05", snapshot.durationLabel)
    }

    @Test
    fun `negative position label is zero`() {
        val snapshot = PlayerPositionSnapshot.from(
            positionMs = -1_000,
            durationMs = 65_000,
            maxProgress = 1_000
        )

        assertEquals("0:00", snapshot.currentTimeLabel)
    }

    @Test
    fun `negative duration label is zero and duration is unavailable`() {
        val snapshot = PlayerPositionSnapshot.from(
            positionMs = 7_000,
            durationMs = -1_000,
            maxProgress = 1_000
        )

        assertEquals("0:00", snapshot.durationLabel)
        assertFalse(snapshot.hasDuration)
        assertFalse(snapshot.isSeekable)
    }

    @Test
    fun `progress uses provided max progress`() {
        val snapshot = PlayerPositionSnapshot.from(
            positionMs = 32_500,
            durationMs = 65_000,
            maxProgress = 1_000
        )

        assertEquals(500, snapshot.progress)
    }

    @Test
    fun `progress clamps position above duration`() {
        val snapshot = PlayerPositionSnapshot.from(
            positionMs = 70_000,
            durationMs = 65_000,
            maxProgress = 1_000
        )

        assertEquals(1_000, snapshot.progress)
    }

    @Test
    fun `snapshot preserves received position and duration`() {
        val snapshot = PlayerPositionSnapshot.from(
            positionMs = -1_000,
            durationMs = -5_000,
            maxProgress = 1_000
        )

        assertEquals(-1_000, snapshot.positionMs)
        assertEquals(-5_000, snapshot.durationMs)
    }
}
