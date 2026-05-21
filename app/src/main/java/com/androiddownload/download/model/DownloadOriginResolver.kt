package com.androiddownload.download.model

import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.utils.DownloadSourceClassifier
import java.net.URI
import java.util.Locale

object DownloadOriginResolver {
    fun resolve(download: DownloadEntity): DownloadOrigin {
        val sourceHost = hostOf(download.sourceUrl)
        val headers = download.httpHeadersJson.orEmpty().lowercase(Locale.US)

        return when {
            isYoutubeHost(sourceHost) -> DownloadOrigin.YOUTUBE
            isTikTokHost(sourceHost) -> DownloadOrigin.TIKTOK
            isInstagramHost(sourceHost) -> DownloadOrigin.INSTAGRAM
            isInstagramCdnHost(sourceHost) -> DownloadOrigin.INSTAGRAM
            isFacebookCdnHost(sourceHost) && headers.contains("referer") && headers.contains("instagram.com") ->
                DownloadOrigin.INSTAGRAM
            DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl) -> DownloadOrigin.FILES
            else -> DownloadOrigin.FILES
        }
    }

    private fun hostOf(url: String): String {
        return runCatching { URI(url).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")
    }

    private fun isYoutubeHost(host: String): Boolean {
        return host == "youtube.com" ||
            host == "www.youtube.com" ||
            host == "m.youtube.com" ||
            host == "music.youtube.com" ||
            host == "youtu.be"
    }

    private fun isTikTokHost(host: String): Boolean {
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }

    private fun isInstagramHost(host: String): Boolean {
        return host == "instagram.com" || host.endsWith(".instagram.com")
    }

    private fun isInstagramCdnHost(host: String): Boolean {
        return host.contains("cdninstagram")
    }

    private fun isFacebookCdnHost(host: String): Boolean {
        return host.contains("fbcdn")
    }
}
