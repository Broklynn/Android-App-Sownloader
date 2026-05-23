package com.androiddownload.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAdjacentNavigatorTest {
    @Test
    fun emptyListReturnsNoTargetAndDoesNotStart() {
        val target = resolve(itemCount = 0, currentIndex = -1, offset = 1)

        assertEquals(null, target.targetIndex)
        assertFalse(target.shouldStart)
    }

    @Test
    fun noSelectionWithItemsTargetsFirstItemAndStarts() {
        val target = resolve(itemCount = 3, currentIndex = -1, offset = 1)

        assertEquals(0, target.targetIndex)
        assertTrue(target.shouldStart)
    }

    @Test
    fun firstItemWithPreviousTargetsFirstItemAndDoesNotStart() {
        val target = resolve(itemCount = 3, currentIndex = 0, offset = -1)

        assertEquals(0, target.targetIndex)
        assertFalse(target.shouldStart)
    }

    @Test
    fun firstItemWithNextTargetsSecondItemAndStarts() {
        val target = resolve(itemCount = 3, currentIndex = 0, offset = 1)

        assertEquals(1, target.targetIndex)
        assertTrue(target.shouldStart)
    }

    @Test
    fun lastItemWithNextTargetsLastItemAndDoesNotStart() {
        val target = resolve(itemCount = 3, currentIndex = 2, offset = 1)

        assertEquals(2, target.targetIndex)
        assertFalse(target.shouldStart)
    }

    @Test
    fun lastItemWithPreviousTargetsPreviousItemAndStarts() {
        val target = resolve(itemCount = 3, currentIndex = 2, offset = -1)

        assertEquals(1, target.targetIndex)
        assertTrue(target.shouldStart)
    }

    @Test
    fun intermediateItemWithNextTargetsNextItemAndStarts() {
        val target = resolve(itemCount = 3, currentIndex = 1, offset = 1)

        assertEquals(2, target.targetIndex)
        assertTrue(target.shouldStart)
    }

    @Test
    fun intermediateItemWithPreviousTargetsPreviousItemAndStarts() {
        val target = resolve(itemCount = 3, currentIndex = 1, offset = -1)

        assertEquals(0, target.targetIndex)
        assertTrue(target.shouldStart)
    }

    @Test
    fun singleItemListDoesNotRestartForPrevious() {
        val target = resolve(itemCount = 1, currentIndex = 0, offset = -1)

        assertEquals(0, target.targetIndex)
        assertFalse(target.shouldStart)
    }

    @Test
    fun singleItemListDoesNotRestartForNext() {
        val target = resolve(itemCount = 1, currentIndex = 0, offset = 1)

        assertEquals(0, target.targetIndex)
        assertFalse(target.shouldStart)
    }

    @Test
    fun indexAboveLastCoercesToLastIndexAndStarts() {
        val target = resolve(itemCount = 3, currentIndex = 4, offset = 1)

        assertEquals(2, target.targetIndex)
        assertTrue(target.shouldStart)
    }

    @Test
    fun offsetZeroWithValidIndexDoesNotStart() {
        val target = resolve(itemCount = 3, currentIndex = 1, offset = 0)

        assertEquals(1, target.targetIndex)
        assertFalse(target.shouldStart)
    }

    private fun resolve(
        itemCount: Int,
        currentIndex: Int,
        offset: Int
    ): PlayerAdjacentTarget {
        return PlayerAdjacentNavigator.resolveTarget(
            itemCount = itemCount,
            currentIndex = currentIndex,
            offset = offset
        )
    }
}
