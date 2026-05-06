package com.androiddownload.download.http

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.DownloadErrorFormatter
import com.androiddownload.core.utils.FileNameUtils
import com.androiddownload.core.utils.NetworkUtils
import com.androiddownload.core.utils.YtDlpDiagnostics
import com.androiddownload.download.data.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.FileOutputStream
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
                repository.updateStatus(downloadId, DownloadStatus.FAILED, message)
                recordNetworkDiagnostic(
                    download = initial,
                    attempt = "preflight",
                    result = "internet indisponivel",
                    error = message
                )
                onProgress()
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
                repository.updateStatus(downloadId, DownloadStatus.FAILED, lastFailure.message)
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
        cancelRequests.add(downloadId)
        activeCalls[downloadId]?.cancel()
    }

    fun pause(downloadId: Long) {
        pauseRequests.add(downloadId)
        activeCalls[downloadId]?.cancel()
    }

    private suspend fun performAttempt(
        downloadId: Long,
        current: DownloadEntity,
        mode: DownloadMode,
        forceFromZero: Boolean,
        onProgress: suspend () -> Unit
    ) {
        repository.updateStatus(downloadId, DownloadStatus.PREPARING)
        onProgress()

        val tempFile = createTempFile(downloadId)
        var resumeOffset = when {
            forceFromZero -> 0L
            mode == DownloadMode.RESUME && current.status == DownloadStatus.PAUSED && tempFile.exists() -> {
                current.downloadedBytes.coerceAtLeast(0)
            }
            mode == DownloadMode.RETRY && tempFile.exists() && current.downloadedBytes > 0L -> {
                current.downloadedBytes.coerceAtLeast(0)
            }
            else -> 0L
        }

        val requestBuilder = Request.Builder()
            .url(current.sourceUrl)
            .get()

        if (resumeOffset > 0L) {
            requestBuilder.header("Range", "bytes=$resumeOffset-")
        }

        val call = client.newCall(requestBuilder.build())
        activeCalls[downloadId] = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    handleHttpError(response.code)
                }

                if (resumeOffset > 0L && response.code != 206) {
                    if (mode == DownloadMode.RETRY && !forceFromZero) {
                        deleteTempFile(downloadId)
                        performAttempt(
                            downloadId = downloadId,
                            current = current.copy(downloadedBytes = 0, tempPath = null),
                            mode = DownloadMode.NORMAL,
                            forceFromZero = true,
                            onProgress = onProgress
                        )
                        return
                    }
                    throw DownloadFailureException(
                        DownloadFailure(
                            message = context.getString(R.string.download_resume_not_supported),
                            retryable = false
                        )
                    )
                }

                val body = response.body ?: throw DownloadFailureException(
                    DownloadFailure(
                        message = context.getString(R.string.download_http_error),
                        retryable = true
                    )
                )

                val contentRange = response.header("Content-Range")
                val contentLength = body.contentLength()
                val totalBytes = resolveTotalBytes(
                    responseCode = response.code,
                    contentRange = contentRange,
                    resumeOffset = resumeOffset,
                    fallback = current.totalBytes,
                    bodyLength = contentLength
                ) ?: throw DownloadFailureException(
                    DownloadFailure(
                        message = context.getString(R.string.download_missing_content_length),
                        retryable = false
                    )
                )

                if (resumeOffset > 0L) {
                    val contentRangeStart = parseContentRangeStart(contentRange)
                    if (contentRangeStart != null && contentRangeStart != resumeOffset) {
                        if (mode == DownloadMode.RETRY && !forceFromZero) {
                            deleteTempFile(downloadId)
                            performAttempt(
                                downloadId = downloadId,
                                current = current.copy(downloadedBytes = 0, tempPath = null),
                                mode = DownloadMode.NORMAL,
                                forceFromZero = true,
                                onProgress = onProgress
                            )
                            return
                        }
                        throw DownloadFailureException(
                            DownloadFailure(
                                message = context.getString(R.string.download_resume_not_supported),
                                retryable = false
                            )
                        )
                    }
                }

                val mimeType = body.contentType()?.toString() ?: current.mimeType
                val fileName = resolveDownloadFileName(
                    url = response.request.url.toString(),
                    contentDisposition = response.header("Content-Disposition"),
                    mimeType = mimeType
                )
                val finalFile = resolveFinalFile(fileName, resumeOffset > 0L)

                repository.update(
                    current.copy(
                        finalUrl = response.request.url.toString(),
                        fileName = finalFile.name,
                        mimeType = mimeType,
                        tempPath = tempFile.absolutePath,
                        totalBytes = totalBytes,
                        downloadedBytes = resumeOffset,
                        progress = calculateProgress(resumeOffset, totalBytes),
                        speed = 0,
                        status = DownloadStatus.RUNNING,
                        errorMessage = null
                    )
                )
                onProgress()
                YtDlpDiagnostics.record(
                    context = context,
                    url = current.sourceUrl,
                    option = "HTTP direto",
                    attempt = "progresso",
                    result = "throttle Room HTTP aplicado: 500ms/1s",
                    type = "desempenho"
                )

                var sessionDownloadedBytes = 0L
                val startedAt = SystemClock.elapsedRealtime()
                var lastUpdateAt = 0L
                var lastProgress = calculateProgress(resumeOffset, totalBytes)

                body.byteStream().use { input ->
                    FileOutputStream(tempFile, resumeOffset > 0L).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            throwIfCancelRequested(downloadId)
                            throwIfPauseRequested(downloadId)
                            val read = input.read(buffer)
                            if (read == -1) break
                            throwIfCancelRequested(downloadId)
                            throwIfPauseRequested(downloadId)

                            output.write(buffer, 0, read)
                            sessionDownloadedBytes += read

                            val downloadedBytes = resumeOffset + sessionDownloadedBytes
                            val now = SystemClock.elapsedRealtime()
                            val progress = calculateProgress(downloadedBytes, totalBytes)
                            if (shouldPersistProgress(
                                    lastUpdateAt = lastUpdateAt,
                                    lastProgress = lastProgress,
                                    now = now,
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            ) {
                                repository.update(
                                    current.copy(
                                        finalUrl = response.request.url.toString(),
                                        fileName = finalFile.name,
                                        mimeType = mimeType,
                                        tempPath = tempFile.absolutePath,
                                        totalBytes = totalBytes,
                                        downloadedBytes = downloadedBytes,
                                        progress = progress,
                                        speed = calculateSpeed(sessionDownloadedBytes, startedAt, now),
                                        status = DownloadStatus.RUNNING,
                                        errorMessage = null
                                    )
                                )
                                onProgress()
                                lastUpdateAt = now
                                lastProgress = progress
                            }
                        }
                    }
                }

                throwIfCancelRequested(downloadId)
                throwIfPauseRequested(downloadId)

                moveTempToFinal(tempFile, finalFile)
                val validatedBytes = validateFinalFile(finalFile)
                repository.update(
                    current.copy(
                        finalUrl = response.request.url.toString(),
                        fileName = finalFile.name,
                        mimeType = mimeType,
                        destinationUri = Uri.fromFile(finalFile).toString(),
                        tempPath = null,
                        totalBytes = if (totalBytes >= 0) totalBytes else validatedBytes,
                        downloadedBytes = validatedBytes,
                        progress = 100,
                        speed = 0,
                        status = DownloadStatus.COMPLETED,
                        errorMessage = null
                    )
                )
                onProgress()
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

    private fun createFinalFile(fileName: String): File {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        directory.mkdirs()

        val cleanName = FileNameUtils.sanitize(fileName)
        val name = cleanName.substringBeforeLast('.', cleanName)
        val extension = cleanName.substringAfterLast('.', missingDelimiterValue = "")
        var candidate = File(directory, cleanName)
        var index = 1
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

    private fun resolveDownloadFileName(
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ): String {
        val fromDisposition = contentDisposition
            ?.takeIf { it.isNotBlank() }
            ?.let { FileNameUtils.guessFileName(url, it, mimeType) }

        val fromUrl = FileNameUtils.guessFileName(url, null, null)
        val preferred = fromDisposition ?: fromUrl
        return FileNameUtils.ensureExtension(preferred, mimeType)
    }

    private fun moveTempToFinal(tempFile: File, finalFile: File) {
        if (tempFile.renameTo(finalFile)) return

        tempFile.inputStream().use { input ->
            FileOutputStream(finalFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile.delete()
    }

    private fun validateFinalFile(finalFile: File): Long {
        val uri = Uri.fromFile(finalFile)
        val length = finalFile.length()
        if (!finalFile.exists() || !finalFile.isFile || length <= 0L || uri.path.isNullOrBlank()) {
            throw DownloadFailureException(
                DownloadFailure(
                    message = context.getString(R.string.download_final_file_invalid),
                    retryable = false
                )
            )
        }
        return length
    }

    private suspend fun handleCanceled(downloadId: Long) {
        deleteTempFile(downloadId)
        repository.markCanceled(downloadId)
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

    private fun resolveFinalFile(fileName: String, preserveName: Boolean): File {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        directory.mkdirs()

        val cleanName = FileNameUtils.sanitize(fileName)
        if (preserveName) {
            return File(directory, cleanName)
        }

        val name = cleanName.substringBeforeLast('.', cleanName)
        val extension = cleanName.substringAfterLast('.', missingDelimiterValue = "")
        var candidate = File(directory, cleanName)
        var index = 1
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

    private fun resolveTotalBytes(
        responseCode: Int,
        contentRange: String?,
        resumeOffset: Long,
        fallback: Long,
        bodyLength: Long
    ): Long? {
        if (responseCode == 206) {
            val parsedTotal = parseContentRangeTotal(contentRange)
            if (parsedTotal != null) {
                return parsedTotal
            }
            if (bodyLength >= 0) {
                return resumeOffset + bodyLength
            }
            return null
        }

        if (bodyLength >= 0) {
            return bodyLength
        }

        if (fallback > 0L) {
            return fallback
        }

        return null
    }

    private fun parseContentRangeTotal(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val match = CONTENT_RANGE_REGEX.find(contentRange.trim()) ?: return null
        val totalPart = match.groupValues[3]
        return totalPart.takeIf { it != "*" }?.toLongOrNull()
    }

    private fun parseContentRangeStart(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val match = CONTENT_RANGE_REGEX.find(contentRange.trim()) ?: return null
        return match.groupValues[1].toLongOrNull()
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
        val CONTENT_RANGE_REGEX = Regex("""bytes\s+(\d+)-(\d+|\*)/(\d+|\*)""")
        val HTTP_CODE_REGEX = Regex("""HTTP\s+(\d{3})""")
    }

    private data class DownloadFailure(
        val message: String,
        val retryable: Boolean
    )

    private class DownloadFailureException(val failure: DownloadFailure) : IOException(failure.message)
}
