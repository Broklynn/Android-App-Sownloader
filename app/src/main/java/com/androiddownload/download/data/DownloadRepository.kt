package com.androiddownload.download.data

import com.androiddownload.core.database.DownloadDao
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.FileNameUtils
import kotlinx.coroutines.flow.Flow

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

    suspend fun update(download: DownloadEntity) = dao.update(
        download.copy(updatedAt = System.currentTimeMillis())
    )

    suspend fun updateStatus(
        id: Long,
        status: DownloadStatus,
        errorMessage: String? = null
    ) {
        dao.updateStatus(
            id = id,
            status = status,
            errorMessage = errorMessage,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun markCanceled(id: Long) {
        val current = dao.getById(id) ?: return
        if (current.status == DownloadStatus.COMPLETED ||
            current.status == DownloadStatus.FAILED ||
            current.status == DownloadStatus.CANCELED
        ) {
            return
        }

        deleteTempPath(current.tempPath)
        dao.update(
            current.copy(
                status = DownloadStatus.CANCELED,
                errorMessage = null,
                tempPath = null,
                speed = 0,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markPaused(id: Long) {
        val current = dao.getById(id) ?: return
        if (current.status == DownloadStatus.COMPLETED ||
            current.status == DownloadStatus.FAILED ||
            current.status == DownloadStatus.CANCELED ||
            current.status == DownloadStatus.PAUSED
        ) {
            return
        }

        dao.update(
            current.copy(
                status = DownloadStatus.PAUSED,
                errorMessage = null,
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

    private fun deleteTempPath(tempPath: String?) {
        val path = tempPath?.trim().orEmpty()
        if (path.isBlank()) return
        runCatching {
            java.io.File(path).deleteRecursively()
        }
    }
}
