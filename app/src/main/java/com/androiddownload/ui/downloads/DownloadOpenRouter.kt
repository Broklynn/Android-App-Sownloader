package com.androiddownload.ui.downloads

import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.ui.player.PlayerCategory
import java.util.Locale

class DownloadOpenRouter(
    private val getDownloads: () -> List<DownloadEntity>,
    private val setPlayerCategoryForOpen: (PlayerCategory) -> Unit,
    private val showPlayer: () -> Unit,
    private val startPlaybackAt: (Int) -> Unit,
    private val openExternal: (DownloadEntity) -> Unit,
    private val formatLabelProvider: (DownloadEntity) -> String
) {
    fun open(download: DownloadEntity) {
        if (tryOpenInInternalPlayer(download)) return
        openExternal(download)
    }

    private fun tryOpenInInternalPlayer(download: DownloadEntity): Boolean {
        val category = internalPlayerCategoryFor(download) ?: return false
        val targetIndex = getDownloads()
            .filter { matchesPlayerCategory(it, category, formatLabelProvider) }
            .indexOfFirst { it.id == download.id }
        if (targetIndex < 0) return false

        setPlayerCategoryForOpen(category)
        showPlayer()
        startPlaybackAt(targetIndex)
        return true
    }

    private fun internalPlayerCategoryFor(download: DownloadEntity): PlayerCategory? {
        return when {
            matchesPlayerCategory(download, PlayerCategory.MUSIC, formatLabelProvider) -> PlayerCategory.MUSIC
            matchesPlayerCategory(download, PlayerCategory.VIDEO, formatLabelProvider) -> PlayerCategory.VIDEO
            else -> null
        }
    }

    companion object {
        fun matchesPlayerCategory(
            download: DownloadEntity,
            category: PlayerCategory,
            formatLabelProvider: (DownloadEntity) -> String
        ): Boolean {
            if (download.status != DownloadStatus.COMPLETED) return false
            if (download.destinationUri.isNullOrBlank()) return false
            val extension = finalFileExtension(download).lowercase(Locale.US)
            val formatLabel = formatLabelProvider(download).uppercase(Locale.US)
            val normalizedMime = normalizeMimeType(download.mimeType).orEmpty().lowercase(Locale.US)
            return when (category) {
                PlayerCategory.MUSIC ->
                    extension == "mp3" &&
                        ("MP3" in formatLabel || normalizedMime == "audio/mpeg")
                PlayerCategory.VIDEO ->
                    extension == "mp4" &&
                        ("MP4" in formatLabel || normalizedMime == "video/mp4")
            }
        }

        private fun finalFileExtension(download: DownloadEntity): String {
            val uriPath = download.destinationUri
                ?.takeIf { it.isNotBlank() }
                ?.substringBefore('?')
                ?.substringBefore('#')
                ?.substringAfterLast('/')
                .orEmpty()
            return listOf(download.fileName, uriPath)
                .firstNotNullOfOrNull { name ->
                    name.substringAfterLast('.', missingDelimiterValue = "")
                        .takeIf { it.isNotBlank() }
                }
                .orEmpty()
        }

        private fun normalizeMimeType(mimeType: String?): String? {
            return mimeType
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.contains('/') }
        }
    }
}
