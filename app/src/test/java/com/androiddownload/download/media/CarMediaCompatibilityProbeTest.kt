package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CarMediaCompatibilityProbeTest {
    private val profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P

    @Test
    fun completeCompatibleJsonIsAccepted() = runBlocking {
        assertCompatible(compatibleRoot())
    }

    @Test
    fun emptyMalformedAndTruncatedJsonFailClosed() = runBlocking {
        listOf("", "{not-json", """{"streams":[""").forEach { json ->
            val decision = inspect(CarMediaProbeExecutionResult.Success(json))
            assertTrue(
                "Expected probe failure for: $json",
                decision is CarMediaCompatibilityDecision.ProbeFailed
            )
        }
    }

    @Test
    fun missingRootSectionsFailProbe() = runBlocking {
        listOf(
            JSONObject().put("format", JSONObject()).toString(),
            JSONObject().put("streams", JSONArray()).toString()
        ).forEach { json ->
            assertTrue(
                inspect(CarMediaProbeExecutionResult.Success(json)) is
                    CarMediaCompatibilityDecision.ProbeFailed
            )
        }
    }

    @Test
    fun missingRequiredFieldIsIncompatible() = runBlocking {
        val root = compatibleRoot()
        video(root).remove("codec_name")

        assertIncompatible(root, CarMediaIncompatibilityReason.VIDEO_CODEC)
    }

    @Test
    fun numericFieldsMayBeNumbersOrStrings() = runBlocking {
        val root = compatibleRoot()
        video(root)
            .put("index", 0)
            .put("level", 31)
            .put("width", 1280)
            .put("height", 720)
            .put("bit_rate", 5_000_000)
        audio(root)
            .put("index", 1)
            .put("sample_rate", 48_000)
            .put("channels", 2)
            .put("bit_rate", 192_000)
        root.getJSONObject("format")
            .put("duration", 30.0)
            .put("bit_rate", 5_192_000)
            .put("nb_streams", 2)

        assertCompatible(root)
    }

    @Test
    fun acceptedFrameRatesUseExactRationalParsing() = runBlocking {
        listOf("24/1", "25/1", "30000/1001", "29.97", "30/1").forEach { rate ->
            val root = compatibleRoot()
            video(root).put("avg_frame_rate", rate).put("r_frame_rate", rate)
            assertCompatible(root)
        }
    }

    @Test
    fun invalidRationalAndZeroDenominatorAreRejected() = runBlocking {
        listOf("not-a-rate", "30/0").forEach { rate ->
            val root = compatibleRoot()
            video(root).put("avg_frame_rate", rate).put("r_frame_rate", rate)
            assertIncompatible(root, CarMediaIncompatibilityReason.VIDEO_FRAME_RATE)
        }
    }

    @Test
    fun videoProfileMatchingIsCaseInsensitiveButConservative() = runBlocking {
        listOf("Baseline", "bAsElInE", "Constrained Baseline", "CONSTRAINED BASELINE")
            .forEach { profileName ->
                val root = compatibleRoot()
                video(root).put("profile", profileName)
                assertCompatible(root)
            }

        listOf("Main", "High", "Unknown").forEach { profileName ->
            val root = compatibleRoot()
            video(root).put("profile", profileName)
            assertIncompatible(root, CarMediaIncompatibilityReason.VIDEO_PROFILE)
        }
    }

    @Test
    fun rotationInTagsOrSideDataIsRejectedButMissingOrZeroIsAccepted() = runBlocking {
        val noRotation = compatibleRoot()
        video(noRotation).remove("tags")
        video(noRotation).remove("side_data_list")
        assertCompatible(noRotation)

        val tagRotation = compatibleRoot()
        video(tagRotation).getJSONObject("tags").put("rotate", "90")
        assertIncompatible(tagRotation, CarMediaIncompatibilityReason.VIDEO_ROTATION)

        val sideRotation = compatibleRoot()
        video(sideRotation).getJSONArray("side_data_list")
            .getJSONObject(0)
            .put("rotation", -90)
        assertIncompatible(sideRotation, CarMediaIncompatibilityReason.VIDEO_ROTATION)
    }

    @Test
    fun malformedRotationIsRejected() = runBlocking {
        val root = compatibleRoot()
        video(root).getJSONObject("tags").put("rotate", "unknown")

        assertIncompatible(root, CarMediaIncompatibilityReason.VIDEO_ROTATION)
    }

    @Test
    fun attachedPictureIsRejectedAndDispositionMustBeKnown() = runBlocking {
        val attached = compatibleRoot()
        video(attached).getJSONObject("disposition").put("attached_pic", 1)
        assertIncompatible(attached, CarMediaIncompatibilityReason.VIDEO_ATTACHED_PICTURE)

        val unknown = compatibleRoot()
        video(unknown).remove("disposition")
        assertIncompatible(unknown, CarMediaIncompatibilityReason.VIDEO_ATTACHED_PICTURE)
    }

    @Test
    fun progressiveFieldOrderIsRequired() = runBlocking {
        listOf("unknown", "tt", "bb", "interlaced").forEach { fieldOrder ->
            val root = compatibleRoot()
            video(root).put("field_order", fieldOrder)
            assertIncompatible(root, CarMediaIncompatibilityReason.VIDEO_FIELD_ORDER)
        }
    }

    @Test
    fun unsupportedOrMissingStreamTypesAreRejected() = runBlocking {
        listOf("subtitle", "data", "attachment", "unknown").forEach { type ->
            val root = compatibleRoot()
            streams(root).put(
                JSONObject()
                    .put("index", 2)
                    .put("codec_type", type)
            )
            format(root).put("nb_streams", 3)
            assertIncompatible(root, CarMediaIncompatibilityReason.UNSUPPORTED_STREAM_TYPE)
        }
    }

    @Test
    fun streamIndexesMustBePresentUniqueAndNonNegative() = runBlocking {
        val missing = compatibleRoot()
        video(missing).remove("index")
        assertIncompatible(missing, CarMediaIncompatibilityReason.STREAM_INDEX)

        val duplicate = compatibleRoot()
        audio(duplicate).put("index", 0)
        assertIncompatible(duplicate, CarMediaIncompatibilityReason.STREAM_INDEX)
    }

    @Test
    fun videoCodecMustBeH264() = runBlocking {
        listOf("hevc", "vp9", "av1").forEach { codec ->
            val root = compatibleRoot()
            video(root).put("codec_name", codec)
            assertIncompatible(root, CarMediaIncompatibilityReason.VIDEO_CODEC)
        }
    }

    @Test
    fun level31IsAcceptedAndHigherOrMissingLevelIsRejected() = runBlocking {
        val level31 = compatibleRoot()
        video(level31).put("level", "31")
        assertCompatible(level31)

        val higher = compatibleRoot()
        video(higher).put("level", "32")
        assertIncompatible(higher, CarMediaIncompatibilityReason.VIDEO_LEVEL)

        val missing = compatibleRoot()
        video(missing).remove("level")
        assertIncompatible(missing, CarMediaIncompatibilityReason.VIDEO_LEVEL)
    }

    @Test
    fun dimensionsMustFit720pAndBeEven() = runBlocking {
        val exact = compatibleRoot()
        assertCompatible(exact)

        val tooWide = compatibleRoot()
        video(tooWide).put("width", 1282)
        assertIncompatible(tooWide, CarMediaIncompatibilityReason.VIDEO_DIMENSIONS)

        val tooHigh = compatibleRoot()
        video(tooHigh).put("height", 722)
        assertIncompatible(tooHigh, CarMediaIncompatibilityReason.VIDEO_DIMENSIONS)

        val odd = compatibleRoot()
        video(odd).put("width", 1279)
        assertIncompatible(odd, CarMediaIncompatibilityReason.VIDEO_DIMENSIONS_ODD)
    }

    @Test
    fun onlyYuv420pIsAccepted() = runBlocking {
        val compatible = compatibleRoot()
        video(compatible).put("pix_fmt", "yuv420p")
        assertCompatible(compatible)

        val tenBit = compatibleRoot()
        video(tenBit).put("pix_fmt", "yuv420p10le")
        assertIncompatible(tenBit, CarMediaIncompatibilityReason.VIDEO_PIXEL_FORMAT)
    }

    @Test
    fun frameRateAbove30OrOutsideAllowlistIsRejected() = runBlocking {
        listOf("60/1", "15/1", "24000/1001").forEach { rate ->
            val root = compatibleRoot()
            video(root).put("avg_frame_rate", rate).put("r_frame_rate", rate)
            assertIncompatible(root, CarMediaIncompatibilityReason.VIDEO_FRAME_RATE)
        }
    }

    @Test
    fun divergentAverageAndRealFrameRatesAreTreatedAsVfr() = runBlocking {
        val root = compatibleRoot()
        video(root)
            .put("avg_frame_rate", "30/1")
            .put("r_frame_rate", "30000/1001")

        assertIncompatible(root, CarMediaIncompatibilityReason.VIDEO_VARIABLE_FRAME_RATE)
    }

    @Test
    fun sampleAspectRatioMustBeExactlyOneToOne() = runBlocking {
        val root = compatibleRoot()
        video(root).put("sample_aspect_ratio", "4:3")

        assertIncompatible(root, CarMediaIncompatibilityReason.VIDEO_SAMPLE_ASPECT_RATIO)
    }

    @Test
    fun displayAspectRatioMustBeValidAndMatchSquarePixelDimensions() = runBlocking {
        listOf("0:1", "unknown", "4:3").forEach { ratio ->
            val root = compatibleRoot()
            video(root).put("display_aspect_ratio", ratio)
            assertIncompatible(root, CarMediaIncompatibilityReason.VIDEO_DISPLAY_ASPECT_RATIO)
        }
    }

    @Test
    fun videoBitrateMustBeKnownPositiveAndAtMostProfileLimit() = runBlocking {
        val exactLimit = compatibleRoot()
        video(exactLimit).put("bit_rate", profile.maxVideoBitrate.toString())
        assertCompatible(exactLimit)

        val missing = compatibleRoot()
        video(missing).remove("bit_rate")
        assertIncompatible(missing, CarMediaIncompatibilityReason.VIDEO_BITRATE)

        val over = compatibleRoot()
        video(over).put("bit_rate", (profile.maxVideoBitrate + 1L).toString())
        assertIncompatible(over, CarMediaIncompatibilityReason.VIDEO_BITRATE)
    }

    @Test
    fun audioMustBeAacLc() = runBlocking {
        val lowercase = compatibleRoot()
        audio(lowercase).put("profile", "aac-lc")
        assertCompatible(lowercase)

        val wrongCodec = compatibleRoot()
        audio(wrongCodec).put("codec_name", "opus")
        assertIncompatible(wrongCodec, CarMediaIncompatibilityReason.AUDIO_CODEC)

        listOf("HE-AAC", "HE-AACv2", "unknown").forEach { audioProfile ->
            val root = compatibleRoot()
            audio(root).put("profile", audioProfile)
            assertIncompatible(root, CarMediaIncompatibilityReason.AUDIO_PROFILE)
        }

        val missing = compatibleRoot()
        audio(missing).remove("profile")
        assertIncompatible(missing, CarMediaIncompatibilityReason.AUDIO_PROFILE)
    }

    @Test
    fun stereoAudioAt44100Or48000RemainsEligibleForRemux() = runBlocking {
        listOf(44_100, 48_000).forEach { sampleRate ->
            val root = compatibleRoot()
            audio(root)
                .put("sample_rate", sampleRate.toString())
                .put("channels", "2")
                .put("channel_layout", "stereo")
            assertCompatible(root)
        }

        val incompatible = compatibleRoot()
        audio(incompatible).put("sample_rate", "32000")
        assertIncompatible(incompatible, CarMediaIncompatibilityReason.AUDIO_SAMPLE_RATE)
    }

    @Test
    fun audioChannelsMustBeMonoOrStereoWithMatchingLayoutWhenPresent() = runBlocking {
        val mono = compatibleRoot()
        audio(mono).put("channels", "1").put("channel_layout", "mono")
        assertCompatible(mono)

        val noLayout = compatibleRoot()
        audio(noLayout).remove("channel_layout")
        assertCompatible(noLayout)

        listOf(192_000L, 193_146L, 200_000L).forEach { bitrate ->
            val multichannel = compatibleRoot()
            audio(multichannel)
                .put("channels", "6")
                .put("channel_layout", "5.1")
                .put("bit_rate", bitrate.toString())
            assertIncompatible(multichannel, CarMediaIncompatibilityReason.AUDIO_CHANNELS)
        }

        val mismatched = compatibleRoot()
        audio(mismatched).put("channels", "2").put("channel_layout", "mono")
        assertIncompatible(mismatched, CarMediaIncompatibilityReason.AUDIO_CHANNEL_LAYOUT)

        val missing = compatibleRoot()
        audio(missing).remove("channels")
        assertIncompatible(missing, CarMediaIncompatibilityReason.AUDIO_CHANNELS)
    }

    @Test
    fun audioBitrateUsesCompatibilityCeilingWithEncoderMargin() = runBlocking {
        listOf(192_000L, 193_146L, 200_000L).forEach { bitrate ->
            val compatible = compatibleRoot()
            audio(compatible).put("bit_rate", bitrate.toString())
            assertCompatible(compatible)
        }

        val missing = compatibleRoot()
        audio(missing).remove("bit_rate")
        assertIncompatible(missing, CarMediaIncompatibilityReason.AUDIO_BITRATE)

        val zero = compatibleRoot()
        audio(zero).put("bit_rate", "0")
        assertIncompatible(zero, CarMediaIncompatibilityReason.AUDIO_BITRATE)

        val over = compatibleRoot()
        audio(over).put(
            "bit_rate",
            (profile.audioCompatibilityBitrateCeiling + 1L).toString()
        )
        assertIncompatible(over, CarMediaIncompatibilityReason.AUDIO_BITRATE)
    }

    @Test
    fun exactlyOneVideoAndOneAudioAreRequired() = runBlocking {
        val secondVideo = compatibleRoot()
        streams(secondVideo).put(JSONObject(video(secondVideo).toString()).put("index", 2))
        format(secondVideo).put("nb_streams", 3)
        assertIncompatible(secondVideo, CarMediaIncompatibilityReason.VIDEO_STREAM_COUNT)

        val secondAudio = compatibleRoot()
        streams(secondAudio).put(JSONObject(audio(secondAudio).toString()).put("index", 2))
        format(secondAudio).put("nb_streams", 3)
        assertIncompatible(secondAudio, CarMediaIncompatibilityReason.AUDIO_STREAM_COUNT)

        val noAudio = compatibleRoot()
        streams(noAudio).remove(1)
        format(noAudio).put("nb_streams", 1)
        assertIncompatible(noAudio, CarMediaIncompatibilityReason.AUDIO_STREAM_COUNT)
    }

    @Test
    fun inputContainerMayDifferButFinalOutputMustBeMp4OrMov() = runBlocking {
        val matroska = compatibleRoot()
        format(matroska).put("format_name", "matroska,webm")
        assertCompatible(matroska, requireMp4Container = false)
        assertIncompatible(
            matroska,
            CarMediaIncompatibilityReason.OUTPUT_CONTAINER,
            requireMp4Container = true
        )

        val mp4 = compatibleRoot()
        format(mp4).put("format_name", "mov,mp4,m4a,3gp,3g2,mj2")
        assertCompatible(mp4, requireMp4Container = true)
    }

    @Test
    fun incompleteFormatMetadataIsRejected() = runBlocking {
        val missingDuration = compatibleRoot()
        format(missingDuration).remove("duration")
        assertIncompatible(missingDuration, CarMediaIncompatibilityReason.FORMAT_DURATION)

        val missingBitrate = compatibleRoot()
        format(missingBitrate).remove("bit_rate")
        assertIncompatible(missingBitrate, CarMediaIncompatibilityReason.FORMAT_BITRATE)

        val mismatchedCount = compatibleRoot()
        format(mismatchedCount).put("nb_streams", 3)
        assertIncompatible(mismatchedCount, CarMediaIncompatibilityReason.FORMAT_STREAM_COUNT)
    }

    @Test
    fun runnerFailureTimeoutAndOutputLimitFailClosed() = runBlocking {
        val cases = listOf(
            CarMediaProbeExecutionResult.Failure("failed"),
            CarMediaProbeExecutionResult.TimedOut(
                timeoutMillis = 30L,
                diagnostics = CarMediaProbeDiagnostics()
            ),
            CarMediaProbeExecutionResult.OutputLimitExceeded(
                limitBytes = 256,
                diagnostics = CarMediaProbeDiagnostics()
            )
        )

        cases.forEach { result ->
            assertTrue(inspect(result) is CarMediaCompatibilityDecision.ProbeFailed)
        }
    }

    @Test
    fun cancellationFromRunnerIsPropagated() = runBlocking {
        val input = nonEmptyTempFile()
        val probe = CarMediaCompatibilityProbe(
            object : CarMediaProbeRunner {
                override suspend fun run(arguments: List<String>): CarMediaProbeExecutionResult {
                    throw CancellationException("cancelled")
                }
            }
        )

        var cancellation: CancellationException? = null
        try {
            probe.inspect(input, profile, requireMp4Container = false)
        } catch (exception: CancellationException) {
            cancellation = exception
        }

        assertEquals("cancelled", cancellation?.message)
    }

    @Test
    fun commandRequestsOnlyStructuredMetadataAndKeepsInputAsSeparateArgument() {
        val input = File("/private/input.mp4")
        val probe = CarMediaCompatibilityProbe(StaticProbeRunner("{}"))

        val command = probe.buildCommand(input)

        assertContainsSequence(command, "-v", "error")
        assertContainsSequence(command, "-print_format", "json")
        assertTrue(command.contains("-show_streams"))
        assertTrue(command.contains("-show_format"))
        val entries = command.optionValue("-show_entries").orEmpty()
        listOf(
            "codec_type",
            "codec_name",
            "profile",
            "level",
            "avg_frame_rate",
            "r_frame_rate",
            "sample_aspect_ratio",
            "display_aspect_ratio",
            "field_order",
            "bit_rate",
            "sample_rate",
            "channels",
            "channel_layout",
            "rotate",
            "rotation",
            "attached_pic",
            "format_name",
            "duration",
            "nb_streams"
        ).forEach { field ->
            assertTrue("$field missing from show_entries", entries.contains(field))
        }
        assertEquals(input.absolutePath, command.last())
        assertFalse(command.contains("-v quiet"))
    }

    private suspend fun assertCompatible(
        root: JSONObject,
        requireMp4Container: Boolean = false
    ) {
        val decision = inspect(
            result = CarMediaProbeExecutionResult.Success(root.toString()),
            requireMp4Container = requireMp4Container
        )
        assertEquals(CarMediaCompatibilityDecision.Compatible, decision)
    }

    private suspend fun assertIncompatible(
        root: JSONObject,
        expectedReason: CarMediaIncompatibilityReason,
        requireMp4Container: Boolean = false
    ) {
        val decision = inspect(
            result = CarMediaProbeExecutionResult.Success(root.toString()),
            requireMp4Container = requireMp4Container
        )
        assertTrue("Expected incompatible, got $decision", decision is CarMediaCompatibilityDecision.Incompatible)
        decision as CarMediaCompatibilityDecision.Incompatible
        assertTrue(
            "Expected $expectedReason in ${decision.reasons}",
            expectedReason in decision.reasons
        )
    }

    private suspend fun inspect(
        result: CarMediaProbeExecutionResult,
        requireMp4Container: Boolean = false
    ): CarMediaCompatibilityDecision {
        val input = nonEmptyTempFile()
        return CarMediaCompatibilityProbe(StaticProbeRunner(result))
            .inspect(input, profile, requireMp4Container)
    }

    private fun compatibleRoot(): JSONObject {
        val video = JSONObject()
            .put("index", "0")
            .put("codec_type", "video")
            .put("codec_name", "h264")
            .put("profile", "Constrained Baseline")
            .put("level", "31")
            .put("width", "1280")
            .put("height", "720")
            .put("pix_fmt", "yuv420p")
            .put("avg_frame_rate", "30/1")
            .put("r_frame_rate", "30/1")
            .put("sample_aspect_ratio", "1:1")
            .put("display_aspect_ratio", "16:9")
            .put("field_order", "progressive")
            .put("bit_rate", "5000000")
            .put("disposition", JSONObject().put("attached_pic", "0"))
            .put("tags", JSONObject().put("rotate", "0"))
            .put(
                "side_data_list",
                JSONArray().put(JSONObject().put("rotation", "0"))
            )
        val audio = JSONObject()
            .put("index", "1")
            .put("codec_type", "audio")
            .put("codec_name", "aac")
            .put("profile", "LC")
            .put("sample_rate", "48000")
            .put("channels", "2")
            .put("channel_layout", "stereo")
            .put("bit_rate", "192000")
        return JSONObject()
            .put("streams", JSONArray().put(video).put(audio))
            .put(
                "format",
                JSONObject()
                    .put("format_name", "mov,mp4,m4a,3gp,3g2,mj2")
                    .put("duration", "30.000000")
                    .put("bit_rate", "5192000")
                    .put("nb_streams", "2")
            )
    }

    private fun streams(root: JSONObject): JSONArray = root.getJSONArray("streams")
    private fun video(root: JSONObject): JSONObject = streams(root).getJSONObject(0)
    private fun audio(root: JSONObject): JSONObject = streams(root).getJSONObject(1)
    private fun format(root: JSONObject): JSONObject = root.getJSONObject("format")

    private fun nonEmptyTempFile(): File {
        return File.createTempFile("car-probe", ".media").apply {
            writeBytes(byteArrayOf(1))
            deleteOnExit()
        }
    }

    private fun assertContainsSequence(arguments: List<String>, first: String, second: String) {
        val index = arguments.indexOf(first)
        assertTrue("$first not found", index >= 0)
        assertEquals(second, arguments.getOrNull(index + 1))
    }

    private fun List<String>.optionValue(option: String): String? {
        return getOrNull(indexOf(option) + 1)
    }

    private class StaticProbeRunner(
        private val result: CarMediaProbeExecutionResult
    ) : CarMediaProbeRunner {
        constructor(stdout: String) : this(CarMediaProbeExecutionResult.Success(stdout))

        override suspend fun run(arguments: List<String>): CarMediaProbeExecutionResult {
            return result
        }
    }
}
