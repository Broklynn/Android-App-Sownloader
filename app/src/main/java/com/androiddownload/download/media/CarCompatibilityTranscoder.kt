package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.Locale

class CarCompatibilityTranscoder(
    private val runner: FfmpegCommandRunner
) {
    suspend fun transcode(
        inputFile: File,
        outputFile: File,
        profile: VideoCompatibilityProfile
    ): TranscodeResult {
        val validationError = validateInput(inputFile, outputFile)
        if (validationError != null) {
            return TranscodeResult.Skipped(validationError)
        }

        val command = buildCommand(inputFile, outputFile, profile)
        return try {
            val parent = outputFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return TranscodeResult.Failure("Nao foi possivel preparar a pasta de saida.")
            }

            when (val result = runner.run(command)) {
                is FfmpegExecutionResult.Success -> {
                    if (outputFile.isFile && outputFile.length() > 0L) {
                        TranscodeResult.Success(outputFile)
                    } else {
                        TranscodeResult.Failure("FFmpeg terminou, mas o arquivo de saida nao foi gerado.")
                    }
                }
                is FfmpegExecutionResult.Failure -> TranscodeResult.Failure(
                    message = result.message,
                    cause = result.exception
                )
                is FfmpegExecutionResult.TimedOut -> TranscodeResult.TimedOut(
                    timeoutMillis = result.timeoutMillis
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            TranscodeResult.Failure(
                message = exception.message ?: "Falha ao converter video para modo carro.",
                cause = exception
            )
        }
    }

    internal fun buildCommand(
        inputFile: File,
        outputFile: File,
        profile: VideoCompatibilityProfile
    ): List<String> {
        val videoFilter = CarVideoScaleResolver.buildFilter(profile)
        return listOf(
            "-y",
            "-i",
            inputFile.absolutePath,
            "-vf",
            videoFilter,
            "-c:v",
            "libx264",
            "-profile:v",
            "baseline",
            "-level",
            "3.1",
            "-preset",
            "veryfast",
            "-crf",
            "23",
            "-c:a",
            profile.audioCodec,
            "-b:a",
            "${profile.audioEncodingTargetBitrate / BITS_PER_KILOBIT}k",
            "-ac",
            "2",
            "-ar",
            "48000",
            "-movflags",
            "+faststart",
            outputFile.absolutePath
        )
    }

    private fun validateInput(inputFile: File, outputFile: File): String? {
        return when {
            !inputFile.exists() -> "Arquivo de entrada inexistente."
            !inputFile.isFile -> "Arquivo de entrada invalido."
            inputFile.length() <= 0L -> "Arquivo de entrada vazio."
            sameFile(inputFile, outputFile) -> "Arquivo de saida nao pode ser o mesmo da entrada."
            else -> null
        }
    }

    private fun sameFile(first: File, second: File): Boolean {
        return normalizedPath(first) == normalizedPath(second)
    }

    private fun normalizedPath(file: File): String {
        return runCatching { file.canonicalPath }
            .getOrElse { file.absolutePath }
            .lowercase(Locale.US)
    }

    sealed class TranscodeResult {
        data class Success(val outputFile: File) : TranscodeResult()
        data class Skipped(val reason: String) : TranscodeResult()
        data class Failure(
            val message: String,
            val cause: Throwable? = null
        ) : TranscodeResult()
        data class TimedOut(
            val timeoutMillis: Long
        ) : TranscodeResult()
    }

    private companion object {
        const val BITS_PER_KILOBIT = 1_000L
    }
}
