package com.androiddownload.ui.player

/**
 * Pure parser for stored playback destination strings.
 *
 * This only classifies the raw destination value. It does not validate file
 * existence, content URI readability, MIME type, or playback support.
 */
sealed class PlaybackUriCandidate {
    object Missing : PlaybackUriCandidate()

    data class ContentUri(val rawValue: String) : PlaybackUriCandidate()

    data class FileUri(val rawValue: String) : PlaybackUriCandidate()

    data class LocalPath(val rawValue: String) : PlaybackUriCandidate()

    data class UnsupportedScheme(
        val rawValue: String,
        val scheme: String
    ) : PlaybackUriCandidate()
}

object PlaybackDestinationParser {
    fun parse(destinationUri: String?): PlaybackUriCandidate {
        val value = destinationUri?.trim()?.takeIf { it.isNotBlank() }
            ?: return PlaybackUriCandidate.Missing
        val scheme = schemeOf(value)
            ?: return PlaybackUriCandidate.LocalPath(value)

        return when (scheme) {
            "content" -> PlaybackUriCandidate.ContentUri(value)
            "file" -> PlaybackUriCandidate.FileUri(value)
            else -> PlaybackUriCandidate.UnsupportedScheme(
                rawValue = value,
                scheme = scheme
            )
        }
    }

    private fun schemeOf(value: String): String? {
        val colonIndex = value.indexOf(':')
        if (colonIndex <= 0) return null

        val firstSlashIndex = value.indexOf('/')
        if (firstSlashIndex >= 0 && colonIndex > firstSlashIndex) return null

        val candidate = value.substring(0, colonIndex)
        return candidate
            .takeIf { it.first().isLetter() }
            ?.takeIf { scheme -> scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' } }
            ?.lowercase()
    }
}
