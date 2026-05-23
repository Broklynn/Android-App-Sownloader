package com.androiddownload.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlayerCompletionResolverTest {
    @Test
    fun emptyListStopsAtEnd() {
        val action = resolve(itemCount = 0, currentIndex = 0)

        assertSame(PlayerCompletionAction.StopAtEnd, action)
    }

    @Test
    fun noCurrentIndexStopsAtEnd() {
        val action = resolve(itemCount = 3, currentIndex = -1)

        assertSame(PlayerCompletionAction.StopAtEnd, action)
    }

    @Test
    fun firstItemWithNextItemPlaysNext() {
        val action = resolve(itemCount = 3, currentIndex = 0)

        assertEquals(PlayerCompletionAction.PlayNext(1), action)
    }

    @Test
    fun intermediateItemPlaysNext() {
        val action = resolve(itemCount = 4, currentIndex = 1)

        assertEquals(PlayerCompletionAction.PlayNext(2), action)
    }

    @Test
    fun penultimateItemPlaysLastIndex() {
        val action = resolve(itemCount = 4, currentIndex = 2)

        assertEquals(PlayerCompletionAction.PlayNext(3), action)
    }

    @Test
    fun lastItemStopsAtEnd() {
        val action = resolve(itemCount = 3, currentIndex = 2)

        assertSame(PlayerCompletionAction.StopAtEnd, action)
    }

    @Test
    fun singleItemStopsAtEnd() {
        val action = resolve(itemCount = 1, currentIndex = 0)

        assertSame(PlayerCompletionAction.StopAtEnd, action)
    }

    @Test
    fun indexAboveLastStopsAtEnd() {
        val action = resolve(itemCount = 3, currentIndex = 4)

        assertSame(PlayerCompletionAction.StopAtEnd, action)
    }

    @Test
    fun negativeItemCountStopsAtEnd() {
        val action = resolve(itemCount = -1, currentIndex = 0)

        assertSame(PlayerCompletionAction.StopAtEnd, action)
    }

    private fun resolve(
        itemCount: Int,
        currentIndex: Int
    ): PlayerCompletionAction {
        return PlayerCompletionResolver.resolveCompletion(
            itemCount = itemCount,
            currentIndex = currentIndex
        )
    }
}
