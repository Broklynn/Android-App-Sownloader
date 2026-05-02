package com.androiddownload.download.maintenance

import android.content.Context
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.DownloadSourceClassifier
import com.androiddownload.download.data.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class DownloadStartupMaintenance(
    private val context: Context,
    private val repository: DownloadRepository
) {
    suspend fun recoverAndClean() = withContext(Dispatchers.IO) {
        val downloads = repository.observeDownloads().first()
        val recoveredDownloads = downloads.map { recoverInterruptedDownload(it) }
        val protectedIds = recoveredDownloads
            .filter { it.status in PROTECTED_STATUSES }
            .mapTo(mutableSetOf()) { it.id }

        cleanupHttpTempFiles(protectedIds)
        cleanupYtDlpTempDirs(protectedIds)
    }

    private suspend fun recoverInterruptedDownload(download: DownloadEntity): DownloadEntity {
        if (download.status != DownloadStatus.RUNNING &&
            download.status != DownloadStatus.PREPARING
        ) {
            return download
        }

        val canResumeHttp = DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl) &&
            !download.tempPath.isNullOrBlank() &&
            download.downloadedBytes > 0L
        val interruptedMessage = if (canResumeHttp) {
            context.getString(R.string.download_interrupted_resume)
        } else {
            context.getString(R.string.download_interrupted_retry)
        }

        val recovered = if (canResumeHttp) {
            download.copy(
                status = DownloadStatus.PAUSED,
                errorMessage = interruptedMessage,
                speed = 0,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            download.copy(
                status = DownloadStatus.FAILED,
                errorMessage = interruptedMessage,
                tempPath = null,
                downloadedBytes = 0,
                progress = 0,
                speed = 0,
                updatedAt = System.currentTimeMillis()
            )
        }

        repository.update(recovered)
        return recovered
    }

    private fun cleanupHttpTempFiles(protectedIds: Set<Long>) {
        val tempDir = File(context.cacheDir, HTTP_TEMP_DIR_NAME)
        val files = tempDir.listFiles().orEmpty()
        files.forEach { file ->
            if (!file.isFile || !file.name.endsWith(HTTP_TEMP_EXTENSION, ignoreCase = true)) {
                return@forEach
            }
            val downloadId = file.nameWithoutExtension.toLongOrNull() ?: return@forEach
            if (downloadId !in protectedIds) {
                file.delete()
            }
        }
    }

    private fun cleanupYtDlpTempDirs(protectedIds: Set<Long>) {
        val root = File(context.cacheDir, YTDLP_TEMP_ROOT)
        val children = root.listFiles().orEmpty()
        children.forEach { child ->
            val downloadId = child.name.toLongOrNull()
            if (downloadId == null || downloadId !in protectedIds) {
                child.deleteRecursively()
            }
        }
    }

    companion object {
        private const val HTTP_TEMP_DIR_NAME = "downloads"
        private const val HTTP_TEMP_EXTENSION = ".part"
        private const val YTDLP_TEMP_ROOT = "ytdlp"
        private val PROTECTED_STATUSES = setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.PREPARING,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED
        )
    }
}
