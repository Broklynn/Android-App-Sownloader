package com.androiddownload.download.data

import com.androiddownload.core.database.DownloadDao
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.FileNameUtils
import kotlinx.coroutines.flow.Flow

sealed interface DownloadTransitionResult {
    data object Applied : DownloadTransitionResult

    data class Rejected(
        val currentStatus: DownloadStatus?
    ) : DownloadTransitionResult
}

class DownloadRepository(
    private val dao: DownloadDao
) {
    fun observeDownloads(): Flow<List<DownloadEntity>> = dao.observeDownloads()

    suspend fun getByStatuses(statuses: List<DownloadStatus>): List<DownloadEntity> {
        return dao.getByStatuses(statuses.map { it.name })
    }

    suspend fun enqueue(
        sourceUrl: String,
        qualitySelector: String? = null,
        httpHeadersJson: String? = null,
        suggestedFileName: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val fileName = suggestedFileName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { FileNameUtils.sanitize(it) }
            ?: FileNameUtils.guessFileName(sourceUrl)
        return dao.insert(
            DownloadEntity(
                sourceUrl = sourceUrl,
                fileName = fileName,
                qualitySelector = qualitySelector,
                httpHeadersJson = httpHeadersJson,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun getById(id: Long): DownloadEntity? = dao.getById(id)

    suspend fun markPreparingIfQueued(id: Long): DownloadTransitionResult {
        return transitionResult(
            id = id,
            operation = "QUEUED -> PREPARING",
            affectedRows = dao.markPreparingIfQueued(
                id = id,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markPreparingIfPaused(id: Long): DownloadTransitionResult {
        return transitionResult(
            id = id,
            operation = "PAUSED -> PREPARING",
            affectedRows = dao.markPreparingIfPaused(
                id = id,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markRunningIfPreparingOrRunning(
        id: Long,
        finalUrl: String?,
        fileName: String,
        mimeType: String?,
        tempPath: String?,
        totalBytes: Long,
        downloadedBytes: Long,
        progress: Int,
        speed: Long = 0
    ): DownloadTransitionResult {
        return transitionResult(
            id = id,
            operation = "PREPARING/RUNNING -> RUNNING",
            affectedRows = dao.markRunningIfPreparingOrRunning(
                id = id,
                finalUrl = finalUrl,
                fileName = fileName,
                mimeType = mimeType,
                tempPath = tempPath,
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
                progress = progress,
                speed = speed,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateProgressIfRunning(
        id: Long,
        tempPath: String?,
        totalBytes: Long,
        downloadedBytes: Long,
        progress: Int,
        speed: Long
    ): DownloadTransitionResult {
        return transitionResult(
            id = id,
            operation = "RUNNING progress",
            affectedRows = dao.updateProgressIfRunning(
                id = id,
                tempPath = tempPath,
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
                progress = progress,
                speed = speed,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markCompletedIfRunning(
        id: Long,
        finalUrl: String?,
        fileName: String,
        mimeType: String?,
        destinationUri: String,
        totalBytes: Long,
        downloadedBytes: Long
    ): DownloadTransitionResult {
        return transitionResult(
            id = id,
            operation = "RUNNING -> COMPLETED",
            affectedRows = dao.markCompletedIfRunning(
                id = id,
                finalUrl = finalUrl,
                fileName = fileName,
                mimeType = mimeType,
                destinationUri = destinationUri,
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markFailedIfActive(
        id: Long,
        errorMessage: String?,
        clearTempPath: Boolean = false,
        resetProgress: Boolean = false
    ): DownloadTransitionResult {
        return transitionResult(
            id = id,
            operation = "active -> FAILED",
            affectedRows = dao.markFailedIfActive(
                id = id,
                errorMessage = errorMessage,
                clearTempPath = clearTempPath.toSqlFlag(),
                resetProgress = resetProgress.toSqlFlag(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markCanceled(id: Long): DownloadTransitionResult {
        val tempPath = dao.getById(id)?.tempPath
        val result = transitionResult(
            id = id,
            operation = "active -> CANCELED",
            affectedRows = dao.markCanceledIfActive(
                id = id,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (result == DownloadTransitionResult.Applied) {
            deleteTempPath(tempPath)
        }
        return result
    }

    suspend fun markPaused(id: Long): DownloadTransitionResult {
        return transitionResult(
            id = id,
            operation = "RUNNING -> PAUSED",
            affectedRows = dao.markPausedIfRunning(
                id = id,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun retryIfFailed(
        id: Long,
        observedUpdatedAt: Long,
        tempPath: String?,
        totalBytes: Long,
        downloadedBytes: Long,
        progress: Int
    ): DownloadTransitionResult {
        return transitionResult(
            id = id,
            operation = "FAILED -> QUEUED",
            affectedRows = dao.retryIfFailed(
                id = id,
                observedUpdatedAt = observedUpdatedAt,
                tempPath = tempPath,
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
                progress = progress,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recoverIfSnapshotCurrent(
        observed: DownloadEntity,
        recoveredStatus: DownloadStatus,
        errorMessage: String?,
        tempPath: String?,
        totalBytes: Long,
        downloadedBytes: Long,
        progress: Int
    ): DownloadTransitionResult {
        return transitionResult(
            id = observed.id,
            operation = "startup recovery",
            affectedRows = dao.recoverIfSnapshotCurrent(
                id = observed.id,
                observedStatus = observed.status,
                observedUpdatedAt = observed.updatedAt,
                recoveredStatus = recoveredStatus,
                errorMessage = errorMessage,
                tempPath = tempPath,
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
                progress = progress,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeFinalizedDownloads(): List<DownloadEntity> {
        val finalizedStatuses = listOf(
            DownloadStatus.COMPLETED,
            DownloadStatus.CANCELED,
            DownloadStatus.FAILED
        )
        val finalizedDownloads = getByStatuses(finalizedStatuses)
        finalizedDownloads.forEach { download ->
            if (download.status != DownloadStatus.COMPLETED) {
                deleteTempPath(download.tempPath)
            }
        }
        dao.deleteByStatuses(finalizedStatuses.map { it.name })
        return finalizedDownloads
    }

    private suspend fun transitionResult(
        id: Long,
        operation: String,
        affectedRows: Int
    ): DownloadTransitionResult {
        return when (affectedRows) {
            1 -> DownloadTransitionResult.Applied
            0 -> DownloadTransitionResult.Rejected(dao.getById(id)?.status)
            else -> error(
                "Invariante violada em $operation para download $id: " +
                    "$affectedRows linhas atualizadas"
            )
        }
    }

    private fun Boolean.toSqlFlag(): Int = if (this) 1 else 0

    private fun deleteTempPath(tempPath: String?) {
        val path = tempPath?.trim().orEmpty()
        if (path.isBlank()) return
        runCatching {
            java.io.File(path).deleteRecursively()
        }
    }
}
