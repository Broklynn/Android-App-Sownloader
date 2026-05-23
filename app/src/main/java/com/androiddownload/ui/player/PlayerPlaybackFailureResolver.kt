package com.androiddownload.ui.player

sealed class PlayerPlaybackFailureAction {
    data class SkipToNext(val index: Int) : PlayerPlaybackFailureAction()
    object Stop : PlayerPlaybackFailureAction()
}

object PlayerPlaybackFailureResolver {
    fun resolveStartFailure(
        itemCount: Int,
        failedIndex: Int,
        skipBudget: Int
    ): PlayerPlaybackFailureAction {
        if (itemCount <= 0 || failedIndex < 0 || skipBudget <= 1) {
            return PlayerPlaybackFailureAction.Stop
        }

        val nextIndex = failedIndex + 1
        return if (nextIndex < itemCount) {
            PlayerPlaybackFailureAction.SkipToNext(nextIndex)
        } else {
            PlayerPlaybackFailureAction.Stop
        }
    }

    fun resolvePlaybackError(
        itemCount: Int,
        currentIndex: Int
    ): PlayerPlaybackFailureAction {
        if (itemCount <= 0 || currentIndex < 0) {
            return PlayerPlaybackFailureAction.Stop
        }

        val nextIndex = currentIndex + 1
        return if (nextIndex < itemCount) {
            PlayerPlaybackFailureAction.SkipToNext(nextIndex)
        } else {
            PlayerPlaybackFailureAction.Stop
        }
    }
}
