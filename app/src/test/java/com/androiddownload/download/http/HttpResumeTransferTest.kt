package com.androiddownload.download.http

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class HttpResumeTransferTest {
    @Test(timeout = TEST_TIMEOUT_MS)
    fun roomBehindPartUsesPartOffsetAndProducesExactFile() {
        val remoteContent = knownContent()
        val execution = executeTransfer(
            initialPart = remoteContent.copyOfRange(0, PART_SIZE),
            persistedDownloadedBytes = 800L,
            expectedTotalBytes = remoteContent.size.toLong()
        ) { request ->
            rangeResponse(request, remoteContent)
        }

        assertEquals("bytes=$PART_SIZE-", execution.request.getHeader("Range"))
        assertTrue(execution.result is HttpResumeTransfer.Result.Transferred)
        assertEquals(remoteContent.size.toLong(), execution.fileBytes.size.toLong())
        assertEquals(sha256(remoteContent), sha256(execution.fileBytes))
        assertArrayEquals(remoteContent, execution.fileBytes)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun matchingPartialContentAppendsAtRequestedOffset() {
        val remoteContent = knownContent()
        val execution = executeTransfer(
            initialPart = remoteContent.copyOfRange(0, PART_SIZE),
            persistedDownloadedBytes = PART_SIZE.toLong(),
            expectedTotalBytes = remoteContent.size.toLong()
        ) { request ->
            rangeResponse(request, remoteContent)
        }

        val transferred = execution.result as HttpResumeTransfer.Result.Transferred
        assertEquals(PART_SIZE.toLong(), transferred.responseInfo.writeOffset)
        assertEquals(remoteContent.size.toLong(), transferred.responseInfo.trustedTotalBytes)
        assertArrayEquals(remoteContent, execution.fileBytes)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun serverIgnoringRangeOverwritesFromZero() {
        val remoteContent = knownContent()
        val execution = executeTransfer(
            initialPart = ByteArray(PART_SIZE) { 0x5A },
            persistedDownloadedBytes = 800L,
            expectedTotalBytes = remoteContent.size.toLong()
        ) {
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(remoteContent))
        }

        val transferred = execution.result as HttpResumeTransfer.Result.Transferred
        assertEquals("bytes=$PART_SIZE-", execution.request.getHeader("Range"))
        assertEquals(0L, transferred.responseInfo.writeOffset)
        assertEquals(remoteContent.size.toLong(), transferred.responseInfo.trustedTotalBytes)
        assertArrayEquals(remoteContent, execution.fileBytes)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun partialContentWithWrongStartDoesNotAppend() {
        val remoteContent = knownContent()
        val initialPart = remoteContent.copyOfRange(0, PART_SIZE)
        val wrongStart = PART_SIZE - 100
        val execution = executeTransfer(
            initialPart = initialPart,
            persistedDownloadedBytes = 800L,
            expectedTotalBytes = remoteContent.size.toLong()
        ) {
            MockResponse()
                .setResponseCode(206)
                .setHeader(
                    "Content-Range",
                    "bytes $wrongStart-${remoteContent.lastIndex}/${remoteContent.size}"
                )
                .setBody(Buffer().write(remoteContent.copyOfRange(wrongStart, remoteContent.size)))
        }

        assertTrue(execution.result is HttpResumeTransfer.Result.RestartFromZero)
        assertArrayEquals(initialPart, execution.fileBytes)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun rangeNotSatisfiableRequestsControlledRestartWithoutWriting() {
        val remoteContent = knownContent()
        val initialPart = remoteContent.copyOfRange(0, PART_SIZE)
        val execution = executeTransfer(
            initialPart = initialPart,
            persistedDownloadedBytes = 800L,
            expectedTotalBytes = remoteContent.size.toLong()
        ) {
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */${remoteContent.size}")
                .setBody("")
        }

        assertTrue(execution.result is HttpResumeTransfer.Result.RestartFromZero)
        assertArrayEquals(initialPart, execution.fileBytes)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun truncatedChunkedPartialBodyFailsIntegrityValidation() {
        val remoteContent = knownContent()
        val truncatedBody = remoteContent.copyOfRange(PART_SIZE, PART_SIZE + 1_500)
        val execution = executeTransfer(
            initialPart = remoteContent.copyOfRange(0, PART_SIZE),
            persistedDownloadedBytes = 800L,
            expectedTotalBytes = remoteContent.size.toLong()
        ) {
            MockResponse()
                .setResponseCode(206)
                .setHeader(
                    "Content-Range",
                    "bytes $PART_SIZE-${remoteContent.lastIndex}/${remoteContent.size}"
                )
                .setChunkedBody(Buffer().write(truncatedBody), CHUNK_SIZE)
        }

        val failure = execution.result as HttpResumeTransfer.Result.IntegrityFailure
        assertEquals((remoteContent.size - PART_SIZE).toLong(), failure.expectedBytes)
        assertEquals(truncatedBody.size.toLong(), failure.actualBytes)
        assertEquals(PART_SIZE + truncatedBody.size, execution.fileBytes.size)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun partialBodyLargerThanDeclaredRangeFailsIntegrityValidation() {
        val remoteContent = knownContent()
        val declaredLength = 1_000
        val actualBody = remoteContent.copyOfRange(PART_SIZE, PART_SIZE + declaredLength + 200)
        val execution = executeTransfer(
            initialPart = remoteContent.copyOfRange(0, PART_SIZE),
            persistedDownloadedBytes = PART_SIZE.toLong(),
            expectedTotalBytes = remoteContent.size.toLong()
        ) {
            MockResponse()
                .setResponseCode(206)
                .setHeader(
                    "Content-Range",
                    "bytes $PART_SIZE-${PART_SIZE + declaredLength - 1}/${remoteContent.size}"
                )
                .setChunkedBody(Buffer().write(actualBody), CHUNK_SIZE)
        }

        val failure = execution.result as HttpResumeTransfer.Result.IntegrityFailure
        assertEquals(declaredLength.toLong(), failure.expectedBytes)
        assertEquals(actualBody.size.toLong(), failure.actualBytes)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun finalFileSizeDifferentFromValidatedTotalFailsIntegrityValidation() {
        val remoteContent = knownContent()
        val partialEnd = PART_SIZE + 999
        val responseBody = remoteContent.copyOfRange(PART_SIZE, partialEnd + 1)
        val execution = executeTransfer(
            initialPart = remoteContent.copyOfRange(0, PART_SIZE),
            persistedDownloadedBytes = PART_SIZE.toLong(),
            expectedTotalBytes = remoteContent.size.toLong()
        ) {
            MockResponse()
                .setResponseCode(206)
                .setHeader(
                    "Content-Range",
                    "bytes $PART_SIZE-$partialEnd/${remoteContent.size}"
                )
                .setChunkedBody(Buffer().write(responseBody), CHUNK_SIZE)
        }

        val failure = execution.result as HttpResumeTransfer.Result.IntegrityFailure
        assertEquals(remoteContent.size.toLong(), failure.expectedBytes)
        assertEquals((PART_SIZE + responseBody.size).toLong(), failure.actualBytes)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun fullResponseWithKnownContentLengthValidatesAndCompletes() {
        val remoteContent = knownContent()
        val execution = executeTransfer(
            initialPart = ByteArray(0),
            persistedDownloadedBytes = 0L,
            expectedTotalBytes = 0L,
            resumeAllowed = false
        ) {
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(remoteContent))
        }

        val transferred = execution.result as HttpResumeTransfer.Result.Transferred
        assertEquals(remoteContent.size.toLong(), transferred.responseInfo.responseBodyLength)
        assertEquals(remoteContent.size.toLong(), transferred.responseInfo.trustedTotalBytes)
        assertEquals(remoteContent.size.toLong(), transferred.finalFileLength)
        assertArrayEquals(remoteContent, execution.fileBytes)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun chunkedFullResponseDoesNotInventTrustedTotal() {
        val remoteContent = knownContent()
        val execution = executeTransfer(
            initialPart = ByteArray(PART_SIZE) { 0x5A },
            persistedDownloadedBytes = PART_SIZE.toLong(),
            expectedTotalBytes = remoteContent.size.toLong()
        ) {
            MockResponse()
                .setResponseCode(200)
                .setChunkedBody(Buffer().write(remoteContent), CHUNK_SIZE)
        }

        val transferred = execution.result as HttpResumeTransfer.Result.Transferred
        assertEquals(-1L, transferred.responseInfo.responseBodyLength)
        assertNull(transferred.responseInfo.trustedTotalBytes)
        assertEquals(remoteContent.size.toLong(), transferred.finalFileLength)
        assertArrayEquals(remoteContent, execution.fileBytes)
    }

    private fun executeTransfer(
        initialPart: ByteArray,
        persistedDownloadedBytes: Long,
        expectedTotalBytes: Long,
        resumeAllowed: Boolean = true,
        responseFactory: (RecordedRequest) -> MockResponse
    ): Execution {
        val directory = Files.createTempDirectory("darkwave-http-transfer").toFile()
        return try {
            val partFile = File(directory, "download.part").apply {
                writeBytes(initialPart)
            }
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        return responseFactory(request)
                    }
                }
                server.start()
                val transfer = HttpResumeTransfer(OkHttpClient())
                val result = runBlocking {
                    transfer.execute(
                        baseRequest = Request.Builder()
                            .url(server.url("/file.bin"))
                            .get()
                            .build(),
                        tempFile = partFile,
                        persistedDownloadedBytes = persistedDownloadedBytes,
                        resumeAllowed = resumeAllowed,
                        expectedTotalBytes = expectedTotalBytes,
                        canRestartFromZero = true
                    )
                }
                val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)) {
                    "MockWebServer nao recebeu a requisicao esperada."
                }
                Execution(
                    result = result,
                    request = request,
                    fileBytes = partFile.readBytes()
                )
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun rangeResponse(request: RecordedRequest, content: ByteArray): MockResponse {
        val start = request.getHeader("Range")
            ?.removePrefix("bytes=")
            ?.substringBefore('-')
            ?.toIntOrNull()
            ?: 0
        if (start >= content.size) {
            return MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */${content.size}")
                .setBody("")
        }
        val body = content.copyOfRange(start, content.size)
        return MockResponse()
            .setResponseCode(if (start > 0) 206 else 200)
            .apply {
                if (start > 0) {
                    setHeader(
                        "Content-Range",
                        "bytes $start-${content.lastIndex}/${content.size}"
                    )
                }
            }
            .setBody(Buffer().write(body))
    }

    private fun knownContent(): ByteArray {
        return ByteArray(4_096) { index -> ((index * 31) % 251).toByte() }
    }

    private fun sha256(value: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value)
            .joinToString("") { "%02x".format(it) }
    }

    private data class Execution(
        val result: HttpResumeTransfer.Result,
        val request: RecordedRequest,
        val fileBytes: ByteArray
    )

    private companion object {
        const val PART_SIZE = 1_000
        const val CHUNK_SIZE = 128
        const val TEST_TIMEOUT_MS = 10_000L
    }
}
