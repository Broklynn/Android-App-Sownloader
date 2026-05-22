package com.androiddownload.ui.downloads

import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.ui.player.PlayerCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadOpenRouterTest {
    @Test
    fun completedMp3OpensInternalMusicPlayer() {
        val mp3 = download(
            id = 2,
            fileName = "song.mp3",
            mimeType = "audio/mpeg",
            destinationUri = "content://downloads/song.mp3"
        )
        val state = RouterState(downloads = listOf(mp3))

        state.router.open(mp3)

        assertEquals(PlayerCategory.MUSIC, state.selectedCategory)
        assertEquals(0, state.startedIndex)
        assertTrue(state.showPlayerCalled)
        assertNull(state.externalDownload)
    }

    @Test
    fun completedMp4OpensInternalVideoPlayer() {
        val mp4 = download(
            id = 3,
            fileName = "video.mp4",
            mimeType = "video/mp4",
            destinationUri = "content://downloads/video.mp4"
        )
        val state = RouterState(downloads = listOf(mp4))

        state.router.open(mp4)

        assertEquals(PlayerCategory.VIDEO, state.selectedCategory)
        assertEquals(0, state.startedIndex)
        assertTrue(state.showPlayerCalled)
        assertNull(state.externalDownload)
    }

    @Test
    fun nonMediaDownloadOpensExternally() {
        val file = download(
            id = 4,
            fileName = "Android.gitignore",
            mimeType = "text/plain",
            destinationUri = "content://downloads/Android.gitignore"
        )
        val state = RouterState(downloads = listOf(file))

        state.router.open(file)

        assertSame(file, state.externalDownload)
        assertNull(state.selectedCategory)
        assertNull(state.startedIndex)
    }

    @Test
    fun mediaNotPresentInPlayerListOpensExternally() {
        val mp3 = download(
            id = 5,
            fileName = "song.mp3",
            mimeType = "audio/mpeg",
            destinationUri = "content://downloads/song.mp3"
        )
        val state = RouterState(downloads = emptyList())

        state.router.open(mp3)

        assertSame(mp3, state.externalDownload)
        assertNull(state.selectedCategory)
        assertNull(state.startedIndex)
    }

    @Test
    fun indexUsesFilteredPlayerList() {
        val firstVideo = download(
            id = 6,
            fileName = "first.mp4",
            mimeType = "video/mp4",
            destinationUri = "content://downloads/first.mp4"
        )
        val audio = download(
            id = 7,
            fileName = "song.mp3",
            mimeType = "audio/mpeg",
            destinationUri = "content://downloads/song.mp3"
        )
        val secondVideo = download(
            id = 8,
            fileName = "second.mp4",
            mimeType = "video/mp4",
            destinationUri = "content://downloads/second.mp4"
        )
        val state = RouterState(downloads = listOf(firstVideo, audio, secondVideo))

        state.router.open(secondVideo)

        assertEquals(PlayerCategory.VIDEO, state.selectedCategory)
        assertEquals(1, state.startedIndex)
        assertNull(state.externalDownload)
    }

    private class RouterState(downloads: List<DownloadEntity>) {
        var selectedCategory: PlayerCategory? = null
        var startedIndex: Int? = null
        var showPlayerCalled = false
        var externalDownload: DownloadEntity? = null

        val router = DownloadOpenRouter(
            getDownloads = { downloads },
            setPlayerCategoryForOpen = { category -> selectedCategory = category },
            showPlayer = { showPlayerCalled = true },
            startPlaybackAt = { index -> startedIndex = index },
            openExternal = { download -> externalDownload = download },
            formatLabelProvider = { download ->
                when (download.mimeType) {
                    "audio/mpeg" -> "MP3 - 320k"
                    "video/mp4" -> "MP4 - 720p"
                    else -> "Download direto"
                }
            }
        )
    }

    private fun download(
        id: Long,
        fileName: String,
        mimeType: String?,
        destinationUri: String?
    ): DownloadEntity {
        return DownloadEntity(
            id = id,
            sourceUrl = "https://example.com/$fileName",
            fileName = fileName,
            mimeType = mimeType,
            destinationUri = destinationUri,
            status = DownloadStatus.COMPLETED
        )
    }
}
