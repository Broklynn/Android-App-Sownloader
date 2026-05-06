package com.androiddownload.core.utils

import android.content.Context
import com.androiddownload.R
import java.util.Locale

object DownloadErrorFormatter {
    enum class ErrorKind {
        NO_INTERNET,
        UNKNOWN_HOST,
        TIMEOUT,
        CONNECT_FAILED,
        SSL,
        RATE_LIMITED,
        SERVER_UNSTABLE,
        CONNECTION_INTERRUPTED,
        YOUTUBE_VERIFICATION,
        YOUTUBE_TEMPORARY_BLOCK,
        YOUTUBE_UNAVAILABLE,
        UNABLE_TO_DOWNLOAD,
        GENERIC
    }

    fun classify(errorMessage: String?): ErrorKind {
        val normalized = errorMessage.orEmpty().lowercase(Locale.US)
        return when {
            "sem conexão com a internet" in normalized ||
                "sem internet" in normalized ||
                "no route to host" in normalized -> ErrorKind.NO_INTERNET
            "unknownhostexception" in normalized ||
                "unable to resolve host" in normalized ||
                "no address associated with hostname" in normalized -> ErrorKind.UNKNOWN_HOST
            "sockettimeoutexception" in normalized ||
                "read timed out" in normalized ||
                "connect timed out" in normalized ||
                "timeout execute" in normalized ||
                "timeout getinfo" in normalized ||
                "sem callback/progresso" in normalized ||
                "demorou demais" in normalized -> ErrorKind.TIMEOUT
            "connectexception" in normalized ||
                "failed to connect" in normalized ||
                "connection refused" in normalized -> ErrorKind.CONNECT_FAILED
            "sslexception" in normalized ||
                "sslhandshakeexception" in normalized ||
                "certificate" in normalized && "ssl" in normalized -> ErrorKind.SSL
            "http 429" in normalized ||
                "http error 429" in normalized ||
                "erro http. 429" in normalized ||
                "erro http 429" in normalized -> ErrorKind.RATE_LIMITED
            Regex("""http(?: error)?\s*5\d\d""").containsMatchIn(normalized) ||
                Regex("""erro http\.?\s*5\d\d""").containsMatchIn(normalized) -> ErrorKind.SERVER_UNSTABLE
            "connection reset" in normalized ||
                "broken pipe" in normalized ||
                "unexpected end of stream" in normalized ||
                "stream closed" in normalized ||
                "conexão foi interrompida" in normalized -> ErrorKind.CONNECTION_INTERRUPTED
            "sign in to confirm you're not a bot" in normalized ||
                "sign in to confirm you" in normalized && "not a bot" in normalized -> {
                ErrorKind.YOUTUBE_VERIFICATION
            }
            "http error 403" in normalized ||
                "http 403" in normalized ||
                "unable to download video data" in normalized ||
                "requested format is not available" in normalized ||
                "sabr" in normalized ||
                "some web client https formats have been skipped" in normalized ||
                "no title found in player responses" in normalized -> {
                ErrorKind.YOUTUBE_TEMPORARY_BLOCK
            }
            "video unavailable" in normalized -> ErrorKind.YOUTUBE_UNAVAILABLE
            "unable to download" in normalized -> ErrorKind.UNABLE_TO_DOWNLOAD
            else -> ErrorKind.GENERIC
        }
    }

    fun friendlyMessage(context: Context, errorMessage: String?): String {
        return when (classify(errorMessage)) {
            ErrorKind.NO_INTERNET -> context.getString(R.string.download_no_internet)
            ErrorKind.UNKNOWN_HOST -> context.getString(R.string.download_unknown_host)
            ErrorKind.TIMEOUT -> context.getString(R.string.download_connection_timeout)
            ErrorKind.CONNECT_FAILED -> context.getString(R.string.download_connect_failed)
            ErrorKind.SSL -> context.getString(R.string.download_ssl_failed)
            ErrorKind.RATE_LIMITED -> context.getString(R.string.download_rate_limited)
            ErrorKind.SERVER_UNSTABLE -> context.getString(R.string.download_server_unstable)
            ErrorKind.CONNECTION_INTERRUPTED -> context.getString(R.string.download_connection_interrupted)
            ErrorKind.YOUTUBE_VERIFICATION -> context.getString(R.string.download_error_youtube_verification)
            ErrorKind.YOUTUBE_TEMPORARY_BLOCK -> context.getString(R.string.download_error_youtube_temporary_block)
            ErrorKind.YOUTUBE_UNAVAILABLE -> context.getString(R.string.download_error_video_unavailable)
            ErrorKind.UNABLE_TO_DOWNLOAD -> context.getString(R.string.download_error_unable_to_download)
            ErrorKind.GENERIC -> context.getString(R.string.download_error_generic_short)
        }
    }

    fun isYoutubeRecoverable(errorMessage: String?): Boolean {
        return when (classify(errorMessage)) {
            ErrorKind.YOUTUBE_VERIFICATION,
            ErrorKind.YOUTUBE_TEMPORARY_BLOCK -> true
            ErrorKind.NO_INTERNET,
            ErrorKind.UNKNOWN_HOST,
            ErrorKind.TIMEOUT,
            ErrorKind.CONNECT_FAILED,
            ErrorKind.SSL,
            ErrorKind.RATE_LIMITED,
            ErrorKind.SERVER_UNSTABLE,
            ErrorKind.CONNECTION_INTERRUPTED,
            ErrorKind.YOUTUBE_UNAVAILABLE,
            ErrorKind.UNABLE_TO_DOWNLOAD,
            ErrorKind.GENERIC -> false
        }
    }

    fun isYtDlpFallbackRecoverable(errorMessage: String?): Boolean {
        return when (classify(errorMessage)) {
            ErrorKind.YOUTUBE_VERIFICATION,
            ErrorKind.YOUTUBE_TEMPORARY_BLOCK -> true
            ErrorKind.NO_INTERNET,
            ErrorKind.UNKNOWN_HOST,
            ErrorKind.TIMEOUT,
            ErrorKind.CONNECT_FAILED,
            ErrorKind.SSL,
            ErrorKind.RATE_LIMITED,
            ErrorKind.SERVER_UNSTABLE,
            ErrorKind.CONNECTION_INTERRUPTED,
            ErrorKind.YOUTUBE_UNAVAILABLE,
            ErrorKind.UNABLE_TO_DOWNLOAD,
            ErrorKind.GENERIC -> false
        }
    }

    fun isYoutubeAutoUpdateRecoverable(errorMessage: String?): Boolean {
        val normalized = errorMessage.orEmpty().lowercase(Locale.US)
        return classify(normalized) == ErrorKind.YOUTUBE_VERIFICATION ||
            "http error 403" in normalized ||
            "http 403" in normalized ||
            "sabr" in normalized
    }

    fun isTemporaryNetworkError(errorMessage: String?): Boolean {
        return when (classify(errorMessage)) {
            ErrorKind.UNKNOWN_HOST,
            ErrorKind.TIMEOUT,
            ErrorKind.CONNECT_FAILED,
            ErrorKind.RATE_LIMITED,
            ErrorKind.SERVER_UNSTABLE,
            ErrorKind.CONNECTION_INTERRUPTED -> true
            ErrorKind.NO_INTERNET,
            ErrorKind.SSL,
            ErrorKind.YOUTUBE_VERIFICATION,
            ErrorKind.YOUTUBE_TEMPORARY_BLOCK,
            ErrorKind.YOUTUBE_UNAVAILABLE,
            ErrorKind.UNABLE_TO_DOWNLOAD,
            ErrorKind.GENERIC -> false
        }
    }
}
