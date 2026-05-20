package com.androiddownload.download.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMediaPreviewTest {
    @Test
    fun singleItemPreviewDoesNotHaveMultipleItems() {
        val preview = SharedMediaPreview(
            originalUrl = "https://www.instagram.com/p/example/",
            title = "Example post",
            items = listOf(sharedMediaItem(index = 1))
        )

        assertFalse(preview.hasMultipleItems)
    }

    @Test
    fun twoItemPreviewHasMultipleItems() {
        val preview = SharedMediaPreview(
            originalUrl = "https://www.instagram.com/p/example/",
            title = "Example post",
            items = listOf(
                sharedMediaItem(index = 1),
                sharedMediaItem(index = 2)
            )
        )

        assertTrue(preview.hasMultipleItems)
    }

    private fun sharedMediaItem(index: Int): SharedMediaItem {
        return SharedMediaItem(
            id = "item-$index",
            title = "Item $index",
            index = index,
            type = SharedMediaType.VIDEO,
            thumbnailUrl = null,
            sourceUrl = "https://www.instagram.com/p/example/"
        )
    }
}
