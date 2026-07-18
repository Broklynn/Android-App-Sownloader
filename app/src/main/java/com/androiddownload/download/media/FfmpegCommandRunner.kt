package com.androiddownload.download.media

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

interface FfmpegCommandRunner {
    suspend fun run(arguments: List<String>): FfmpegExecutionResult
}

sealed class FfmpegExecutionResult {
    abstract val diagnostics: FfmpegExecutionDiagnostics

    data class Success(
        override val diagnostics: FfmpegExecutionDiagnostics = FfmpegExecutionDiagnostics()
    ) : FfmpegExecutionResult()

    data class Failure(
        val message: String,
        val exitCode: Int? = null,
        val exception: Throwable? = null,
        override val diagnostics: FfmpegExecutionDiagnostics = FfmpegExecutionDiagnostics()
    ) : FfmpegExecutionResult()

    data class TimedOut(
        val timeoutMillis: Long,
        override val diagnostics: FfmpegExecutionDiagnostics
    ) : FfmpegExecutionResult()
}

data class FfmpegExecutionDiagnostics(
    val logTail: List<String> = emptyList(),
    val durationMillis: Long = 0L,
    val forcedTermination: Boolean = false
)

data class FfmpegExecutionPolicy(
    val timeoutMillis: Long = TimeUnit.MINUTES.toMillis(60),
    val gracefulShutdownMillis: Long = TimeUnit.SECONDS.toMillis(2),
    val forcedShutdownMillis: Long = TimeUnit.SECONDS.toMillis(2),
    val logReaderShutdownMillis: Long = TimeUnit.SECONDS.toMillis(2),
    val maxLogLines: Int = 200,
    val maxLogCharacters: Int = 64 * 1024
) {
    init {
        require(timeoutMillis > 0L)
        require(gracefulShutdownMillis > 0L)
        require(forcedShutdownMillis > 0L)
        require(logReaderShutdownMillis > 0L)
        require(maxLogLines > 0)
        require(maxLogCharacters > 0)
    }
}

class AndroidFfmpegCommandRunner(
    context: Context,
    private val executionPolicy: FfmpegExecutionPolicy = FfmpegExecutionPolicy()
) : FfmpegCommandRunner {
    private val appContext = context.applicationContext
    private val processExecutor = FfmpegProcessExecutor(
        policy = executionPolicy,
        processFactory = FfmpegProcessFactory { command, environment ->
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply {
                    this.environment().putAll(environment)
                }
                .start()
        }
    )

    override suspend fun run(arguments: List<String>): FfmpegExecutionResult {
        return withContext(Dispatchers.IO) {
            val startedAtNanos = System.nanoTime()
            try {
                currentCoroutineContext().ensureActive()
                FFmpeg.getInstance().init(appContext)
                currentCoroutineContext().ensureActive()

                val nativeLibraryDir = File(appContext.applicationInfo.nativeLibraryDir)
                val ffmpegBinary = File(nativeLibraryDir, FFMPEG_BINARY_NAME)
                if (!ffmpegBinary.isFile) {
                    return@withContext failure(
                        message = "Binario FFmpeg nao encontrado.",
                        startedAtNanos = startedAtNanos
                    )
                }
                val ffmpegLibraryDir = File(appContext.noBackupFilesDir, FFMPEG_LIBRARY_RELATIVE_PATH)
                if (!ffmpegLibraryDir.isDirectory) {
                    return@withContext failure(
                        message = "Bibliotecas FFmpeg nao encontradas.",
                        startedAtNanos = startedAtNanos
                    )
                }
                val pythonLibraryDir = File(appContext.noBackupFilesDir, PYTHON_LIBRARY_RELATIVE_PATH)
                if (!pythonLibraryDir.isDirectory) {
                    return@withContext failure(
                        message = "Bibliotecas Python nao encontradas.",
                        startedAtNanos = startedAtNanos
                    )
                }
                val aria2cLibraryDir = File(appContext.noBackupFilesDir, ARIA2C_LIBRARY_RELATIVE_PATH)
                val environment = mapOf(
                    "LD_LIBRARY_PATH" to buildList {
                        add(pythonLibraryDir.absolutePath)
                        add(ffmpegLibraryDir.absolutePath)
                        if (aria2cLibraryDir.isDirectory) add(aria2cLibraryDir.absolutePath)
                    }.joinToString(File.pathSeparator)
                )

                processExecutor.execute(
                    command = listOf(ffmpegBinary.absolutePath) + arguments,
                    environment = environment
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                failure(
                    message = exception.message ?: "Falha ao executar FFmpeg.",
                    startedAtNanos = startedAtNanos,
                    exception = exception
                )
            }
        }
    }

    private fun failure(
        message: String,
        startedAtNanos: Long,
        exception: Throwable? = null
    ): FfmpegExecutionResult.Failure {
        return FfmpegExecutionResult.Failure(
            message = message,
            exception = exception,
            diagnostics = FfmpegExecutionDiagnostics(
                durationMillis = elapsedMillis(startedAtNanos)
            )
        )
    }

    private fun elapsedMillis(startedAtNanos: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
            .coerceAtLeast(0L)
    }

    private companion object {
        const val FFMPEG_BINARY_NAME = "libffmpeg.so"
        const val FFMPEG_LIBRARY_RELATIVE_PATH = "youtubedl-android/packages/ffmpeg/usr/lib"
        const val PYTHON_LIBRARY_RELATIVE_PATH = "youtubedl-android/packages/python/usr/lib"
        const val ARIA2C_LIBRARY_RELATIVE_PATH = "youtubedl-android/packages/aria2c/usr/lib"
    }
}
