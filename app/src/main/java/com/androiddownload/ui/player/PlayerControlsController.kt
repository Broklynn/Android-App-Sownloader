package com.androiddownload.ui.player

import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import com.androiddownload.R

class PlayerControlsController(
    private val playerMusicChip: Button,
    private val playerVideoChip: Button,
    private val playerVideoView: VideoView,
    private val playerFullscreenButton: ImageButton,
    private val playerNowPlayingTitle: TextView,
    private val playerNowPlayingSubtitle: TextView,
    private val playerNowPlayingMetaText: TextView,
    private val playerSeekBar: SeekBar,
    private val playerCurrentTimeText: TextView,
    private val playerDurationText: TextView,
    private val playerPreviousButton: Button,
    private val playerPlayPauseButton: Button,
    private val playerNextButton: Button,
    private val videoFullscreenSeekBar: SeekBar,
    private val videoFullscreenCurrentTimeText: TextView,
    private val videoFullscreenDurationText: TextView,
    private val videoFullscreenPlayPauseButton: ImageButton
) {
    fun updateCategoryUi(
        category: PlayerCategory,
        isVideoVisible: Boolean,
        showFullscreenButton: Boolean
    ) {
        listOf(playerMusicChip, playerVideoChip).forEach { button ->
            val selected = when (button.id) {
                R.id.playerMusicChip -> category == PlayerCategory.MUSIC
                R.id.playerVideoChip -> category == PlayerCategory.VIDEO
                else -> false
            }
            button.setBackgroundResource(
                if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
            )
            button.setTextColor(
                button.context.getColor(if (selected) R.color.brand else R.color.text_muted)
            )
        }
        playerVideoView.visibility = if (isVideoVisible) View.VISIBLE else View.GONE
        playerFullscreenButton.visibility = if (showFullscreenButton) View.VISIBLE else View.GONE
    }

    fun updatePlaybackButtons(
        hasItems: Boolean,
        currentIndex: Int,
        lastIndex: Int,
        isRunning: Boolean,
        playText: String,
        pauseText: String
    ) {
        playerPlayPauseButton.isEnabled = hasItems
        playerPreviousButton.isEnabled = hasItems && currentIndex > 0
        playerNextButton.isEnabled = hasItems && currentIndex >= 0 && currentIndex < lastIndex
        playerPlayPauseButton.text = if (isRunning) pauseText else playText
        videoFullscreenPlayPauseButton.setImageResource(
            if (isRunning) R.drawable.ic_pause_simple else R.drawable.ic_play_simple
        )
        videoFullscreenPlayPauseButton.contentDescription = if (isRunning) pauseText else playText
    }

    fun updateNowPlaying(title: String, subtitle: String, meta: String) {
        playerNowPlayingTitle.text = title
        playerNowPlayingSubtitle.text = subtitle
        playerNowPlayingMetaText.text = meta
    }

    fun updateInlineSeekPreview(currentTime: String) {
        playerCurrentTimeText.text = currentTime
    }

    fun updateFullscreenSeekPreview(currentTime: String) {
        videoFullscreenCurrentTimeText.text = currentTime
    }

    fun resetInlineProgress(zeroTime: String) {
        playerSeekBar.progress = 0
        playerCurrentTimeText.text = zeroTime
        playerDurationText.text = zeroTime
    }

    fun resetFullscreenProgress(zeroTime: String) {
        videoFullscreenSeekBar.progress = 0
        videoFullscreenCurrentTimeText.text = zeroTime
        videoFullscreenDurationText.text = zeroTime
    }

    fun updateProgress(
        progress: Int,
        currentTime: String,
        duration: String,
        updateInlineSeek: Boolean,
        updateFullscreenSeek: Boolean
    ) {
        if (updateInlineSeek) {
            playerSeekBar.progress = progress
        }
        if (updateFullscreenSeek) {
            videoFullscreenSeekBar.progress = progress
        }
        playerCurrentTimeText.text = currentTime
        playerDurationText.text = duration
        videoFullscreenCurrentTimeText.text = currentTime
        videoFullscreenDurationText.text = duration
    }

    fun updateFullscreenProgress(progress: Int, currentTime: String, duration: CharSequence) {
        videoFullscreenSeekBar.progress = progress
        videoFullscreenCurrentTimeText.text = currentTime
        videoFullscreenDurationText.text = duration
    }
}
