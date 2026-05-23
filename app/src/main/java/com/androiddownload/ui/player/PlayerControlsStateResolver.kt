package com.androiddownload.ui.player

data class PlayerControlsState(
    val playPauseEnabled: Boolean,
    val previousEnabled: Boolean,
    val nextEnabled: Boolean,
    val showPause: Boolean
)

object PlayerControlsStateResolver {
    fun resolvePlaybackButtons(
        hasItems: Boolean,
        currentIndex: Int,
        lastIndex: Int,
        isRunning: Boolean
    ): PlayerControlsState {
        return PlayerControlsState(
            playPauseEnabled = hasItems,
            previousEnabled = hasItems && currentIndex > 0,
            nextEnabled = hasItems && currentIndex >= 0 && currentIndex < lastIndex,
            showPause = isRunning
        )
    }
}
