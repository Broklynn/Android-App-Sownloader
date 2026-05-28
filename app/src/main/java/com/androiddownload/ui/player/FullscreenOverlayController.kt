package com.androiddownload.ui.player

import android.view.View
import android.widget.TextView

class FullscreenOverlayController(
    private val overlay: View,
    private val titleText: TextView
) {
    fun show(title: String) {
        titleText.text = title
        overlay.visibility = View.VISIBLE
    }

    fun hide() {
        overlay.visibility = View.GONE
    }

    fun isOpen(): Boolean {
        return overlay.visibility == View.VISIBLE
    }
}
