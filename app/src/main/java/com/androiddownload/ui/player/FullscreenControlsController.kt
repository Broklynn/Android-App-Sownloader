package com.androiddownload.ui.player

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.TextView

class FullscreenControlsController(
    private val controls: View,
    private val closeButton: ImageButton,
    private val playPauseButton: ImageButton,
    private val seekFeedbackText: TextView,
    private val shouldAutoHide: () -> Boolean,
    private val onPlayPauseClick: () -> Unit,
    private val onCloseClick: () -> Unit
) {
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val feedbackHandler = Handler(Looper.getMainLooper())
    private var controlsVisible = true

    init {
        closeButton.setOnClickListener { onCloseClick() }
        playPauseButton.setOnClickListener { onPlayPauseClick() }
    }

    fun showControls() {
        controlsVisible = true
        controls.visibility = View.VISIBLE
    }

    fun hideControls() {
        if (!shouldAutoHide()) return
        controlsVisible = false
        controls.visibility = View.GONE
    }

    fun toggleControls() {
        if (controlsVisible) {
            hideControls()
        } else {
            showControls()
            scheduleAutoHide()
        }
    }

    fun scheduleAutoHide() {
        controlsHandler.removeCallbacksAndMessages(null)
        if (!shouldAutoHide()) return
        controlsHandler.postDelayed({ hideControls() }, 3_000L)
    }

    fun clearCallbacks() {
        controlsHandler.removeCallbacksAndMessages(null)
        feedbackHandler.removeCallbacksAndMessages(null)
        seekFeedbackText.visibility = View.GONE
    }

    fun showSeekFeedback(text: String) {
        seekFeedbackText.text = text
        seekFeedbackText.visibility = View.VISIBLE
        feedbackHandler.removeCallbacksAndMessages(null)
        feedbackHandler.postDelayed({
            seekFeedbackText.visibility = View.GONE
        }, 700L)
    }
}
