package com.androiddownload.core.preferences

import android.content.SharedPreferences

class DefaultQualityPreferences(
    private val preferences: SharedPreferences
) {
    fun load(): String? {
        return preferences.getString(PREF_DEFAULT_YTDLP_QUALITY, DEFAULT_QUALITY_ASK_VALUE)
    }

    fun save(value: String?) {
        if (value == null) {
            clear()
            return
        }
        preferences.edit()
            .putString(PREF_DEFAULT_YTDLP_QUALITY, value)
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(PREF_DEFAULT_YTDLP_QUALITY)
            .apply()
    }

    companion object {
        const val DEFAULT_QUALITY_ASK_VALUE = "ask"
        private const val PREF_DEFAULT_YTDLP_QUALITY = "default_ytdlp_quality"
    }
}
