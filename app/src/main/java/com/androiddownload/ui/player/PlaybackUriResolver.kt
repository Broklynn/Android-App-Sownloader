package com.androiddownload.ui.player

import android.content.ContentResolver
import android.net.Uri
import java.io.File

/**
 * Resolves stored destination URI strings into URIs that the internal player can open.
 *
 * This is Android-dependent, uses ContentResolver and File.exists(), does not
 * decide internal/external opening or permissions, and is not connected to the
 * current runtime yet.
 */
class PlaybackUriResolver(
    private val contentResolver: ContentResolver
) {
    fun resolve(destinationUri: String?): Uri? {
        return when (val candidate = PlaybackDestinationParser.parse(destinationUri)) {
            PlaybackUriCandidate.Missing -> null
            is PlaybackUriCandidate.UnsupportedScheme -> null
            is PlaybackUriCandidate.ContentUri -> resolveContentUri(candidate.rawValue)
            is PlaybackUriCandidate.FileUri -> resolveFileUri(candidate.rawValue)
            is PlaybackUriCandidate.LocalPath -> resolveLocalPath(candidate.rawValue)
        }
    }

    private fun resolveContentUri(rawValue: String): Uri? {
        val uri = Uri.parse(rawValue)
        return if (canOpenContentUri(uri)) uri else null
    }

    private fun resolveFileUri(rawValue: String): Uri? {
        val uri = Uri.parse(rawValue)
        val file = File(uri.path ?: return null)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    private fun resolveLocalPath(rawValue: String): Uri? {
        val file = File(rawValue)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    private fun canOpenContentUri(uri: Uri): Boolean {
        return runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
        }.getOrDefault(false)
    }
}
