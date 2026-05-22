package com.androiddownload.ui.player

import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.ui.downloads.DownloadOpenRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerListControllerTest {
    private val controller = PlayerListController()

    @Test
    fun filtersMp3ForMusic() {
        val mp3 = download(id = 1, fileName = "song.mp3", mimeType = "audio/mpeg")
        val mp4 = download(id = 2, fileName = "video.mp4", mimeType = "video/mp4")

        val state = buildState(
            downloads = listOf(mp3, mp4),
            category = PlayerCategory.MUSIC
        )

        assertEquals(listOf(mp3), state.items)
        assertEquals(-1, state.currentIndex)
        assertFalse(state.shouldClearSelection)
    }

    @Test
    fun filtersMp4ForVideo() {
        val mp3 = download(id = 1, fileName = "song.mp3", mimeType = "audio/mpeg")
        val mp4 = download(id = 2, fileName = "video.mp4", mimeType = "video/mp4")

        val state = buildState(
            downloads = listOf(mp3, mp4),
            category = PlayerCategory.VIDEO
        )

        assertEquals(listOf(mp4), state.items)
        assertEquals(-1, state.currentIndex)
        assertFalse(state.shouldClearSelection)
    }

    @Test
    fun ignoresNonCompletedDownloads() {
        val runningMp3 = download(
            id = 1,
            fileName = "song.mp3",
            mimeType = "audio/mpeg",
            status = DownloadStatus.RUNNING
        )

        val state = buildState(
            downloads = listOf(runningMp3),
            category = PlayerCategory.MUSIC
        )

        assertTrue(state.items.isEmpty())
        assertEquals(-1, state.currentIndex)
        assertFalse(state.shouldClearSelection)
    }

    @Test
    fun preservesIndexWhenCurrentDownloadStillExistsAtSamePosition() {
        val first = download(id = 1, fileName = "first.mp3", mimeType = "audio/mpeg")
        val second = download(id = 2, fileName = "second.mp3", mimeType = "audio/mpeg")

        val state = buildState(
            downloads = listOf(first, second),
            category = PlayerCategory.MUSIC,
            currentIndex = 1,
            currentPlayingId = second.id
        )

        assertEquals(listOf(first, second), state.items)
        assertEquals(1, state.currentIndex)
        assertFalse(state.shouldClearSelection)
    }

    @Test
    fun recalculatesIndexWhenCurrentDownloadMoves() {
        val first = download(id = 1, fileName = "first.mp3", mimeType = "audio/mpeg")
        val second = download(id = 2, fileName = "second.mp3", mimeType = "audio/mpeg")
        val video = download(id = 3, fileName = "video.mp4", mimeType = "video/mp4")

        val state = buildState(
            downloads = listOf(video, second, first),
            category = PlayerCategory.MUSIC,
            currentIndex = 0,
            currentPlayingId = first.id
        )

        assertEquals(listOf(second, first), state.items)
        assertEquals(1, state.currentIndex)
        assertFalse(state.shouldClearSelection)
    }

    @Test
    fun signalsClearSelectionWhenCurrentDownloadLeavesList() {
        val first = download(id = 1, fileName = "first.mp3", mimeType = "audio/mpeg")
        val second = download(id = 2, fileName = "second.mp3", mimeType = "audio/mpeg")

        val state = buildState(
            downloads = listOf(second),
            category = PlayerCategory.MUSIC,
            currentIndex = 0,
            currentPlayingId = first.id
        )

        assertEquals(listOf(second), state.items)
        assertEquals(-1, state.currentIndex)
        assertTrue(state.shouldClearSelection)
    }

    @Test
    fun returnsEmptyListAndSafeIndexWhenThereAreNoCompatibleItems() {
        val video = download(id = 1, fileName = "video.mp4", mimeType = "video/mp4")

        val state = buildState(
            downloads = listOf(video),
            category = PlayerCategory.MUSIC,
            currentIndex = 0,
            currentPlayingId = video.id
        )

        assertTrue(state.items.isEmpty())
        assertEquals(-1, state.currentIndex)
        assertTrue(state.shouldClearSelection)
    }

    @Test
    fun doesNotClearSelectionWhenThereWasNoCurrentSelection() {
        val mp3 = download(id = 1, fileName = "song.mp3", mimeType = "audio/mpeg")

        val state = buildState(
            downloads = listOf(mp3),
            category = PlayerCategory.MUSIC,
            currentIndex = -1,
            currentPlayingId = null
        )

        assertEquals(listOf(mp3), state.items)
        assertEquals(-1, state.currentIndex)
        assertFalse(state.shouldClearSelection)
    }

    private fun buildState(
        downloads: List<DownloadEntity>,
        category: PlayerCategory,
        currentIndex: Int = -1,
        currentPlayingId: Long? = null
    ): PlayerListController.PlayerListState {
        return controller.buildState(
            downloads = downloads,
            category = category,
            currentIndex = currentIndex,
            currentPlayingId = currentPlayingId,
            matchesPlayerCategory = { download, playerCategory ->
                DownloadOpenRouter.matchesPlayerCategory(download, playerCategory, ::formatLabel)
            }
        )
    }

    private fun formatLabel(download: DownloadEntity): String {
        return when (download.mimeType) {
            "audio/mpeg" -> "MP3 - 320k"
            "video/mp4" -> "MP4 - 720p"
            else -> "Download direto"
        }
    }

    private fun download(
        id: Long,
        fileName: String,
        mimeType: String?,
        status: DownloadStatus = DownloadStatus.COMPLETED
    ): DownloadEntity {
        return DownloadEntity(
            id = id,
            sourceUrl = "https://example.com/$fileName",
            fileName = fileName,
            mimeType = mimeType,
            destinationUri = "content://downloads/$fileName",
            status = status
        )
    }
}
