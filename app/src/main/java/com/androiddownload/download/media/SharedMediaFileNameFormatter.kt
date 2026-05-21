package com.androiddownload.download.media

import com.androiddownload.core.utils.FileNameUtils
import java.util.Locale

object SharedMediaFileNameFormatter {
    fun format(preview: SharedMediaPreview, item: SharedMediaItem): String {
        return format(previewTitle = preview.title, item = item)
    }

    fun format(previewTitle: String?, item: SharedMediaItem): String {
        val pageName = previewTitle.toPageName()
        val itemTitle = item.title.cleanTitle()
        val label = itemTitle.takeIf { it.isUsefulItemTitle() }
            ?: item.genericLabel()

        val baseName = when {
            pageName != null && label.isNotBlank() && !label.equals(pageName, ignoreCase = true) -> {
                "$pageName - $label"
            }
            pageName != null -> pageName
            itemTitle.isNotBlank() -> itemTitle
            else -> "instagram-${item.index.coerceAtLeast(1)}"
        }

        val sanitizedBase = FileNameUtils.sanitizeBaseName(baseName)
            .take(MAX_BASE_NAME_LENGTH)
            .ifBlank { "instagram-${item.index.coerceAtLeast(1)}" }
        return "$sanitizedBase.${item.resolveExtension()}"
    }

    private fun String?.toPageName(): String? {
        val cleaned = cleanTitle()
        if (cleaned.isBlank()) return null

        val normalized = cleaned
            .replace(Regex("\\s+"), " ")
            .removePrefixIgnoringCase("Instagram Post by ")
            .removePrefixIgnoringCase("Post by ")
            .removePrefixIgnoringCase("Reel by ")
            .removePrefixIgnoringCase("Video by ")
            .removeSuffixIgnoringCase(" - Instagram")
            .trim()

        return normalized.takeIf { it.isNotBlank() && !it.isGenericPreviewTitle() }
    }

    private fun String?.cleanTitle(): String {
        return orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.isUsefulItemTitle(): Boolean {
        if (isBlank()) return false
        val normalized = lowercase(Locale.US)
        return !GENERIC_ITEM_TITLE.matches(normalized)
    }

    private fun String.isGenericPreviewTitle(): Boolean {
        val normalized = lowercase(Locale.US)
        return normalized == "instagram" ||
            normalized == "post" ||
            normalized == "reel" ||
            normalized == "video"
    }

    private fun SharedMediaItem.genericLabel(): String {
        val index = index.coerceAtLeast(1)
        return when (type) {
            SharedMediaType.VIDEO -> "Video $index"
            SharedMediaType.AUDIO -> "Audio $index"
            SharedMediaType.IMAGE -> "Imagem $index"
            SharedMediaType.UNKNOWN -> "Midia $index"
        }
    }

    private fun SharedMediaItem.resolveExtension(): String {
        val fromUrl = sourceUrl
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/', missingDelimiterValue = "")
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
            .takeIf { it in ALLOWED_EXTENSIONS }

        if (fromUrl != null) return fromUrl

        return when (type) {
            SharedMediaType.VIDEO -> "mp4"
            SharedMediaType.AUDIO -> "mp3"
            SharedMediaType.IMAGE -> "jpg"
            SharedMediaType.UNKNOWN -> "bin"
        }
    }

    private fun String.removePrefixIgnoringCase(prefix: String): String {
        return if (startsWith(prefix, ignoreCase = true)) {
            drop(prefix.length)
        } else {
            this
        }
    }

    private fun String.removeSuffixIgnoringCase(suffix: String): String {
        return if (endsWith(suffix, ignoreCase = true)) {
            dropLast(suffix.length)
        } else {
            this
        }
    }

    private const val MAX_BASE_NAME_LENGTH = 120
    private val GENERIC_ITEM_TITLE = Regex("^(item|media|midia|video|audio|image|imagem|photo|foto)(\\s+\\d+)?$")
    private val ALLOWED_EXTENSIONS = setOf(
        "mp4",
        "m4v",
        "webm",
        "mov",
        "mkv",
        "mp3",
        "m4a",
        "aac",
        "opus",
        "ogg",
        "wav",
        "jpg",
        "jpeg",
        "png",
        "webp",
        "gif"
    )
}
