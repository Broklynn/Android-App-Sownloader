package com.androiddownload.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN (:statuses)")
    suspend fun getByStatuses(statuses: List<String>): List<DownloadEntity>

    @Insert
    suspend fun insert(download: DownloadEntity): Long

    @Query(
        """
        UPDATE downloads
        SET status = 'PREPARING',
            errorMessage = NULL,
            speed = 0,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status = 'QUEUED'
        """
    )
    suspend fun markPreparingIfQueued(
        id: Long,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE downloads
        SET status = 'PREPARING',
            errorMessage = NULL,
            speed = 0,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status = 'PAUSED'
        """
    )
    suspend fun markPreparingIfPaused(
        id: Long,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE downloads
        SET finalUrl = :finalUrl,
            fileName = :fileName,
            mimeType = :mimeType,
            tempPath = :tempPath,
            totalBytes = :totalBytes,
            downloadedBytes = :downloadedBytes,
            progress = :progress,
            speed = :speed,
            status = 'RUNNING',
            errorMessage = NULL,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status IN ('PREPARING', 'RUNNING')
        """
    )
    suspend fun markRunningIfPreparingOrRunning(
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
    ): Int

    @Query(
        """
        UPDATE downloads
        SET tempPath = :tempPath,
            totalBytes = :totalBytes,
            downloadedBytes = :downloadedBytes,
            progress = :progress,
            speed = :speed,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status = 'RUNNING'
        """
    )
    suspend fun updateProgressIfRunning(
        id: Long,
        tempPath: String?,
        totalBytes: Long,
        downloadedBytes: Long,
        progress: Int,
        speed: Long,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE downloads
        SET finalUrl = :finalUrl,
            fileName = :fileName,
            mimeType = :mimeType,
            destinationUri = :destinationUri,
            tempPath = NULL,
            totalBytes = :totalBytes,
            downloadedBytes = :downloadedBytes,
            progress = 100,
            speed = 0,
            status = 'COMPLETED',
            errorMessage = NULL,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status = 'RUNNING'
        """
    )
    suspend fun markCompletedIfRunning(
        id: Long,
        finalUrl: String?,
        fileName: String,
        mimeType: String?,
        destinationUri: String,
        totalBytes: Long,
        downloadedBytes: Long,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE downloads
        SET status = 'FAILED',
            errorMessage = :errorMessage,
            tempPath = CASE WHEN :clearTempPath = 1 THEN NULL ELSE tempPath END,
            totalBytes = CASE WHEN :resetProgress = 1 THEN -1 ELSE totalBytes END,
            downloadedBytes = CASE WHEN :resetProgress = 1 THEN 0 ELSE downloadedBytes END,
            progress = CASE WHEN :resetProgress = 1 THEN 0 ELSE progress END,
            speed = 0,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status IN ('QUEUED', 'PREPARING', 'RUNNING')
        """
    )
    suspend fun markFailedIfActive(
        id: Long,
        errorMessage: String?,
        clearTempPath: Int,
        resetProgress: Int,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE downloads
        SET status = 'CANCELED',
            errorMessage = NULL,
            tempPath = NULL,
            speed = 0,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status IN ('QUEUED', 'PREPARING', 'RUNNING', 'PAUSED')
        """
    )
    suspend fun markCanceledIfActive(
        id: Long,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE downloads
        SET status = 'PAUSED',
            errorMessage = NULL,
            speed = 0,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status = 'RUNNING'
        """
    )
    suspend fun markPausedIfRunning(
        id: Long,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE downloads
        SET destinationUri = NULL,
            tempPath = :tempPath,
            totalBytes = :totalBytes,
            downloadedBytes = :downloadedBytes,
            progress = :progress,
            speed = 0,
            status = 'QUEUED',
            errorMessage = NULL,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status = 'FAILED'
          AND updatedAt = :observedUpdatedAt
        """
    )
    suspend fun retryIfFailed(
        id: Long,
        observedUpdatedAt: Long,
        tempPath: String?,
        totalBytes: Long,
        downloadedBytes: Long,
        progress: Int,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE downloads
        SET status = :recoveredStatus,
            errorMessage = :errorMessage,
            tempPath = :tempPath,
            totalBytes = :totalBytes,
            downloadedBytes = :downloadedBytes,
            progress = :progress,
            speed = 0,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status = :observedStatus
          AND updatedAt = :observedUpdatedAt
          AND status IN ('QUEUED', 'PREPARING', 'RUNNING')
          AND :recoveredStatus IN ('PAUSED', 'FAILED')
        """
    )
    suspend fun recoverIfSnapshotCurrent(
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
    ): Int

    @Query("DELETE FROM downloads WHERE status IN (:statuses)")
    suspend fun deleteByStatuses(statuses: List<String>): Int
}
