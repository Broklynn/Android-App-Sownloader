package com.androiddownload.download.media

import android.content.Context
import com.androiddownload.core.utils.FileNameUtils
import com.androiddownload.core.utils.VideoCompatibilityProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

class DownloadMediaPostProcessor(
    private val carCompatibilityTranscoder: CarCompatibilityTranscoder,
    private val compatibilityChecker: CarMediaCompatibilityChecker,
    private val carCompatibleRemuxer: CarMediaRemuxer
) {
    suspend fun process(
        inputFile: File,
        preferredName: String,
        mimeType: String?,
        qualitySelector: String?
    ): Result {
        val profile = VideoCompatibilityProfile.fromSelector(qualitySelector)
            ?: return Result.Original(inputFile, preferredName, mimeType)
        if (mimeType?.startsWith("video/") != true) {
            return Result.Original(inputFile, preferredName, mimeType)
        }

        val outputName = carCompatibleName(preferredName)
        val outputFile = uniqueSiblingFile(inputFile, outputName)
        val inputDecision = try {
            compatibilityChecker.inspect(
                inputFile = inputFile,
                profile = profile,
                requireMp4Container = false
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            CarMediaCompatibilityDecision.ProbeFailed(
                CarMediaProbeFailureReason.PROCESS_FAILURE
            )
        }

        return if (inputDecision is CarMediaCompatibilityDecision.Compatible) {
            remuxOrTranscode(
                inputFile = inputFile,
                outputFile = outputFile,
                outputName = outputName,
                preferredName = preferredName,
                mimeType = mimeType,
                profile = profile
            )
        } else {
            transcode(
                inputFile = inputFile,
                outputFile = outputFile,
                outputName = outputName,
                preferredName = preferredName,
                mimeType = mimeType,
                profile = profile
            )
        }
    }

    private suspend fun remuxOrTranscode(
        inputFile: File,
        outputFile: File,
        outputName: String,
        preferredName: String,
        mimeType: String?,
        profile: VideoCompatibilityProfile
    ): Result {
        try {
            currentCoroutineContext().ensureActive()
            val remuxResult = carCompatibleRemuxer.remux(inputFile, outputFile)
            currentCoroutineContext().ensureActive()
            if (remuxResult is CarMediaRemuxResult.Success) {
                val outputDecision = try {
                    compatibilityChecker.inspect(
                        inputFile = remuxResult.outputFile,
                        profile = profile,
                        requireMp4Container = true
                    )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    CarMediaCompatibilityDecision.ProbeFailed(
                        CarMediaProbeFailureReason.PROCESS_FAILURE
                    )
                }
                currentCoroutineContext().ensureActive()
                if (outputDecision is CarMediaCompatibilityDecision.Compatible &&
                    outputFile.isFile &&
                    outputFile.length() > 0L
                ) {
                    return Result.Processed(
                        file = outputFile,
                        preferredName = outputName,
                        mimeType = "video/mp4",
                        route = ProcessingRoute.REMUX
                    )
                }
            }
        } catch (exception: CancellationException) {
            deleteOutput(outputFile)
            throw exception
        } catch (_: Exception) {
            // Any ordinary remux/probe failure falls through to the safe transcode path.
        }

        deleteOutput(outputFile)
        return transcode(
            inputFile = inputFile,
            outputFile = outputFile,
            outputName = outputName,
            preferredName = preferredName,
            mimeType = mimeType,
            profile = profile
        )
    }

    private suspend fun transcode(
        inputFile: File,
        outputFile: File,
        outputName: String,
        preferredName: String,
        mimeType: String?,
        profile: VideoCompatibilityProfile
    ): Result {
        return when (val transcodeResult = carCompatibilityTranscoder.transcode(inputFile, outputFile, profile)) {
            is CarCompatibilityTranscoder.TranscodeResult.Success -> Result.Processed(
                file = transcodeResult.outputFile,
                preferredName = outputName,
                mimeType = "video/mp4",
                route = ProcessingRoute.TRANSCODE
            )
            is CarCompatibilityTranscoder.TranscodeResult.Skipped -> Result.Fallback(
                file = inputFile,
                preferredName = preferredName,
                mimeType = mimeType,
                reason = transcodeResult.reason
            )
            is CarCompatibilityTranscoder.TranscodeResult.Failure -> Result.Fallback(
                file = inputFile,
                preferredName = preferredName,
                mimeType = mimeType,
                reason = transcodeResult.message
            )
            is CarCompatibilityTranscoder.TranscodeResult.TimedOut -> Result.Fallback(
                file = inputFile,
                preferredName = preferredName,
                mimeType = mimeType,
                reason = "FFmpeg excedeu o tempo limite de execucao."
            )
        }
    }

    internal fun carCompatibleName(preferredName: String): String {
        val sanitized = FileNameUtils.sanitize(preferredName)
        val baseName = sanitized.substringBeforeLast('.', sanitized).ifBlank { "video" }
        return "$baseName - carro.mp4"
    }

    private fun uniqueSiblingFile(inputFile: File, preferredName: String): File {
        val parent = inputFile.parentFile ?: File(".")
        val baseName = preferredName.substringBeforeLast('.', preferredName)
        val extension = preferredName.substringAfterLast('.', missingDelimiterValue = "")
        var candidate = File(parent, preferredName)
        var index = 1
        while (candidate.exists()) {
            val nextName = if (extension.isBlank()) {
                "$baseName ($index)"
            } else {
                "$baseName ($index).$extension"
            }
            candidate = File(parent, nextName)
            index++
        }
        return candidate
    }

    private fun deleteOutput(outputFile: File) {
        if (outputFile.exists()) {
            runCatching { outputFile.delete() }
        }
    }

    enum class ProcessingRoute {
        REMUX,
        TRANSCODE
    }

    sealed class Result {
        abstract val file: File
        abstract val preferredName: String
        abstract val mimeType: String?

        data class Original(
            override val file: File,
            override val preferredName: String,
            override val mimeType: String?
        ) : Result()

        data class Processed(
            override val file: File,
            override val preferredName: String,
            override val mimeType: String?,
            val route: ProcessingRoute
        ) : Result()

        data class Fallback(
            override val file: File,
            override val preferredName: String,
            override val mimeType: String?,
            val reason: String
        ) : Result()
    }

    companion object {
        fun create(context: Context): DownloadMediaPostProcessor {
            val transcodeRunner = AndroidFfmpegCommandRunner(context)
            val remuxRunner = AndroidFfmpegCommandRunner(
                context = context,
                executionPolicy = FfmpegExecutionPolicy(
                    timeoutMillis = CarMediaFastPathPolicy.REMUX_TIMEOUT_MILLIS
                )
            )
            return DownloadMediaPostProcessor(
                carCompatibilityTranscoder = CarCompatibilityTranscoder(transcodeRunner),
                compatibilityChecker = CarMediaCompatibilityProbe(
                    AndroidCarMediaProbeRunner(context)
                ),
                carCompatibleRemuxer = CarCompatibleRemuxer(remuxRunner)
            )
        }
    }
}
