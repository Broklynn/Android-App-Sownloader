package com.androiddownload.core.preferences

import android.content.SharedPreferences
import org.json.JSONArray

class RecentDownloadsStore(
    private val preferences: SharedPreferences
) {
    fun load(): List<String> {
        val raw = preferences.getString(PREF_RECENT_DOWNLOAD_URLS, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(urls: List<String>) {
        val array = JSONArray()
        urls.take(MAX_RECENT_DOWNLOAD_URLS).forEach { array.put(it) }
        preferences.edit()
            .putString(PREF_RECENT_DOWNLOAD_URLS, array.toString())
            .apply()
    }

    fun add(url: String): List<String> {
        val recentUrls = load().toMutableList()
        recentUrls.removeAll { it == url }
        recentUrls.add(0, url)
        if (recentUrls.size > MAX_RECENT_DOWNLOAD_URLS) {
            recentUrls.subList(MAX_RECENT_DOWNLOAD_URLS, recentUrls.size).clear()
        }
        save(recentUrls)
        return recentUrls
    }

    fun clear() {
        save(emptyList())
    }

    companion object {
        const val MAX_RECENT_DOWNLOAD_URLS_DISPLAYED = 5
        private const val PREF_RECENT_DOWNLOAD_URLS = "recent_download_urls"
        private const val MAX_RECENT_DOWNLOAD_URLS = 10
    }
}
