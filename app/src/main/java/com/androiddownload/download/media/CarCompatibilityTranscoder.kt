package com.androiddownload.download.media

import android.content.Context
import com.androiddownload.core.utils.VideoCompatibilityProfile
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import java.io.IOException
import java.util.Locale

class CarCompatibilityTranscoder(
    private val runner: FfmpegCommandRunner
) {
    fun transcode(
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
                is FfmpegCommandResult.Success -> {
                    if (outputFile.isFile && outputFile.length() > 0L) {
                        TranscodeResult.Success(outputFile)
                    } else {
                        TranscodeResult.Failure("FFmpeg terminou, mas o arquivo de saida nao foi gerado.")
                    }
                }
                is FfmpegCommandResult.Failure -> TranscodeResult.Failure(
                    message = result.message,
                    cause = result.exception
                )
            }
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
            "192k",
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
    }
}

interface FfmpegCommandRunner {
    fun run(arguments: List<String>): FfmpegCommandResult
}

sealed class FfmpegCommandResult {
    data object Success : FfmpegCommandResult()
    data class Failure(
        val message: String,
        val exception: Throwable? = null
    ) : FfmpegCommandResult()
}

class AndroidFfmpegCommandRunner(
    context: Context
) : FfmpegCommandRunner {
    private val appContext = context.applicationContext

    override fun run(arguments: List<String>): FfmpegCommandResult {
        return try {
            FFmpeg.getInstance().init(appContext)
            val nativeLibraryDir = File(appContext.applicationInfo.nativeLibraryDir)
            val ffmpegBinary = File(nativeLibraryDir, FFMPEG_BINARY_NAME)
            if (!ffmpegBinary.isFile) {
                return FfmpegCommandResult.Failure("Binario FFmpeg nao encontrado.")
            }
            val ffmpegLibraryDir = File(appContext.noBackupFilesDir, FFMPEG_LIBRARY_RELATIVE_PATH)
            if (!ffmpegLibraryDir.isDirectory) {
                return FfmpegCommandResult.Failure("Bibliotecas FFmpeg nao encontradas.")
            }
            val pythonLibraryDir = File(appContext.noBackupFilesDir, PYTHON_LIBRARY_RELATIVE_PATH)
            if (!pythonLibraryDir.isDirectory) {
                return FfmpegCommandResult.Failure("Bibliotecas Python nao encontradas.")
            }
            val aria2cLibraryDir = File(appContext.noBackupFilesDir, ARIA2C_LIBRARY_RELATIVE_PATH)

            val process = ProcessBuilder(listOf(ffmpegBinary.absolutePath) + arguments)
                .redirectErrorStream(true)
                .apply {
                    environment()["LD_LIBRARY_PATH"] = buildList {
                        add(pythonLibraryDir.absolutePath)
                        add(ffmpegLibraryDir.absolutePath)
                        if (aria2cLibraryDir.isDirectory) add(aria2cLibraryDir.absolutePath)
                    }.joinToString(File.pathSeparator)
                }
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                FfmpegCommandResult.Success
            } else {
                FfmpegCommandResult.Failure(
                    message = output.trim().ifBlank { "FFmpeg falhou com codigo $exitCode." }
                )
            }
        } catch (exception: IOException) {
            FfmpegCommandResult.Failure(
                message = exception.message ?: "Falha ao executar FFmpeg.",
                exception = exception
            )
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            FfmpegCommandResult.Failure(
                message = exception.message ?: "Execucao FFmpeg interrompida.",
                exception = exception
            )
        }
    }

    private companion object {
        const val FFMPEG_BINARY_NAME = "libffmpeg.so"
        const val FFMPEG_LIBRARY_RELATIVE_PATH = "youtubedl-android/packages/ffmpeg/usr/lib"
        const val PYTHON_LIBRARY_RELATIVE_PATH = "youtubedl-android/packages/python/usr/lib"
        const val ARIA2C_LIBRARY_RELATIVE_PATH = "youtubedl-android/packages/aria2c/usr/lib"
    }
}
