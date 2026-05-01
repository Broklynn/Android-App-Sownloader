package com.androiddownload.download.http

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.FileNameUtils
import com.androiddownload.download.data.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class HttpDownloader(
    private val context: Context,
    private val client: OkHttpClient,
    private val repository: DownloadRepository
) {
    private val activeCalls = ConcurrentHashMap<Long, Call>()
    private val cancelRequests = ConcurrentHashMap.newKeySet<Long>()

    suspend fun download(downloadId: Long, onProgress: suspend () -> Unit = {}) {
        try {
            val queued = repository.getById(downloadId) ?: return
            if (queued.status == DownloadStatus.CANCELED ||
                queued.status == DownloadStatus.COMPLETED ||
                queued.status == DownloadStatus.FAILED
            ) {
                return
            }

            repository.updateStatus(downloadId, DownloadStatus.PREPARING)

            val request = Request.Builder()
                .url(queued.sourceUrl)
                .get()
                .build()

            val call = client.newCall(request)
            activeCalls[downloadId] = call

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }

                val body = response.body ?: throw IOException("Resposta sem corpo")
                val mimeType = body.contentType()?.toString()
                val fileName = FileNameUtils.guessFileName(
                    url = response.request.url.toString(),
                    contentDisposition = response.header("Content-Disposition"),
                    mimeType = mimeType
                )
                val totalBytes = body.contentLength()
                val tempFile = createTempFile(downloadId)
                val finalFile = createFinalFile(fileName)

                repository.update(
                    queued.copy(
                        finalUrl = response.request.url.toString(),
                        fileName = finalFile.name,
                        mimeType = mimeType,
                        tempPath = tempFile.absolutePath,
                        totalBytes = totalBytes,
                        downloadedBytes = 0,
                        progress = 0,
                        speed = 0,
                        status = DownloadStatus.RUNNING,
                        errorMessage = null
                    )
                )
                onProgress()

                var downloadedBytes = 0L
                val startedAt = SystemClock.elapsedRealtime()
                var lastUpdateAt = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            throwIfCancelRequested(downloadId)
                            val read = input.read(buffer)
                            if (read == -1) break
                            throwIfCancelRequested(downloadId)

                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = SystemClock.elapsedRealtime()
                            if (now - lastUpdateAt >= PROGRESS_INTERVAL_MS || downloadedBytes == totalBytes) {
                                repository.update(
                                    queued.copy(
                                        finalUrl = response.request.url.toString(),
                                        fileName = finalFile.name,
                                        mimeType = mimeType,
                                        tempPath = tempFile.absolutePath,
                                        totalBytes = totalBytes,
                                        downloadedBytes = downloadedBytes,
                                        progress = calculateProgress(downloadedBytes, totalBytes),
                                        speed = calculateSpeed(downloadedBytes, startedAt, now),
                                        status = DownloadStatus.RUNNING,
                                        errorMessage = null
                                    )
                                )
                                onProgress()
                                lastUpdateAt = now
                            }
                        }
                    }
                }

                throwIfCancelRequested(downloadId)
                moveTempToFinal(tempFile, finalFile)
                repository.update(
                    queued.copy(
                        finalUrl = response.request.url.toString(),
                        fileName = finalFile.name,
                        mimeType = mimeType,
                        destinationUri = Uri.fromFile(finalFile).toString(),
                        tempPath = null,
                        totalBytes = if (totalBytes >= 0) totalBytes else downloadedBytes,
                        downloadedBytes = downloadedBytes,
                        progress = 100,
                        speed = 0,
                        status = DownloadStatus.COMPLETED,
                        errorMessage = null
                    )
                )
                onProgress()
            }
        } catch (exception: CancellationException) {
            withContext(NonCancellable) {
                handleCanceled(downloadId)
            }
            throw exception
        } catch (exception: Exception) {
            if (cancelRequests.contains(downloadId)) {
                withContext(NonCancellable) {
                    handleCanceled(downloadId)
                }
            } else {
                repository.updateStatus(downloadId, DownloadStatus.FAILED, exception.message)
            }
        } finally {
            activeCalls.remove(downloadId)
            cancelRequests.remove(downloadId)
        }
    }

    fun cancel(downloadId: Long) {
        cancelRequests.add(downloadId)
        activeCalls[downloadId]?.cancel()
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

    private fun moveTempToFinal(tempFile: File, finalFile: File) {
        if (tempFile.renameTo(finalFile)) return

        tempFile.inputStream().use { input ->
            FileOutputStream(finalFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile.delete()
    }

    private suspend fun handleCanceled(downloadId: Long) {
        deleteTempFile(downloadId)
        repository.markCanceled(downloadId)
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

    private fun calculateProgress(downloadedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0) return 0
        return ((downloadedBytes * 100) / totalBytes).coerceIn(0, 100).toInt()
    }

    private fun calculateSpeed(downloadedBytes: Long, startedAt: Long, now: Long): Long {
        val elapsedMs = (now - startedAt).coerceAtLeast(1)
        return (downloadedBytes * 1000) / elapsedMs
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
