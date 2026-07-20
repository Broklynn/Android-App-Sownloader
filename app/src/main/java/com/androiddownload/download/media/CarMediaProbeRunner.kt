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

interface CarMediaProbeRunner {
    suspend fun run(arguments: List<String>): CarMediaProbeExecutionResult
}

sealed interface CarMediaProbeExecutionResult {
    abstract val diagnostics: CarMediaProbeDiagnostics

    data class Success(
        val stdout: String,
        override val diagnostics: CarMediaProbeDiagnostics = CarMediaProbeDiagnostics()
    ) : CarMediaProbeExecutionResult

    data class Failure(
        val message: String,
        val exitCode: Int? = null,
        val exception: Throwable? = null,
        override val diagnostics: CarMediaProbeDiagnostics = CarMediaProbeDiagnostics()
    ) : CarMediaProbeExecutionResult

    data class TimedOut(
        val timeoutMillis: Long,
        override val diagnostics: CarMediaProbeDiagnostics
    ) : CarMediaProbeExecutionResult

    data class OutputLimitExceeded(
        val limitBytes: Int,
        override val diagnostics: CarMediaProbeDiagnostics
    ) : CarMediaProbeExecutionResult
}

data class CarMediaProbeDiagnostics(
    val stderrTail: List<String> = emptyList(),
    val durationMillis: Long = 0L,
    val forcedTermination: Boolean = false
)

data class CarMediaProbeExecutionPolicy(
    val timeoutMillis: Long = CarMediaFastPathPolicy.PROBE_TIMEOUT_MILLIS,
    val gracefulShutdownMillis: Long = TimeUnit.SECONDS.toMillis(1),
    val forcedShutdownMillis: Long = TimeUnit.SECONDS.toMillis(2),
    val readerShutdownMillis: Long = TimeUnit.SECONDS.toMillis(2),
    val maxStdoutBytes: Int = 256 * 1024,
    val maxStderrLines: Int = 100,
    val maxStderrCharacters: Int = 16 * 1024
) {
    init {
        require(timeoutMillis > 0L)
        require(gracefulShutdownMillis > 0L)
        require(forcedShutdownMillis > 0L)
        require(readerShutdownMillis > 0L)
        require(maxStdoutBytes > 0)
        require(maxStderrLines > 0)
        require(maxStderrCharacters > 0)
    }
}

object CarMediaFastPathPolicy {
    val PROBE_TIMEOUT_MILLIS: Long = TimeUnit.SECONDS.toMillis(30)
    val REMUX_TIMEOUT_MILLIS: Long = TimeUnit.MINUTES.toMillis(10)
}

class AndroidCarMediaProbeRunner(
    context: Context,
    private val executionPolicy: CarMediaProbeExecutionPolicy = CarMediaProbeExecutionPolicy()
) : CarMediaProbeRunner {
    private val appContext = context.applicationContext
    private val processExecutor = CarMediaProbeProcessExecutor(
        policy = executionPolicy,
        processFactory = CarMediaProbeProcessFactory { command, environment ->
            ProcessBuilder(command)
                .redirectErrorStream(false)
                .apply {
                    this.environment().putAll(environment)
                }
                .start()
        }
    )

    override suspend fun run(arguments: List<String>): CarMediaProbeExecutionResult {
        return withContext(Dispatchers.IO) {
            val startedAtNanos = System.nanoTime()
            try {
                currentCoroutineContext().ensureActive()
                FFmpeg.getInstance().init(appContext)
                currentCoroutineContext().ensureActive()

                val nativeLibraryDir = File(appContext.applicationInfo.nativeLibraryDir)
                val ffprobeBinary = File(nativeLibraryDir, FFPROBE_BINARY_NAME)
                if (!ffprobeBinary.isFile) {
                    return@withContext failure(
                        message = "Binario ffprobe nao encontrado.",
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
                    command = listOf(ffprobeBinary.absolutePath) + arguments,
                    environment = environment
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                failure(
                    message = exception.message ?: "Falha ao executar ffprobe.",
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
    ): CarMediaProbeExecutionResult.Failure {
        return CarMediaProbeExecutionResult.Failure(
            message = message,
            exception = exception,
            diagnostics = CarMediaProbeDiagnostics(
                durationMillis = elapsedMillis(startedAtNanos)
            )
        )
    }

    private fun elapsedMillis(startedAtNanos: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
            .coerceAtLeast(0L)
    }

    private companion object {
        const val FFPROBE_BINARY_NAME = "libffprobe.so"
        const val FFMPEG_LIBRARY_RELATIVE_PATH = "youtubedl-android/packages/ffmpeg/usr/lib"
        const val PYTHON_LIBRARY_RELATIVE_PATH = "youtubedl-android/packages/python/usr/lib"
        const val ARIA2C_LIBRARY_RELATIVE_PATH = "youtubedl-android/packages/aria2c/usr/lib"
    }
}
