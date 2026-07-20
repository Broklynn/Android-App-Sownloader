package com.androiddownload.download.data

import com.androiddownload.core.database.DownloadDao
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DownloadRepositoryTest {
    @Test
    fun rejectedTransitionReportsCurrentWinner() = runBlocking {
        val dao = FakeDownloadDao(
            current = entity(DownloadStatus.CANCELED),
            affectedRows = 0
        )

        val result = DownloadRepository(dao).markCompletedIfRunning(
            id = DOWNLOAD_ID,
            finalUrl = "https://example.invalid/final",
            fileName = "final.bin",
            mimeType = "application/octet-stream",
            destinationUri = "content://attempt/final",
            totalBytes = 100,
            downloadedBytes = 100
        )

        assertEquals(
            DownloadTransitionResult.Rejected(DownloadStatus.CANCELED),
            result
        )
    }

    @Test
    fun unexpectedAffectedRowCountIsReportedAsInvariantViolation() = runBlocking {
        val dao = FakeDownloadDao(
            current = entity(DownloadStatus.RUNNING),
            affectedRows = 2
        )

        val failure = runCatching {
            DownloadRepository(dao).markPaused(DOWNLOAD_ID)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("2 linhas"))
    }

    @Test
    fun cancellationDeletesCapturedTempOnlyAfterCasWins() = runBlocking {
        val directory = Files.createTempDirectory("darkwave-cancel-winner").toFile()
        val tempFile = directory.resolve("download.part").apply { writeText("partial") }
        try {
            val dao = FakeDownloadDao(
                current = entity(DownloadStatus.RUNNING, tempPath = tempFile.absolutePath),
                affectedRows = 1
            )

            val result = DownloadRepository(dao).markCanceled(DOWNLOAD_ID)

            assertEquals(DownloadTransitionResult.Applied, result)
            assertFalse(tempFile.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectedCancellationPreservesCapturedTemp() = runBlocking {
        val directory = Files.createTempDirectory("darkwave-cancel-rejected").toFile()
        val tempFile = directory.resolve("download.part").apply { writeText("winner") }
        try {
            val dao = FakeDownloadDao(
                current = entity(DownloadStatus.COMPLETED, tempPath = tempFile.absolutePath),
                affectedRows = 0
            )

            val result = DownloadRepository(dao).markCanceled(DOWNLOAD_ID)

            assertEquals(
                DownloadTransitionResult.Rejected(DownloadStatus.COMPLETED),
                result
            )
            assertTrue(tempFile.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun duplicateCancellationIsReportedAsIdempotentRejection() = runBlocking {
        val dao = FakeDownloadDao(
            current = entity(DownloadStatus.CANCELED),
            affectedRows = 0
        )

        val result = DownloadRepository(dao).markCanceled(DOWNLOAD_ID)

        assertEquals(
            DownloadTransitionResult.Rejected(DownloadStatus.CANCELED),
            result
        )
    }

    @Test
    fun rejectedPauseReportsPersistedWinner() = runBlocking {
        val dao = FakeDownloadDao(
            current = entity(DownloadStatus.COMPLETED),
            affectedRows = 0
        )

        val result = DownloadRepository(dao).markPaused(DOWNLOAD_ID)

        assertEquals(
            DownloadTransitionResult.Rejected(DownloadStatus.COMPLETED),
            result
        )
    }

    private fun entity(
        status: DownloadStatus,
        tempPath: String? = null
    ): DownloadEntity {
        return DownloadEntity(
            id = DOWNLOAD_ID,
            sourceUrl = "https://example.invalid/file",
            fileName = "file.bin",
            tempPath = tempPath,
            status = status,
            createdAt = 1,
            updatedAt = 1
        )
    }

    private class FakeDownloadDao(
        var current: DownloadEntity?,
        var affectedRows: Int
    ) : DownloadDao {
        override fun observeDownloads(): Flow<List<DownloadEntity>> = emptyFlow()

        override suspend fun getById(id: Long): DownloadEntity? = current

        override suspend fun getByStatuses(statuses: List<String>): List<DownloadEntity> = emptyList()

        override suspend fun insert(download: DownloadEntity): Long = error("Nao usado")

        override suspend fun markPreparingIfQueued(
            id: Long,
            updatedAt: Long
        ): Int = affectedRows

        override suspend fun markPreparingIfPaused(
            id: Long,
            updatedAt: Long
        ): Int = affectedRows

        override suspend fun markRunningIfPreparingOrRunning(
            id: Long,
            finalUrl: String?,
            fileName: String,
            mimeType: String?,
            tempPath: String?,
            totalBytes: Long,
            downloadedBytes: Long,
            progress: Int,
            speed: Long,
            updatedAt: Long
        ): Int = affectedRows

        override suspend fun updateProgressIfRunning(
            id: Long,
            tempPath: String?,
            totalBytes: Long,
            downloadedBytes: Long,
            progress: Int,
            speed: Long,
            updatedAt: Long
        ): Int = affectedRows

        override suspend fun markCompletedIfRunning(
            id: Long,
            finalUrl: String?,
            fileName: String,
            mimeType: String?,
            destinationUri: String,
            totalBytes: Long,
            downloadedBytes: Long,
            updatedAt: Long
        ): Int = affectedRows

        override suspend fun markFailedIfActive(
            id: Long,
            errorMessage: String?,
            clearTempPath: Int,
            resetProgress: Int,
            updatedAt: Long
        ): Int = affectedRows

        override suspend fun markCanceledIfActive(
            id: Long,
            updatedAt: Long
        ): Int = affectedRows

        override suspend fun markPausedIfRunning(
            id: Long,
            updatedAt: Long
        ): Int = affectedRows

        override suspend fun retryIfFailed(
            id: Long,
            observedUpdatedAt: Long,
            tempPath: String?,
            totalBytes: Long,
            downloadedBytes: Long,
            progress: Int,
            updatedAt: Long
        ): Int = affectedRows

        override suspend fun recoverIfSnapshotCurrent(
            id: Long,
            observedStatus: DownloadStatus,
            observedUpdatedAt: Long,
            recoveredStatus: DownloadStatus,
            errorMessage: String?,
            tempPath: String?,
            totalBytes: Long,
            downloadedBytes: Long,
            progress: Int,
            updatedAt: Long
        ): Int = affectedRows

        override suspend fun deleteByStatuses(statuses: List<String>): Int = 0
    }

    private companion object {
        const val DOWNLOAD_ID = 7L
    }
}
