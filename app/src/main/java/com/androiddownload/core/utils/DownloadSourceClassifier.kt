package com.androiddownload.core.utils

import android.net.Uri
import java.util.Locale

object DownloadSourceClassifier {
    fun shouldUseHttpDownloader(url: String): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        if (host.isBlank()) return true
        if (host in YTDLP_HOSTS) return false
        if (YTDLP_HOSTS.any { host.endsWith(".$it") }) return false
        return hasDirectFileExtension(uri)
    }

    private fun hasDirectFileExtension(uri: Uri): Boolean {
        val lastSegment = uri.lastPathSegment?.substringBefore('?')?.lowercase(Locale.US).orEmpty()
        val extension = lastSegment.substringAfterLast('.', missingDelimiterValue = "")
        return extension in DIRECT_FILE_EXTENSIONS
    }

    private val DIRECT_FILE_EXTENSIONS = setOf(
        "mp4",
        "mp3",
        "jpg",
        "jpeg",
        "png",
        "zip",
        "pdf",
        "apk",
        "bin"
    )

    private val YTDLP_HOSTS = setOf(
        "youtube.com",
        "youtu.be",
        "m.youtube.com",
        "music.youtube.com",
        "instagram.com",
        "www.instagram.com",
        "facebook.com",
        "www.facebook.com",
        "fb.watch",
        "tiktok.com",
        "www.tiktok.com",
        "x.com",
        "www.x.com",
        "twitter.com",
        "www.twitter.com",
        "vimeo.com",
        "www.vimeo.com"
    )
}
