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

    suspend fun enqueue(sourceUrl: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            DownloadEntity(
                sourceUrl = sourceUrl,
                fileName = FileNameUtils.guessFileName(sourceUrl),
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
}
