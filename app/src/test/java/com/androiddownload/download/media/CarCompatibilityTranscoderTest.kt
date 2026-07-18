package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CarCompatibilityTranscoderTest {
    @Test
    fun missingInputIsSkipped() = runBlocking {
        val runner = RecordingRunner()
        val result = transcoder(runner).transcode(
            inputFile = tempPath("missing-input.mp4"),
            outputFile = tempPath("missing-output.mp4"),
            profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
        )

        assertTrue(result is CarCompatibilityTranscoder.TranscodeResult.Skipped)
        assertFalse(runner.wasCalled)
    }

    @Test
    fun emptyInputIsSkipped() = runBlocking {
        val input = File.createTempFile("empty-input", ".mp4")
        val runner = RecordingRunner()

        val result = transcoder(runner).transcode(
            inputFile = input,
            outputFile = tempPath("empty-output.mp4"),
            profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
        )

        assertTrue(result is CarCompatibilityTranscoder.TranscodeResult.Skipped)
        assertFalse(runner.wasCalled)
    }

    @Test
    fun sameInputAndOutputIsSkipped() = runBlocking {
        val input = nonEmptyTempFile("same-file-input", ".mp4")
        val runner = RecordingRunner()

        val result = transcoder(runner).transcode(
            inputFile = input,
            outputFile = input,
            profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
        )

        assertTrue(result is CarCompatibilityTranscoder.TranscodeResult.Skipped)
        assertFalse(runner.wasCalled)
    }

    @Test
    fun commandContainsCarCompatibilityVideoOptions() = runBlocking {
        val input = nonEmptyTempFile("command-input", ".mp4")
        val output = tempPath("command-output.mp4")
        val runner = RecordingRunner()

        transcoder(runner).transcode(
            inputFile = input,
            outputFile = output,
            profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
        )

        val command = runner.lastArguments
        assertContainsSequence(command, "-c:v", "libx264")
        assertContainsSequence(command, "-profile:v", "baseline")
        assertContainsSequence(command, "-level", "3.1")
        assertContainsSequence(command, "-movflags", "+faststart")
        assertTrue(command.optionValue("-vf")?.contains("fps=30") == true)
        assertTrue(command.optionValue("-vf")?.contains("format=yuv420p") == true)
    }

    @Test
    fun commandConstrainsPortraitVideoTo1280By720BoxWithoutUpscaling() = runBlocking {
        val command = transcoder(RecordingRunner()).buildCommand(
            inputFile = tempPath("portrait-input.mp4"),
            outputFile = tempPath("portrait-output.mp4"),
            profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
        )

        assertEquals(
            "scale='min(1280,iw)':'min(720,ih)':" +
                "force_original_aspect_ratio=decrease:force_divisible_by=2," +
                "fps=30,format=yuv420p",
            command.optionValue("-vf")
        )
    }

    @Test
    fun commandContainsCarCompatibilityAudioOptions() = runBlocking {
        val input = nonEmptyTempFile("audio-input", ".mp4")
        val output = tempPath("audio-output.mp4")
        val runner = RecordingRunner()

        transcoder(runner).transcode(
            inputFile = input,
            outputFile = output,
            profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
        )

        val command = runner.lastArguments
        assertContainsSequence(command, "-c:a", "aac")
        assertContainsSequence(command, "-b:a", "192k")
    }

    @Test
    fun successReturnsExpectedOutputFile() = runBlocking {
        val input = nonEmptyTempFile("success-input", ".mp4")
        val output = tempPath("success-output.mp4")
        val runner = RecordingRunner { arguments ->
            File(arguments.last()).writeBytes(byteArrayOf(4, 2))
            FfmpegExecutionResult.Success()
        }

        val result = transcoder(runner).transcode(
            inputFile = input,
            outputFile = output,
            profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
        )

        assertEquals(
            output.canonicalPath,
            (result as CarCompatibilityTranscoder.TranscodeResult.Success).outputFile.canonicalPath
        )
    }

    @Test
    fun failedRunnerReturnsFailureWithoutCrash() = runBlocking {
        val input = nonEmptyTempFile("failure-input", ".mp4")
        val output = tempPath("failure-output.mp4")
        val runner = RecordingRunner {
            FfmpegExecutionResult.Failure("encoder unavailable")
        }

        val result = transcoder(runner).transcode(
            inputFile = input,
            outputFile = output,
            profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
        )

        assertTrue(result is CarCompatibilityTranscoder.TranscodeResult.Failure)
        assertEquals("encoder unavailable", (result as CarCompatibilityTranscoder.TranscodeResult.Failure).message)
    }

    @Test
    fun throwingRunnerReturnsFailureWithoutCrash() = runBlocking {
        val input = nonEmptyTempFile("throwing-input", ".mp4")
        val output = tempPath("throwing-output.mp4")
        val runner = RecordingRunner {
            throw IllegalStateException("boom")
        }

        val result = transcoder(runner).transcode(
            inputFile = input,
            outputFile = output,
            profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
        )

        assertTrue(result is CarCompatibilityTranscoder.TranscodeResult.Failure)
        assertEquals("boom", (result as CarCompatibilityTranscoder.TranscodeResult.Failure).message)
    }

    @Test
    fun canceledRunnerPropagatesCancellationException() = runBlocking {
        val input = nonEmptyTempFile("cancel-input", ".mp4")
        val runner = RecordingRunner {
            throw CancellationException("cancelled")
        }

        var cancellation: CancellationException? = null
        try {
            transcoder(runner).transcode(
                inputFile = input,
                outputFile = tempPath("cancel-output.mp4"),
                profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
            )
        } catch (exception: CancellationException) {
            cancellation = exception
        }

        assertEquals("cancelled", cancellation?.message)
    }

    @Test
    fun timedOutRunnerReturnsTimedOutResult() = runBlocking {
        val input = nonEmptyTempFile("timeout-input", ".mp4")
        val runner = RecordingRunner {
            FfmpegExecutionResult.TimedOut(
                timeoutMillis = 500L,
                diagnostics = FfmpegExecutionDiagnostics()
            )
        }

        val result = transcoder(runner).transcode(
            inputFile = input,
            outputFile = tempPath("timeout-output.mp4"),
            profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
        )

        assertEquals(
            500L,
            (result as CarCompatibilityTranscoder.TranscodeResult.TimedOut).timeoutMillis
        )
    }

    private fun transcoder(runner: FfmpegCommandRunner): CarCompatibilityTranscoder {
        return CarCompatibilityTranscoder(runner)
    }

    private fun nonEmptyTempFile(prefix: String, suffix: String): File {
        return File.createTempFile(prefix, suffix).apply {
            writeBytes(byteArrayOf(1))
            deleteOnExit()
        }
    }

    private fun tempPath(name: String): File {
        return File(System.getProperty("java.io.tmpdir"), "darkwave-test-$name-${System.nanoTime()}")
            .apply { deleteOnExit() }
    }

    private fun assertContainsSequence(arguments: List<String>, first: String, second: String) {
        val index = arguments.indexOf(first)
        assertTrue("$first not found", index >= 0)
        assertEquals(second, arguments.getOrNull(index + 1))
    }

    private fun List<String>.optionValue(option: String): String? {
        return getOrNull(indexOf(option) + 1)
    }

    private class RecordingRunner(
        private val resultProvider: (List<String>) -> FfmpegExecutionResult = { arguments ->
            File(arguments.last()).writeBytes(byteArrayOf(1))
            FfmpegExecutionResult.Success()
        }
    ) : FfmpegCommandRunner {
        var wasCalled = false
            private set
        var lastArguments: List<String> = emptyList()
            private set

        override suspend fun run(arguments: List<String>): FfmpegExecutionResult {
            wasCalled = true
            lastArguments = arguments
            return resultProvider(arguments)
        }
    }
}
