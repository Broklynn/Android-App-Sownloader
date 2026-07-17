package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CarCompatibilityTranscoderTest {
    @Test
    fun missingInputIsSkipped() {
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
    fun emptyInputIsSkipped() {
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
    fun sameInputAndOutputIsSkipped() {
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
    fun commandContainsCarCompatibilityVideoOptions() {
        val input = nonEmptyTempFile("command-input", ".mp4")
        val output = tempPath("command-output.mp4")
        val runner = RecordingRunner()

        transcoder(runner).transcode(
            inputFile = input,
            outputFile = output,
            profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P
        )

        val command = runner.lastArguments.joinToString(" ")
        assertTrue(command.contains("libx264"))
        assertTrue(command.contains("yuv420p"))
        assertTrue(command.contains("fps=30"))
        assertTrue(command.contains("+faststart"))
    }

    @Test
    fun commandContainsCarCompatibilityAudioOptions() {
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
    fun successReturnsExpectedOutputFile() {
        val input = nonEmptyTempFile("success-input", ".mp4")
        val output = tempPath("success-output.mp4")
        val runner = RecordingRunner { arguments ->
            File(arguments.last()).writeBytes(byteArrayOf(4, 2))
            FfmpegCommandResult.Success
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
    fun failedRunnerReturnsFailureWithoutCrash() {
        val input = nonEmptyTempFile("failure-input", ".mp4")
        val output = tempPath("failure-output.mp4")
        val runner = RecordingRunner {
            FfmpegCommandResult.Failure("encoder unavailable")
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
    fun throwingRunnerReturnsFailureWithoutCrash() {
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

    private class RecordingRunner(
        private val resultProvider: (List<String>) -> FfmpegCommandResult = { arguments ->
            File(arguments.last()).writeBytes(byteArrayOf(1))
            FfmpegCommandResult.Success
        }
    ) : FfmpegCommandRunner {
        var wasCalled = false
            private set
        var lastArguments: List<String> = emptyList()
            private set

        override fun run(arguments: List<String>): FfmpegCommandResult {
            wasCalled = true
            lastArguments = arguments
            return resultProvider(arguments)
        }
    }
}
