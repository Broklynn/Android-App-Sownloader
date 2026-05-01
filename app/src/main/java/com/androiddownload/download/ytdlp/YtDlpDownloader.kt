package com.androiddownload.download.ytdlp

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.FileNameUtils
import com.androiddownload.download.data.DownloadRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class YtDlpDownloader(
    private val context: Context,
    private val repository: DownloadRepository
) {
    private val activeProcessIds = ConcurrentHashMap<Long, String>()

    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                YoutubeDL.getInstance().init(context.applicationContext)
                initialized = true
            } catch (_: YoutubeDLException) {
                initialized = false
            }
        }
    }

    suspend fun download(
        downloadId: Long,
        onProgress: suspend () -> Unit = {}
    ) {
        withContext(Dispatchers.IO) {
            initialize()
            if (!initialized) {
                repository.updateStatus(
                    downloadId,
                    DownloadStatus.FAILED,
                    context.getString(R.string.download_ytdlp_error)
                )
                return@withContext
            }

            val current = repository.getById(downloadId) ?: return@withContext
            if (current.status == DownloadStatus.CANCELED ||
                current.status == DownloadStatus.COMPLETED
            ) {
                return@withContext
            }

            repository.updateStatus(downloadId, DownloadStatus.PREPARING)

            val tempDir = createTempDir(downloadId)
            val processId = downloadId.toString()
            activeProcessIds[downloadId] = processId
            val metadata = runCatching {
                YoutubeDL.getInstance().getInfo(current.sourceUrl)
            }.getOrNull()
            val metadataTitle = FileNameUtils.sanitizeBaseName(
                metadata?.title?.takeIf { it.isNotBlank() } ?: current.fileName
            )
            val metadataExt = normalizeExtension(metadata?.ext)
            val expectedFileName = if (metadataExt.isNullOrBlank()) {
                "$metadataTitle.%(ext)s"
            } else {
                "$metadataTitle.$metadataExt"
            }
            val outputTemplate = File(tempDir, expectedFileName).absolutePath
            val expectedMimeType = metadataExt?.let { mimeTypeFromExtension(it) }

            val request = YoutubeDLRequest(current.sourceUrl).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("-f", "best")
                addOption("-o", outputTemplate)
            }

            repository.update(
                current.copy(
                    fileName = if (metadataExt.isNullOrBlank()) current.fileName.ifBlank { metadataTitle } else "$metadataTitle.$metadataExt",
                    mimeType = expectedMimeType ?: current.mimeType,
                    tempPath = tempDir.absolutePath,
                    totalBytes = -1,
                    downloadedBytes = 0,
                    progress = 0,
                    speed = 0,
                    status = DownloadStatus.RUNNING,
                    errorMessage = null
                )
            )
            onProgress()

            try {
                YoutubeDL.getInstance().execute(
                    request = request,
                    processId = processId
                ) { progress: Float, _, _ ->
                    runBlocking {
                        val latest = repository.getById(downloadId) ?: return@runBlocking
                        repository.update(
                            latest.copy(
                                tempPath = tempDir.absolutePath,
                                progress = progress.toInt().coerceIn(0, 100),
                                status = DownloadStatus.RUNNING,
                                errorMessage = null
                            )
                        )
                        onProgress()
                    }
                }

                currentCoroutineContext().ensureActive()
                finalizeDownload(downloadId, current, tempDir)
            } catch (exception: Exception) {
                val latest = repository.getById(downloadId)
                if (latest?.status == DownloadStatus.CANCELED || !activeProcessIds.containsKey(downloadId)) {
                    tempDir.deleteRecursively()
                    return@withContext
                }
                handleFailure(downloadId, current, tempDir, exception)
            } finally {
                activeProcessIds.remove(downloadId)
            }
        }
    }

    fun cancel(downloadId: Long) {
        activeProcessIds.remove(downloadId)?.let { processId ->
            runCatching {
                YoutubeDL.getInstance().destroyProcessById(processId)
            }
        }
        createTempDir(downloadId).deleteRecursively()
    }

    private suspend fun finalizeDownload(
        downloadId: Long,
        current: DownloadEntity,
        tempDir: File
    ) {
        val outputFile = findFinalOutputFile(tempDir)
            ?: throw IOException(context.getString(R.string.download_ytdlp_error))

        val latest = repository.getById(downloadId) ?: current
        val finalFile = resolveFinalFile(outputFile.name)
        if (!outputFile.renameTo(finalFile)) {
            outputFile.copyTo(finalFile, overwrite = true)
            outputFile.delete()
        }
        tempDir.deleteRecursively()
        val fileBytes = finalFile.length().takeIf { it > 0 } ?: current.downloadedBytes

        repository.update(
            latest.copy(
                finalUrl = latest.sourceUrl,
                fileName = finalFile.name,
                mimeType = latest.mimeType ?: inferMimeType(finalFile.name),
                destinationUri = Uri.fromFile(finalFile).toString(),
                tempPath = null,
                totalBytes = fileBytes,
                downloadedBytes = fileBytes,
                progress = 100,
                speed = 0,
                status = DownloadStatus.COMPLETED,
                errorMessage = null
            )
        )
    }

    private suspend fun handleFailure(
        downloadId: Long,
        current: DownloadEntity,
        tempDir: File,
        exception: Exception
    ) {
        val message = buildErrorMessage(exception)
        tempDir.deleteRecursively()
        repository.update(
            current.copy(
                tempPath = null,
                status = DownloadStatus.FAILED,
                errorMessage = message
            )
        )
        repository.updateStatus(downloadId, DownloadStatus.FAILED, message)
    }

    private fun buildErrorMessage(exception: Exception): String {
        val detail = exception.message?.trim().orEmpty()
        return if (detail.isBlank()) {
            context.getString(R.string.download_ytdlp_error)
        } else {
            "${context.getString(R.string.download_ytdlp_error)}: $detail"
        }
    }

    private fun createTempDir(downloadId: Long): File {
        val directory = File(context.cacheDir, "ytdlp/$downloadId")
        directory.mkdirs()
        return directory
    }

    private fun findFinalOutputFile(tempDir: File): File? {
        return tempDir.listFiles()
            ?.filter { it.isFile }
            ?.filterNot { it.name.endsWith(".part", ignoreCase = true) }
            ?.filterNot { it.name.endsWith(".ytdl", ignoreCase = true) }
            ?.filterNot { it.name.endsWith(".temp", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun resolveFinalFile(preferredName: String): File {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        directory.mkdirs()

        val candidateName = FileNameUtils.ensureExtension(
            FileNameUtils.sanitize(preferredName)
        )
        var candidate = File(directory, candidateName)
        var index = 1
        val name = candidateName.substringBeforeLast('.', candidateName)
        val extension = candidateName.substringAfterLast('.', missingDelimiterValue = "")
        while (candidate.exists()) {
            val nextName = if (extension.isBlank()) {
                "$name ($index)"
            } else {
                "$name ($index).$extension"
            }
            candidate = File(directory, nextName)
            index++
        }
        return candidate
    }

    private fun normalizeExtension(extension: String?): String? {
        val clean = extension
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (clean == "bin") return null
        return clean
    }

    private fun mimeTypeFromExtension(extension: String): String? {
        return when (extension.lowercase(Locale.US)) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
    }

    private fun inferMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?: return null
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}
