package com.androiddownload.download.ytdlp

import com.androiddownload.core.utils.YtDlpQualityOptions
import java.net.URI
import java.util.Locale

object YtDlpQualitySelectorResolver {
    const val TIKTOK_MP4_SELECTOR = "best[ext=mp4]/best"

    fun resolve(url: String, selectedSelector: String?): String? {
        val selector = selectedSelector?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (isTikTokUrl(url) && selector.isMp4Preset()) {
            TIKTOK_MP4_SELECTOR
        } else {
            selector
        }
    }

    private fun isTikTokUrl(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase(Locale.US).orEmpty() }.getOrDefault("")
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }

    private fun String.isMp4Preset(): Boolean {
        return this == YtDlpQualityOptions.SELECTOR_MP4_1440P ||
            this == YtDlpQualityOptions.SELECTOR_MP4_1080P ||
            this == YtDlpQualityOptions.SELECTOR_MP4_720P ||
            this == YtDlpQualityOptions.SELECTOR_MP4_480P
    }
}
