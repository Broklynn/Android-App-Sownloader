package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadMediaPostProcessorFastPathTest {
    @Test
    fun compatibleInputIsRemuxedReprobedAndUsedWithoutTranscode() = runBlocking {
        val input = nonEmptyTempFile("fast-path-compatible")
        val checker = QueueChecker(
            ProbeResponse.Decision(CarMediaCompatibilityDecision.Compatible),
            ProbeResponse.Decision(CarMediaCompatibilityDecision.Compatible)
        )
        val remuxer = RecordingRemuxer(SuccessMarker)
        val transcodeRunner = RecordingTranscodeRunner()

        val result = processor(checker, remuxer, transcodeRunner).processCar(input)

        assertTrue(result is DownloadMediaPostProcessor.Result.Processed)
        result as DownloadMediaPostProcessor.Result.Processed
        assertEquals(DownloadMediaPostProcessor.ProcessingRoute.REMUX, result.route)
        assertEquals(1, remuxer.callCount)
        assertEquals(0, transcodeRunner.callCount)
        assertEquals(2, checker.calls.size)
        assertFalse(checker.calls.first().requireMp4Container)
        assertTrue(checker.calls.last().requireMp4Container)
        assertEquals(remuxer.lastOutput?.canonicalPath, checker.calls.last().file.canonicalPath)
        assertTrue(input.exists())
    }

    @Test
    fun compatibleNonMp4VideoContainerCanEnterRemuxPath() = runBlocking {
        val input = File.createTempFile("fast-path-webm", ".webm").apply {
            writeBytes(byteArrayOf(9, 8, 7))
            deleteOnExit()
        }
        val checker = QueueChecker(
            ProbeResponse.Decision(CarMediaCompatibilityDecision.Compatible),
            ProbeResponse.Decision(CarMediaCompatibilityDecision.Compatible)
        )
        val remuxer = RecordingRemuxer(SuccessMarker)
        val transcodeRunner = RecordingTranscodeRunner()

        val result = processor(checker, remuxer, transcodeRunner).process(
            inputFile = input,
            preferredName = "Video.webm",
            mimeType = "video/webm",
            qualitySelector = VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P
        )

        assertEquals(
            DownloadMediaPostProcessor.ProcessingRoute.REMUX,
            (result as DownloadMediaPostProcessor.Result.Processed).route
        )
        assertEquals("Video - carro.mp4", result.preferredName)
        assertEquals("video/mp4", result.mimeType)
        assertEquals(0, transcodeRunner.callCount)
    }

    @Test
    fun incompatibleOrFailedInputProbeUsesTranscodeWithoutRemux() = runBlocking {
        val decisions = listOf(
            CarMediaCompatibilityDecision.Incompatible(
                listOf(CarMediaIncompatibilityReason.VIDEO_CODEC)
            ),
            CarMediaCompatibilityDecision.ProbeFailed(
                CarMediaProbeFailureReason.PROCESS_FAILURE
            ),
            CarMediaCompatibilityDecision.ProbeFailed(
                CarMediaProbeFailureReason.TIMEOUT
            )
        )

        decisions.forEach { decision ->
            val input = nonEmptyTempFile("fast-path-fallback")
            val checker = QueueChecker(ProbeResponse.Decision(decision))
            val remuxer = RecordingRemuxer(SuccessMarker)
            val transcodeRunner = RecordingTranscodeRunner()

            val result = processor(checker, remuxer, transcodeRunner).processCar(input)

            assertEquals(
                DownloadMediaPostProcessor.ProcessingRoute.TRANSCODE,
                (result as DownloadMediaPostProcessor.Result.Processed).route
            )
            assertEquals(0, remuxer.callCount)
            assertEquals(1, transcodeRunner.callCount)
            assertTrue(input.exists())
        }
    }

    @Test
    fun unexpectedInputProbeExceptionAlsoUsesTranscode() = runBlocking {
        val input = nonEmptyTempFile("probe-exception")
        val checker = QueueChecker(ProbeResponse.Failure(IllegalStateException("probe failed")))
        val remuxer = RecordingRemuxer(SuccessMarker)
        val transcodeRunner = RecordingTranscodeRunner()

        val result = processor(checker, remuxer, transcodeRunner).processCar(input)

        assertEquals(
            DownloadMediaPostProcessor.ProcessingRoute.TRANSCODE,
            (result as DownloadMediaPostProcessor.Result.Processed).route
        )
        assertEquals(0, remuxer.callCount)
        assertEquals(1, transcodeRunner.callCount)
    }

    @Test
    fun remuxFailureOrTimeoutRemovesPartialBeforeTranscode() = runBlocking {
        val outcomes = listOf(
            CarMediaRemuxResult.Failure("remux failed"),
            CarMediaRemuxResult.TimedOut(500L)
        )

        outcomes.forEach { outcome ->
            val input = nonEmptyTempFile("remux-fallback")
            val checker = QueueChecker(
                ProbeResponse.Decision(CarMediaCompatibilityDecision.Compatible)
            )
            val remuxer = RecordingRemuxer(
                outcome = outcome,
                writePartialBeforeResult = true
            )
            val transcodeRunner = RecordingTranscodeRunner()

            val result = processor(checker, remuxer, transcodeRunner).processCar(input)

            assertEquals(
                DownloadMediaPostProcessor.ProcessingRoute.TRANSCODE,
                (result as DownloadMediaPostProcessor.Result.Processed).route
            )
            assertFalse(transcodeRunner.outputExistedBeforeRun)
            assertEquals(1, transcodeRunner.callCount)
            assertTrue(input.exists())
        }
    }

    @Test
    fun cleanupIsIdempotentWhenFailedRemuxCreatedNoOutput() = runBlocking {
        val input = nonEmptyTempFile("idempotent-cleanup")
        val checker = QueueChecker(
            ProbeResponse.Decision(CarMediaCompatibilityDecision.Compatible)
        )
        val remuxer = RecordingRemuxer(
            outcome = CarMediaRemuxResult.Failure("failed before output"),
            writePartialBeforeResult = false
        )
        val transcodeRunner = RecordingTranscodeRunner()

        val result = processor(checker, remuxer, transcodeRunner).processCar(input)

        assertEquals(
            DownloadMediaPostProcessor.ProcessingRoute.TRANSCODE,
            (result as DownloadMediaPostProcessor.Result.Processed).route
        )
        assertFalse(transcodeRunner.outputExistedBeforeRun)
        assertTrue(input.exists())
    }

    @Test
    fun invalidOrFailedFinalProbeRemovesRemuxOutputBeforeTranscode() = runBlocking {
        val finalDecisions = listOf(
            CarMediaCompatibilityDecision.Incompatible(
                listOf(CarMediaIncompatibilityReason.OUTPUT_CONTAINER)
            ),
            CarMediaCompatibilityDecision.ProbeFailed(
                CarMediaProbeFailureReason.MALFORMED_OUTPUT
            ),
            CarMediaCompatibilityDecision.ProbeFailed(
                CarMediaProbeFailureReason.TIMEOUT
            )
        )

        finalDecisions.forEach { finalDecision ->
            val input = nonEmptyTempFile("final-probe-fallback")
            val checker = QueueChecker(
                ProbeResponse.Decision(CarMediaCompatibilityDecision.Compatible),
                ProbeResponse.Decision(finalDecision)
            )
            val remuxer = RecordingRemuxer(SuccessMarker)
            val transcodeRunner = RecordingTranscodeRunner()

            val result = processor(checker, remuxer, transcodeRunner).processCar(input)

            assertEquals(
                DownloadMediaPostProcessor.ProcessingRoute.TRANSCODE,
                (result as DownloadMediaPostProcessor.Result.Processed).route
            )
            assertFalse(transcodeRunner.outputExistedBeforeRun)
            assertEquals(2, checker.calls.size)
            assertEquals(1, transcodeRunner.callCount)
        }
    }

    @Test
    fun zeroLengthOrMissingRemuxOutputIsNeverUsedAsSuccess() = runBlocking {
        val input = nonEmptyTempFile("missing-remux-output")
        val checker = QueueChecker(
            ProbeResponse.Decision(CarMediaCompatibilityDecision.Compatible),
            ProbeResponse.Decision(CarMediaCompatibilityDecision.Compatible)
        )
        val remuxer = RecordingRemuxer(
            outcome = SuccessMarker,
            createOutputOnSuccess = false
        )
        val transcodeRunner = RecordingTranscodeRunner()

        val result = processor(checker, remuxer, transcodeRunner).processCar(input)

        assertEquals(
            DownloadMediaPostProcessor.ProcessingRoute.TRANSCODE,
            (result as DownloadMediaPostProcessor.Result.Processed).route
        )
        assertEquals(1, transcodeRunner.callCount)
    }

    @Test
    fun cancellationDuringInputProbePropagatesWithoutStartingOtherRoutes() = runBlocking {
        val input = nonEmptyTempFile("cancel-input-probe")
        val checker = QueueChecker(ProbeResponse.Cancel("input cancelled"))
        val remuxer = RecordingRemuxer(SuccessMarker)
        val transcodeRunner = RecordingTranscodeRunner()

        val cancellation = catchCancellation {
            processor(checker, remuxer, transcodeRunner).processCar(input)
        }

        assertEquals("input cancelled", cancellation.message)
        assertEquals(0, remuxer.callCount)
        assertEquals(0, transcodeRunner.callCount)
        assertTrue(input.exists())
    }

    @Test
    fun cancellationDuringRemuxRemovesPartialAndNeverTranscodes() = runBlocking {
        val input = nonEmptyTempFile("cancel-remux")
        val checker = QueueChecker(
            ProbeResponse.Decision(CarMediaCompatibilityDecision.Compatible)
        )
        val remuxer = RecordingRemuxer(
            outcome = SuccessMarker,
            cancellationMessage = "remux cancelled",
            writePartialBeforeResult = true
        )
        val transcodeRunner = RecordingTranscodeRunner()

        val cancellation = catchCancellation {
            processor(checker, remuxer, transcodeRunner).processCar(input)
        }

        assertEquals("remux cancelled", cancellation.message)
        assertFalse(remuxer.lastOutput?.exists() == true)
        assertEquals(0, transcodeRunner.callCount)
        assertTrue(input.exists())
    }

    @Test
    fun cancellationDuringFinalProbeRemovesUnapprovedOutputAndNeverTranscodes() = runBlocking {
        val input = nonEmptyTempFile("cancel-final-probe")
        val checker = QueueChecker(
            ProbeResponse.Decision(CarMediaCompatibilityDecision.Compatible),
            ProbeResponse.Cancel("final probe cancelled")
        )
        val remuxer = RecordingRemuxer(SuccessMarker)
        val transcodeRunner = RecordingTranscodeRunner()

        val cancellation = catchCancellation {
            processor(checker, remuxer, transcodeRunner).processCar(input)
        }

        assertEquals("final probe cancelled", cancellation.message)
        assertFalse(remuxer.lastOutput?.exists() == true)
        assertEquals(0, transcodeRunner.callCount)
        assertTrue(input.exists())
    }

    @Test
    fun cancellationDuringTranscodeKeepsExistingSemantics() = runBlocking {
        val input = nonEmptyTempFile("cancel-transcode")
        val checker = QueueChecker(
            ProbeResponse.Decision(
                CarMediaCompatibilityDecision.Incompatible(
                    listOf(CarMediaIncompatibilityReason.VIDEO_CODEC)
                )
            )
        )
        val transcodeRunner = RecordingTranscodeRunner(cancellationMessage = "transcode cancelled")

        val cancellation = catchCancellation {
            processor(
                checker,
                RecordingRemuxer(SuccessMarker),
                transcodeRunner
            ).processCar(input)
        }

        assertEquals("transcode cancelled", cancellation.message)
        assertEquals(1, transcodeRunner.callCount)
        assertTrue(input.exists())
    }

    @Test
    fun ordinaryTranscodeFailureStillFallsBackToOriginal() = runBlocking {
        val input = nonEmptyTempFile("transcode-failure")
        val checker = QueueChecker(
            ProbeResponse.Decision(
                CarMediaCompatibilityDecision.Incompatible(
                    listOf(CarMediaIncompatibilityReason.VIDEO_CODEC)
                )
            )
        )
        val transcodeRunner = RecordingTranscodeRunner(failureMessage = "encoder unavailable")

        val result = processor(
            checker,
            RecordingRemuxer(SuccessMarker),
            transcodeRunner
        ).processCar(input)

        assertTrue(result is DownloadMediaPostProcessor.Result.Fallback)
        assertEquals(input.canonicalPath, result.file.canonicalPath)
        assertEquals(
            "encoder unavailable",
            (result as DownloadMediaPostProcessor.Result.Fallback).reason
        )
    }

    @Test
    fun normalSelectorDoesNotProbeRemuxOrTranscode() = runBlocking {
        val input = nonEmptyTempFile("normal-selector")
        val checker = QueueChecker(ProbeResponse.Cancel("must not probe"))
        val remuxer = RecordingRemuxer(SuccessMarker)
        val transcodeRunner = RecordingTranscodeRunner()
        val processor = processor(checker, remuxer, transcodeRunner)

        val result = processor.process(
            inputFile = input,
            preferredName = "Video.mp4",
            mimeType = "video/mp4",
            qualitySelector = "best[height<=720]"
        )

        assertTrue(result is DownloadMediaPostProcessor.Result.Original)
        assertTrue(checker.calls.isEmpty())
        assertEquals(0, remuxer.callCount)
        assertEquals(0, transcodeRunner.callCount)
    }

    private fun processor(
        checker: CarMediaCompatibilityChecker,
        remuxer: CarMediaRemuxer,
        transcodeRunner: FfmpegCommandRunner
    ): DownloadMediaPostProcessor {
        return DownloadMediaPostProcessor(
            carCompatibilityTranscoder = CarCompatibilityTranscoder(transcodeRunner),
            compatibilityChecker = checker,
            carCompatibleRemuxer = remuxer
        )
    }

    private suspend fun DownloadMediaPostProcessor.processCar(
        input: File
    ): DownloadMediaPostProcessor.Result {
        return process(
            inputFile = input,
            preferredName = "Video.mp4",
            mimeType = "video/mp4",
            qualitySelector = VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P
        )
    }

    private suspend fun catchCancellation(
        block: suspend () -> Unit
    ): CancellationException {
        try {
            block()
        } catch (exception: CancellationException) {
            return exception
        }
        error("Expected CancellationException")
    }

    private fun nonEmptyTempFile(prefix: String): File {
        return File.createTempFile(prefix, ".mp4").apply {
            writeBytes(byteArrayOf(9, 8, 7))
            deleteOnExit()
        }
    }

    private sealed interface ProbeResponse {
        data class Decision(
            val decision: CarMediaCompatibilityDecision
        ) : ProbeResponse

        data class Cancel(val message: String) : ProbeResponse
        data class Failure(val exception: Exception) : ProbeResponse
    }

    private data class ProbeCall(
        val file: File,
        val requireMp4Container: Boolean
    )

    private class QueueChecker(
        vararg responses: ProbeResponse
    ) : CarMediaCompatibilityChecker {
        private val responses = ArrayDeque(responses.toList())
        val calls = mutableListOf<ProbeCall>()

        override suspend fun inspect(
            inputFile: File,
            profile: VideoCompatibilityProfile,
            requireMp4Container: Boolean
        ): CarMediaCompatibilityDecision {
            calls += ProbeCall(inputFile, requireMp4Container)
            return when (val response = responses.removeFirst()) {
                is ProbeResponse.Decision -> response.decision
                is ProbeResponse.Cancel -> throw CancellationException(response.message)
                is ProbeResponse.Failure -> throw response.exception
            }
        }
    }

    private class RecordingRemuxer(
        private val outcome: Any,
        private val createOutputOnSuccess: Boolean = true,
        private val writePartialBeforeResult: Boolean = false,
        private val cancellationMessage: String? = null
    ) : CarMediaRemuxer {
        var callCount = 0
            private set
        var lastOutput: File? = null
            private set

        override suspend fun remux(inputFile: File, outputFile: File): CarMediaRemuxResult {
            callCount++
            lastOutput = outputFile
            if (writePartialBeforeResult) {
                outputFile.writeBytes(byteArrayOf(1))
            }
            cancellationMessage?.let { throw CancellationException(it) }
            return if (outcome === SuccessMarker) {
                if (createOutputOnSuccess) {
                    outputFile.writeBytes(byteArrayOf(1, 2, 3))
                }
                CarMediaRemuxResult.Success(outputFile)
            } else {
                outcome as CarMediaRemuxResult
            }
        }
    }

    private class RecordingTranscodeRunner(
        private val cancellationMessage: String? = null,
        private val failureMessage: String? = null
    ) : FfmpegCommandRunner {
        var callCount = 0
            private set
        var outputExistedBeforeRun = false
            private set

        override suspend fun run(arguments: List<String>): FfmpegExecutionResult {
            callCount++
            val output = File(arguments.last())
            outputExistedBeforeRun = output.exists()
            cancellationMessage?.let { throw CancellationException(it) }
            failureMessage?.let { return FfmpegExecutionResult.Failure(it) }
            output.writeBytes(byteArrayOf(4, 5, 6))
            return FfmpegExecutionResult.Success()
        }
    }

    private data object SuccessMarker
}
