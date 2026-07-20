package com.androiddownload.download.media

import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.Locale

interface CarMediaRemuxer {
    suspend fun remux(inputFile: File, outputFile: File): CarMediaRemuxResult
}

sealed interface CarMediaRemuxResult {
    data class Success(val outputFile: File) : CarMediaRemuxResult
    data class Failure(val message: String, val cause: Throwable? = null) : CarMediaRemuxResult
    data class TimedOut(val timeoutMillis: Long) : CarMediaRemuxResult
}

class CarCompatibleRemuxer(
    private val runner: FfmpegCommandRunner
) : CarMediaRemuxer {
    override suspend fun remux(
        inputFile: File,
        outputFile: File
    ): CarMediaRemuxResult {
        val validationError = validateInput(inputFile, outputFile)
        if (validationError != null) {
            return CarMediaRemuxResult.Failure(validationError)
        }

        return try {
            val parent = outputFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return CarMediaRemuxResult.Failure(
                    "Nao foi possivel preparar a pasta de remux."
                )
            }
            deleteOutput(outputFile)
            when (val result = runner.run(buildCommand(inputFile, outputFile))) {
                is FfmpegExecutionResult.Success -> {
                    if (outputFile.isFile &&
                        outputFile.length() > 0L &&
                        outputFile.extension.equals("mp4", ignoreCase = true)
                    ) {
                        CarMediaRemuxResult.Success(outputFile)
                    } else {
                        deleteOutput(outputFile)
                        CarMediaRemuxResult.Failure(
                            "FFmpeg terminou o remux sem gerar um MP4 valido."
                        )
                    }
                }
                is FfmpegExecutionResult.Failure -> {
                    deleteOutput(outputFile)
                    CarMediaRemuxResult.Failure(
                        message = result.message,
                        cause = result.exception
                    )
                }
                is FfmpegExecutionResult.TimedOut -> {
                    deleteOutput(outputFile)
                    CarMediaRemuxResult.TimedOut(result.timeoutMillis)
                }
            }
        } catch (exception: CancellationException) {
            deleteOutput(outputFile)
            throw exception
        } catch (exception: Exception) {
            deleteOutput(outputFile)
            CarMediaRemuxResult.Failure(
                message = exception.message ?: "Falha ao remuxar video para modo carro.",
                cause = exception
            )
        }
    }

    internal fun buildCommand(
        inputFile: File,
        outputFile: File
    ): List<String> {
        return listOf(
            "-y",
            "-i",
            inputFile.absolutePath,
            "-map",
            "0:v:0",
            "-map",
            "0:a:0",
            "-c",
            "copy",
            "-map_metadata",
            "-1",
            "-map_chapters",
            "-1",
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
            sameFile(inputFile, outputFile) ->
                "Arquivo de saida do remux nao pode ser o mesmo da entrada."
            !outputFile.extension.equals("mp4", ignoreCase = true) ->
                "Arquivo de saida do remux precisa ser MP4."
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

    private fun deleteOutput(outputFile: File) {
        if (outputFile.exists()) {
            runCatching { outputFile.delete() }
        }
    }
}
