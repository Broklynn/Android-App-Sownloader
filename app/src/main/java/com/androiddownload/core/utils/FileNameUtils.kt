package com.androiddownload.core.utils

import android.webkit.URLUtil

object FileNameUtils {
    fun guessFileName(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null
    ): String {
        val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
        return sanitize(guessed.ifBlank { "download.bin" })
    }

    fun sanitize(fileName: String): String {
        return fileName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(180)
            .ifBlank { "download.bin" }
    }
}
