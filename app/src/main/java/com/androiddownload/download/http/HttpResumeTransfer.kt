package com.androiddownload.download.http

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

internal class HttpResumeTransfer(
    private val client: OkHttpClient
) {
    suspend fun execute(
        baseRequest: Request,
        tempFile: File,
        persistedDownloadedBytes: Long,
        resumeAllowed: Boolean,
        expectedTotalBytes: Long,
        canRestartFromZero: Boolean,
        onCallReady: (Call, HttpResumePlanner.OffsetResolution) -> Unit = { _, _ -> },
        onResponseReady: suspend (ResponseInfo) -> Unit = {},
        onBytesWritten: suspend (ResponseInfo, Long) -> Unit = { _, _ -> },
        checkActive: suspend () -> Unit = {}
    ): Result {
        val offsetResolution = HttpResumePlanner.resolveOffset(
            persistedDownloadedBytes = persistedDownloadedBytes,
            tempFileExists = tempFile.exists(),
            tempFileLength = tempFile.length(),
            resumeAllowed = resumeAllowed
        )
        val requestedOffset = offsetResolution.requestedOffset
        val request = baseRequest.newBuilder()
            .removeHeader(RANGE_HEADER)
            .apply {
                if (requestedOffset > 0L) {
                    header(RANGE_HEADER, "bytes=$requestedOffset-")
                }
            }
            .build()
        val call = client.newCall(request)
        onCallReady(call, offsetResolution)

        call.execute().use { response ->
            if (!response.isSuccessful &&
                !(requestedOffset > 0L && response.code == HTTP_RANGE_NOT_SATISFIABLE)
            ) {
                return Result.HttpError(response.code)
            }

            val body = response.body ?: return Result.Rejected(
                reason = "Resposta HTTP sem corpo.",
                offsetResolution = offsetResolution
            )
            val bodyLength = body.contentLength()
            val contentRangeHeader = response.header(CONTENT_RANGE_HEADER)
            val responsePlan = HttpResumePlanner.planResponse(
                requestedOffset = requestedOffset,
                responseCode = response.code,
                contentRange = contentRangeHeader,
                canRestartFromZero = canRestartFromZero,
                responseBodyLength = bodyLength,
                expectedTotalBytes = expectedTotalBytes
            )
            val contentRange = when (responsePlan) {
                is HttpResumePlanner.ResponsePlan.Append -> responsePlan.contentRange
                is HttpResumePlanner.ResponsePlan.WriteFromStart -> responsePlan.contentRange
                is HttpResumePlanner.ResponsePlan.RestartFromZero -> {
                    return Result.RestartFromZero(responsePlan.reason, offsetResolution)
                }
                is HttpResumePlanner.ResponsePlan.Reject -> {
                    return Result.Rejected(responsePlan.reason, offsetResolution)
                }
            }
            val writeOffset = when (responsePlan) {
                is HttpResumePlanner.ResponsePlan.Append -> requestedOffset
                is HttpResumePlanner.ResponsePlan.WriteFromStart -> 0L
                else -> error("Plano de resposta ja tratado.")
            }
            val trustedTotalBytes = when {
                response.code == HTTP_PARTIAL_CONTENT -> contentRange?.total
                response.code == HTTP_OK && bodyLength >= 0L -> bodyLength
                else -> null
            }
            val responseInfo = ResponseInfo(
                responseCode = response.code,
                finalUrl = response.request.url.toString(),
                contentType = body.contentType()?.toString(),
                contentDisposition = response.header(CONTENT_DISPOSITION_HEADER),
                contentRange = contentRangeHeader,
                responseBodyLength = bodyLength,
                offsetResolution = offsetResolution,
                writeOffset = writeOffset,
                trustedTotalBytes = trustedTotalBytes
            )

            onResponseReady(responseInfo)

            if (writeOffset > 0L && tempFile.length() != writeOffset) {
                return integrityFailure(
                    responseInfo = responseInfo,
                    reason = "O tamanho do arquivo temporario mudou antes do append.",
                    expectedBytes = writeOffset,
                    actualBytes = tempFile.length()
                )
            }

            var sessionDownloadedBytes = 0L
            body.byteStream().use { input ->
                FileOutputStream(tempFile, writeOffset > 0L).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        checkActive()
                        output.write(buffer, 0, read)
                        sessionDownloadedBytes += read
                        onBytesWritten(responseInfo, sessionDownloadedBytes)
                    }
                }
            }

            val rangeLength = contentRange?.length
            if (rangeLength != null && sessionDownloadedBytes != rangeLength) {
                return integrityFailure(
                    responseInfo = responseInfo,
                    reason = "O corpo recebido nao corresponde ao intervalo declarado.",
                    expectedBytes = rangeLength,
                    actualBytes = sessionDownloadedBytes
                )
            }
            if (bodyLength >= 0L && sessionDownloadedBytes != bodyLength) {
                return integrityFailure(
                    responseInfo = responseInfo,
                    reason = "O corpo recebido nao corresponde ao Content-Length.",
                    expectedBytes = bodyLength,
                    actualBytes = sessionDownloadedBytes
                )
            }

            val actualFileLength = tempFile.length()
            val expectedWrittenLength = writeOffset + sessionDownloadedBytes
            if (actualFileLength != expectedWrittenLength) {
                return integrityFailure(
                    responseInfo = responseInfo,
                    reason = "A posicao final do arquivo temporario e incompativel com a escrita.",
                    expectedBytes = expectedWrittenLength,
                    actualBytes = actualFileLength
                )
            }
            if (trustedTotalBytes != null && actualFileLength != trustedTotalBytes) {
                return integrityFailure(
                    responseInfo = responseInfo,
                    reason = "O tamanho final do arquivo temporario difere do total validado.",
                    expectedBytes = trustedTotalBytes,
                    actualBytes = actualFileLength
                )
            }

            return Result.Transferred(
                responseInfo = responseInfo,
                sessionDownloadedBytes = sessionDownloadedBytes,
                finalFileLength = actualFileLength
            )
        }
    }

    private fun integrityFailure(
        responseInfo: ResponseInfo,
        reason: String,
        expectedBytes: Long,
        actualBytes: Long
    ): Result.IntegrityFailure {
        return Result.IntegrityFailure(
            responseInfo = responseInfo,
            reason = "$reason Esperado=$expectedBytes, obtido=$actualBytes.",
            expectedBytes = expectedBytes,
            actualBytes = actualBytes
        )
    }

    data class ResponseInfo(
        val responseCode: Int,
        val finalUrl: String,
        val contentType: String?,
        val contentDisposition: String?,
        val contentRange: String?,
        val responseBodyLength: Long,
        val offsetResolution: HttpResumePlanner.OffsetResolution,
        val writeOffset: Long,
        val trustedTotalBytes: Long?
    )

    sealed class Result {
        data class Transferred(
            val responseInfo: ResponseInfo,
            val sessionDownloadedBytes: Long,
            val finalFileLength: Long
        ) : Result()

        data class RestartFromZero(
            val reason: String,
            val offsetResolution: HttpResumePlanner.OffsetResolution
        ) : Result()

        data class Rejected(
            val reason: String,
            val offsetResolution: HttpResumePlanner.OffsetResolution
        ) : Result()

        data class HttpError(val responseCode: Int) : Result()

        data class IntegrityFailure(
            val responseInfo: ResponseInfo,
            val reason: String,
            val expectedBytes: Long,
            val actualBytes: Long
        ) : Result()
    }

    private companion object {
        const val RANGE_HEADER = "Range"
        const val CONTENT_RANGE_HEADER = "Content-Range"
        const val CONTENT_DISPOSITION_HEADER = "Content-Disposition"
        const val HTTP_OK = 200
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
