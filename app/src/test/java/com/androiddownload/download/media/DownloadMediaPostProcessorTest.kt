package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadMediaPostProcessorTest {
    @Test
    fun normalSelectorKeepsOriginalFileWithoutCallingTranscoder() {
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
    fun carCompatibleSelectorReturnsProcessedFileWithFriendlyName() {
        val input = nonEmptyTempFile("car-selector", ".mp4")
        val runner = RecordingRunner { arguments ->
            File(arguments.last()).writeBytes(byteArrayOf(1, 2, 3))
            FfmpegCommandResult.Success
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
        assertTrue(runner.wasCalled)
    }

    @Test
    fun carCompatibleSelectorDoesNotProcessNonMp4Output() {
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
    fun carCompatibleFailureFallsBackToOriginalFile() {
        val input = nonEmptyTempFile("car-fallback", ".mp4")
        val runner = RecordingRunner {
            FfmpegCommandResult.Failure("encoder unavailable")
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

    private fun processor(runner: FfmpegCommandRunner): DownloadMediaPostProcessor {
        return DownloadMediaPostProcessor(CarCompatibilityTranscoder(runner))
    }

    private fun nonEmptyTempFile(prefix: String, suffix: String): File {
        return File.createTempFile(prefix, suffix).apply {
            writeBytes(byteArrayOf(1))
            deleteOnExit()
        }
    }

    private class RecordingRunner(
        private val resultProvider: (List<String>) -> FfmpegCommandResult = {
            FfmpegCommandResult.Success
        }
    ) : FfmpegCommandRunner {
        var wasCalled = false
            private set

        override fun run(arguments: List<String>): FfmpegCommandResult {
            wasCalled = true
            return resultProvider(arguments)
        }
    }
}
