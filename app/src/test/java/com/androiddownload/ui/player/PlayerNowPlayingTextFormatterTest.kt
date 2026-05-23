package com.androiddownload.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerNowPlayingTextFormatterTest {
    @Test
    fun buildEmptyTextPreservesReceivedTitleSubtitleAndMeta() {
        val text = PlayerNowPlayingTextFormatter.buildEmptyText(
            title = "Nenhum arquivo selecionado",
            subtitle = "Escolha um download para reproduzir.",
            meta = "Parado"
        )

        assertEquals("Nenhum arquivo selecionado", text.title)
        assertEquals("Escolha um download para reproduzir.", text.subtitle)
        assertEquals("Parado", text.meta)
    }

    @Test
    fun buildSelectedTextBuildsMp3Subtitle() {
        val text = selectedText(typeLabel = "MP3", formatLabel = "MP3 - 320k")

        assertEquals("MP3 - MP3 - 320k", text.subtitle)
    }

    @Test
    fun buildSelectedTextBuildsMp4Subtitle() {
        val text = selectedText(typeLabel = "MP4", formatLabel = "MP4 - 1080p")

        assertEquals("MP4 - MP4 - 1080p", text.subtitle)
    }

    @Test
    fun buildSelectedTextBuildsPausedMeta() {
        val text = selectedText(statusLabel = "Pausado")

        assertEquals("Pausado - 0:07/0:11", text.meta)
    }

    @Test
    fun buildSelectedTextBuildsPlayingMeta() {
        val text = selectedText(statusLabel = "Tocando")

        assertEquals("Tocando - 0:07/0:11", text.meta)
    }

    @Test
    fun buildSelectedTextAcceptsCurrentTimeAndDurationAsCharSequence() {
        val currentTime: CharSequence = StringBuilder("0:07")
        val duration: CharSequence = StringBuilder("0:11")

        val text = selectedText(currentTime = currentTime, duration = duration)

        assertEquals("Pausado - 0:07/0:11", text.meta)
    }

    @Test
    fun buildSelectedTextPreservesDashSeparator() {
        val text = selectedText(typeLabel = "MP3", formatLabel = "MP3 - 320k")

        assertEquals("MP3 - MP3 - 320k", text.subtitle)
    }

    @Test
    fun buildSelectedTextPreservesSlashSeparator() {
        val text = selectedText(currentTime = "0:07", duration = "0:11")

        assertEquals("Pausado - 0:07/0:11", text.meta)
    }

    @Test
    fun buildSelectedTextDoesNotAlterReceivedFileName() {
        val text = selectedText(fileName = "Deep Focus Music.mp4")

        assertEquals("Deep Focus Music.mp4", text.title)
    }

    private fun selectedText(
        fileName: String = "track.mp3",
        typeLabel: String = "MP3",
        formatLabel: String = "MP3 - 320k",
        statusLabel: String = "Pausado",
        currentTime: CharSequence = "0:07",
        duration: CharSequence = "0:11"
    ): PlayerNowPlayingText {
        return PlayerNowPlayingTextFormatter.buildSelectedText(
            fileName = fileName,
            typeLabel = typeLabel,
            formatLabel = formatLabel,
            statusLabel = statusLabel,
            currentTime = currentTime,
            duration = duration
        )
    }
}
