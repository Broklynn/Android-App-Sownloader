package com.androiddownload.download.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CarCompatibleRemuxerTest {
    @Test
    fun commandCopiesExactlyOneVideoAndAudioIntoFaststartMp4() {
        val command = CarCompatibleRemuxer(RecordingRunner()).buildCommand(
            inputFile = File("/private/input.mkv"),
            outputFile = File("/private/output.mp4")
        )

        assertEquals("-y", command.first())
        assertContainsSequence(command, "-i", File("/private/input.mkv").absolutePath)
        assertContainsSequence(command, "-map", "0:v:0")
        assertContainsSequenceAfter(command, "-map", "0:a:0", startIndex = 4)
        assertContainsSequence(command, "-c", "copy")
        assertContainsSequence(command, "-map_metadata", "-1")
        assertContainsSequence(command, "-map_chapters", "-1")
        assertContainsSequence(command, "-movflags", "+faststart")
        assertEquals(File("/private/output.mp4").absolutePath, command.last())

        listOf("libx264", "aac", "-vf", "scale", "fps=30", "-crf", "-preset").forEach {
            assertFalse("$it must not be present", command.any { argument -> argument.contains(it) })
        }
    }

    @Test
    fun successfulRemuxReturnsSeparateNonEmptyOutputAndPreservesInput() = runBlocking {
        val input = nonEmptyTempFile("remux-input", ".mkv")
        val originalBytes = input.readBytes()
        val output = tempPath("remux-output.mp4")
        val runner = RecordingRunner { arguments ->
            File(arguments.last()).writeBytes(byteArrayOf(4, 2))
            FfmpegExecutionResult.Success()
        }

        val result = CarCompatibleRemuxer(runner).remux(input, output)

        assertTrue(result is CarMediaRemuxResult.Success)
        assertEquals(output.canonicalPath, (result as CarMediaRemuxResult.Success).outputFile.canonicalPath)
        assertTrue(output.length() > 0L)
        assertTrue(input.exists())
        assertTrue(originalBytes.contentEquals(input.readBytes()))
    }

    @Test
    fun runnerFailureRemovesPartialOutput() = runBlocking {
        val input = nonEmptyTempFile("remux-failure-input", ".mp4")
        val output = tempPath("remux-failure-output.mp4")
        val runner = RecordingRunner { arguments ->
            File(arguments.last()).writeBytes(byteArrayOf(1, 2, 3))
            FfmpegExecutionResult.Failure("remux failed")
        }

        val result = CarCompatibleRemuxer(runner).remux(input, output)

        assertTrue(result is CarMediaRemuxResult.Failure)
        assertFalse(output.exists())
        assertTrue(input.exists())
    }

    @Test
    fun timeoutRemovesPartialOutput() = runBlocking {
        val input = nonEmptyTempFile("remux-timeout-input", ".mp4")
        val output = tempPath("remux-timeout-output.mp4")
        val runner = RecordingRunner { arguments ->
            File(arguments.last()).writeBytes(byteArrayOf(1))
            FfmpegExecutionResult.TimedOut(
                timeoutMillis = 500L,
                diagnostics = FfmpegExecutionDiagnostics()
            )
        }

        val result = CarCompatibleRemuxer(runner).remux(input, output)

        assertEquals(500L, (result as CarMediaRemuxResult.TimedOut).timeoutMillis)
        assertFalse(output.exists())
        assertTrue(input.exists())
    }

    @Test
    fun cancellationRemovesPartialOutputAndPropagates() = runBlocking {
        val input = nonEmptyTempFile("remux-cancel-input", ".mp4")
        val output = tempPath("remux-cancel-output.mp4")
        val runner = RecordingRunner { arguments ->
            File(arguments.last()).writeBytes(byteArrayOf(1))
            throw CancellationException("cancelled")
        }

        var cancellation: CancellationException? = null
        try {
            CarCompatibleRemuxer(runner).remux(input, output)
        } catch (exception: CancellationException) {
            cancellation = exception
        }

        assertEquals("cancelled", cancellation?.message)
        assertFalse(output.exists())
        assertTrue(input.exists())
    }

    @Test
    fun zeroLengthOutputIsNeverReportedAsSuccess() = runBlocking {
        val input = nonEmptyTempFile("remux-empty-input", ".mp4")
        val output = tempPath("remux-empty-output.mp4")
        val runner = RecordingRunner { arguments ->
            File(arguments.last()).writeBytes(ByteArray(0))
            FfmpegExecutionResult.Success()
        }

        val result = CarCompatibleRemuxer(runner).remux(input, output)

        assertTrue(result is CarMediaRemuxResult.Failure)
        assertFalse(output.exists())
    }

    @Test
    fun sameInputOutputOrNonMp4OutputFailsBeforeStartingRunner() = runBlocking {
        val input = nonEmptyTempFile("remux-invalid-input", ".mp4")
        val runner = RecordingRunner()
        val remuxer = CarCompatibleRemuxer(runner)

        assertTrue(remuxer.remux(input, input) is CarMediaRemuxResult.Failure)
        assertTrue(
            remuxer.remux(input, tempPath("remux-output.mkv")) is CarMediaRemuxResult.Failure
        )
        assertFalse(runner.wasCalled)
        assertTrue(input.exists())
    }

    private fun nonEmptyTempFile(prefix: String, suffix: String): File {
        return File.createTempFile(prefix, suffix).apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
    }

    private fun tempPath(name: String): File {
        val baseName = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        val uniqueName = if (extension.isBlank()) {
            "darkwave-$baseName-${System.nanoTime()}"
        } else {
            "darkwave-$baseName-${System.nanoTime()}.$extension"
        }
        return File(System.getProperty("java.io.tmpdir"), uniqueName)
            .apply { deleteOnExit() }
    }

    private fun assertContainsSequence(arguments: List<String>, first: String, second: String) {
        val index = arguments.indexOf(first)
        assertTrue("$first not found", index >= 0)
        assertEquals(second, arguments.getOrNull(index + 1))
    }

    private fun assertContainsSequenceAfter(
        arguments: List<String>,
        first: String,
        second: String,
        startIndex: Int
    ) {
        val relativeIndex = arguments.drop(startIndex).indexOf(first)
        val index = if (relativeIndex >= 0) startIndex + relativeIndex else -1
        assertTrue("$first not found after $startIndex", index >= 0)
        assertEquals(second, arguments.getOrNull(index + 1))
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
}
