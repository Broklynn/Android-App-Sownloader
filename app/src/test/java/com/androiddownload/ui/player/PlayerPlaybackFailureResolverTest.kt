package com.androiddownload.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlayerPlaybackFailureResolverTest {
    @Test
    fun startFailureWithEmptyListStops() {
        val action = resolveStartFailure(itemCount = 0, failedIndex = 0, skipBudget = 2)

        assertSame(PlayerPlaybackFailureAction.Stop, action)
    }

    @Test
    fun startFailureWithNegativeItemCountStops() {
        val action = resolveStartFailure(itemCount = -1, failedIndex = 0, skipBudget = 2)

        assertSame(PlayerPlaybackFailureAction.Stop, action)
    }

    @Test
    fun startFailureWithNegativeFailedIndexStops() {
        val action = resolveStartFailure(itemCount = 3, failedIndex = -1, skipBudget = 2)

        assertSame(PlayerPlaybackFailureAction.Stop, action)
    }

    @Test
    fun startFailureWithFailedIndexAboveEndStops() {
        val action = resolveStartFailure(itemCount = 3, failedIndex = 4, skipBudget = 2)

        assertSame(PlayerPlaybackFailureAction.Stop, action)
    }

    @Test
    fun startFailureOnLastItemStops() {
        val action = resolveStartFailure(itemCount = 3, failedIndex = 2, skipBudget = 2)

        assertSame(PlayerPlaybackFailureAction.Stop, action)
    }

    @Test
    fun startFailureWithSkipBudgetOneStops() {
        val action = resolveStartFailure(itemCount = 3, failedIndex = 0, skipBudget = 1)

        assertSame(PlayerPlaybackFailureAction.Stop, action)
    }

    @Test
    fun startFailureWithNonPositiveSkipBudgetStops() {
        val action = resolveStartFailure(itemCount = 3, failedIndex = 0, skipBudget = 0)

        assertSame(PlayerPlaybackFailureAction.Stop, action)
    }

    @Test
    fun startFailureOnIntermediateItemWithBudgetSkipsToNext() {
        val action = resolveStartFailure(itemCount = 4, failedIndex = 1, skipBudget = 2)

        assertEquals(PlayerPlaybackFailureAction.SkipToNext(2), action)
    }

    @Test
    fun startFailureOnFirstItemWithNextAndBudgetSkipsToSecond() {
        val action = resolveStartFailure(itemCount = 3, failedIndex = 0, skipBudget = 2)

        assertEquals(PlayerPlaybackFailureAction.SkipToNext(1), action)
    }

    @Test
    fun playbackErrorWithEmptyListStops() {
        val action = resolvePlaybackError(itemCount = 0, currentIndex = 0)

        assertSame(PlayerPlaybackFailureAction.Stop, action)
    }

    @Test
    fun playbackErrorWithNegativeCurrentIndexStops() {
        val action = resolvePlaybackError(itemCount = 3, currentIndex = -1)

        assertSame(PlayerPlaybackFailureAction.Stop, action)
    }

    @Test
    fun playbackErrorWithCurrentIndexAboveEndStops() {
        val action = resolvePlaybackError(itemCount = 3, currentIndex = 4)

        assertSame(PlayerPlaybackFailureAction.Stop, action)
    }

    @Test
    fun playbackErrorOnLastItemStops() {
        val action = resolvePlaybackError(itemCount = 3, currentIndex = 2)

        assertSame(PlayerPlaybackFailureAction.Stop, action)
    }

    @Test
    fun playbackErrorOnIntermediateItemSkipsToNext() {
        val action = resolvePlaybackError(itemCount = 4, currentIndex = 1)

        assertEquals(PlayerPlaybackFailureAction.SkipToNext(2), action)
    }

    @Test
    fun playbackErrorOnFirstItemWithNextSkipsToSecond() {
        val action = resolvePlaybackError(itemCount = 3, currentIndex = 0)

        assertEquals(PlayerPlaybackFailureAction.SkipToNext(1), action)
    }

    private fun resolveStartFailure(
        itemCount: Int,
        failedIndex: Int,
        skipBudget: Int
    ): PlayerPlaybackFailureAction {
        return PlayerPlaybackFailureResolver.resolveStartFailure(
            itemCount = itemCount,
            failedIndex = failedIndex,
            skipBudget = skipBudget
        )
    }

    private fun resolvePlaybackError(
        itemCount: Int,
        currentIndex: Int
    ): PlayerPlaybackFailureAction {
        return PlayerPlaybackFailureResolver.resolvePlaybackError(
            itemCount = itemCount,
            currentIndex = currentIndex
        )
    }
}
