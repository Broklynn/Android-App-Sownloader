package com.androiddownload.download.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMediaFileNameFormatterTest {
    @Test
    fun postByPreviewTitleAndGenericVideoTitleBuildFriendlyMp4Name() {
        val fileName = SharedMediaFileNameFormatter.format(
            preview = preview(title = "Post by updatecharts"),
            item = item(title = "Video 2", index = 2, sourceUrl = "https://video.fbcdn.net/example.mp4")
        )

        assertTrue(fileName.contains("updatecharts"))
        assertTrue(fileName.contains("Video 2"))
        assertTrue(fileName.endsWith(".mp4"))
    }

    @Test
    fun previewTitleIsUsedWhenItemTitleIsBlank() {
        val fileName = SharedMediaFileNameFormatter.format(
            preview = preview(title = "Post by bringmethehorizonmena"),
            item = item(title = "", index = 3, sourceUrl = "https://video.fbcdn.net/example.mp4")
        )

        assertTrue(fileName.contains("bringmethehorizonmena"))
        assertTrue(fileName.contains("Video 3"))
        assertTrue(fileName.endsWith(".mp4"))
    }

    @Test
    fun imageKeepsImageExtensionFromSourceUrl() {
        val fileName = SharedMediaFileNameFormatter.format(
            preview = preview(title = "Post by olhonocine"),
            item = item(
                title = "Imagem 1",
                index = 1,
                type = SharedMediaType.IMAGE,
                sourceUrl = "https://image.fbcdn.net/photo.jpg?stp=dst-jpg"
            )
        )

        assertTrue(fileName.endsWith(".jpg"))
    }

    @Test
    fun invalidFileNameCharactersAreSanitized() {
        val fileName = SharedMediaFileNameFormatter.format(
            preview = preview(title = "Post by update:charts/bad*name?"),
            item = item(title = "Video 1", index = 1, sourceUrl = "https://video.fbcdn.net/example.mp4")
        )

        assertFalse(fileName.contains(":"))
        assertFalse(fileName.contains("/"))
        assertFalse(fileName.contains("*"))
        assertFalse(fileName.contains("?"))
        assertTrue(fileName.endsWith(".mp4"))
    }

    @Test
    fun emptyTitlesUseSafeFallbackName() {
        val fileName = SharedMediaFileNameFormatter.format(
            preview = preview(title = ""),
            item = item(title = "", index = 1, sourceUrl = "https://video.fbcdn.net/example.mp4")
        )

        assertTrue(fileName.startsWith("Video 1") || fileName.startsWith("instagram-1"))
        assertTrue(fileName.endsWith(".mp4"))
    }

    private fun preview(title: String?): SharedMediaPreview {
        return SharedMediaPreview(
            originalUrl = "https://www.instagram.com/p/example/",
            title = title,
            items = emptyList()
        )
    }

    private fun item(
        title: String,
        index: Int,
        type: SharedMediaType = SharedMediaType.VIDEO,
        sourceUrl: String
    ): SharedMediaItem {
        return SharedMediaItem(
            id = "item-$index",
            title = title,
            index = index,
            type = type,
            thumbnailUrl = null,
            sourceUrl = sourceUrl
        )
    }
}
