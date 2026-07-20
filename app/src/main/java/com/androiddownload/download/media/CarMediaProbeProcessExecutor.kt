package com.androiddownload.download.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

internal fun interface CarMediaProbeProcessFactory {
    fun start(command: List<String>, environment: Map<String, String>): Process
}

internal class CarMediaProbeProcessExecutor(
    private val policy: CarMediaProbeExecutionPolicy,
    private val processFactory: CarMediaProbeProcessFactory,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nanoTime: () -> Long = System::nanoTime
) {
    suspend fun execute(
        command: List<String>,
        environment: Map<String, String> = emptyMap()
    ): CarMediaProbeExecutionResult = coroutineScope {
        val startedAtNanos = nanoTime()
        val stdout = BoundedProbeStdout(policy.maxStdoutBytes)
        val stderr = BoundedProbeStderr(
            maxLines = policy.maxStderrLines,
            maxCharacters = policy.maxStderrCharacters
        )
        var process: Process? = null
        var stdoutReader: Job? = null
        var stderrReader: Job? = null
        var forcedTermination = false
        var outcome: ProbeExecutionOutcome = ProbeExecutionOutcome.Failure(
            message = "Falha ao executar ffprobe.",
            exitCode = null,
            exception = null
        )

        try {
            currentCoroutineContext().ensureActive()
            val ownedProcess = processFactory.start(command, environment)
            process = ownedProcess
            closeProcessInput(ownedProcess)
            currentCoroutineContext().ensureActive()

            stdoutReader = launch(dispatcher) {
                drainStdout(ownedProcess, stdout)
            }
            stderrReader = launch(dispatcher) {
                drainStderr(ownedProcess, stderr)
            }

            val finished = runInterruptible(dispatcher) {
                ownedProcess.waitFor(policy.timeoutMillis, TimeUnit.MILLISECONDS)
            }
            currentCoroutineContext().ensureActive()
            outcome = if (finished) {
                ProbeExecutionOutcome.Completed(ownedProcess.exitValue())
            } else {
                ProbeExecutionOutcome.Timeout
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            outcome = ProbeExecutionOutcome.Failure(
                message = exception.message ?: "Falha ao executar ffprobe.",
                exitCode = null,
                exception = exception
            )
        } finally {
            withContext(NonCancellable + dispatcher) {
                process?.let { ownedProcess ->
                    if (ownedProcess.isAlive) {
                        forcedTermination = terminate(ownedProcess)
                    }
                    finishReaders(ownedProcess, listOfNotNull(stdoutReader, stderrReader))
                    closeProcessStreams(ownedProcess)
                }
            }
        }
        currentCoroutineContext().ensureActive()

        val diagnostics = CarMediaProbeDiagnostics(
            stderrTail = stderr.snapshot(),
            durationMillis = elapsedMillis(startedAtNanos),
            forcedTermination = forcedTermination
        )
        if (stdout.exceededLimit) {
            return@coroutineScope CarMediaProbeExecutionResult.OutputLimitExceeded(
                limitBytes = policy.maxStdoutBytes,
                diagnostics = diagnostics
            )
        }
        when (val completedOutcome = outcome) {
            is ProbeExecutionOutcome.Completed -> {
                if (completedOutcome.exitCode == 0) {
                    CarMediaProbeExecutionResult.Success(
                        stdout = stdout.snapshot(),
                        diagnostics = diagnostics
                    )
                } else {
                    CarMediaProbeExecutionResult.Failure(
                        message = failureMessage(stderr.snapshot(), completedOutcome.exitCode),
                        exitCode = completedOutcome.exitCode,
                        diagnostics = diagnostics
                    )
                }
            }
            is ProbeExecutionOutcome.Failure -> CarMediaProbeExecutionResult.Failure(
                message = completedOutcome.message,
                exitCode = completedOutcome.exitCode,
                exception = completedOutcome.exception,
                diagnostics = diagnostics
            )
            ProbeExecutionOutcome.Timeout -> CarMediaProbeExecutionResult.TimedOut(
                timeoutMillis = policy.timeoutMillis,
                diagnostics = diagnostics
            )
        }
    }

    private suspend fun drainStdout(
        process: Process,
        stdout: BoundedProbeStdout
    ) {
        try {
            runInterruptible(dispatcher) {
                process.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (!stdout.append(buffer, count)) {
                            process.destroy()
                            break
                        }
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: IOException) {
            // Closing the process stream during cancellation/timeout is expected.
        }
    }

    private suspend fun drainStderr(
        process: Process,
        stderr: BoundedProbeStderr
    ) {
        try {
            runInterruptible(dispatcher) {
                InputStreamReader(process.errorStream, StandardCharsets.UTF_8).use { reader ->
                    val buffer = CharArray(2048)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        stderr.append(buffer, count)
                    }
                    stderr.finish()
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

    private suspend fun finishReaders(process: Process, readers: List<Job>) {
        if (readers.isEmpty()) return
        val completed = withTimeoutOrNull(policy.readerShutdownMillis) {
            readers.joinAll()
            true
        } == true
        if (completed) return

        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        readers.forEach(Job::cancel)
        withTimeoutOrNull(policy.readerShutdownMillis) {
            readers.joinAll()
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

    private fun failureMessage(stderrTail: List<String>, exitCode: Int): String {
        return stderrTail.lastOrNull { it.isNotBlank() }?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "ffprobe falhou com codigo $exitCode."
    }

    private fun elapsedMillis(startedAtNanos: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(nanoTime() - startedAtNanos)
            .coerceAtLeast(0L)
    }

    private sealed interface ProbeExecutionOutcome {
        data class Completed(val exitCode: Int) : ProbeExecutionOutcome
        data class Failure(
            val message: String,
            val exitCode: Int?,
            val exception: Throwable?
        ) : ProbeExecutionOutcome
        data object Timeout : ProbeExecutionOutcome
    }
}

private class BoundedProbeStdout(
    private val maxBytes: Int
) {
    private val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))

    @Volatile
    var exceededLimit: Boolean = false
        private set

    @Synchronized
    fun append(buffer: ByteArray, count: Int): Boolean {
        if (exceededLimit) return false
        if (output.size() + count > maxBytes) {
            exceededLimit = true
            return false
        }
        output.write(buffer, 0, count)
        return true
    }

    @Synchronized
    fun snapshot(): String {
        return output.toString(StandardCharsets.UTF_8.name())
    }
}

private class BoundedProbeStderr(
    private val maxLines: Int,
    private val maxCharacters: Int
) {
    private val lines = ArrayDeque<String>()
    private val currentLine = StringBuilder()
    private var characterCount = 0
    private var previousWasCarriageReturn = false

    @Synchronized
    fun append(buffer: CharArray, count: Int) {
        for (index in 0 until count) {
            val character = buffer[index]
            when (character) {
                '\r' -> {
                    commitLine()
                    previousWasCarriageReturn = true
                }
                '\n' -> {
                    if (!previousWasCarriageReturn) commitLine()
                    previousWasCarriageReturn = false
                }
                else -> {
                    previousWasCarriageReturn = false
                    if (currentLine.length >= maxCharacters) {
                        currentLine.delete(0, minOf(currentLine.length, 1024))
                    }
                    currentLine.append(character)
                }
            }
        }
    }

    @Synchronized
    fun finish() {
        if (currentLine.isNotEmpty()) commitLine()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    private fun commitLine() {
        val retainedLine = currentLine.toString().takeLast(maxCharacters)
        currentLine.setLength(0)
        while (lines.isNotEmpty() &&
            (lines.size >= maxLines || characterCount + retainedLine.length > maxCharacters)
        ) {
            characterCount -= lines.removeFirst().length
        }
        lines.addLast(retainedLine)
        characterCount += retainedLine.length
    }
}
