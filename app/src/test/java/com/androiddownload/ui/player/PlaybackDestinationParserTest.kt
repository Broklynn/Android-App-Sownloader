package com.androiddownload.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDestinationParserTest {
    @Test
    fun `null destination is missing`() {
        assertSame(
            PlaybackUriCandidate.Missing,
            PlaybackDestinationParser.parse(null)
        )
    }

    @Test
    fun `blank destination is missing`() {
        assertSame(
            PlaybackUriCandidate.Missing,
            PlaybackDestinationParser.parse("   ")
        )
    }

    @Test
    fun `content uri is classified as content uri`() {
        val candidate = PlaybackDestinationParser.parse("content://media/external/audio/media/1")

        assertEquals(
            PlaybackUriCandidate.ContentUri("content://media/external/audio/media/1"),
            candidate
        )
    }

    @Test
    fun `file uri is classified as file uri`() {
        val candidate = PlaybackDestinationParser.parse("file:///storage/emulated/0/Music/a.mp3")

        assertEquals(
            PlaybackUriCandidate.FileUri("file:///storage/emulated/0/Music/a.mp3"),
            candidate
        )
    }

    @Test
    fun `absolute path is classified as local path`() {
        val candidate = PlaybackDestinationParser.parse("/storage/emulated/0/Music/a.mp3")

        assertEquals(
            PlaybackUriCandidate.LocalPath("/storage/emulated/0/Music/a.mp3"),
            candidate
        )
    }

    @Test
    fun `relative path is classified as local path`() {
        val candidate = PlaybackDestinationParser.parse("relative/path/a.mp3")

        assertEquals(
            PlaybackUriCandidate.LocalPath("relative/path/a.mp3"),
            candidate
        )
    }

    @Test
    fun `http uri is unsupported scheme`() {
        val candidate = PlaybackDestinationParser.parse("http://example.com/a.mp3")

        assertEquals(
            PlaybackUriCandidate.UnsupportedScheme(
                rawValue = "http://example.com/a.mp3",
                scheme = "http"
            ),
            candidate
        )
    }

    @Test
    fun `https uri is unsupported scheme`() {
        val candidate = PlaybackDestinationParser.parse("https://example.com/a.mp4")

        assertEquals(
            PlaybackUriCandidate.UnsupportedScheme(
                rawValue = "https://example.com/a.mp4",
                scheme = "https"
            ),
            candidate
        )
    }

    @Test
    fun `ftp uri is unsupported scheme`() {
        val candidate = PlaybackDestinationParser.parse("ftp://example.com/a.mp3")

        assertEquals(
            PlaybackUriCandidate.UnsupportedScheme(
                rawValue = "ftp://example.com/a.mp3",
                scheme = "ftp"
            ),
            candidate
        )
    }

    @Test
    fun `parser stores trimmed raw value`() {
        val candidate = PlaybackDestinationParser.parse(" content://x ")

        assertEquals(
            PlaybackUriCandidate.ContentUri("content://x"),
            candidate
        )
    }

    @Test
    fun `colon after slash does not create scheme`() {
        val candidate = PlaybackDestinationParser.parse("/storage/emulated/0/Music/a:b.mp3")

        assertEquals(
            PlaybackUriCandidate.LocalPath("/storage/emulated/0/Music/a:b.mp3"),
            candidate
        )
    }

    @Test
    fun `windows style path is treated as unsupported single letter scheme`() {
        val candidate = PlaybackDestinationParser.parse("C:\\Music\\a.mp3")

        assertTrue(candidate is PlaybackUriCandidate.UnsupportedScheme)
        assertEquals("c", (candidate as PlaybackUriCandidate.UnsupportedScheme).scheme)
    }
}
