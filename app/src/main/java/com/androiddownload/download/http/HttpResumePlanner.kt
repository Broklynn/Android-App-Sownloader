package com.androiddownload.download.http

internal object HttpResumePlanner {
    fun resolveOffset(
        persistedDownloadedBytes: Long,
        tempFileExists: Boolean,
        tempFileLength: Long,
        resumeAllowed: Boolean
    ): OffsetResolution {
        val persistedOffset = persistedDownloadedBytes.coerceAtLeast(0L)
        val requestedOffset = if (resumeAllowed && tempFileExists) {
            tempFileLength.coerceAtLeast(0L)
        } else {
            0L
        }
        return OffsetResolution(
            requestedOffset = requestedOffset,
            persistedOffset = persistedOffset,
            reconciled = resumeAllowed &&
                tempFileExists &&
                persistedOffset != requestedOffset
        )
    }

    fun planResponse(
        requestedOffset: Long,
        responseCode: Int,
        contentRange: String?,
        canRestartFromZero: Boolean,
        responseBodyLength: Long = -1L,
        expectedTotalBytes: Long = -1L
    ): ResponsePlan {
        if (requestedOffset <= 0L) {
            return if (responseCode == HTTP_PARTIAL_CONTENT) {
                val range = parseContentRange(contentRange)
                val bodyLengthMatches = responseBodyLength < 0L ||
                    range?.length == responseBodyLength
                if (
                    range != null &&
                    range.start == 0L &&
                    range.coversWholeRepresentation() &&
                    bodyLengthMatches &&
                    totalMatchesExpected(range, expectedTotalBytes)
                ) {
                    ResponsePlan.WriteFromStart(range)
                } else {
                    ResponsePlan.Reject("Resposta parcial invalida para download iniciado do zero.")
                }
            } else {
                ResponsePlan.WriteFromStart()
            }
        }

        return when (responseCode) {
            HTTP_OK -> ResponsePlan.WriteFromStart()
            HTTP_PARTIAL_CONTENT -> {
                val range = parseContentRange(contentRange)
                val bodyLengthMatches = responseBodyLength < 0L ||
                    range?.length == responseBodyLength
                if (
                    range != null &&
                    range.start == requestedOffset &&
                    bodyLengthMatches &&
                    totalMatchesExpected(range, expectedTotalBytes)
                ) {
                    ResponsePlan.Append(range)
                } else {
                    restartOrReject(
                        canRestartFromZero,
                        "Content-Range incompativel com o offset solicitado."
                    )
                }
            }
            HTTP_RANGE_NOT_SATISFIABLE -> restartOrReject(
                canRestartFromZero,
                "Servidor rejeitou o offset de retomada."
            )
            else -> ResponsePlan.Reject("Resposta HTTP incompativel com retomada.")
        }
    }

    private fun totalMatchesExpected(range: ContentRange, expectedTotalBytes: Long): Boolean {
        if (expectedTotalBytes <= 0L) return true
        return range.total != null && range.total == expectedTotalBytes
    }

    private fun restartOrReject(canRestartFromZero: Boolean, reason: String): ResponsePlan {
        return if (canRestartFromZero) {
            ResponsePlan.RestartFromZero(reason)
        } else {
            ResponsePlan.Reject(reason)
        }
    }

    private fun parseContentRange(value: String?): ContentRange? {
        val match = value
            ?.trim()
            ?.let(CONTENT_RANGE_REGEX::matchEntire)
            ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].takeIf { it != "*" }?.toLongOrNull()
        if (end < start) return null
        if (total != null && total <= end) return null
        return ContentRange(start = start, end = end, total = total)
    }

    sealed class ResponsePlan {
        data class Append(val contentRange: ContentRange) : ResponsePlan()
        data class WriteFromStart(val contentRange: ContentRange? = null) : ResponsePlan()
        data class RestartFromZero(val reason: String) : ResponsePlan()
        data class Reject(val reason: String) : ResponsePlan()
    }

    data class ContentRange(
        val start: Long,
        val end: Long,
        val total: Long?
    ) {
        val length: Long
            get() = end - start + 1L

        fun coversWholeRepresentation(): Boolean {
            return start == 0L && total != null && end == total - 1L
        }
    }

    data class OffsetResolution(
        val requestedOffset: Long,
        val persistedOffset: Long,
        val reconciled: Boolean
    )

    private const val HTTP_OK = 200
    private const val HTTP_PARTIAL_CONTENT = 206
    private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    private val CONTENT_RANGE_REGEX = Regex(
        pattern = """bytes\s+(\d+)-(\d+)/(\d+|\*)""",
        option = RegexOption.IGNORE_CASE
    )
}
