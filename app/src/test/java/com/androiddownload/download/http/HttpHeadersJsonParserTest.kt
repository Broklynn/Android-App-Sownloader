package com.androiddownload.download.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpHeadersJsonParserTest {
    @Test
    fun validJsonReturnsAllowedHeaders() {
        val headers = HttpHeadersJsonParser.parse(
            """
                {
                  "User-Agent": "Mozilla/5.0",
                  "Referer": "https://www.instagram.com/",
                  "Accept": "video/mp4"
                }
            """.trimIndent()
        )

        assertEquals(
            mapOf(
                "User-Agent" to "Mozilla/5.0",
                "Referer" to "https://www.instagram.com/",
                "Accept" to "video/mp4"
            ),
            headers
        )
    }

    @Test
    fun sensitiveAndBlockedHeadersAreDiscarded() {
        val headers = HttpHeadersJsonParser.parse(
            """
                {
                  "Cookie": "sessionid=secret",
                  "Authorization": "Bearer secret",
                  "Proxy-Authorization": "Basic secret",
                  "X-CSRFToken": "secret",
                  "X-IG-App-ID": "secret",
                  "X-ASBD-ID": "secret",
                  "X-MID": "secret",
                  "Range": "bytes=0-",
                  "Host": "cdn.example.com",
                  "Connection": "keep-alive",
                  "Content-Length": "10",
                  "Transfer-Encoding": "chunked",
                  "Content-Encoding": "gzip",
                  "Accept-Language": "en-US"
                }
            """.trimIndent()
        )

        assertEquals(mapOf("Accept-Language" to "en-US"), headers)
    }

    @Test
    fun invalidJsonReturnsEmptyMap() {
        assertTrue(HttpHeadersJsonParser.parse("{not-json").isEmpty())
    }

    @Test
    fun invalidHeaderValuesAreDiscarded() {
        val headers = HttpHeadersJsonParser.parse(
            """
                {
                  "User-Agent": "Mozilla\nBad",
                  "Accept": "video/mp4\rBad",
                  "Referer": "https://www.instagram.com/\u0000",
                  "Sec-Fetch-Mode": "navigate"
                }
            """.trimIndent()
        )

        assertEquals(mapOf("Sec-Fetch-Mode" to "navigate"), headers)
    }

    @Test
    fun emptyHeadersAreDiscarded() {
        val headers = HttpHeadersJsonParser.parse(
            """
                {
                  "": "value",
                  "Accept": "",
                  "Referer": "   ",
                  "User-Agent": "Mozilla/5.0"
                }
            """.trimIndent()
        )

        assertEquals(mapOf("User-Agent" to "Mozilla/5.0"), headers)
    }
}
