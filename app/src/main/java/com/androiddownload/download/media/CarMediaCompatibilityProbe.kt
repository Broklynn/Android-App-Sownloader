package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import kotlinx.coroutines.CancellationException
import java.io.File

interface CarMediaCompatibilityChecker {
    suspend fun inspect(
        inputFile: File,
        profile: VideoCompatibilityProfile,
        requireMp4Container: Boolean
    ): CarMediaCompatibilityDecision
}

sealed interface CarMediaCompatibilityDecision {
    data object Compatible : CarMediaCompatibilityDecision

    data class Incompatible(
        val reasons: List<CarMediaIncompatibilityReason>
    ) : CarMediaCompatibilityDecision

    data class ProbeFailed(
        val reason: CarMediaProbeFailureReason
    ) : CarMediaCompatibilityDecision
}

enum class CarMediaProbeFailureReason {
    INVALID_INPUT,
    EMPTY_OUTPUT,
    MALFORMED_OUTPUT,
    OUTPUT_LIMIT_EXCEEDED,
    PROCESS_FAILURE,
    TIMEOUT
}

enum class CarMediaIncompatibilityReason {
    FORMAT_NAME,
    FORMAT_DURATION,
    FORMAT_BITRATE,
    FORMAT_STREAM_COUNT,
    OUTPUT_CONTAINER,
    STREAM_STRUCTURE,
    STREAM_INDEX,
    VIDEO_STREAM_COUNT,
    AUDIO_STREAM_COUNT,
    UNSUPPORTED_STREAM_TYPE,
    VIDEO_CODEC,
    VIDEO_PROFILE,
    VIDEO_LEVEL,
    VIDEO_DIMENSIONS,
    VIDEO_DIMENSIONS_ODD,
    VIDEO_PIXEL_FORMAT,
    VIDEO_FIELD_ORDER,
    VIDEO_ROTATION,
    VIDEO_SAMPLE_ASPECT_RATIO,
    VIDEO_DISPLAY_ASPECT_RATIO,
    VIDEO_ATTACHED_PICTURE,
    VIDEO_BITRATE,
    VIDEO_FRAME_RATE,
    VIDEO_VARIABLE_FRAME_RATE,
    AUDIO_CODEC,
    AUDIO_PROFILE,
    AUDIO_SAMPLE_RATE,
    AUDIO_CHANNELS,
    AUDIO_CHANNEL_LAYOUT,
    AUDIO_BITRATE
}

class CarMediaCompatibilityProbe(
    private val runner: CarMediaProbeRunner
) : CarMediaCompatibilityChecker {
    override suspend fun inspect(
        inputFile: File,
        profile: VideoCompatibilityProfile,
        requireMp4Container: Boolean
    ): CarMediaCompatibilityDecision {
        if (!inputFile.isFile || inputFile.length() <= 0L) {
            return CarMediaCompatibilityDecision.ProbeFailed(
                CarMediaProbeFailureReason.INVALID_INPUT
            )
        }

        return try {
            when (val result = runner.run(buildCommand(inputFile))) {
                is CarMediaProbeExecutionResult.Success -> {
                    if (result.stdout.isBlank()) {
                        CarMediaCompatibilityDecision.ProbeFailed(
                            CarMediaProbeFailureReason.EMPTY_OUTPUT
                        )
                    } else {
                        when (val parsed = CarMediaMetadataParser.parse(result.stdout)) {
                            is CarMediaMetadataParseResult.Success -> {
                                CarMediaCompatibilityEvaluator.evaluate(
                                    metadata = parsed.metadata,
                                    profile = profile,
                                    requireMp4Container = requireMp4Container
                                )
                            }
                            CarMediaMetadataParseResult.Failure ->
                                CarMediaCompatibilityDecision.ProbeFailed(
                                    CarMediaProbeFailureReason.MALFORMED_OUTPUT
                                )
                        }
                    }
                }
                is CarMediaProbeExecutionResult.Failure ->
                    CarMediaCompatibilityDecision.ProbeFailed(
                        CarMediaProbeFailureReason.PROCESS_FAILURE
                    )
                is CarMediaProbeExecutionResult.TimedOut ->
                    CarMediaCompatibilityDecision.ProbeFailed(
                        CarMediaProbeFailureReason.TIMEOUT
                    )
                is CarMediaProbeExecutionResult.OutputLimitExceeded ->
                    CarMediaCompatibilityDecision.ProbeFailed(
                        CarMediaProbeFailureReason.OUTPUT_LIMIT_EXCEEDED
                    )
            }
        } catch (exception: CancellationException) {
            throw exception
        }
    }

    internal fun buildCommand(inputFile: File): List<String> {
        return listOf(
            "-v",
            "error",
            "-print_format",
            "json",
            "-show_streams",
            "-show_format",
            "-show_entries",
            SHOW_ENTRIES,
            inputFile.absolutePath
        )
    }

    private companion object {
        const val SHOW_ENTRIES =
            "stream=index,codec_type,codec_name,profile,level,width,height,pix_fmt," +
                "avg_frame_rate,r_frame_rate,sample_aspect_ratio,display_aspect_ratio," +
                "field_order,bit_rate,sample_rate,channels,channel_layout:" +
                "stream_tags=rotate:stream_side_data=rotation:" +
                "stream_disposition=attached_pic:" +
                "format=format_name,duration,bit_rate,nb_streams"
    }
}
