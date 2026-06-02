package com.androiddownload.ui.player

import android.content.Context
import com.androiddownload.R

class PlayerPlaybackButtonsPresenter(
    private val context: Context,
    private val controlsController: PlayerControlsController
) {
    fun render(
        hasItems: Boolean,
        currentIndex: Int,
        lastIndex: Int,
        isRunning: Boolean
    ) {
        controlsController.updatePlaybackButtons(
            hasItems = hasItems,
            currentIndex = currentIndex,
            lastIndex = lastIndex,
            isRunning = isRunning,
            playText = context.getString(R.string.player_play),
            pauseText = context.getString(R.string.player_pause)
        )
    }
}
