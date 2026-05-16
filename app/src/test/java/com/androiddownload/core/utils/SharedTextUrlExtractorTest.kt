package com.androiddownload.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextUrlExtractorTest {
    @Test
    fun emptyTextReturnsNull() {
        assertNull(extract(""))
    }

    @Test
    fun pureValidUrlReturnsUrl() {
        assertEquals(
            "https://example.com/video",
            extract("  https://example.com/video  ")
        )
    }

    @Test
    fun textWithUrlReturnsUrl() {
        assertEquals(
            "https://example.com/video",
            extract("Veja isto: https://example.com/video agora")
        )
    }

    @Test
    fun urlWithFinalPeriodReturnsUrlWithoutPeriod() {
        assertEquals(
            "https://example.com/video",
            extract("Veja isto: https://example.com/video.")
        )
    }

    @Test
    fun textWithoutUrlReturnsNull() {
        assertNull(extract("sem link aqui"))
    }

    @Test
    fun invalidUrlReturnsNull() {
        assertNull(extract("https://"))
    }

    @Test
    fun urlWithFinalPunctuationReturnsUrlWithoutPunctuation() {
        assertEquals(
            "https://example.com/video",
            extract("Link (https://example.com/video)")
        )
    }

    private fun extract(sharedText: String): String? {
        return SharedTextUrlExtractor.extract(
            sharedText = sharedText,
            isValidHttpUrl = ::isValidHttpUrl
        )
    }

    private fun isValidHttpUrl(value: String): Boolean {
        return value == "https://example.com/video"
    }
}
