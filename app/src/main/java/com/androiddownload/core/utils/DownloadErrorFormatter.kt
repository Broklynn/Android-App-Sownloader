package com.androiddownload.core.utils

import android.content.Context
import com.androiddownload.R
import java.util.Locale

object DownloadErrorFormatter {
    enum class ErrorKind {
        YOUTUBE_VERIFICATION,
        YOUTUBE_TEMPORARY_BLOCK,
        YOUTUBE_UNAVAILABLE,
        UNABLE_TO_DOWNLOAD,
        GENERIC
    }

    fun classify(errorMessage: String?): ErrorKind {
        val normalized = errorMessage.orEmpty().lowercase(Locale.US)
        return when {
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
            ErrorKind.YOUTUBE_UNAVAILABLE,
            ErrorKind.UNABLE_TO_DOWNLOAD,
            ErrorKind.GENERIC -> false
        }
    }

    fun isYtDlpFallbackRecoverable(errorMessage: String?): Boolean {
        return when (classify(errorMessage)) {
            ErrorKind.YOUTUBE_VERIFICATION,
            ErrorKind.YOUTUBE_TEMPORARY_BLOCK -> true
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
}
