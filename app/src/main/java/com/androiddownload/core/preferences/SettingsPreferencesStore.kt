package com.androiddownload.core.preferences

import android.content.SharedPreferences

class SettingsPreferencesStore(
    private val preferences: SharedPreferences
) {
    fun isAutoUpdateYtDlpOnYoutubeErrorsEnabled(): Boolean {
        return preferences.getBoolean(PREF_AUTO_UPDATE_YTDLP_ON_YOUTUBE_ERRORS, true)
    }

    fun setAutoUpdateYtDlpOnYoutubeErrorsEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(PREF_AUTO_UPDATE_YTDLP_ON_YOUTUBE_ERRORS, enabled)
            .apply()
    }

    fun toggleAutoUpdateYtDlpOnYoutubeErrors(): Boolean {
        val enabled = !isAutoUpdateYtDlpOnYoutubeErrorsEnabled()
        setAutoUpdateYtDlpOnYoutubeErrorsEnabled(enabled)
        return enabled
    }

    companion object {
        private const val PREF_AUTO_UPDATE_YTDLP_ON_YOUTUBE_ERRORS = "auto_update_ytdlp_on_youtube_errors"
    }
}
