package com.androiddownload.download.request

sealed class DownloadRequestDecision {
    data class InvalidUrl(
        val rawUrl: String,
        val message: String? = null
    ) : DownloadRequestDecision()

    data class DirectDownload(
        val url: String
    ) : DownloadRequestDecision()

    data class YtDlpAskQuality(
        val url: String
    ) : DownloadRequestDecision()

    data class YtDlpFixedQuality(
        val url: String,
        val qualitySelector: String
    ) : DownloadRequestDecision()
}

