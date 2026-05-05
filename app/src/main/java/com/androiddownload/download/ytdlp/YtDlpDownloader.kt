package com.androiddownload.download.ytdlp

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.DownloadErrorFormatter
import com.androiddownload.core.utils.FileNameUtils
import com.androiddownload.core.utils.YtDlpQualityOptions
import com.androiddownload.core.utils.YtDlpDiagnostics
import com.androiddownload.download.data.DownloadRepository
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class YtDlpDownloader(
    private val context: Context,
    private val repository: DownloadRepository
) {
    private val activeProcessIds = ConcurrentHashMap<Long, String>()
    private val progressStates = ConcurrentHashMap<Long, ProgressSnapshot>()
    private val downloadTotalRegex = Regex("""of\s+~?([\d.]+)\s*([KMGTPE]?i?B)""", RegexOption.IGNORE_CASE)
    private val downloadSpeedRegex = Regex("""at\s+([\d.]+)\s*([KMGTPE]?i?B/s)""", RegexOption.IGNORE_CASE)
    private val progressUpdateIntervalMs = 1000L

    @Volatile
    private var initialized = false

    @Volatile
    private var autoUpdateInProgress = false

    @Volatile
    private var lastAutoUpdateAttemptAt = 0L

    data class ProgressSnapshot(
        val percent: Int = 0,
        val downloadedBytes: Long = -1,
        val totalBytes: Long = -1,
        val speedBytesPerSecond: Long = -1,
        val etaSeconds: Long = -1,
        val lastUpdatedAt: Long = 0L
    )

    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val startedAt = System.currentTimeMillis()
            try {
                FFmpeg.getInstance().init(context.applicationContext)
                YoutubeDL.getInstance().init(context.applicationContext)
                initialized = true
                YtDlpDiagnostics.record(
                    context = context,
                    url = "app",
                    option = "init",
                    attempt = "inicializacao",
                    result = "yt-dlp/ffmpeg prontos",
                    durationMs = elapsedMs(startedAt)
                )
            } catch (_: YoutubeDLException) {
                initialized = false
                YtDlpDiagnostics.record(
                    context = context,
                    url = "app",
                    option = "init",
                    attempt = "inicializacao",
                    result = "falha init",
                    durationMs = elapsedMs(startedAt)
                )
            }
        }
    }

    suspend fun updateManually(): Boolean {
        return withContext(Dispatchers.IO) {
            initialize()
            if (!initialized) return@withContext false
            runYtDlpUpdate()
        }
    }

    suspend fun download(
        downloadId: Long,
        formatSelector: String = "best",
        onProgress: suspend () -> Unit = {}
    ) {
        download(
            downloadId = downloadId,
            formatSelector = formatSelector,
            onProgress = onProgress,
            allowAutoUpdateRetry = true,
            autoUpdateApplied = false
        )
    }

    private suspend fun download(
        downloadId: Long,
        formatSelector: String = "best",
        onProgress: suspend () -> Unit = {},
        allowAutoUpdateRetry: Boolean,
        autoUpdateApplied: Boolean
    ) {
        withContext(Dispatchers.IO) {
            val flowStartedAt = System.currentTimeMillis()
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
            YtDlpDiagnostics.record(
                context = context,
                url = current.sourceUrl,
                option = formatSelector,
                attempt = "download recebido",
                result = "inicio",
                durationMs = elapsedMs(flowStartedAt)
            )
            if (current.status == DownloadStatus.CANCELED ||
                current.status == DownloadStatus.COMPLETED
            ) {
                return@withContext
            }

            repository.updateStatus(downloadId, DownloadStatus.PREPARING)
            val preparingStartedAt = System.currentTimeMillis()
            YtDlpDiagnostics.record(
                context = context,
                url = current.sourceUrl,
                option = formatSelector,
                attempt = "preparing",
                result = "status PREPARING",
                durationMs = elapsedMs(flowStartedAt)
            )
            progressStates[downloadId] = ProgressSnapshot(lastUpdatedAt = System.currentTimeMillis())

            val processId = downloadId.toString()
            activeProcessIds[downloadId] = processId
            val metadata = loadMetadataWithTimeout(downloadId, current, formatSelector)
            val metadataTitle = FileNameUtils.sanitizeBaseName(
                metadata?.title?.takeIf { it.isNotBlank() } ?: current.fileName
            )
            if (!metadata?.title.isNullOrBlank()) {
                YtDlpDiagnostics.record(
                    context = context,
                    url = current.sourceUrl,
                    option = formatSelector,
                    attempt = "getInfo",
                    result = "titulo getInfo",
                    error = metadata?.title
                )
            }
            val selector = current.qualitySelector.orEmpty()
            val metadataExt = normalizeExtension(metadata?.ext)
            val attempts = buildAttempts(current.sourceUrl, selector)
            YtDlpDiagnostics.record(
                context = context,
                url = current.sourceUrl,
                option = selector.ifBlank { formatSelector },
                attempt = "selector",
                result = "selector montado (${attempts.size} tentativa(s))",
                durationMs = elapsedMs(preparingStartedAt)
            )
            var lastException: Exception? = null
            var lastAttemptName = attempts.firstOrNull()?.name.orEmpty()
            var failedAttemptCount = 0

            try {
                for ((index, attempt) in attempts.withIndex()) {
                    if (System.currentTimeMillis() - preparingStartedAt > PREPARE_BEFORE_EXECUTE_TIMEOUT_MS) {
                        val timeout = IOException(context.getString(R.string.download_prepare_timeout))
                        YtDlpDiagnostics.record(
                            context = context,
                            url = current.sourceUrl,
                            option = selector.ifBlank { formatSelector },
                            attempt = "preparing",
                            result = "timeout preparing",
                            error = "PREPARING excedeu ${PREPARE_BEFORE_EXECUTE_TIMEOUT_MS / 1000}s antes do execute",
                            durationMs = elapsedMs(preparingStartedAt)
                        )
                        handleFailure(
                            downloadId = downloadId,
                            current = current,
                            exception = timeout,
                            attemptsApplied = failedAttemptCount > 0 || autoUpdateApplied,
                            lastAttemptName = "preparing",
                            autoUpdateApplied = autoUpdateApplied,
                            flowStartedAt = flowStartedAt
                        )
                        return@withContext
                    }
                    lastAttemptName = attempt.name
                    YtDlpDiagnostics.record(
                        context = context,
                        url = current.sourceUrl,
                        option = selector.ifBlank { formatSelector },
                        attempt = attempt.name,
                        result = "iniciando tentativa",
                        durationMs = elapsedMs(preparingStartedAt)
                    )
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
                        YtDlpDiagnostics.record(
                            context = context,
                            url = current.sourceUrl,
                            option = selector.ifBlank { formatSelector },
                            attempt = attempt.name,
                            result = "sucesso",
                            autoUpdate = autoUpdateApplied,
                            durationMs = elapsedMs(flowStartedAt)
                        )
                        return@withContext
                    } catch (exception: Exception) {
                        lastException = exception
                        failedAttemptCount++
                        if (isCanceled(downloadId)) {
                            tempDir.deleteRecursively()
                            YtDlpDiagnostics.record(
                                context = context,
                                url = current.sourceUrl,
                                option = selector.ifBlank { formatSelector },
                                attempt = attempt.name,
                                result = "cancelado",
                                error = exception.message,
                                autoUpdate = autoUpdateApplied,
                                durationMs = elapsedMs(flowStartedAt)
                            )
                            return@withContext
                        }
                        tempDir.deleteRecursively()
                        YtDlpDiagnostics.record(
                            context = context,
                            url = current.sourceUrl,
                            option = selector.ifBlank { formatSelector },
                            attempt = attempt.name,
                            result = "falha tentativa",
                            error = exception.message,
                            autoUpdate = autoUpdateApplied,
                            durationMs = elapsedMs(flowStartedAt)
                        )
                        if (!shouldRetryYtDlpAttempt(exception, index, attempts.lastIndex)) {
                            break
                        }
                    }
                }

                val latest = repository.getById(downloadId) ?: current
                if (latest.status != DownloadStatus.CANCELED) {
                    val failure = lastException ?: IOException(context.getString(R.string.download_audio_error))
                    if (allowAutoUpdateRetry && shouldAutoUpdateAndRetry(current, failure)) {
                        val updated = runAutoUpdateIfAllowed()
                        YtDlpDiagnostics.record(
                            context = context,
                            url = current.sourceUrl,
                            option = selector.ifBlank { formatSelector },
                            attempt = lastAttemptName.ifBlank { "auto-update" },
                            result = if (updated) "auto-update sucesso" else "auto-update ignorado/falhou",
                            error = failure.message,
                            autoUpdate = true,
                            durationMs = elapsedMs(flowStartedAt)
                        )
                        if (updated && !isCanceled(downloadId)) {
                            download(
                                downloadId = downloadId,
                                formatSelector = formatSelector,
                                onProgress = onProgress,
                                allowAutoUpdateRetry = false,
                                autoUpdateApplied = true
                            )
                            return@withContext
                        }
                    }
                    handleFailure(
                        downloadId = downloadId,
                        current = current,
                        exception = failure,
                        attemptsApplied = failedAttemptCount > 1 || autoUpdateApplied,
                        lastAttemptName = lastAttemptName,
                        autoUpdateApplied = autoUpdateApplied,
                        flowStartedAt = flowStartedAt
                    )
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
        progressStates.remove(downloadId)
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
        val attemptStartedAt = System.currentTimeMillis()
        val outputTemplate = File(tempDir, "%(title).200B.%(ext)s").absolutePath
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
        YtDlpDiagnostics.record(
            context = context,
            url = current.sourceUrl,
            option = current.qualitySelector.orEmpty(),
            attempt = attempt.name,
            result = "chamando execute",
            durationMs = elapsedMs(attemptStartedAt)
        )

        executeWithWatchdog(
            downloadId = downloadId,
            request = request,
            processId = processId,
            current = current,
            tempDir = tempDir,
            attempt = attempt,
            attemptStartedAt = attemptStartedAt,
            onProgress = onProgress
        )

        currentCoroutineContext().ensureActive()
        val finalizeStartedAt = System.currentTimeMillis()
        finalizeDownload(
            downloadId = downloadId,
            current = current,
            tempDir = tempDir,
            expectedMimeType = expectedMimeType,
            metadataTitle = metadataTitle,
            finalizeStartedAt = finalizeStartedAt
        )
    }

    private suspend fun executeWithWatchdog(
        downloadId: Long,
        request: YoutubeDLRequest,
        processId: String,
        current: DownloadEntity,
        tempDir: File,
        attempt: YtDlpAttempt,
        attemptStartedAt: Long,
        onProgress: suspend () -> Unit
    ) {
        val lastCallbackAt = AtomicLong(System.currentTimeMillis())
        val callbackSeen = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit(Callable {
            YoutubeDL.getInstance().execute(
                request = request,
                processId = processId
            ) { progress: Float, etaSeconds: Long, line: String ->
                lastCallbackAt.set(System.currentTimeMillis())
                if (callbackSeen.compareAndSet(false, true)) {
                    YtDlpDiagnostics.record(
                        context = context,
                        url = current.sourceUrl,
                        option = current.qualitySelector.orEmpty(),
                        attempt = attempt.name,
                        result = "primeiro callback",
                        durationMs = elapsedMs(attemptStartedAt)
                    )
                }
            runBlocking {
                val latest = repository.getById(downloadId) ?: return@runBlocking
                val previousState = progressStates[downloadId]
                val snapshot = parseProgressSnapshot(progress, etaSeconds, line, previousState)
                val now = System.currentTimeMillis()
                val updatedSnapshot = snapshot.copy(lastUpdatedAt = now)

                if (!shouldPersistProgress(previousState, updatedSnapshot)) {
                    if (shouldNotifyProgress(previousState, updatedSnapshot, now)) {
                        onProgress()
                    }
                    return@runBlocking
                }

                progressStates[downloadId] = updatedSnapshot

                val updatedDownload = latest.copy(
                    tempPath = tempDir.absolutePath,
                    progress = updatedSnapshot.percent.coerceIn(0, 100),
                    downloadedBytes = if (updatedSnapshot.downloadedBytes >= 0) {
                        updatedSnapshot.downloadedBytes
                    } else {
                        latest.downloadedBytes
                    },
                    totalBytes = if (updatedSnapshot.totalBytes >= 0) {
                        updatedSnapshot.totalBytes
                    } else {
                        latest.totalBytes
                    },
                    speed = if (updatedSnapshot.speedBytesPerSecond >= 0) {
                        updatedSnapshot.speedBytesPerSecond
                    } else {
                        latest.speed
                    },
                    status = DownloadStatus.RUNNING,
                    errorMessage = null
                )
                repository.update(updatedDownload)
                if (shouldNotifyProgress(previousState, updatedSnapshot, now)) {
                    onProgress()
                }
            }
            }
        })
        try {
            while (true) {
                try {
                    future.get(EXECUTE_WATCHDOG_POLL_MS, TimeUnit.MILLISECONDS)
                    if (callbackSeen.get()) {
                        YtDlpDiagnostics.record(
                            context = context,
                            url = current.sourceUrl,
                            option = current.qualitySelector.orEmpty(),
                            attempt = attempt.name,
                            result = "execute concluiu",
                            durationMs = elapsedMs(attemptStartedAt)
                        )
                    }
                    return
                } catch (_: TimeoutException) {
                    currentCoroutineContext().ensureActive()
                    if (isCanceled(downloadId)) {
                        future.cancel(true)
                        throw IOException(context.getString(R.string.status_canceled))
                    }
                    val silenceMs = System.currentTimeMillis() - lastCallbackAt.get()
                    if (silenceMs >= EXECUTE_NO_CALLBACK_TIMEOUT_MS) {
                        YoutubeDL.getInstance().destroyProcessById(processId)
                        future.cancel(true)
                        YtDlpDiagnostics.record(
                            context = context,
                            url = current.sourceUrl,
                            option = current.qualitySelector.orEmpty(),
                            attempt = attempt.name,
                            result = "timeout execute",
                            error = "Sem callback/progresso por ${silenceMs / 1000}s",
                            durationMs = elapsedMs(attemptStartedAt)
                        )
                        throw IOException(context.getString(R.string.download_prepare_timeout))
                    }
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    fun getProgressSnapshot(downloadId: Long): ProgressSnapshot? {
        return progressStates[downloadId]
    }

    private fun shouldPersistProgress(
        previous: ProgressSnapshot?,
        current: ProgressSnapshot
    ): Boolean {
        if (previous == null) return true
        if (current.percent != previous.percent) return true
        if (current.totalBytes != previous.totalBytes) return true
        return current.lastUpdatedAt - previous.lastUpdatedAt >= progressUpdateIntervalMs
    }

    private fun shouldNotifyProgress(
        previous: ProgressSnapshot?,
        current: ProgressSnapshot,
        now: Long
    ): Boolean {
        if (previous == null) return true
        if (current.percent != previous.percent) return true
        if (current.totalBytes != previous.totalBytes) return true
        if (current.etaSeconds != previous.etaSeconds) return true
        return now - previous.lastUpdatedAt >= progressUpdateIntervalMs
    }

    private fun parseProgressSnapshot(
        progress: Float,
        etaSeconds: Long,
        line: String,
        previous: ProgressSnapshot?
    ): ProgressSnapshot {
        val percent = if (progress >= 0f) {
            progress.roundToInt().coerceIn(0, 100)
        } else {
            previous?.percent ?: 0
        }
        val totalBytes = parseTotalBytes(line) ?: previous?.totalBytes ?: -1
        val speedBytesPerSecond = parseSpeedBytes(line) ?: previous?.speedBytesPerSecond ?: -1
        val downloadedBytes = when {
            totalBytes > 0 -> ((totalBytes * percent) / 100.0).toLong().coerceAtLeast(0)
            previous != null -> previous.downloadedBytes
            else -> -1
        }

        return ProgressSnapshot(
            percent = percent,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speedBytesPerSecond = speedBytesPerSecond,
            etaSeconds = etaSeconds
        )
    }

    private fun parseTotalBytes(line: String): Long? {
        val match = downloadTotalRegex.find(line) ?: return null
        return parseByteValue(match.groupValues[1], match.groupValues[2])
    }

    private fun parseSpeedBytes(line: String): Long? {
        val match = downloadSpeedRegex.find(line) ?: return null
        return parseByteValue(match.groupValues[1], match.groupValues[2].removeSuffix("/s"))
    }

    private fun parseByteValue(valueText: String, unitText: String): Long? {
        val value = valueText.toDoubleOrNull() ?: return null
        val unit = unitText.trim().uppercase(Locale.US)
        val multiplier = when (unit) {
            "B" -> 1L
            "KB", "KIB" -> 1024L
            "MB", "MIB" -> 1024L * 1024L
            "GB", "GIB" -> 1024L * 1024L * 1024L
            "TB", "TIB" -> 1024L * 1024L * 1024L * 1024L
            else -> return null
        }
        return (value * multiplier).toLong()
    }

    private suspend fun finalizeDownload(
        downloadId: Long,
        current: DownloadEntity,
        tempDir: File,
        expectedMimeType: String?,
        metadataTitle: String,
        finalizeStartedAt: Long
    ) {
        val outputFile = findFinalOutputFile(tempDir)
            ?: throw IOException(context.getString(R.string.download_ytdlp_error))

        val latest = repository.getById(downloadId) ?: current
        val finalName = resolveFinalName(
            actualName = outputFile.name,
            expectedMimeType = expectedMimeType,
            metadataTitle = metadataTitle
        )
        val finalFile = resolveFinalFile(finalName, expectedMimeType)
        if (!outputFile.renameTo(finalFile)) {
            outputFile.copyTo(finalFile, overwrite = true)
            outputFile.delete()
        }
        tempDir.deleteRecursively()
        progressStates.remove(downloadId)
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
        YtDlpDiagnostics.record(
            context = context,
            url = current.sourceUrl,
            option = current.qualitySelector.orEmpty(),
            attempt = "nome final",
            result = "arquivo finalizado",
            error = "getInfo=${metadataTitle}; real=${outputFile.name}; final=${finalFile.name}",
            durationMs = elapsedMs(finalizeStartedAt)
        )
    }

    private suspend fun handleFailure(
        downloadId: Long,
        current: DownloadEntity,
        exception: Exception,
        attemptsApplied: Boolean,
        lastAttemptName: String,
        autoUpdateApplied: Boolean,
        flowStartedAt: Long? = null
    ) {
        val message = buildErrorMessage(current, exception, attemptsApplied)
        progressStates.remove(downloadId)
        repository.update(
            current.copy(
                tempPath = null,
                status = DownloadStatus.FAILED,
                errorMessage = message
            )
        )
        repository.updateStatus(downloadId, DownloadStatus.FAILED, message)
        YtDlpDiagnostics.record(
            context = context,
            url = current.sourceUrl,
            option = current.qualitySelector.orEmpty(),
            attempt = lastAttemptName,
            result = "falha final",
            error = exception.message,
            autoUpdate = autoUpdateApplied,
            durationMs = flowStartedAt?.let { elapsedMs(it) }
        )
    }

    private fun buildErrorMessage(
        current: DownloadEntity,
        exception: Exception,
        attemptsApplied: Boolean
    ): String {
        val detail = exception.message?.trim().orEmpty()
        val attemptsMessage = if (attemptsApplied) {
            "\n\n${context.getString(R.string.download_error_fallback_applied)}"
        } else {
            ""
        }
        if (isMp3Request(current.qualitySelector.orEmpty()) && detail.contains("ffmpeg", ignoreCase = true)) {
            return context.getString(R.string.download_mp3_error) + attemptsMessage
        }
        if (DownloadErrorFormatter.classify(detail) != DownloadErrorFormatter.ErrorKind.GENERIC) {
            val friendly = DownloadErrorFormatter.friendlyMessage(context, detail)
            return if (detail.isBlank()) {
                friendly + attemptsMessage
            } else {
                "$friendly$attemptsMessage\n\n${context.getString(R.string.download_error_technical_details)}\n$detail"
            }
        }
        if (isAudioRequest(current.qualitySelector.orEmpty())) {
            return context.getString(R.string.download_audio_error) + attemptsMessage
        }
        return if (detail.isBlank()) {
            context.getString(R.string.download_ytdlp_error) + attemptsMessage
        } else {
            "${context.getString(R.string.download_ytdlp_error)}$attemptsMessage: $detail"
        }
    }

    private fun createTempDir(downloadId: Long): File {
        val directory = File(context.cacheDir, "ytdlp/$downloadId")
        directory.mkdirs()
        return directory
    }

    private fun loadMetadataWithTimeout(
        downloadId: Long,
        current: DownloadEntity,
        option: String
    ): VideoInfo? {
        val startedAt = System.currentTimeMillis()
        YtDlpDiagnostics.record(
            context = context,
            url = current.sourceUrl,
            option = option,
            attempt = "getInfo",
            result = "inicio getInfo"
        )
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit(Callable {
            YoutubeDL.getInstance().getInfo(current.sourceUrl)
        })
        return try {
            val info = future.get(GET_INFO_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            YtDlpDiagnostics.record(
                context = context,
                url = current.sourceUrl,
                option = option,
                attempt = "getInfo",
                result = "fim getInfo",
                durationMs = elapsedMs(startedAt)
            )
            info
        } catch (exception: TimeoutException) {
            future.cancel(true)
            YtDlpDiagnostics.record(
                context = context,
                url = current.sourceUrl,
                option = option,
                attempt = "getInfo",
                result = "timeout getInfo",
                error = "getInfo excedeu ${GET_INFO_TIMEOUT_MS / 1000}s",
                durationMs = elapsedMs(startedAt)
            )
            null
        } catch (exception: Exception) {
            YtDlpDiagnostics.record(
                context = context,
                url = current.sourceUrl,
                option = option,
                attempt = "getInfo",
                result = "falha getInfo",
                error = exception.message,
                durationMs = elapsedMs(startedAt)
            )
            null
        } finally {
            executor.shutdownNow()
            if (runBlocking { isCanceled(downloadId) }) {
                future.cancel(true)
            }
        }
    }

    private fun buildAttempts(sourceUrl: String, selector: String): List<YtDlpAttempt> {
        val normalized = selector.ifBlank { "best" }
        val mp3AudioQuality = mp3AudioQualityForSelector(normalized)
        return when {
            mp3AudioQuality != null -> listOf(
                YtDlpAttempt(
                    name = "mp3 preferido",
                    formatSelector = "ba[ext=m4a]/ba[ext=webm]/bestaudio/best",
                    extractorArgs = youtubeExtractorArgs(sourceUrl, fallback = false),
                    convertToMp3 = true,
                    audioQuality = mp3AudioQuality
                ),
                YtDlpAttempt(
                    name = "mp3 fallback bestaudio",
                    formatSelector = "bestaudio/best",
                    extractorArgs = youtubeExtractorArgs(sourceUrl, fallback = true),
                    convertToMp3 = true,
                    audioQuality = mp3AudioQuality
                )
            )
            normalized == "bestaudio" -> listOf(
                YtDlpAttempt(
                    name = "audio preferido",
                    formatSelector = "ba[ext=m4a]/ba[ext=webm]/bestaudio/best",
                    extractorArgs = youtubeExtractorArgs(sourceUrl, fallback = false)
                ),
                YtDlpAttempt(
                    name = "audio fallback bestaudio",
                    formatSelector = "bestaudio/best",
                    extractorArgs = youtubeExtractorArgs(sourceUrl, fallback = true)
                )
            )
            mp4HeightForSelector(normalized) != null -> {
                val height = mp4HeightForSelector(normalized) ?: 720
                listOf(
                    YtDlpAttempt(
                        name = "mp4 selecionado ${height}p",
                        formatSelector = normalized,
                        mergeOutputFormat = "mp4"
                    ),
                    YtDlpAttempt(
                        name = "mp4 fallback permissivo ${height}p",
                        formatSelector = "bv*[height<=$height]+ba/b[height<=$height]/best[height<=$height]",
                        mergeOutputFormat = "mp4"
                    ),
                    YtDlpAttempt(
                        name = "mp4 fallback best",
                        formatSelector = "best[ext=mp4]/best",
                        mergeOutputFormat = "mp4"
                    )
                )
            }
            else -> listOf(
                YtDlpAttempt(
                    name = "formato selecionado",
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

    private fun shouldRetryYtDlpAttempt(
        exception: Exception,
        attemptIndex: Int,
        lastIndex: Int
    ): Boolean {
        if (attemptIndex >= lastIndex) return false
        return DownloadErrorFormatter.isYtDlpFallbackRecoverable(exception.message)
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

    private fun mp4HeightForSelector(selector: String): Int? {
        return when (selector) {
            YtDlpQualityOptions.SELECTOR_MP4_1440P -> 1440
            YtDlpQualityOptions.SELECTOR_MP4_1080P -> 1080
            YtDlpQualityOptions.SELECTOR_MP4_720P -> 720
            YtDlpQualityOptions.SELECTOR_MP4_480P -> 480
            else -> null
        }
    }

    private fun shouldAutoUpdateAndRetry(current: DownloadEntity, exception: Exception): Boolean {
        if (!isYoutubeUrl(current.sourceUrl)) return false
        if (!isAutoUpdateEnabled()) return false
        return DownloadErrorFormatter.isYoutubeAutoUpdateRecoverable(exception.message)
    }

    private fun isAutoUpdateEnabled(): Boolean {
        return context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AUTO_UPDATE_YTDLP_ON_YOUTUBE_ERRORS, true)
    }

    private fun runAutoUpdateIfAllowed(): Boolean {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (autoUpdateInProgress) return false
            if (now - lastAutoUpdateAttemptAt < AUTO_UPDATE_COOLDOWN_MS) return false
            autoUpdateInProgress = true
            lastAutoUpdateAttemptAt = now
        }
        return try {
            runYtDlpUpdate()
        } finally {
            autoUpdateInProgress = false
        }
    }

    private fun runYtDlpUpdate(): Boolean {
        val startedAt = System.currentTimeMillis()
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit(Callable {
            YoutubeDL.getInstance().updateYoutubeDL(
                context.applicationContext,
                YoutubeDL.UpdateChannel.NIGHTLY
            )
            true
        })
        return try {
            future.get(UPDATE_TIMEOUT_MS, TimeUnit.MILLISECONDS).also {
                YtDlpDiagnostics.record(
                    context = context,
                    url = "app",
                    option = "update",
                    attempt = "auto/manual update",
                    result = "update concluido",
                    durationMs = elapsedMs(startedAt)
                )
            }
        } catch (exception: TimeoutException) {
            future.cancel(true)
            YtDlpDiagnostics.record(
                context = context,
                url = "app",
                option = "update",
                attempt = "auto/manual update",
                result = "timeout update",
                durationMs = elapsedMs(startedAt)
            )
            false
        } catch (exception: Exception) {
            YtDlpDiagnostics.record(
                context = context,
                url = "app",
                option = "update",
                attempt = "auto/manual update",
                result = "falha update",
                error = exception.message,
                durationMs = elapsedMs(startedAt)
            )
            false
        } finally {
            executor.shutdownNow()
        }
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

    private fun resolveFinalName(
        actualName: String,
        expectedMimeType: String?,
        metadataTitle: String
    ): String {
        val actual = FileNameUtils.ensureExtension(
            FileNameUtils.sanitize(actualName),
            expectedMimeType
        )
        if (isUsableFinalName(actual, expectedMimeType)) {
            return actual
        }

        val metadataName = FileNameUtils.ensureExtension(
            FileNameUtils.sanitizeBaseName(metadataTitle),
            expectedMimeType
        )
        if (isUsableFinalName(metadataName, expectedMimeType)) {
            return metadataName
        }

        val fallbackBase = if (expectedMimeType == "audio/mpeg") {
            "audio_${System.currentTimeMillis()}"
        } else {
            "video_${System.currentTimeMillis()}"
        }
        return FileNameUtils.ensureExtension(fallbackBase, expectedMimeType)
    }

    private fun isUsableFinalName(fileName: String, expectedMimeType: String?): Boolean {
        val clean = fileName.trim()
        if (clean.isBlank()) return false
        if ("%(" in clean || clean == ")") return false
        val extension = clean.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
        if (extension.isBlank() || extension == "bin") return false
        if (expectedMimeType == "video/mp4" && extension != "mp4") return false
        if (expectedMimeType == "audio/mpeg" && extension != "mp3") return false

        val base = clean.substringBeforeLast('.', clean)
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("[_\\-\\s]+"), "")
        if (base.isBlank()) return false
        return base !in GENERIC_OUTPUT_NAMES
    }

    private fun resolveFinalFile(preferredName: String, expectedMimeType: String?): File {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        directory.mkdirs()

        val candidateName = FileNameUtils.ensureExtension(FileNameUtils.sanitize(preferredName), expectedMimeType)
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

    private fun elapsedMs(startedAt: Long): Long {
        return (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
    }

    private data class YtDlpAttempt(
        val name: String,
        val formatSelector: String,
        val extractorArgs: String? = null,
        val convertToMp3: Boolean = false,
        val audioQuality: String? = null,
        val mergeOutputFormat: String? = null
    )

    companion object {
        private const val SETTINGS_PREFS_NAME = "aio_downloader_settings"
        private const val PREF_AUTO_UPDATE_YTDLP_ON_YOUTUBE_ERRORS = "auto_update_ytdlp_on_youtube_errors"
        private const val AUTO_UPDATE_COOLDOWN_MS = 30L * 60L * 1000L
        private const val GET_INFO_TIMEOUT_MS = 10L * 1000L
        private const val UPDATE_TIMEOUT_MS = 60L * 1000L
        private const val PREPARE_BEFORE_EXECUTE_TIMEOUT_MS = 60L * 1000L
        private const val EXECUTE_WATCHDOG_POLL_MS = 5L * 1000L
        private const val EXECUTE_NO_CALLBACK_TIMEOUT_MS = 120L * 1000L
        private val GENERIC_OUTPUT_NAMES = setOf(
            "download",
            "file",
            "video",
            "audio",
            "watch",
            "videoplayback",
            "youtube",
            "index",
            "untitled"
        )
    }
}
