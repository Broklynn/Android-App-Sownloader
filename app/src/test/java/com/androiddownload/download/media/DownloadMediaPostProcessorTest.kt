package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadMediaPostProcessorTest {
    @Test
    fun normalSelectorKeepsOriginalFileWithoutCallingTranscoder() = runBlocking {
        val input = nonEmptyTempFile("normal-selector", ".mp4")
        val runner = RecordingRunner()

        val result = processor(runner).process(
            inputFile = input,
            preferredName = "Video.mp4",
            mimeType = "video/mp4",
            qualitySelector = "best[height<=720]"
        )

        assertTrue(result is DownloadMediaPostProcessor.Result.Original)
        assertEquals(input.canonicalPath, result.file.canonicalPath)
        assertEquals("Video.mp4", result.preferredName)
        assertFalse(runner.wasCalled)
    }

    @Test
    fun carCompatibleSelectorReturnsProcessedFileWithFriendlyName() = runBlocking {
        val input = nonEmptyTempFile("car-selector", ".mp4")
        val runner = RecordingRunner { arguments ->
            File(arguments.last()).writeBytes(byteArrayOf(1, 2, 3))
            FfmpegExecutionResult.Success()
        }

        val result = processor(runner).process(
            inputFile = input,
            preferredName = "Video.mp4",
            mimeType = "video/mp4",
            qualitySelector = VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P
        )

        assertTrue(result is DownloadMediaPostProcessor.Result.Processed)
        assertEquals("Video - carro.mp4", result.preferredName)
        assertEquals("video/mp4", result.mimeType)
        assertTrue(result.file.name.startsWith("Video - carro"))
        assertTrue(result.file.length() > 0L)
        assertEquals(
            DownloadMediaPostProcessor.ProcessingRoute.TRANSCODE,
            (result as DownloadMediaPostProcessor.Result.Processed).route
        )
        assertTrue(runner.wasCalled)
    }

    @Test
    fun carCompatibleSelectorDoesNotProcessNonMp4Output() = runBlocking {
        val input = nonEmptyTempFile("car-audio", ".mp3")
        val runner = RecordingRunner()

        val result = processor(runner).process(
            inputFile = input,
            preferredName = "Audio.mp3",
            mimeType = "audio/mpeg",
            qualitySelector = VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P
        )

        assertTrue(result is DownloadMediaPostProcessor.Result.Original)
        assertEquals(input.canonicalPath, result.file.canonicalPath)
        assertFalse(runner.wasCalled)
    }

    @Test
    fun carCompatibleFailureFallsBackToOriginalFile() = runBlocking {
        val input = nonEmptyTempFile("car-fallback", ".mp4")
        val runner = RecordingRunner {
            FfmpegExecutionResult.Failure("encoder unavailable")
        }

        val result = processor(runner).process(
            inputFile = input,
            preferredName = "Video.mp4",
            mimeType = "video/mp4",
            qualitySelector = VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P
        )

        assertTrue(result is DownloadMediaPostProcessor.Result.Fallback)
        assertEquals(input.canonicalPath, result.file.canonicalPath)
        assertEquals("Video.mp4", result.preferredName)
        assertEquals("encoder unavailable", (result as DownloadMediaPostProcessor.Result.Fallback).reason)
    }

    @Test
    fun carCompatibleTimeoutFallsBackToOriginalFile() = runBlocking {
        val input = nonEmptyTempFile("car-timeout", ".mp4")
        val runner = RecordingRunner {
            FfmpegExecutionResult.TimedOut(
                timeoutMillis = 500L,
                diagnostics = FfmpegExecutionDiagnostics()
            )
        }

        val result = processor(runner).process(
            inputFile = input,
            preferredName = "Video.mp4",
            mimeType = "video/mp4",
            qualitySelector = VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P
        )

        assertTrue(result is DownloadMediaPostProcessor.Result.Fallback)
        assertEquals(input.canonicalPath, result.file.canonicalPath)
        assertEquals(
            "FFmpeg excedeu o tempo limite de execucao.",
            (result as DownloadMediaPostProcessor.Result.Fallback).reason
        )
    }

    @Test
    fun carCompatibleCancellationIsNotConvertedToFallback() = runBlocking {
        val input = nonEmptyTempFile("car-cancel", ".mp4")
        val runner = RecordingRunner {
            throw CancellationException("cancelled")
        }

        var cancellation: CancellationException? = null
        try {
            processor(runner).process(
                inputFile = input,
                preferredName = "Video.mp4",
                mimeType = "video/mp4",
                qualitySelector = VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P
            )
        } catch (exception: CancellationException) {
            cancellation = exception
        }

        assertEquals("cancelled", cancellation?.message)
    }

    private fun processor(runner: FfmpegCommandRunner): DownloadMediaPostProcessor {
        return DownloadMediaPostProcessor(
            carCompatibilityTranscoder = CarCompatibilityTranscoder(runner),
            compatibilityChecker = IncompatibleChecker,
            carCompatibleRemuxer = UnexpectedRemuxer
        )
    }

    private fun nonEmptyTempFile(prefix: String, suffix: String): File {
        return File.createTempFile(prefix, suffix).apply {
            writeBytes(byteArrayOf(1))
            deleteOnExit()
        }
    }

    private class RecordingRunner(
        private val resultProvider: (List<String>) -> FfmpegExecutionResult = {
            FfmpegExecutionResult.Success()
        }
    ) : FfmpegCommandRunner {
        var wasCalled = false
            private set

        override suspend fun run(arguments: List<String>): FfmpegExecutionResult {
            wasCalled = true
            return resultProvider(arguments)
        }
    }

    private object IncompatibleChecker : CarMediaCompatibilityChecker {
        override suspend fun inspect(
            inputFile: File,
            profile: VideoCompatibilityProfile,
            requireMp4Container: Boolean
        ): CarMediaCompatibilityDecision {
            return CarMediaCompatibilityDecision.Incompatible(
                listOf(CarMediaIncompatibilityReason.VIDEO_CODEC)
            )
        }
    }

    private object UnexpectedRemuxer : CarMediaRemuxer {
        override suspend fun remux(inputFile: File, outputFile: File): CarMediaRemuxResult {
            error("Remux must not be called for an incompatible probe.")
        }
    }
}
