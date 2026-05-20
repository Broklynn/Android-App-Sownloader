package com.androiddownload.download.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMediaPreviewParserTest {
    @Test
    fun multipleEntriesAreMappedToPreviewItems() {
        val originalUrl = "https://www.instagram.com/p/example/"
        val preview = SharedMediaPreviewParser.parse(
            originalUrl = originalUrl,
            json = """
                {
                  "title": "Carousel post",
                  "entries": [
                    {
                      "id": "video-1",
                      "title": "First item",
                      "ext": "mp4",
                      "thumbnail": "https://example.com/thumb-1.jpg",
                      "url": "https://cdn.example.com/video-1.mp4",
                      "webpage_url": "https://www.instagram.com/p/example/?img_index=1"
                    },
                    {
                      "id": "image-2",
                      "title": "Second item",
                      "ext": "jpg",
                      "thumbnail": "https://example.com/thumb-2.jpg",
                      "url": "https://cdn.example.com/image-2.jpg",
                      "webpage_url": "https://www.instagram.com/p/example/?img_index=2"
                    }
                  ]
                }
            """.trimIndent()
        )

        assertEquals(originalUrl, preview.originalUrl)
        assertEquals("Carousel post", preview.title)
        assertEquals(2, preview.items.size)
        assertTrue(preview.hasMultipleItems)
        assertEquals(1, preview.items[0].index)
        assertEquals(2, preview.items[1].index)
        assertEquals("https://cdn.example.com/video-1.mp4", preview.items[0].sourceUrl)
        assertEquals("https://cdn.example.com/image-2.jpg", preview.items[1].sourceUrl)
        assertEquals("https://example.com/thumb-1.jpg", preview.items[0].thumbnailUrl)
        assertEquals("https://example.com/thumb-2.jpg", preview.items[1].thumbnailUrl)
    }

    @Test
    fun entryWithDirectUrlAndWebpageUrlPrefersDirectMediaUrl() {
        val preview = SharedMediaPreviewParser.parse(
            originalUrl = "https://www.instagram.com/p/example/",
            json = """
                {
                  "entries": [
                    {
                      "id": "video-1",
                      "title": "Video 1",
                      "url": "https://cdn.example.com/video-1.mp4",
                      "webpage_url": "https://www.instagram.com/p/example/?img_index=1",
                      "thumbnail": "https://example.com/thumb.jpg",
                      "ext": "mp4"
                    }
                  ]
                }
            """.trimIndent()
        )

        val item = preview.items.single()
        assertEquals("https://cdn.example.com/video-1.mp4", item.sourceUrl)
        assertEquals("https://example.com/thumb.jpg", item.thumbnailUrl)
        assertEquals(SharedMediaType.VIDEO, item.type)
    }

    @Test
    fun singleItemJsonWithoutEntriesIsMappedAsOneItem() {
        val originalUrl = "https://www.instagram.com/reel/example/"
        val preview = SharedMediaPreviewParser.parse(
            originalUrl = originalUrl,
            json = """
                {
                  "id": "single-video",
                  "title": "Single reel",
                  "webpage_url": "https://www.instagram.com/reel/example/",
                  "thumbnail": "https://example.com/single.jpg",
                  "mime_type": "video/mp4"
                }
            """.trimIndent()
        )

        assertEquals(1, preview.items.size)
        assertFalse(preview.hasMultipleItems)
        assertEquals("Single reel", preview.items[0].title)
        assertEquals("https://www.instagram.com/reel/example/", preview.items[0].sourceUrl)
    }

    @Test
    fun detectsVideoTypeFromExtensionMimeTypeOrCodec() {
        assertEquals(
            SharedMediaType.VIDEO,
            parseFirstItemType("""{"entries":[{"ext":"mp4"}]}""")
        )
        assertEquals(
            SharedMediaType.VIDEO,
            parseFirstItemType("""{"entries":[{"mime_type":"video/mp4"}]}""")
        )
        assertEquals(
            SharedMediaType.VIDEO,
            parseFirstItemType("""{"entries":[{"vcodec":"h264"}]}""")
        )
    }

    @Test
    fun detectsImageTypeFromExtensionOrMimeType() {
        assertEquals(
            SharedMediaType.IMAGE,
            parseFirstItemType("""{"entries":[{"ext":"jpg"}]}""")
        )
        assertEquals(
            SharedMediaType.IMAGE,
            parseFirstItemType("""{"entries":[{"ext":"webp"}]}""")
        )
        assertEquals(
            SharedMediaType.IMAGE,
            parseFirstItemType("""{"entries":[{"mime_type":"image/jpeg"}]}""")
        )
    }

    @Test
    fun missingEntryFieldsUseStableFallbacks() {
        val originalUrl = "https://www.instagram.com/p/example/"
        val preview = SharedMediaPreviewParser.parse(
            originalUrl = originalUrl,
            json = """{"entries":[{}]}"""
        )
        val item = preview.items.single()

        assertNotEquals("", item.id)
        assertEquals("Item 1", item.title)
        assertEquals(originalUrl, item.sourceUrl)
        assertEquals(SharedMediaType.UNKNOWN, item.type)
    }

    private fun parseFirstItemType(json: String): SharedMediaType {
        return SharedMediaPreviewParser.parse(
            originalUrl = "https://www.instagram.com/p/example/",
            json = json
        ).items.first().type
    }
}
