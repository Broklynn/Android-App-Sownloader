package com.androiddownload.download.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

internal fun interface FfmpegProcessFactory {
    fun start(command: List<String>, environment: Map<String, String>): Process
}

internal class FfmpegProcessExecutor(
    private val policy: FfmpegExecutionPolicy,
    private val processFactory: FfmpegProcessFactory,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nanoTime: () -> Long = System::nanoTime
) {
    suspend fun execute(
        command: List<String>,
        environment: Map<String, String> = emptyMap()
    ): FfmpegExecutionResult = coroutineScope {
        val startedAtNanos = nanoTime()
        val logTail = BoundedLogTail(policy.maxLogLines, policy.maxLogCharacters)
        var process: Process? = null
        var logReaderJob: Job? = null
        var forcedTermination = false
        var outcome: ExecutionOutcome = ExecutionOutcome.Failure(
            message = "Falha ao executar FFmpeg.",
            exitCode = null,
            exception = null
        )

        try {
            currentCoroutineContext().ensureActive()
            val ownedProcess = processFactory.start(command, environment)
            process = ownedProcess
            closeProcessInput(ownedProcess)
            currentCoroutineContext().ensureActive()

            logReaderJob = launch(dispatcher) {
                drainOutput(ownedProcess, logTail)
            }

            val finished = runInterruptible(dispatcher) {
                ownedProcess.waitFor(policy.timeoutMillis, TimeUnit.MILLISECONDS)
            }
            currentCoroutineContext().ensureActive()
            outcome = if (finished) {
                ExecutionOutcome.Completed(ownedProcess.exitValue())
            } else {
                ExecutionOutcome.Timeout
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            outcome = ExecutionOutcome.Failure(
                message = exception.message ?: "Falha ao executar FFmpeg.",
                exitCode = null,
                exception = exception
            )
        } finally {
            withContext(NonCancellable + dispatcher) {
                process?.let { ownedProcess ->
                    if (ownedProcess.isAlive) {
                        forcedTermination = terminate(ownedProcess)
                    }
                    finishLogReader(ownedProcess, logReaderJob)
                    closeProcessStreams(ownedProcess)
                }
            }
        }

        val diagnostics = FfmpegExecutionDiagnostics(
            logTail = logTail.snapshot(),
            durationMillis = elapsedMillis(startedAtNanos),
            forcedTermination = forcedTermination
        )
        when (val completedOutcome = outcome) {
            is ExecutionOutcome.Completed -> {
                if (completedOutcome.exitCode == 0) {
                    FfmpegExecutionResult.Success(diagnostics)
                } else {
                    FfmpegExecutionResult.Failure(
                        message = failureMessage(logTail.snapshot(), completedOutcome.exitCode),
                        exitCode = completedOutcome.exitCode,
                        diagnostics = diagnostics
                    )
                }
            }
            is ExecutionOutcome.Failure -> FfmpegExecutionResult.Failure(
                message = completedOutcome.message,
                exitCode = completedOutcome.exitCode,
                exception = completedOutcome.exception,
                diagnostics = diagnostics
            )
            ExecutionOutcome.Timeout -> FfmpegExecutionResult.TimedOut(
                timeoutMillis = policy.timeoutMillis,
                diagnostics = diagnostics
            )
        }
    }

    private suspend fun drainOutput(process: Process, logTail: BoundedLogTail) {
        try {
            runInterruptible(dispatcher) {
                process.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        logTail.add(line)
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: IOException) {
            // Closing the process stream during cancellation/timeout is expected.
        }
    }

    private fun terminate(process: Process): Boolean {
        process.destroy()
        if (waitFor(process, policy.gracefulShutdownMillis)) {
            return false
        }

        process.destroyForcibly()
        waitFor(process, policy.forcedShutdownMillis)
        return true
    }

    private fun waitFor(process: Process, timeoutMillis: Long): Boolean {
        return try {
            process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private suspend fun finishLogReader(process: Process, logReaderJob: Job?) {
        if (logReaderJob == null) return
        val completed = withTimeoutOrNull(policy.logReaderShutdownMillis) {
            logReaderJob.join()
            true
        } == true
        if (completed) return

        runCatching { process.inputStream.close() }
        logReaderJob.cancel()
        withTimeoutOrNull(policy.logReaderShutdownMillis) {
            logReaderJob.join()
        }
    }

    private fun closeProcessInput(process: Process) {
        runCatching { process.outputStream.close() }
    }

    private fun closeProcessStreams(process: Process) {
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
    }

    private fun failureMessage(logTail: List<String>, exitCode: Int): String {
        return logTail.lastOrNull { it.isNotBlank() }?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "FFmpeg falhou com codigo $exitCode."
    }

    private fun elapsedMillis(startedAtNanos: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(nanoTime() - startedAtNanos)
            .coerceAtLeast(0L)
    }

    private sealed interface ExecutionOutcome {
        data class Completed(val exitCode: Int) : ExecutionOutcome
        data class Failure(
            val message: String,
            val exitCode: Int?,
            val exception: Throwable?
        ) : ExecutionOutcome
        data object Timeout : ExecutionOutcome
    }
}

private class BoundedLogTail(
    private val maxLines: Int,
    private val maxCharacters: Int
) {
    private val lines = ArrayDeque<String>()
    private var characterCount = 0

    @Synchronized
    fun add(line: String) {
        val retainedLine = line.takeLast(maxCharacters)
        while (lines.isNotEmpty() &&
            (lines.size >= maxLines || characterCount + retainedLine.length > maxCharacters)
        ) {
            characterCount -= lines.removeFirst().length
        }
        lines.addLast(retainedLine)
        characterCount += retainedLine.length
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()
}
