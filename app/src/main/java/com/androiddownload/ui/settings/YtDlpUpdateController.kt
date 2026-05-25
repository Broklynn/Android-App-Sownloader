package com.androiddownload.ui.settings

import android.content.Context
import com.androiddownload.R
import com.androiddownload.core.preferences.SettingsPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class YtDlpUpdateController(
    private val context: Context,
    private val settingsController: SettingsController,
    private val settingsPreferencesStore: SettingsPreferencesStore,
    private val scope: CoroutineScope,
    private val updateManually: suspend () -> Boolean,
    private val showToast: (String) -> Unit
) {
    private var hasActiveDownloads = false
    private var ytDlpUpdateInProgress = false
    private var ytDlpUpdateMessage: String? = null

    fun setHasActiveDownloads(hasActiveDownloads: Boolean) {
        this.hasActiveDownloads = hasActiveDownloads
        updateUiState()
    }

    fun updateYtDlpManually() {
        if (ytDlpUpdateInProgress) return
        if (hasActiveDownloads) {
            showToast(context.getString(R.string.update_ytdlp_busy))
            updateUiState()
            return
        }

        ytDlpUpdateInProgress = true
        ytDlpUpdateMessage = null
        updateUiState()

        scope.launch {
            val success = updateManually()
            ytDlpUpdateInProgress = false
            ytDlpUpdateMessage = context.getString(
                if (success) R.string.update_ytdlp_success else R.string.update_ytdlp_failed
            )
            updateUiState()
        }
    }

    fun toggleAutoUpdateYtDlp() {
        settingsPreferencesStore.toggleAutoUpdateYtDlpOnYoutubeErrors()
        updateAutoUpdateUiState()
    }

    fun updateUiState() {
        settingsController.setYtDlpUpdateState(
            isInProgress = ytDlpUpdateInProgress,
            hasActiveDownloads = hasActiveDownloads,
            message = ytDlpUpdateMessage,
            inProgressText = context.getString(R.string.update_ytdlp_in_progress),
            busyText = context.getString(R.string.update_ytdlp_busy)
        )
    }

    fun updateAutoUpdateUiState() {
        val enabled = settingsPreferencesStore.isAutoUpdateYtDlpOnYoutubeErrorsEnabled()
        settingsController.setAutoUpdateEnabled(
            isEnabled = enabled,
            enabledText = context.getString(R.string.auto_update_ytdlp_enabled),
            disabledText = context.getString(R.string.auto_update_ytdlp_disabled)
        )
    }
}
