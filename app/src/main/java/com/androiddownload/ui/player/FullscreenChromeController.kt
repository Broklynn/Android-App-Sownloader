package com.androiddownload.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View

class FullscreenChromeController(
    private val activity: Activity,
    private val appHeader: View,
    private val mainTabBar: View,
    private val isSettingsVisible: () -> Boolean
) {
    private var previousRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var previousSystemUiVisibility = 0
    private var fullscreenChromeApplied = false

    fun enterFullscreen() {
        if (fullscreenChromeApplied) return
        fullscreenChromeApplied = true
        previousRequestedOrientation = activity.requestedOrientation
        previousSystemUiVisibility = activity.window.decorView.systemUiVisibility
        appHeader.visibility = View.GONE
        mainTabBar.visibility = View.GONE
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    fun exitFullscreen() {
        if (!fullscreenChromeApplied) return
        fullscreenChromeApplied = false
        activity.requestedOrientation = previousRequestedOrientation
        activity.window.decorView.systemUiVisibility = previousSystemUiVisibility
        if (!isSettingsVisible()) {
            appHeader.visibility = View.VISIBLE
            mainTabBar.visibility = View.VISIBLE
        }
    }
}
