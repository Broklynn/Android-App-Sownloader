package com.androiddownload.ui.player

import android.content.Context
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.ui.downloads.DownloadTextProvider

class PlayerNowPlayingPresenter(
    private val context: Context,
    private val controlsController: PlayerControlsController,
    private val downloadTextProvider: DownloadTextProvider
) {
    fun render(
        download: DownloadEntity?,
        hasSelection: Boolean,
        isRunning: Boolean,
        isPrepared: Boolean,
        currentTime: CharSequence,
        duration: CharSequence
    ) {
        val text = if (download == null || !hasSelection) {
            PlayerNowPlayingTextFormatter.buildEmptyText(
                title = context.getString(R.string.player_nothing_selected),
                subtitle = context.getString(R.string.player_select_file),
                meta = context.getString(R.string.player_status_stopped)
            )
        } else {
            val formatLabel = downloadTextProvider.formatLabel(download)
            PlayerNowPlayingTextFormatter.buildSelectedText(
                fileName = download.fileName,
                typeLabel = playerTypeLabel(download, formatLabel),
                formatLabel = formatLabel,
                statusLabel = playbackStatusLabel(
                    hasSelection = hasSelection,
                    isRunning = isRunning,
                    isPrepared = isPrepared
                ),
                currentTime = currentTime,
                duration = duration
            )
        }

        controlsController.updateNowPlaying(
            title = text.title,
            subtitle = text.subtitle,
            meta = text.meta
        )
    }

    private fun playbackStatusLabel(
        hasSelection: Boolean,
        isRunning: Boolean,
        isPrepared: Boolean
    ): String {
        return when {
            !hasSelection -> context.getString(R.string.player_status_stopped)
            isRunning -> context.getString(R.string.player_status_playing)
            isPrepared -> context.getString(R.string.player_status_paused)
            else -> context.getString(R.string.player_status_stopped)
        }
    }

    private fun playerTypeLabel(download: DownloadEntity, formatLabel: String): String {
        return PlayerMediaLabelResolver.typeLabel(
            download = download,
            formatLabel = formatLabel
        )
    }
}
