package com.androiddownload.download.media

data class SharedMediaItem(
    val id: String,
    val title: String,
    val index: Int,
    val type: SharedMediaType,
    val thumbnailUrl: String?,
    val sourceUrl: String
)

enum class SharedMediaType {
    VIDEO,
    AUDIO,
    IMAGE,
    UNKNOWN
}

data class SharedMediaPreview(
    val originalUrl: String,
    val title: String?,
    val items: List<SharedMediaItem>
) {
    val hasMultipleItems: Boolean
        get() = items.size > 1
}
