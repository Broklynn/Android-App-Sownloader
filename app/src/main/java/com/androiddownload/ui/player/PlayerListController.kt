package com.androiddownload.ui.player

import com.androiddownload.core.model.DownloadEntity

class PlayerListController {
    data class PlayerListState(
        val items: List<DownloadEntity>,
        val currentIndex: Int,
        val shouldClearSelection: Boolean
    )

    fun buildState(
        downloads: List<DownloadEntity>,
        category: PlayerCategory,
        currentIndex: Int,
        currentPlayingId: Long?,
        matchesPlayerCategory: (DownloadEntity, PlayerCategory) -> Boolean
    ): PlayerListState {
        val items = downloads.filter { download -> matchesPlayerCategory(download, category) }
        val shouldClearSelection = currentIndex >= items.size ||
            currentIndex >= 0 && items.none { it.id == currentPlayingId }
        val resolvedIndex = if (shouldClearSelection) {
            -1
        } else if (currentIndex >= 0 && currentPlayingId != null) {
            items.indexOfFirst { it.id == currentPlayingId }
        } else {
            currentIndex
        }
        return PlayerListState(
            items = items,
            currentIndex = resolvedIndex,
            shouldClearSelection = shouldClearSelection
        )
    }
}
