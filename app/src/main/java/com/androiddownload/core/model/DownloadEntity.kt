package com.androiddownload.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceUrl: String,
    val finalUrl: String? = null,
    val fileName: String,
    val mimeType: String? = null,
    val destinationUri: String? = null,
    val tempPath: String? = null,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val progress: Int = 0,
    val speed: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
