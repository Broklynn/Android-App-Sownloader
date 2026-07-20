package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CarMediaFastPathReproductionTest {
    @Test
    fun provenCompatibleInputUsesRemuxInsteadOfTranscode() = runBlocking {
        val input = nonEmptyTempFile("compatible-input", ".mp4")
        val transcodeRunner = RecordingFfmpegRunner()
        val checker = RecordingCompatibilityChecker(
            decisions = ArrayDeque(
                listOf(
                    CarMediaCompatibilityDecision.Compatible,
                    CarMediaCompatibilityDecision.Compatible
                )
            )
        )
        val remuxer = RecordingRemuxer()
        val processor = DownloadMediaPostProcessor(
            carCompatibilityTranscoder = CarCompatibilityTranscoder(transcodeRunner),
            compatibilityChecker = checker,
            carCompatibleRemuxer = remuxer
        )

        val result = processor.process(
            inputFile = input,
            preferredName = "Video.mp4",
            mimeType = "video/mp4",
            qualitySelector = VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P
        )

        assertTrue(result is DownloadMediaPostProcessor.Result.Processed)
        assertEquals(
            DownloadMediaPostProcessor.ProcessingRoute.REMUX,
            (result as DownloadMediaPostProcessor.Result.Processed).route
        )
        assertTrue(remuxer.wasCalled)
        assertFalse(transcodeRunner.wasCalled)
        assertEquals(2, checker.callCount)
    }

    @Test
    fun incompatibleInputUsesExistingTranscodePathWithoutCallingRemux() = runBlocking {
        val input = nonEmptyTempFile("incompatible-input", ".mp4")
        val transcodeRunner = RecordingFfmpegRunner()
        val remuxer = RecordingRemuxer()
        val processor = DownloadMediaPostProcessor(
            carCompatibilityTranscoder = CarCompatibilityTranscoder(transcodeRunner),
            compatibilityChecker = RecordingCompatibilityChecker(
                ArrayDeque(
                    listOf(
                        CarMediaCompatibilityDecision.Incompatible(
                            listOf(CarMediaIncompatibilityReason.VIDEO_CODEC)
                        )
                    )
                )
            ),
            carCompatibleRemuxer = remuxer
        )

        val result = processor.process(
            inputFile = input,
            preferredName = "Video.mp4",
            mimeType = "video/mp4",
            qualitySelector = VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P
        )

        assertTrue(result is DownloadMediaPostProcessor.Result.Processed)
        assertEquals(
            DownloadMediaPostProcessor.ProcessingRoute.TRANSCODE,
            (result as DownloadMediaPostProcessor.Result.Processed).route
        )
        assertTrue(transcodeRunner.wasCalled)
        assertFalse(remuxer.wasCalled)
    }

    @Test
    fun remuxCommandCopiesStreamsWithoutTranscodingOptions() {
        val command = CarCompatibleRemuxer(RecordingFfmpegRunner()).buildCommand(
            inputFile = File("/tmp/input.mp4"),
            outputFile = File("/tmp/output.mp4")
        )

        assertContainsSequence(command, "-c", "copy")
        assertContainsSequence(command, "-movflags", "+faststart")
        assertFalse(command.contains("libx264"))
        assertFalse(command.contains("aac"))
        assertFalse(command.contains("-vf"))
    }

    private fun nonEmptyTempFile(prefix: String, suffix: String): File {
        return File.createTempFile(prefix, suffix).apply {
            writeBytes(byteArrayOf(1))
            deleteOnExit()
        }
    }

    private fun assertContainsSequence(arguments: List<String>, first: String, second: String) {
        val index = arguments.indexOf(first)
        assertTrue("$first not found", index >= 0)
        assertEquals(second, arguments.getOrNull(index + 1))
    }

    private class RecordingCompatibilityChecker(
        private val decisions: ArrayDeque<CarMediaCompatibilityDecision>
    ) : CarMediaCompatibilityChecker {
        var callCount: Int = 0
            private set

        override suspend fun inspect(
            inputFile: File,
            profile: VideoCompatibilityProfile,
            requireMp4Container: Boolean
        ): CarMediaCompatibilityDecision {
            callCount++
            return decisions.removeFirst()
        }
    }

    private class RecordingRemuxer : CarMediaRemuxer {
        var wasCalled = false
            private set

        override suspend fun remux(inputFile: File, outputFile: File): CarMediaRemuxResult {
            wasCalled = true
            outputFile.writeBytes(byteArrayOf(1, 2, 3))
            return CarMediaRemuxResult.Success(outputFile)
        }
    }

    private class RecordingFfmpegRunner : FfmpegCommandRunner {
        var wasCalled = false
            private set

        override suspend fun run(arguments: List<String>): FfmpegExecutionResult {
            wasCalled = true
            File(arguments.last()).writeBytes(byteArrayOf(1, 2, 3))
            return FfmpegExecutionResult.Success()
        }
    }
}
