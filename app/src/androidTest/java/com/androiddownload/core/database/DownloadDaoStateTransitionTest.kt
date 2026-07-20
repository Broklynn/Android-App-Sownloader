package com.androiddownload.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadDaoStateTransitionTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: DownloadDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.downloadDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun allowedStateTransitionsReturnOne() = runBlocking {
        val queued = insert(DownloadStatus.QUEUED)
        assertEquals(1, markPreparingIfQueued(queued.id))
        assertEquals(1, markRunning(queued.id))
        assertEquals(1, markPaused(queued.id))
        assertEquals(1, markPreparingIfPaused(queued.id))
        assertEquals(1, markRunning(queued.id))
        assertEquals(1, markCompleted(queued.id))

        val failed = insert(DownloadStatus.FAILED)
        assertEquals(1, retryIfFailed(failed.id))

        val cancelable = insert(DownloadStatus.RUNNING)
        assertEquals(1, markCanceled(cancelable.id))
    }

    @Test
    fun canceledRejectsCompletionFailureAndProgress() = runBlocking {
        val canceled = insert(DownloadStatus.CANCELED)

        assertEquals(0, markCompleted(canceled.id))
        assertEquals(0, markFailed(canceled.id))
        assertEquals(0, updateProgress(canceled.id))
        assertEquals(DownloadStatus.CANCELED, dao.getById(canceled.id)?.status)
    }

    @Test
    fun completedRejectsCancellationAndFailure() = runBlocking {
        val completed = insert(DownloadStatus.COMPLETED)

        assertEquals(0, markCanceled(completed.id))
        assertEquals(0, markFailed(completed.id))
        assertEquals(DownloadStatus.COMPLETED, dao.getById(completed.id)?.status)
    }

    @Test
    fun failureAcceptsOnlyActiveSourceStates() = runBlocking {
        listOf(
            DownloadStatus.QUEUED,
            DownloadStatus.PREPARING,
            DownloadStatus.RUNNING
        ).forEach { status ->
            val active = insert(status)
            assertEquals(1, markFailed(active.id))
            assertEquals(DownloadStatus.FAILED, dao.getById(active.id)?.status)
        }
    }

    @Test
    fun cancellationAcceptsAllNonTerminalUserControllableStates() = runBlocking {
        listOf(
            DownloadStatus.QUEUED,
            DownloadStatus.PREPARING,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED
        ).forEach { status ->
            val active = insert(status)
            assertEquals(1, markCanceled(active.id))
            assertEquals(DownloadStatus.CANCELED, dao.getById(active.id)?.status)
        }
    }

    @Test
    fun failedRejectsCompletionAndRetryIsExplicit() = runBlocking {
        val failed = insert(DownloadStatus.FAILED)

        assertEquals(0, markCompleted(failed.id))
        assertEquals(1, retryIfFailed(failed.id))
        assertEquals(DownloadStatus.QUEUED, dao.getById(failed.id)?.status)
    }

    @Test
    fun pausedRejectsProgressAndResumeIsExplicit() = runBlocking {
        val paused = insert(
            status = DownloadStatus.PAUSED,
            tempPath = "/tmp/resume.part",
            totalBytes = 1_000,
            downloadedBytes = 400,
            progress = 40
        )

        assertEquals(0, updateProgress(paused.id))
        assertEquals(0, markCompleted(paused.id))
        assertEquals(0, markFailed(paused.id))
        assertEquals(1, markPreparingIfPaused(paused.id))
        val resumed = requireNotNull(dao.getById(paused.id))
        assertEquals(DownloadStatus.PREPARING, resumed.status)
        assertEquals("/tmp/resume.part", resumed.tempPath)
        assertEquals(1_000L, resumed.totalBytes)
        assertEquals(400L, resumed.downloadedBytes)
        assertEquals(40, resumed.progress)
    }

    @Test
    fun duplicateCancellationAndPauseAreRejectedWithoutChangingWinner() = runBlocking {
        val canceled = insert(DownloadStatus.CANCELED)
        val paused = insert(DownloadStatus.PAUSED)

        assertEquals(0, markCanceled(canceled.id))
        assertEquals(0, markPaused(paused.id))
        assertEquals(DownloadStatus.CANCELED, dao.getById(canceled.id)?.status)
        assertEquals(DownloadStatus.PAUSED, dao.getById(paused.id)?.status)
    }

    @Test
    fun retryAndResumeRejectWrongSourceStates() = runBlocking {
        val running = insert(DownloadStatus.RUNNING)
        val canceled = insert(DownloadStatus.CANCELED)

        assertEquals(0, retryIfFailed(running.id))
        assertEquals(0, markPreparingIfPaused(canceled.id))
    }

    @Test
    fun retryRejectsStaleFailedSnapshot() = runBlocking {
        val failed = insert(DownloadStatus.FAILED)

        assertEquals(
            0,
            dao.retryIfFailed(
                id = failed.id,
                observedUpdatedAt = failed.updatedAt - 1,
                tempPath = null,
                totalBytes = -1,
                downloadedBytes = 0,
                progress = 0,
                updatedAt = nextUpdatedAt()
            )
        )
    }

    @Test
    fun retryCanPreserveReconciledHttpPartialFields() = runBlocking {
        val failed = insert(
            status = DownloadStatus.FAILED,
            tempPath = "/tmp/retry.part",
            totalBytes = 2_000,
            downloadedBytes = 750,
            progress = 37
        )

        assertEquals(
            1,
            dao.retryIfFailed(
                id = failed.id,
                observedUpdatedAt = failed.updatedAt,
                tempPath = failed.tempPath,
                totalBytes = failed.totalBytes,
                downloadedBytes = failed.downloadedBytes,
                progress = failed.progress,
                updatedAt = nextUpdatedAt()
            )
        )

        val retried = requireNotNull(dao.getById(failed.id))
        assertEquals(DownloadStatus.QUEUED, retried.status)
        assertEquals("/tmp/retry.part", retried.tempPath)
        assertEquals(2_000L, retried.totalBytes)
        assertEquals(750L, retried.downloadedBytes)
        assertEquals(37, retried.progress)
    }

    @Test
    fun delayedProgressDoesNotOverwriteUnrelatedFields() = runBlocking {
        val running = insert(
            status = DownloadStatus.RUNNING,
            destinationUri = "content://winner/destination",
            errorMessage = "winner-error"
        )

        assertEquals(1, updateProgress(running.id))

        val persisted = requireNotNull(dao.getById(running.id))
        assertEquals(DownloadStatus.RUNNING, persisted.status)
        assertEquals("content://winner/destination", persisted.destinationUri)
        assertEquals("winner-error", persisted.errorMessage)
        assertEquals(512L, persisted.downloadedBytes)
        assertEquals(50, persisted.progress)
    }

    @Test
    fun twoConcurrentCompletionsHaveExactlyOneWinner() = runBlocking {
        val running = insert(DownloadStatus.RUNNING)

        val results = race(
            { markCompleted(running.id, "content://attempt/one") },
            { markCompleted(running.id, "content://attempt/two") }
        )

        assertEquals(listOf(0, 1), results.sorted())
        assertEquals(DownloadStatus.COMPLETED, dao.getById(running.id)?.status)
    }

    @Test
    fun cancellationAndCompletionHaveExactlyOneWinner() = runBlocking {
        val running = insert(DownloadStatus.RUNNING)

        val results = race(
            { markCanceled(running.id) },
            { markCompleted(running.id) }
        )

        assertEquals(listOf(0, 1), results.sorted())
    }

    @Test
    fun cancellationAndFailureHaveExactlyOneWinner() = runBlocking {
        val running = insert(DownloadStatus.RUNNING)

        val results = race(
            { markCanceled(running.id) },
            { markFailed(running.id) }
        )

        assertEquals(listOf(0, 1), results.sorted())
    }

    @Test
    fun startupRecoveryRejectsChangedStatus() = runBlocking {
        val observed = insert(DownloadStatus.RUNNING)
        assertEquals(1, markCanceled(observed.id))

        assertEquals(0, recover(observed, DownloadStatus.FAILED))
        assertEquals(DownloadStatus.CANCELED, dao.getById(observed.id)?.status)
    }

    @Test
    fun startupRecoveryRejectsChangedUpdatedAt() = runBlocking {
        val observed = insert(DownloadStatus.RUNNING)
        assertEquals(1, updateProgress(observed.id))

        assertEquals(0, recover(observed, DownloadStatus.FAILED))
        assertEquals(DownloadStatus.RUNNING, dao.getById(observed.id)?.status)
    }

    @Test
    fun startupRecoveryAppliesWhenSnapshotIsStillCurrent() = runBlocking {
        val observed = insert(DownloadStatus.RUNNING, tempPath = "/tmp/interrupted.part")

        assertEquals(1, recover(observed, DownloadStatus.PAUSED))
        assertEquals(DownloadStatus.PAUSED, dao.getById(observed.id)?.status)
    }

    @Test
    fun cancellationClearsTempOnlyWhenItWins() = runBlocking {
        val running = insert(DownloadStatus.RUNNING, tempPath = "/tmp/attempt.part")
        assertEquals(1, markCanceled(running.id))
        assertNull(dao.getById(running.id)?.tempPath)

        val completed = insert(DownloadStatus.COMPLETED, tempPath = "/tmp/winner.part")
        assertEquals(0, markCanceled(completed.id))
        assertEquals("/tmp/winner.part", dao.getById(completed.id)?.tempPath)
    }

    private suspend fun race(
        first: suspend () -> Int,
        second: suspend () -> Int
    ): List<Int> = withTimeout(TEST_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            val ready = CompletableDeferred<Unit>()
            val jobs = listOf(
                async {
                    ready.await()
                    first()
                },
                async {
                    ready.await()
                    second()
                }
            )
            ready.complete(Unit)
            jobs.awaitAll()
        }
    }

    private suspend fun markPreparingIfQueued(id: Long): Int {
        return dao.markPreparingIfQueued(
            id,
            nextUpdatedAt()
        )
    }

    private suspend fun markPreparingIfPaused(id: Long): Int {
        return dao.markPreparingIfPaused(
            id,
            nextUpdatedAt()
        )
    }

    private suspend fun markRunning(id: Long): Int {
        return dao.markRunningIfPreparingOrRunning(
            id = id,
            finalUrl = "https://example.invalid/final",
            fileName = "file.bin",
            mimeType = "application/octet-stream",
            tempPath = "/tmp/attempt.part",
            totalBytes = 1_024,
            downloadedBytes = 0,
            progress = 0,
            speed = 0,
            updatedAt = nextUpdatedAt()
        )
    }

    private suspend fun updateProgress(id: Long): Int {
        return dao.updateProgressIfRunning(
            id = id,
            tempPath = "/tmp/attempt.part",
            totalBytes = 1_024,
            downloadedBytes = 512,
            progress = 50,
            speed = 100,
            updatedAt = nextUpdatedAt()
        )
    }

    private suspend fun markCompleted(
        id: Long,
        destinationUri: String = "content://attempt/final"
    ): Int {
        return dao.markCompletedIfRunning(
            id = id,
            finalUrl = "https://example.invalid/final",
            fileName = "file.bin",
            mimeType = "application/octet-stream",
            destinationUri = destinationUri,
            totalBytes = 1_024,
            downloadedBytes = 1_024,
            updatedAt = nextUpdatedAt()
        )
    }

    private suspend fun markFailed(id: Long): Int {
        return dao.markFailedIfActive(
            id = id,
            errorMessage = "failure",
            clearTempPath = 0,
            resetProgress = 0,
            updatedAt = nextUpdatedAt()
        )
    }

    private suspend fun markCanceled(id: Long): Int {
        return dao.markCanceledIfActive(
            id = id,
            updatedAt = nextUpdatedAt()
        )
    }

    private suspend fun markPaused(id: Long): Int {
        return dao.markPausedIfRunning(
            id,
            nextUpdatedAt()
        )
    }

    private suspend fun retryIfFailed(id: Long): Int {
        val observedUpdatedAt = requireNotNull(dao.getById(id)).updatedAt
        return dao.retryIfFailed(
            id = id,
            observedUpdatedAt = observedUpdatedAt,
            tempPath = null,
            totalBytes = -1,
            downloadedBytes = 0,
            progress = 0,
            updatedAt = nextUpdatedAt()
        )
    }

    private suspend fun recover(
        observed: DownloadEntity,
        recoveredStatus: DownloadStatus
    ): Int {
        return dao.recoverIfSnapshotCurrent(
            id = observed.id,
            observedStatus = observed.status,
            observedUpdatedAt = observed.updatedAt,
            recoveredStatus = recoveredStatus,
            errorMessage = "recovered",
            tempPath = null,
            totalBytes = -1,
            downloadedBytes = 0,
            progress = 0,
            updatedAt = nextUpdatedAt()
        )
    }

    private suspend fun insert(
        status: DownloadStatus,
        destinationUri: String? = null,
        errorMessage: String? = null,
        tempPath: String? = null,
        totalBytes: Long = -1,
        downloadedBytes: Long = 0,
        progress: Int = 0
    ): DownloadEntity {
        val id = dao.insert(
            DownloadEntity(
                sourceUrl = "https://example.invalid/file",
                fileName = "file.bin",
                destinationUri = destinationUri,
                tempPath = tempPath,
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
                progress = progress,
                status = status,
                errorMessage = errorMessage,
                createdAt = INITIAL_UPDATED_AT,
                updatedAt = INITIAL_UPDATED_AT
            )
        )
        return requireNotNull(dao.getById(id))
    }

    private fun nextUpdatedAt(): Long = System.nanoTime()

    private companion object {
        const val INITIAL_UPDATED_AT = 1_000L
        const val TEST_TIMEOUT_MS = 5_000L
    }
}
