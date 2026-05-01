package com.androiddownload.download.ytdlp

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.FileNameUtils
import com.androiddownload.core.utils.YtDlpQualityOptions
import com.androiddownload.download.data.DownloadRepository
import com.yausername.ffmpeg.FFmpeg
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

    @Volatile
    private var updateAttempted = false

    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                FFmpeg.getInstance().init(context.applicationContext)
                YoutubeDL.getInstance().init(context.applicationContext)
                if (!updateAttempted) {
                    updateAttempted = true
                    runCatching {
                        YoutubeDL.getInstance().updateYoutubeDL(
                            context.applicationContext,
                            YoutubeDL.UpdateChannel.NIGHTLY
                        )
                    }
                }
                initialized = true
            } catch (_: YoutubeDLException) {
                initialized = false
            }
        }
    }

    suspend fun download(
        downloadId: Long,
        formatSelector: String = "best",
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

            val processId = downloadId.toString()
            activeProcessIds[downloadId] = processId
            val metadata = runCatching {
                YoutubeDL.getInstance().getInfo(current.sourceUrl)
            }.getOrNull()
            val metadataTitle = FileNameUtils.sanitizeBaseName(
                metadata?.title?.takeIf { it.isNotBlank() } ?: current.fileName
            )
            val selector = current.qualitySelector.orEmpty()
            val metadataExt = normalizeExtension(metadata?.ext)
            val attempts = buildAttempts(current.sourceUrl, selector)
            var lastException: Exception? = null

            try {
                for ((index, attempt) in attempts.withIndex()) {
                    val tempDir = createTempDir(downloadId)
                    try {
                        executeAttempt(
                            downloadId = downloadId,
                            current = current,
                            tempDir = tempDir,
                            processId = processId,
                            metadataTitle = metadataTitle,
                            metadataExt = metadataExt,
                            attempt = attempt,
                            onProgress = onProgress
                        )
                        return@withContext
                    } catch (exception: Exception) {
                        lastException = exception
                        if (isCanceled(downloadId)) {
                            tempDir.deleteRecursively()
                            return@withContext
                        }
                        tempDir.deleteRecursively()
                        if (!shouldRetryAudioAttempt(selector, exception, index, attempts.lastIndex)) {
                            break
                        }
                    }
                }

                val latest = repository.getById(downloadId) ?: current
                if (latest.status != DownloadStatus.CANCELED) {
                    handleFailure(downloadId, current, lastException ?: IOException(context.getString(R.string.download_audio_error)))
                }
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

    private suspend fun executeAttempt(
        downloadId: Long,
        current: DownloadEntity,
        tempDir: File,
        processId: String,
        metadataTitle: String,
        metadataExt: String?,
        attempt: YtDlpAttempt,
        onProgress: suspend () -> Unit
    ) {
        val outputTemplate = File(tempDir, "$metadataTitle.%(ext)s").absolutePath
        val expectedMimeType = when {
            attempt.convertToMp3 -> "audio/mpeg"
            attempt.mergeOutputFormat == "mp4" -> "video/mp4"
            metadataExt != null -> mimeTypeFromExtension(metadataExt)
            else -> null
        }
        val displayFileName = when {
            attempt.convertToMp3 -> "$metadataTitle.mp3"
            attempt.mergeOutputFormat == "mp4" -> "$metadataTitle.mp4"
            metadataExt.isNullOrBlank() -> FileNameUtils.ensureExtension(metadataTitle, expectedMimeType)
            else -> "$metadataTitle.$metadataExt"
        }

        val request = YoutubeDLRequest(current.sourceUrl).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("-f", attempt.formatSelector)
            attempt.extractorArgs?.let {
                addOption("--extractor-args", it)
            }
            attempt.mergeOutputFormat?.let {
                addOption("--merge-output-format", it)
            }
            if (attempt.convertToMp3) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", attempt.audioQuality ?: "0")
            }
            addOption("-o", outputTemplate)
        }

        repository.update(
            current.copy(
                fileName = displayFileName,
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
        exception: Exception
    ) {
        val message = buildErrorMessage(current, exception)
        repository.update(
            current.copy(
                tempPath = null,
                status = DownloadStatus.FAILED,
                errorMessage = message
            )
        )
        repository.updateStatus(downloadId, DownloadStatus.FAILED, message)
    }

    private fun buildErrorMessage(current: DownloadEntity, exception: Exception): String {
        val detail = exception.message?.trim().orEmpty()
        if (isMp3Request(current.qualitySelector.orEmpty()) && detail.contains("ffmpeg", ignoreCase = true)) {
            return context.getString(R.string.download_mp3_error)
        }
        if (isAudioRequest(current.qualitySelector.orEmpty())) {
            return context.getString(R.string.download_audio_error)
        }
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

    private fun buildAttempts(sourceUrl: String, selector: String): List<YtDlpAttempt> {
        val normalized = selector.ifBlank { "best" }
        val mp3AudioQuality = mp3AudioQualityForSelector(normalized)
        return when {
            mp3AudioQuality != null -> listOf(
                YtDlpAttempt(
                    formatSelector = "ba[ext=m4a]/ba[ext=webm]/bestaudio/best",
                    extractorArgs = youtubeExtractorArgs(sourceUrl, fallback = false),
                    convertToMp3 = true,
                    audioQuality = mp3AudioQuality
                ),
                YtDlpAttempt(
                    formatSelector = "bestaudio/best",
                    extractorArgs = youtubeExtractorArgs(sourceUrl, fallback = true),
                    convertToMp3 = true,
                    audioQuality = mp3AudioQuality
                )
            )
            normalized == "bestaudio" -> listOf(
                YtDlpAttempt(
                    formatSelector = "ba[ext=m4a]/ba[ext=webm]/bestaudio/best",
                    extractorArgs = youtubeExtractorArgs(sourceUrl, fallback = false)
                ),
                YtDlpAttempt(
                    formatSelector = "bestaudio/best",
                    extractorArgs = youtubeExtractorArgs(sourceUrl, fallback = true)
                )
            )
            else -> listOf(
                YtDlpAttempt(
                    formatSelector = normalized,
                    mergeOutputFormat = if (shouldMergeToMp4(normalized)) "mp4" else null
                )
            )
        }
    }

    private fun youtubeExtractorArgs(sourceUrl: String, fallback: Boolean): String? {
        if (!isYoutubeUrl(sourceUrl)) return null
        return if (fallback) {
            "youtube:player-client=android_vr"
        } else {
            "youtube:player-client=android_vr,android"
        }
    }

    private fun shouldRetryAudioAttempt(
        selector: String,
        exception: Exception,
        attemptIndex: Int,
        lastIndex: Int
    ): Boolean {
        if (!isAudioRequest(selector)) return false
        if (attemptIndex >= lastIndex) return false
        val message = exception.message.orEmpty()
        return message.contains("403", ignoreCase = true) ||
            message.contains("unable to download video data", ignoreCase = true) ||
            message.contains("Forbidden", ignoreCase = true)
    }

    private fun isAudioRequest(selector: String): Boolean {
        return selector == "bestaudio" || isMp3Request(selector)
    }

    private fun isMp3Request(selector: String): Boolean {
        return mp3AudioQualityForSelector(selector) != null
    }

    private fun mp3AudioQualityForSelector(selector: String): String? {
        return when (selector) {
            "mp3" -> "0"
            YtDlpQualityOptions.SELECTOR_MP3_320K -> "320K"
            YtDlpQualityOptions.SELECTOR_MP3_256K -> "256K"
            YtDlpQualityOptions.SELECTOR_MP3_192K -> "192K"
            YtDlpQualityOptions.SELECTOR_MP3_128K -> "128K"
            else -> null
        }
    }

    private fun shouldMergeToMp4(selector: String): Boolean {
        return selector == YtDlpQualityOptions.SELECTOR_MP4_1440P ||
            selector == YtDlpQualityOptions.SELECTOR_MP4_1080P ||
            selector == YtDlpQualityOptions.SELECTOR_MP4_720P ||
            selector == YtDlpQualityOptions.SELECTOR_MP4_480P
    }

    private fun isYoutubeUrl(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase(Locale.US).orEmpty() }.getOrDefault("")
        return host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtu.be" ||
            host.endsWith(".youtu.be")
    }

    private suspend fun isCanceled(downloadId: Long): Boolean {
        val current = repository.getById(downloadId) ?: return true
        return current.status == DownloadStatus.CANCELED || !activeProcessIds.containsKey(downloadId)
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

        val candidateName = FileNameUtils.ensureExtension(FileNameUtils.sanitize(preferredName))
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

    private data class YtDlpAttempt(
        val formatSelector: String,
        val extractorArgs: String? = null,
        val convertToMp3: Boolean = false,
        val audioQuality: String? = null,
        val mergeOutputFormat: String? = null
    )
}
