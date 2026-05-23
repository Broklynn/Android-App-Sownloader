package com.androiddownload.ui.player

data class PlayerNowPlayingText(
    val title: String,
    val subtitle: String,
    val meta: String
)

object PlayerNowPlayingTextFormatter {
    fun buildEmptyText(
        title: String,
        subtitle: String,
        meta: String
    ): PlayerNowPlayingText {
        return PlayerNowPlayingText(
            title = title,
            subtitle = subtitle,
            meta = meta
        )
    }

    fun buildSelectedText(
        fileName: String,
        typeLabel: String,
        formatLabel: String,
        statusLabel: String,
        currentTime: CharSequence,
        duration: CharSequence
    ): PlayerNowPlayingText {
        return PlayerNowPlayingText(
            title = fileName,
            subtitle = "$typeLabel - $formatLabel",
            meta = "$statusLabel - $currentTime/$duration"
        )
    }
}
