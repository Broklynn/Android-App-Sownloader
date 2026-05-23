package com.androiddownload.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlsStateResolverTest {
    @Test
    fun noItemsDisablesPlaybackButtons() {
        val state = resolve(hasItems = false, currentIndex = -1, lastIndex = -1)

        assertFalse(state.playPauseEnabled)
        assertFalse(state.previousEnabled)
        assertFalse(state.nextEnabled)
    }

    @Test
    fun itemsWithNoSelectionEnableOnlyPlayPause() {
        val state = resolve(hasItems = true, currentIndex = -1, lastIndex = 2)

        assertTrue(state.playPauseEnabled)
        assertFalse(state.previousEnabled)
        assertFalse(state.nextEnabled)
    }

    @Test
    fun firstItemInMultiItemListDisablesPreviousAndEnablesNext() {
        val state = resolve(hasItems = true, currentIndex = 0, lastIndex = 2)

        assertTrue(state.playPauseEnabled)
        assertFalse(state.previousEnabled)
        assertTrue(state.nextEnabled)
    }

    @Test
    fun lastItemDisablesNextAndEnablesPrevious() {
        val state = resolve(hasItems = true, currentIndex = 2, lastIndex = 2)

        assertTrue(state.playPauseEnabled)
        assertTrue(state.previousEnabled)
        assertFalse(state.nextEnabled)
    }

    @Test
    fun intermediateItemEnablesPreviousAndNext() {
        val state = resolve(hasItems = true, currentIndex = 1, lastIndex = 2)

        assertTrue(state.playPauseEnabled)
        assertTrue(state.previousEnabled)
        assertTrue(state.nextEnabled)
    }

    @Test
    fun singleItemListDisablesPreviousAndNext() {
        val state = resolve(hasItems = true, currentIndex = 0, lastIndex = 0)

        assertTrue(state.playPauseEnabled)
        assertFalse(state.previousEnabled)
        assertFalse(state.nextEnabled)
    }

    @Test
    fun notRunningShowsPlayState() {
        val state = resolve(isRunning = false)

        assertFalse(state.showPause)
    }

    @Test
    fun runningShowsPauseState() {
        val state = resolve(isRunning = true)

        assertTrue(state.showPause)
    }

    @Test
    fun indexBelowNoSelectionPreservesCurrentButtonRules() {
        val state = resolve(hasItems = true, currentIndex = -2, lastIndex = 2)

        assertTrue(state.playPauseEnabled)
        assertFalse(state.previousEnabled)
        assertFalse(state.nextEnabled)
    }

    @Test
    fun indexAboveLastPreservesCurrentButtonRules() {
        val state = resolve(hasItems = true, currentIndex = 3, lastIndex = 2)

        assertTrue(state.playPauseEnabled)
        assertTrue(state.previousEnabled)
        assertFalse(state.nextEnabled)
    }

    private fun resolve(
        hasItems: Boolean = true,
        currentIndex: Int = 0,
        lastIndex: Int = 0,
        isRunning: Boolean = false
    ): PlayerControlsState {
        return PlayerControlsStateResolver.resolvePlaybackButtons(
            hasItems = hasItems,
            currentIndex = currentIndex,
            lastIndex = lastIndex,
            isRunning = isRunning
        )
    }
}
