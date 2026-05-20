package com.androiddownload.download.media

import android.content.Context
import com.androiddownload.download.queue.DownloadQueue
import com.androiddownload.download.service.DownloadForegroundService

class SharedMediaDownloadCoordinator(
    context: Context,
    private val queue: DownloadQueue
) {
    private val appContext = context.applicationContext

    suspend fun enqueueSelected(
        items: List<SharedMediaItem>,
        qualitySelector: String? = null
    ): List<Long> {
        if (items.isEmpty()) return emptyList()

        return items.map { item ->
            queue.enqueue(
                sourceUrl = item.sourceUrl,
                qualitySelector = item.qualitySelectorForDownload(qualitySelector)
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
        qualitySelector: String? = null
    ): List<Long> {
        val downloadIds = enqueueSelected(items, qualitySelector)
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
}
