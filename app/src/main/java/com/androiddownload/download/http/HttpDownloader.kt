package com.androiddownload.download.http

import android.content.Context
import android.os.SystemClock
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.DownloadDestinationResolver
import com.androiddownload.core.utils.DownloadErrorFormatter
import com.androiddownload.core.utils.FileNameUtils
import com.androiddownload.core.utils.NetworkUtils
import com.androiddownload.core.utils.YtDlpDiagnostics
import com.androiddownload.download.data.DownloadRepository
import com.androiddownload.download.data.DownloadTransitionResult
import com.androiddownload.download.model.DownloadDestinationSubfolderResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException

enum class DownloadMode {
    NORMAL,
    RESUME,
    RETRY
}

class HttpDownloader(
    private val context: Context,
    private val client: OkHttpClient,
    private val repository: DownloadRepository
) {
    private val activeCalls = ConcurrentHashMap<Long, Call>()
    private val cancelRequests = ConcurrentHashMap.newKeySet<Long>()
    private val pauseRequests = ConcurrentHashMap.newKeySet<Long>()
    private val resumeTransfer = HttpResumeTransfer(client)

    suspend fun download(
        downloadId: Long,
        mode: DownloadMode = DownloadMode.NORMAL,
        onProgress: suspend () -> Unit = {}
    ) {
        var lastFailure: DownloadFailure? = null

        try {
            val initial = repository.getById(downloadId) ?: return
            if (!NetworkUtils.hasInternet(context)) {
                val message = context.getString(R.string.download_no_internet)
                val failed = repository.markFailedIfActive(downloadId, message)
                recordNetworkDiagnostic(
                    download = initial,
                    attempt = "preflight",
                    result = "internet indisponivel",
                    error = message
                )
                if (failed == DownloadTransitionResult.Applied) {
                    onProgress()
                }
                return
            }

            for (attempt in 1..MAX_ATTEMPTS) {
                val current = repository.getById(downloadId) ?: return
                if (current.status == DownloadStatus.CANCELED ||
                    current.status == DownloadStatus.COMPLETED
                ) {
                    return
                }

                if (current.status == DownloadStatus.FAILED && mode != DownloadMode.RETRY) {
                    return
                }

                try {
                    performAttempt(
                        downloadId = downloadId,
                        current = current,
                        mode = mode,
                        forceFromZero = false,
                        onProgress = onProgress
                    )
                    return
                } catch (exception: DownloadFailureException) {
                    lastFailure = exception.failure
                    if (!exception.failure.retryable || attempt >= MAX_ATTEMPTS || isUserCanceledOrPaused(downloadId)) {
                        break
                    }
                    val nextAttempt = attempt + 1
                    recordNetworkDiagnostic(
                        download = current,
                        attempt = "HTTP tentativa $nextAttempt",
                        result = "${networkDiagnosticResult(exception.failure.message)}; retry HTTP tentativa $nextAttempt",
                        error = exception.failure.message
                    )
                    delay(RETRY_BACKOFF_MS.getOrElse(nextAttempt - 1) { RETRY_BACKOFF_MS.last() })
                }
            }

            if (lastFailure != null && !isUserCanceledOrPaused(downloadId)) {
                repository.getById(downloadId)?.let { current ->
                    recordNetworkDiagnostic(
                        download = current,
                        attempt = "falha final",
                        result = "${networkDiagnosticResult(lastFailure.message)}; falha final de rede",
                        error = lastFailure.message
                    )
                }
                val failed = repository.markFailedIfActive(downloadId, lastFailure.message)
                if (failed == DownloadTransitionResult.Applied) {
                    onProgress()
                }
            }
        } catch (exception: CancellationException) {
            withContext(NonCancellable) {
                if (cancelRequests.contains(downloadId)) {
                    handleCanceled(downloadId)
                } else if (pauseRequests.contains(downloadId)) {
                    handlePaused(downloadId)
                }
            }
            throw exception
        } finally {
            activeCalls.remove(downloadId)
            cancelRequests.remove(downloadId)
            pauseRequests.remove(downloadId)
        }
    }

    fun cancel(downloadId: Long) {
        activeCalls[downloadId]?.let { call ->
            cancelRequests.add(downloadId)
            call.cancel()
        }
    }

    fun pause(downloadId: Long) {
        activeCalls[downloadId]?.let { call ->
            pauseRequests.add(downloadId)
            call.cancel()
        }
    }

    private suspend fun performAttempt(
        downloadId: Long,
        current: DownloadEntity,
        mode: DownloadMode,
        forceFromZero: Boolean,
        onProgress: suspend () -> Unit
    ) {
        val preparing = when (current.status) {
            DownloadStatus.QUEUED -> repository.markPreparingIfQueued(downloadId)
            DownloadStatus.PAUSED -> {
                if (mode == DownloadMode.RESUME) {
                    repository.markPreparingIfPaused(downloadId)
                } else {
                    DownloadTransitionResult.Rejected(current.status)
                }
            }
            DownloadStatus.PREPARING,
            DownloadStatus.RUNNING -> DownloadTransitionResult.Applied
            else -> DownloadTransitionResult.Rejected(current.status)
        }
        if (preparing != DownloadTransitionResult.Applied) {
            return
        }
        if (current.status != DownloadStatus.PREPARING &&
            current.status != DownloadStatus.RUNNING
        ) {
            onProgress()
        }

        val tempFile = createTempFile(downloadId)
        val resumeAllowed = !forceFromZero && (
            mode == DownloadMode.RESUME &&
                (current.status == DownloadStatus.PAUSED ||
                    current.status == DownloadStatus.PREPARING) ||
                mode == DownloadMode.RETRY
            )

        val requestBuilder = Request.Builder()
            .url(current.sourceUrl)
            .get()

        HttpHeadersJsonParser.parse(current.httpHeadersJson).forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        try {
            var displayFileName = current.fileName.orEmpty()
            var resolvedMimeType = current.mimeType
            var resolvedTotalBytes = 0L
            var startedAt = 0L
            var lastUpdateAt = 0L
            var lastProgress = 0

            val transferResult = resumeTransfer.execute(
                baseRequest = requestBuilder.build(),
                tempFile = tempFile,
                persistedDownloadedBytes = current.downloadedBytes,
                resumeAllowed = resumeAllowed,
                expectedTotalBytes = current.totalBytes,
                canRestartFromZero = !forceFromZero,
                onCallReady = { call, offsetResolution ->
                    activeCalls[downloadId] = call
                    if (offsetResolution.reconciled) {
                        YtDlpDiagnostics.record(
                            context = context,
                            url = current.sourceUrl,
                            option = "HTTP direto",
                            attempt = "retomada",
                            result = "offset reconciliado: Room=${offsetResolution.persistedOffset}, " +
                                "arquivo=${offsetResolution.requestedOffset}",
                            type = "diagnostico"
                        )
                    }
                },
                onResponseReady = { responseInfo ->
                    if (responseInfo.offsetResolution.requestedOffset > 0L &&
                        responseInfo.writeOffset == 0L
                    ) {
                        YtDlpDiagnostics.record(
                            context = context,
                            url = current.sourceUrl,
                            option = "HTTP direto",
                            attempt = "retomada",
                            result = "servidor ignorou Range; temporario reiniciado",
                            type = "fallback"
                        )
                    }

                    resolvedMimeType = responseInfo.contentType ?: current.mimeType
                    val fileName = resolveDownloadFileName(
                        url = responseInfo.finalUrl,
                        contentDisposition = responseInfo.contentDisposition,
                        mimeType = resolvedMimeType,
                        suggestedFileName = current.fileName
                    )
                    displayFileName = FileNameUtils.ensureExtension(
                        FileNameUtils.sanitize(fileName),
                        resolvedMimeType
                    )
                    resolvedTotalBytes = responseInfo.trustedTotalBytes ?: 0L
                    startedAt = SystemClock.elapsedRealtime()
                    lastProgress = calculateProgress(responseInfo.writeOffset, resolvedTotalBytes)

                    val running = repository.markRunningIfPreparingOrRunning(
                        id = downloadId,
                        finalUrl = responseInfo.finalUrl,
                        fileName = displayFileName,
                        mimeType = resolvedMimeType,
                        tempPath = tempFile.absolutePath,
                        totalBytes = resolvedTotalBytes,
                        downloadedBytes = responseInfo.writeOffset,
                        progress = lastProgress
                    )
                    throwIfTransitionRejected(running)
                    onProgress()
                    YtDlpDiagnostics.record(
                        context = context,
                        url = current.sourceUrl,
                        option = "HTTP direto",
                        attempt = "progresso",
                        result = "throttle Room HTTP aplicado: 500ms/1s",
                        type = "desempenho"
                    )
                },
                onBytesWritten = { responseInfo, sessionDownloadedBytes ->
                    val downloadedBytes = responseInfo.writeOffset + sessionDownloadedBytes
                    val now = SystemClock.elapsedRealtime()
                    val progress = calculateProgress(downloadedBytes, resolvedTotalBytes)
                    if (shouldPersistProgress(
                            lastUpdateAt = lastUpdateAt,
                            lastProgress = lastProgress,
                            now = now,
                            progress = progress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = resolvedTotalBytes
                        )
                    ) {
                        val persisted = repository.updateProgressIfRunning(
                            id = downloadId,
                            tempPath = tempFile.absolutePath,
                            totalBytes = resolvedTotalBytes,
                            downloadedBytes = downloadedBytes,
                            progress = progress,
                            speed = calculateSpeed(sessionDownloadedBytes, startedAt, now)
                        )
                        throwIfTransitionRejected(persisted)
                        onProgress()
                        lastUpdateAt = now
                        lastProgress = progress
                    }
                },
                checkActive = {
                    currentCoroutineContext().ensureActive()
                    throwIfCancelRequested(downloadId)
                    throwIfPauseRequested(downloadId)
                }
            )

            when (transferResult) {
                is HttpResumeTransfer.Result.RestartFromZero -> {
                    YtDlpDiagnostics.record(
                        context = context,
                        url = current.sourceUrl,
                        option = "HTTP direto",
                        attempt = "retomada",
                        result = "${transferResult.reason} Reinicio controlado sem Range.",
                        type = "fallback"
                    )
                    deleteTempFile(downloadId)
                    performAttempt(
                        downloadId = downloadId,
                        current = current.copy(
                            downloadedBytes = 0,
                            totalBytes = 0,
                            tempPath = null
                        ),
                        mode = DownloadMode.NORMAL,
                        forceFromZero = true,
                        onProgress = onProgress
                    )
                    return
                }
                is HttpResumeTransfer.Result.Rejected -> {
                    YtDlpDiagnostics.record(
                        context = context,
                        url = current.sourceUrl,
                        option = "HTTP direto",
                        attempt = "retomada",
                        result = transferResult.reason,
                        type = "erro"
                    )
                    throw DownloadFailureException(
                        DownloadFailure(
                            message = context.getString(R.string.download_resume_not_supported),
                            retryable = false
                        )
                    )
                }
                is HttpResumeTransfer.Result.HttpError -> {
                    handleHttpError(transferResult.responseCode)
                }
                is HttpResumeTransfer.Result.IntegrityFailure -> {
                    YtDlpDiagnostics.record(
                        context = context,
                        url = current.sourceUrl,
                        option = "HTTP direto",
                        attempt = "integridade",
                        result = transferResult.reason,
                        type = "erro"
                    )
                    deleteTempFile(downloadId)
                    throw DownloadFailureException(
                        DownloadFailure(
                            message = "${context.getString(R.string.download_http_error)} " +
                                transferResult.reason,
                            retryable = true
                        )
                    )
                }
                is HttpResumeTransfer.Result.Transferred -> {
                    val responseInfo = transferResult.responseInfo
                    throwIfCancelRequested(downloadId)
                    throwIfPauseRequested(downloadId)

                    val savedFile = DownloadDestinationResolver.saveToDestination(
                        context = context,
                        sourceFile = tempFile,
                        preferredName = displayFileName,
                        mimeType = resolvedMimeType,
                        preserveName = responseInfo.writeOffset > 0L,
                        destinationSubfolder = DownloadDestinationSubfolderResolver.resolve(current)
                    )
                    val completed = repository.markCompletedIfRunning(
                        id = downloadId,
                        finalUrl = responseInfo.finalUrl,
                        fileName = savedFile.fileName,
                        mimeType = resolvedMimeType,
                        destinationUri = savedFile.uri.toString(),
                        totalBytes = resolvedTotalBytes,
                        downloadedBytes = savedFile.bytes
                    )
                    if (completed == DownloadTransitionResult.Applied) {
                        onProgress()
                    } else {
                        val cleaned = DownloadDestinationResolver.deleteSavedFile(context, savedFile)
                        recordNetworkDiagnostic(
                            download = current,
                            attempt = "conclusao rejeitada",
                            result = if (cleaned) {
                                "destino desta tentativa removido"
                            } else {
                                "nao foi possivel remover o destino desta tentativa"
                            },
                            error = null
                        )
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: DownloadFailureException) {
            throw exception
        } catch (exception: IllegalArgumentException) {
            throw DownloadFailureException(
                DownloadFailure(
                    message = context.getString(R.string.download_invalid_url),
                    retryable = false
                )
            )
        } catch (exception: UnknownHostException) {
            throw DownloadFailureException(
                DownloadFailure(
                    message = context.getString(R.string.download_unknown_host),
                    retryable = true
                )
            )
        } catch (exception: ConnectException) {
            throw DownloadFailureException(
                DownloadFailure(
                    message = context.getString(R.string.download_connect_failed),
                    retryable = true
                )
            )
        } catch (exception: NoRouteToHostException) {
            throw DownloadFailureException(
                DownloadFailure(
                    message = context.getString(R.string.download_no_internet),
                    retryable = true
                )
            )
        } catch (exception: SocketTimeoutException) {
            throw DownloadFailureException(
                DownloadFailure(
                    message = context.getString(R.string.download_connection_timeout),
                    retryable = true
                )
            )
        } catch (exception: SSLException) {
            throw DownloadFailureException(
                DownloadFailure(
                    message = context.getString(R.string.download_ssl_failed),
                    retryable = false
                )
            )
        } catch (exception: InterruptedIOException) {
            throw DownloadFailureException(
                DownloadFailure(
                    message = context.getString(R.string.download_connection_interrupted),
                    retryable = true
                )
            )
        } catch (exception: FileNotFoundException) {
            throw DownloadFailureException(
                DownloadFailure(
                    message = context.getString(R.string.download_save_error),
                    retryable = false
                )
            )
        } catch (exception: SecurityException) {
            throw DownloadFailureException(
                DownloadFailure(
                    message = context.getString(R.string.download_save_error),
                    retryable = false
                )
            )
        } catch (exception: DownloadDestinationResolver.DestinationException) {
            throw DownloadFailureException(
                DownloadFailure(
                    message = exception.message ?: context.getString(R.string.download_save_error),
                    retryable = false
                )
            )
        } catch (exception: IOException) {
            throw DownloadFailureException(mapIoFailure(exception))
        } finally {
            activeCalls.remove(downloadId)
        }
    }

    private fun mapIoFailure(exception: IOException): DownloadFailure {
        val message = exception.message.orEmpty()
        return when {
            isNoSpaceError(message) -> DownloadFailure(
                message = context.getString(R.string.download_no_space),
                retryable = false
            )
            isSaveErrorMessage(message) -> DownloadFailure(
                message = context.getString(R.string.download_save_error),
                retryable = false
            )
            isNetworkIoMessage(message) || exception is EOFException -> DownloadFailure(
                message = context.getString(R.string.download_connection_interrupted),
                retryable = true
            )
            message.contains("HTTP 5") -> DownloadFailure(
                message = "${context.getString(R.string.download_http_error)} ${extractHttpCode(message)}",
                retryable = true
            )
            message.contains("HTTP") -> DownloadFailure(
                message = "${context.getString(R.string.download_http_error)} ${extractHttpCode(message)}",
                retryable = false
            )
            else -> DownloadFailure(
                message = context.getString(R.string.download_save_error),
                retryable = false
            )
        }
    }

    private fun handleHttpError(code: Int): Nothing {
        throw DownloadFailureException(
            DownloadFailure(
                message = when (code) {
                    404 -> context.getString(R.string.download_not_found_404)
                    429 -> "${context.getString(R.string.download_rate_limited)} HTTP 429"
                    in 500..599 -> "${context.getString(R.string.download_server_unstable)} HTTP $code"
                    else -> "${context.getString(R.string.download_http_error)} $code"
                },
                retryable = code >= 500 || code == 408 || code == 429
            )
        )
    }

    private fun recordNetworkDiagnostic(
        download: DownloadEntity,
        attempt: String,
        result: String,
        error: String? = null
    ) {
        YtDlpDiagnostics.record(
            context = context,
            url = download.sourceUrl,
            option = "HTTP direto",
            attempt = attempt,
            result = result,
            error = error,
            type = "rede"
        )
    }

    private fun networkDiagnosticResult(message: String): String {
        return when (DownloadErrorFormatter.classify(message)) {
            DownloadErrorFormatter.ErrorKind.NO_INTERNET -> "internet indisponivel"
            DownloadErrorFormatter.ErrorKind.UNKNOWN_HOST -> "falha de DNS/conexao"
            DownloadErrorFormatter.ErrorKind.TIMEOUT -> "timeout de conexao/leitura"
            DownloadErrorFormatter.ErrorKind.CONNECT_FAILED -> "timeout/falha de conexao"
            DownloadErrorFormatter.ErrorKind.RATE_LIMITED -> "servidor retornou 429"
            DownloadErrorFormatter.ErrorKind.SERVER_UNSTABLE -> "servidor retornou 5xx"
            DownloadErrorFormatter.ErrorKind.CONNECTION_INTERRUPTED -> "conexao interrompida"
            else -> "falha HTTP"
        }
    }

    private fun createTempFile(downloadId: Long): File {
        val directory = File(context.cacheDir, "downloads")
        directory.mkdirs()
        return File(directory, "$downloadId.part")
    }

    private fun resolveDownloadFileName(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        suggestedFileName: String?
    ): String {
        val fromDisposition = contentDisposition
            ?.takeIf { it.isNotBlank() }
            ?.let { FileNameUtils.guessFileName(url, it, mimeType) }

        val fromUrl = FileNameUtils.guessFileName(url, null, null)
        val suggested = suggestedFileName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { FileNameUtils.sanitize(it) }
            ?.takeIf { it != fromUrl }
        val preferred = suggested ?: fromDisposition ?: fromUrl
        return FileNameUtils.ensureExtension(preferred, mimeType)
    }

    private suspend fun handleCanceled(downloadId: Long) {
        if (repository.markCanceled(downloadId) == DownloadTransitionResult.Applied) {
            deleteTempFile(downloadId)
        }
    }

    private suspend fun handlePaused(downloadId: Long) {
        repository.markPaused(downloadId)
    }

    private fun deleteTempFile(downloadId: Long) {
        val tempFile = createTempFile(downloadId)
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    private fun throwIfCancelRequested(downloadId: Long) {
        if (cancelRequests.contains(downloadId)) {
            throw CancellationException("Download cancelado")
        }
    }

    private fun throwIfPauseRequested(downloadId: Long) {
        if (pauseRequests.contains(downloadId) && !cancelRequests.contains(downloadId)) {
            throw CancellationException("Download pausado")
        }
    }

    private fun isUserCanceledOrPaused(downloadId: Long): Boolean {
        return cancelRequests.contains(downloadId) || pauseRequests.contains(downloadId)
    }

    private fun calculateProgress(downloadedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0) return 0
        return ((downloadedBytes * 100) / totalBytes).coerceIn(0, 100).toInt()
    }

    private fun calculateSpeed(downloadedBytes: Long, startedAt: Long, now: Long): Long {
        val elapsedMs = (now - startedAt).coerceAtLeast(1)
        return (downloadedBytes * 1000) / elapsedMs
    }

    fun cleanupCanceledDownload(downloadId: Long) {
        deleteTempFile(downloadId)
    }

    private fun throwIfTransitionRejected(result: DownloadTransitionResult) {
        if (result is DownloadTransitionResult.Rejected) {
            throw DownloadStateChangedException(result.currentStatus)
        }
    }

    private fun shouldPersistProgress(
        lastUpdateAt: Long,
        lastProgress: Int,
        now: Long,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long
    ): Boolean {
        if (downloadedBytes == totalBytes) return true
        if (lastUpdateAt == 0L) return true
        val elapsedMs = now - lastUpdateAt
        if (progress != lastProgress && elapsedMs >= PROGRESS_MIN_INTERVAL_MS) return true
        return elapsedMs >= PROGRESS_HEARTBEAT_INTERVAL_MS
    }

    private fun isNoSpaceError(message: String): Boolean {
        return message.contains("ENOSPC", ignoreCase = true) ||
            message.contains("No space left on device", ignoreCase = true) ||
            message.contains("space", ignoreCase = true) && message.contains("left", ignoreCase = true)
    }

    private fun isSaveErrorMessage(message: String): Boolean {
        return message.contains("permission", ignoreCase = true) ||
            message.contains("denied", ignoreCase = true) ||
            message.contains("read-only", ignoreCase = true) ||
            message.contains("read only", ignoreCase = true) ||
            message.contains("file not found", ignoreCase = true) ||
            message.contains("no such file", ignoreCase = true) ||
            message.contains("directory", ignoreCase = true) && message.contains("exist", ignoreCase = true) ||
            message.contains("cannot create", ignoreCase = true) ||
            message.contains("unable to create", ignoreCase = true)
    }

    private fun isNetworkIoMessage(message: String): Boolean {
        return message.contains("connection", ignoreCase = true) ||
            message.contains("reset", ignoreCase = true) ||
            message.contains("broken pipe", ignoreCase = true) ||
            message.contains("unexpected end of stream", ignoreCase = true) ||
            message.contains("stream closed", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true)
    }

    private fun extractHttpCode(message: String): String {
        val match = HTTP_CODE_REGEX.find(message)
        return match?.groupValues?.getOrNull(1).orEmpty()
    }

    private companion object {
        const val PROGRESS_MIN_INTERVAL_MS = 500L
        const val PROGRESS_HEARTBEAT_INTERVAL_MS = 1000L
        const val MAX_ATTEMPTS = 3
        val RETRY_BACKOFF_MS = longArrayOf(0L, 1000L, 3000L)
        val HTTP_CODE_REGEX = Regex("""HTTP\s+(\d{3})""")
    }

    private data class DownloadFailure(
        val message: String,
        val retryable: Boolean
    )

    private class DownloadStateChangedException(
        status: DownloadStatus?
    ) : CancellationException("Estado persistido mudou para ${status?.name ?: "ausente"}")

    private class DownloadFailureException(val failure: DownloadFailure) : IOException(failure.message)
}
