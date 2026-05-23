package com.androiddownload.ui.player

data class PlayerAdjacentTarget(
    val targetIndex: Int?,
    val shouldStart: Boolean
)

object PlayerAdjacentNavigator {
    fun resolveTarget(
        itemCount: Int,
        currentIndex: Int,
        offset: Int
    ): PlayerAdjacentTarget {
        if (itemCount <= 0) {
            return PlayerAdjacentTarget(targetIndex = null, shouldStart = false)
        }

        val lastIndex = itemCount - 1
        val targetIndex = if (currentIndex < 0) {
            0
        } else {
            (currentIndex + offset).coerceIn(0, lastIndex)
        }
        return PlayerAdjacentTarget(
            targetIndex = targetIndex,
            shouldStart = targetIndex != currentIndex || currentIndex < 0
        )
    }
}
