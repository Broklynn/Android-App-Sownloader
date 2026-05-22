package com.androiddownload.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerProgressCalculatorTest {
    @Test
    fun formatPlaybackTimeFormatsZero() {
        assertEquals("0:00", PlayerProgressCalculator.formatPlaybackTime(0))
    }

    @Test
    fun formatPlaybackTimeTreatsNegativeAsZero() {
        assertEquals("0:00", PlayerProgressCalculator.formatPlaybackTime(-1_000))
    }

    @Test
    fun formatPlaybackTimePadsSecondsBelowTen() {
        assertEquals("0:09", PlayerProgressCalculator.formatPlaybackTime(9_000))
    }

    @Test
    fun formatPlaybackTimeKeepsLongDurationsAsMinutes() {
        assertEquals("61:01", PlayerProgressCalculator.formatPlaybackTime(3_661_000))
    }

    @Test
    fun positionToProgressReturnsZeroForNonPositiveDuration() {
        assertEquals(0, PlayerProgressCalculator.positionToProgress(500, 0, 100))
    }

    @Test
    fun positionToProgressReturnsZeroForNonPositiveMaxProgress() {
        assertEquals(0, PlayerProgressCalculator.positionToProgress(500, 1_000, 0))
    }

    @Test
    fun positionToProgressClampsNegativePositionToZero() {
        assertEquals(0, PlayerProgressCalculator.positionToProgress(-500, 1_000, 100))
    }

    @Test
    fun positionToProgressClampsPositionAboveDurationToMaxProgress() {
        assertEquals(100, PlayerProgressCalculator.positionToProgress(1_500, 1_000, 100))
    }

    @Test
    fun positionToProgressUsesIntegerDivisionLikeExistingBehavior() {
        assertEquals(33, PlayerProgressCalculator.positionToProgress(1_000, 3_000, 100))
    }

    @Test
    fun progressToPositionReturnsZeroForNonPositiveDuration() {
        assertEquals(0, PlayerProgressCalculator.progressToPosition(50, 0, 100))
    }

    @Test
    fun progressToPositionReturnsZeroForNonPositiveMaxProgress() {
        assertEquals(0, PlayerProgressCalculator.progressToPosition(50, 1_000, 0))
    }

    @Test
    fun progressToPositionClampsNegativeProgressToZero() {
        assertEquals(0, PlayerProgressCalculator.progressToPosition(-50, 1_000, 100))
    }

    @Test
    fun progressToPositionClampsProgressAboveMaxToDuration() {
        assertEquals(1_000, PlayerProgressCalculator.progressToPosition(150, 1_000, 100))
    }

    @Test
    fun progressToPositionUsesIntegerDivisionLikeExistingBehavior() {
        assertEquals(990, PlayerProgressCalculator.progressToPosition(33, 3_000, 100))
    }

    @Test
    fun seekByClampsBelowZero() {
        assertEquals(0, PlayerProgressCalculator.seekBy(5_000, -10_000, 60_000))
    }

    @Test
    fun seekByClampsAboveDuration() {
        assertEquals(60_000, PlayerProgressCalculator.seekBy(55_000, 10_000, 60_000))
    }

    @Test
    fun seekByReturnsZeroForNonPositiveDuration() {
        assertEquals(0, PlayerProgressCalculator.seekBy(5_000, 1_000, 0))
    }
}
