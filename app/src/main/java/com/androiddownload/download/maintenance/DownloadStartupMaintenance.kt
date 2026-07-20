package com.androiddownload.download.maintenance

import android.content.Context
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.DownloadSourceClassifier
import com.androiddownload.core.utils.YtDlpDiagnostics
import com.androiddownload.download.data.DownloadRepository
import com.androiddownload.download.data.DownloadTransitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class DownloadStartupMaintenance(
    private val context: Context,
    private val repository: DownloadRepository
) {
    suspend fun recoverAndClean() = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        YtDlpDiagnostics.record(
            context = context,
            url = "app",
            option = "startup-cleanup",
            attempt = "manutencao",
            result = "inicio manutencao/limpeza",
            type = "manutencao"
        )
        YtDlpDiagnostics.pruneOldEvents(context)
        val downloads = repository.observeDownloads().first()
        val recoveryResolutions = downloads.map { recoverInterruptedDownload(it) }
        val recoveredDownloads = recoveryResolutions.mapNotNull { it.download }
        val cleanupBlockedIds = recoveryResolutions
            .filterNot { it.cleanupAllowed }
            .mapTo(mutableSetOf()) { it.observedId }
        val protectedDownloads = recoveredDownloads.filter { it.status in PROTECTED_STATUSES }
        val protectedIds = protectedDownloads.mapTo(mutableSetOf()) { it.id }
        val knownIds = recoveredDownloads.mapTo(mutableSetOf()) { it.id }
        val protectedTempPaths = protectedDownloads
            .mapNotNullTo(mutableSetOf()) { it.tempPath?.takeIf(String::isNotBlank)?.let(::File)?.canonicalPathSafe() }

        val httpResult = cleanupHttpTempFiles(
            knownIds,
            protectedIds,
            protectedTempPaths,
            cleanupBlockedIds
        )
        val ytdlpResult = cleanupYtDlpTempDirs(
            knownIds,
            protectedIds,
            protectedTempPaths,
            cleanupBlockedIds
        )
        recordCleanupDiagnostics(httpResult + ytdlpResult)
        YtDlpDiagnostics.record(
            context = context,
            url = "app",
            option = "startup-cleanup",
            attempt = "manutencao",
            result = "fim manutencao/limpeza",
            durationMs = System.currentTimeMillis() - startedAt,
            type = "manutencao"
        )
    }

    private suspend fun recoverInterruptedDownload(download: DownloadEntity): RecoveryResolution {
        val staleQueued = download.status == DownloadStatus.QUEUED &&
            System.currentTimeMillis() - download.updatedAt > STALE_QUEUED_AGE_MS
        if (download.status != DownloadStatus.RUNNING &&
            download.status != DownloadStatus.PREPARING &&
            !staleQueued
        ) {
            return RecoveryResolution(download.id, download, cleanupAllowed = true)
        }

        val canResumeHttp = DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl) &&
            !download.tempPath.isNullOrBlank() &&
            download.downloadedBytes > 0L
        val interruptedMessage = if (canResumeHttp) {
            context.getString(R.string.download_interrupted_resume)
        } else {
            context.getString(R.string.download_interrupted_retry)
        }

        val recoveredStatus = if (canResumeHttp) {
            DownloadStatus.PAUSED
        } else {
            DownloadStatus.FAILED
        }
        val recoveredTempPath = download.tempPath.takeIf { canResumeHttp }
        val recoveredTotalBytes = download.totalBytes
        val recoveredDownloadedBytes = download.downloadedBytes.takeIf { canResumeHttp } ?: 0L
        val recoveredProgress = download.progress.takeIf { canResumeHttp } ?: 0

        return when (
            repository.recoverIfSnapshotCurrent(
                observed = download,
                recoveredStatus = recoveredStatus,
                errorMessage = interruptedMessage,
                tempPath = recoveredTempPath,
                totalBytes = recoveredTotalBytes,
                downloadedBytes = recoveredDownloadedBytes,
                progress = recoveredProgress
            )
        ) {
            DownloadTransitionResult.Applied -> {
                RecoveryResolution(
                    observedId = download.id,
                    download = repository.getById(download.id),
                    cleanupAllowed = true
                )
            }
            is DownloadTransitionResult.Rejected -> {
                RecoveryResolution(
                    observedId = download.id,
                    download = repository.getById(download.id),
                    cleanupAllowed = false
                )
            }
        }
    }

    private fun cleanupHttpTempFiles(
        knownIds: Set<Long>,
        protectedIds: Set<Long>,
        protectedTempPaths: Set<String>,
        cleanupBlockedIds: Set<Long>
    ): CleanupResult {
        val result = CleanupResult()
        val tempDir = File(context.cacheDir, HTTP_TEMP_DIR_NAME)
        val files = tempDir.listFiles().orEmpty()
        files.forEach { file ->
            if (!file.isFile || !file.name.endsWith(HTTP_TEMP_EXTENSION, ignoreCase = true)) {
                return@forEach
            }
            val downloadId = file.nameWithoutExtension.toLongOrNull() ?: return@forEach
            if (downloadId in cleanupBlockedIds) {
                return@forEach
            }
            if (downloadId in protectedIds || file.canonicalPathSafe() in protectedTempPaths) {
                return@forEach
            }
            val orphan = downloadId !in knownIds
            if ((orphan || downloadId !in protectedIds) && file.isOlderThan(TEMP_MAX_AGE_MS)) {
                if (deleteSafely(file)) {
                    result.filesRemoved++
                } else {
                    result.errors++
                }
            }
        }
        return result
    }

    private fun cleanupYtDlpTempDirs(
        knownIds: Set<Long>,
        protectedIds: Set<Long>,
        protectedTempPaths: Set<String>,
        cleanupBlockedIds: Set<Long>
    ): CleanupResult {
        val result = CleanupResult()
        val root = File(context.cacheDir, YTDLP_TEMP_ROOT)
        val children = root.listFiles().orEmpty()
        children.forEach { child ->
            val downloadId = child.name.toLongOrNull()
            if (downloadId != null && downloadId in cleanupBlockedIds) {
                return@forEach
            }
            if ((downloadId != null && downloadId in protectedIds) ||
                child.canonicalPathSafe() in protectedTempPaths
            ) {
                return@forEach
            }
            val orphan = downloadId == null || downloadId !in knownIds
            if ((orphan || downloadId !in protectedIds) && child.isOlderThan(TEMP_MAX_AGE_MS)) {
                if (deleteSafely(child)) {
                    if (child.isDirectory || downloadId != null) {
                        result.dirsRemoved++
                    } else {
                        result.filesRemoved++
                    }
                } else {
                    result.errors++
                }
            }
        }
        return result
    }

    private fun deleteSafely(file: File): Boolean {
        return runCatching {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }.getOrDefault(false)
    }

    private fun File.isOlderThan(maxAgeMs: Long): Boolean {
        val modified = lastModified()
        if (modified <= 0L) return false
        return System.currentTimeMillis() - modified > maxAgeMs
    }

    private fun File.canonicalPathSafe(): String? {
        return runCatching { canonicalPath }.getOrNull()
    }

    private fun recordCleanupDiagnostics(result: CleanupResult) {
        if (result.filesRemoved == 0 && result.dirsRemoved == 0 && result.errors == 0) return
        YtDlpDiagnostics.record(
            context = context,
            url = "app",
            option = "startup-cleanup",
            attempt = "limpeza automatica",
            result = "temporarios removidos: arquivos=${result.filesRemoved}; pastas=${result.dirsRemoved}",
            error = result.errors.takeIf { it > 0 }?.let {
                String.format(Locale.US, "%d erro(s) ao limpar temporarios", it)
            },
            type = "manutencao"
        )
    }

    companion object {
        private const val HTTP_TEMP_DIR_NAME = "downloads"
        private const val HTTP_TEMP_EXTENSION = ".part"
        private const val YTDLP_TEMP_ROOT = "ytdlp"
        private const val TEMP_MAX_AGE_MS = 48L * 60L * 60L * 1000L
        private val PROTECTED_STATUSES = setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.PREPARING,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED
        )
        private const val STALE_QUEUED_AGE_MS = 2L * 60L * 1000L
    }

    private data class CleanupResult(
        var filesRemoved: Int = 0,
        var dirsRemoved: Int = 0,
        var errors: Int = 0
    ) {
        operator fun plus(other: CleanupResult): CleanupResult {
            return CleanupResult(
                filesRemoved = filesRemoved + other.filesRemoved,
                dirsRemoved = dirsRemoved + other.dirsRemoved,
                errors = errors + other.errors
            )
        }
    }

    private data class RecoveryResolution(
        val observedId: Long,
        val download: DownloadEntity?,
        val cleanupAllowed: Boolean
    )
}
