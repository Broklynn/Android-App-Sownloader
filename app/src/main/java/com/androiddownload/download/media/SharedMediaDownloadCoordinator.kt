package com.androiddownload.download.media

import android.content.Context
import com.androiddownload.download.queue.DownloadQueue
import com.androiddownload.download.service.DownloadForegroundService
import org.json.JSONObject
import java.util.Locale

class SharedMediaDownloadCoordinator(
    context: Context,
    private val queue: DownloadQueue
) {
    private val appContext = context.applicationContext

    suspend fun enqueueSelected(
        items: List<SharedMediaItem>,
        qualitySelector: String? = null,
        preview: SharedMediaPreview? = null
    ): List<Long> {
        if (items.isEmpty()) return emptyList()

        return items.map { item ->
            queue.enqueue(
                sourceUrl = item.sourceUrl,
                qualitySelector = item.qualitySelectorForDownload(qualitySelector),
                httpHeadersJson = headersToJson(item.httpHeaders),
                suggestedFileName = preview?.let { SharedMediaFileNameFormatter.format(it, item) }
                    ?: SharedMediaFileNameFormatter.format(previewTitle = null, item = item)
            )
        }
    }

    fun startServices(downloadIds: List<Long>) {
        downloadIds.forEach { downloadId ->
            DownloadForegroundService.start(appContext, downloadId)
        }
    }

    suspend fun enqueueAndStart(
        items: List<SharedMediaItem>,
        qualitySelector: String? = null,
        preview: SharedMediaPreview? = null
    ): List<Long> {
        val downloadIds = enqueueSelected(items, qualitySelector, preview)
        startServices(downloadIds)
        return downloadIds
    }

    private fun SharedMediaItem.qualitySelectorForDownload(qualitySelector: String?): String? {
        if (sourceUrl.isDirectMediaUrl()) return null
        return when (type) {
            SharedMediaType.IMAGE -> null
            SharedMediaType.UNKNOWN -> null
            SharedMediaType.VIDEO,
            SharedMediaType.AUDIO -> qualitySelector?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun headersToJson(headers: Map<String, String>): String? {
        if (headers.isEmpty()) return null

        val json = JSONObject()
        headers.forEach { (name, value) ->
            val cleanName = name.trim()
            val cleanValue = value.trim()
            if (cleanName.isBlank() || cleanValue.isBlank()) return@forEach
            if (!isSafeHeaderName(cleanName) || !isSafeHeaderValue(cleanValue)) return@forEach
            if (cleanName.isBlockedHeaderName()) return@forEach

            json.put(cleanName, cleanValue)
        }
        return json.takeIf { it.length() > 0 }?.toString()
    }

    private fun isSafeHeaderName(name: String): Boolean {
        return name.all { char -> char.code in 33..126 && char !in INVALID_HEADER_NAME_CHARS }
    }

    private fun isSafeHeaderValue(value: String): Boolean {
        return value.none { char -> char == '\r' || char == '\n' || char.code == 0 }
    }

    private fun String.isBlockedHeaderName(): Boolean {
        val normalized = lowercase(Locale.US)
        if (normalized.startsWith("x-ig-")) return true
        if (SENSITIVE_HEADER_NAME_PARTS.any { normalized.contains(it) }) return true
        return normalized in BLOCKED_HEADER_NAMES
    }

    private fun String.isDirectMediaUrl(): Boolean {
        val normalized = substringBefore('?').lowercase()
        return normalized.endsWith(".mp4") ||
            normalized.endsWith(".m4v") ||
            normalized.endsWith(".webm") ||
            normalized.endsWith(".mov") ||
            normalized.endsWith(".mp3") ||
            normalized.endsWith(".m4a") ||
            normalized.endsWith(".aac") ||
            normalized.endsWith(".ogg") ||
            normalized.endsWith(".jpg") ||
            normalized.endsWith(".jpeg") ||
            normalized.endsWith(".png") ||
            normalized.endsWith(".webp") ||
            normalized.endsWith(".gif")
    }

    private companion object {
        val BLOCKED_HEADER_NAMES = setOf(
            "cookie",
            "authorization",
            "proxy-authorization",
            "x-csrftoken",
            "x-asbd-id",
            "x-mid",
            "range",
            "host",
            "connection",
            "content-length",
            "transfer-encoding",
            "content-encoding"
        )
        val SENSITIVE_HEADER_NAME_PARTS = setOf(
            "token",
            "auth",
            "session",
            "cookie",
            "credential"
        )
        val INVALID_HEADER_NAME_CHARS = setOf(
            '(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']', '?', '=', '{', '}', ' ', '\t'
        )
    }
}
