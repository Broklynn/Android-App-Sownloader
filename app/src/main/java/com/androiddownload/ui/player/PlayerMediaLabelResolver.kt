package com.androiddownload.ui.player

import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.utils.DownloadSourceClassifier
import java.util.Locale

object PlayerMediaLabelResolver {
    fun typeLabel(download: DownloadEntity, formatLabel: String): String {
        val label = formatLabel.uppercase(Locale.US)
        return when {
            "MP3" in label || finalFileExtension(download).equals("mp3", ignoreCase = true) -> "MP3"
            "MP4" in label || finalFileExtension(download).equals("mp4", ignoreCase = true) -> "MP4"
            DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl) -> "HTTP"
            else -> "MIDIA"
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
}
