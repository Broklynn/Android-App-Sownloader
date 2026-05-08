package com.androiddownload.ui.settings

import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView

class SettingsController(
    private val settingsContainer: View,
    private val downloadLocationCard: View,
    private val downloadLocationText: TextView,
    private val chooseDownloadLocationButton: Button,
    private val useDefaultDownloadLocationButton: Button,
    private val ytdlpUpdateStatusText: TextView,
    private val updateYtDlpButton: Button,
    private val autoUpdateYtDlpButton: Button,
    private val diagnosticsButton: Button,
    private val aboutAppButton: Button,
    private val settingsCloseButton: Button,
    private val callbacks: Callbacks
) {
    data class Callbacks(
        val onChooseDownloadLocation: () -> Unit,
        val onUseDefaultDownloadLocation: () -> Unit,
        val onUpdateYtDlp: () -> Unit,
        val onToggleAutoUpdateYtDlp: () -> Unit,
        val onDiagnostics: () -> Unit,
        val onAbout: () -> Unit,
        val onCloseSettings: () -> Unit
    )

    init {
        chooseDownloadLocationButton.setOnClickListener { callbacks.onChooseDownloadLocation() }
        useDefaultDownloadLocationButton.setOnClickListener { callbacks.onUseDefaultDownloadLocation() }
        updateYtDlpButton.setOnClickListener { callbacks.onUpdateYtDlp() }
        autoUpdateYtDlpButton.setOnClickListener { callbacks.onToggleAutoUpdateYtDlp() }
        diagnosticsButton.setOnClickListener { callbacks.onDiagnostics() }
        aboutAppButton.setOnClickListener { callbacks.onAbout() }
        settingsCloseButton.setOnClickListener { callbacks.onCloseSettings() }
    }

    fun show(scrollToDownloadLocation: Boolean = false) {
        settingsContainer.visibility = View.VISIBLE
        if (scrollToDownloadLocation) {
            settingsContainer.post {
                (settingsContainer as? ScrollView)?.smoothScrollTo(0, downloadLocationCard.top)
            }
        }
    }

    fun hide() {
        settingsContainer.visibility = View.GONE
    }

    fun isVisible(): Boolean {
        return settingsContainer.visibility == View.VISIBLE
    }

    fun updateDownloadLocationText(text: String) {
        downloadLocationText.text = text
    }

    fun setYtDlpUpdateState(
        isInProgress: Boolean,
        hasActiveDownloads: Boolean,
        message: String?,
        inProgressText: String,
        busyText: String
    ) {
        updateYtDlpButton.isEnabled = !isInProgress && !hasActiveDownloads
        when {
            isInProgress -> updateYtDlpStatus(inProgressText)
            message != null -> updateYtDlpStatus(message)
            hasActiveDownloads -> updateYtDlpStatus(busyText)
            else -> ytdlpUpdateStatusText.visibility = View.GONE
        }
    }

    fun updateYtDlpStatus(text: String) {
        ytdlpUpdateStatusText.visibility = View.VISIBLE
        ytdlpUpdateStatusText.text = text
    }

    fun setAutoUpdateEnabled(isEnabled: Boolean, enabledText: String, disabledText: String) {
        autoUpdateYtDlpButton.text = if (isEnabled) enabledText else disabledText
    }
}
