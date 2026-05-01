package com.androiddownload.core.utils

import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import java.util.Locale

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

    fun sanitizeBaseName(value: String): String {
        val cleaned = value
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(180)
            .ifBlank { "download" }
        return cleaned.substringBeforeLast('.', cleaned)
    }

    fun resolveExtensionForMimeType(mimeType: String?): String? {
        val normalized = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        if (normalized == "application/octet-stream") return null

        return when (normalized) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "audio/mpeg" -> "mp3"
            "audio/mp4" -> "m4a"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "application/pdf" -> "pdf"
            "application/zip" -> "zip"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(normalized)
        }
    }

    fun ensureExtension(fileName: String, mimeType: String? = null): String {
        val clean = sanitize(fileName)
        val extension = clean.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
            .takeIf { it.isNotBlank() }

        if (extension != null && extension != "bin") {
            return clean
        }

        val mimeExtension = resolveExtensionForMimeType(mimeType)
        if (mimeExtension.isNullOrBlank()) {
            return clean
        }

        val base = clean.substringBeforeLast('.', clean)
        return "$base.$mimeExtension"
    }
}
