package com.androiddownload.ui.player

sealed class PlayerCompletionAction {
    data class PlayNext(val index: Int) : PlayerCompletionAction()
    object StopAtEnd : PlayerCompletionAction()
}

object PlayerCompletionResolver {
    fun resolveCompletion(
        itemCount: Int,
        currentIndex: Int
    ): PlayerCompletionAction {
        if (itemCount <= 0 || currentIndex < 0) {
            return PlayerCompletionAction.StopAtEnd
        }

        val nextIndex = currentIndex + 1
        return if (nextIndex < itemCount) {
            PlayerCompletionAction.PlayNext(nextIndex)
        } else {
            PlayerCompletionAction.StopAtEnd
        }
    }
}
