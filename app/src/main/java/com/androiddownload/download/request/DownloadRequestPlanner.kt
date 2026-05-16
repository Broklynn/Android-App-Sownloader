package com.androiddownload.download.request

import com.androiddownload.core.utils.DownloadSourceClassifier
import com.androiddownload.core.utils.UrlValidator

class DownloadRequestPlanner(
    private val isValidUrl: (String) -> Boolean = UrlValidator::isValidHttpUrl,
    private val shouldUseHttpDownloader: (String) -> Boolean = DownloadSourceClassifier::shouldUseHttpDownloader
) {
    fun plan(
        rawUrl: String,
        defaultQualityPreferenceValue: String,
        defaultQualitySelector: String?
    ): DownloadRequestDecision {
        val url = rawUrl.trim()
        if (!isValidUrl(url)) {
            return DownloadRequestDecision.InvalidUrl(rawUrl)
        }

        if (shouldUseHttpDownloader(url)) {
            return DownloadRequestDecision.DirectDownload(url)
        }

        val selector = defaultQualitySelector?.takeIf { it.isNotBlank() }
        return if (selector == null || defaultQualityPreferenceValue == DEFAULT_QUALITY_ASK_VALUE) {
            DownloadRequestDecision.YtDlpAskQuality(url)
        } else {
            DownloadRequestDecision.YtDlpFixedQuality(url, selector)
        }
    }

    private companion object {
        const val DEFAULT_QUALITY_ASK_VALUE = "ask"
    }
}

