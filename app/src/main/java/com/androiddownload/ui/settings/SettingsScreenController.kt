package com.androiddownload.ui.settings

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.androiddownload.core.preferences.DefaultQualityPreferences
import com.androiddownload.ui.downloads.QualityDialogController
import com.androiddownload.ui.downloads.QualityOptionUi

class SettingsScreenController(
    activity: Activity,
    diagnosticsController: DiagnosticsController,
    defaultQualityPreferences: DefaultQualityPreferences,
    qualityDialogController: QualityDialogController,
    defaultQualityValueText: TextView,
    settingsContainer: View,
    downloadLocationCard: View,
    downloadLocationText: TextView,
    chooseDownloadLocationButton: Button,
    useDefaultDownloadLocationButton: Button,
    ytdlpUpdateStatusText: TextView,
    updateYtDlpButton: Button,
    autoUpdateYtDlpButton: Button,
    diagnosticsButton: Button,
    aboutAppButton: Button,
    settingsCloseButton: Button,
    onChooseDownloadLocation: () -> Unit,
    onUseDefaultDownloadLocation: () -> Unit,
    onUpdateYtDlp: () -> Unit,
    onToggleAutoUpdateYtDlp: () -> Unit,
    onCloseSettings: () -> Unit
) {
    private val settingsInfoController = SettingsInfoController(
        activity = activity,
        diagnosticsController = diagnosticsController
    )

    val settingsController = SettingsController(
        settingsContainer = settingsContainer,
        downloadLocationCard = downloadLocationCard,
        downloadLocationText = downloadLocationText,
        chooseDownloadLocationButton = chooseDownloadLocationButton,
        useDefaultDownloadLocationButton = useDefaultDownloadLocationButton,
        ytdlpUpdateStatusText = ytdlpUpdateStatusText,
        updateYtDlpButton = updateYtDlpButton,
        autoUpdateYtDlpButton = autoUpdateYtDlpButton,
        diagnosticsButton = diagnosticsButton,
        aboutAppButton = aboutAppButton,
        settingsCloseButton = settingsCloseButton,
        callbacks = SettingsController.Callbacks(
            onChooseDownloadLocation = onChooseDownloadLocation,
            onUseDefaultDownloadLocation = onUseDefaultDownloadLocation,
            onUpdateYtDlp = onUpdateYtDlp,
            onToggleAutoUpdateYtDlp = onToggleAutoUpdateYtDlp,
            onDiagnostics = settingsInfoController::showDiagnosticsDialog,
            onAbout = settingsInfoController::showAboutDialog,
            onCloseSettings = onCloseSettings
        )
    )

    private val defaultQualityController = DefaultQualityController(
        activity = activity,
        preferences = defaultQualityPreferences,
        qualityDialogController = qualityDialogController,
        valueText = defaultQualityValueText
    )

    fun show(scrollToDownloadLocation: Boolean = false) {
        settingsController.show(scrollToDownloadLocation)
    }

    fun hide() {
        settingsController.hide()
    }

    fun isVisible(): Boolean {
        return settingsController.isVisible()
    }

    fun showDefaultQualityDialog() {
        defaultQualityController.showDialog()
    }

    fun updateDefaultQualityText() {
        defaultQualityController.updateText()
    }

    fun selectedDefaultQualityOption(): QualityOptionUi {
        return defaultQualityController.selectedOption()
    }

    fun downloadQualityOptions(): List<QualityOptionUi> {
        return defaultQualityController.downloadQualityOptions()
    }
}
