package com.androiddownload.download.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CarMediaProbeProcessExecutorTest {
    @Test
    fun processStartFailureReturnsBoundedFailureWithoutExitCode() = runBlocking {
        val executor = CarMediaProbeProcessExecutor(
            policy = policy(),
            processFactory = CarMediaProbeProcessFactory { _, _ ->
                throw IOException("cannot start")
            }
        )

        val result = executor.execute(listOf("ffprobe"))

        assertTrue(result is CarMediaProbeExecutionResult.Failure)
        result as CarMediaProbeExecutionResult.Failure
        assertEquals("cannot start", result.message)
        assertEquals(null, result.exitCode)
        assertTrue(result.exception is IOException)
    }

    @Test
    fun stdoutBelowAndExactlyAtLimitAreReturnedCompletely() = runBlocking {
        listOf("123456789", "1234567890").forEach { stdout ->
            val process = FakeProcess(stdout = stdout, stderr = "", initialExitCode = 0)
            val result = executor(process, policy(maxStdoutBytes = 10))
                .execute(listOf("ffprobe"))

            assertTrue(result is CarMediaProbeExecutionResult.Success)
            assertEquals(stdout, (result as CarMediaProbeExecutionResult.Success).stdout)
        }
    }

    @Test
    fun stdoutAboveLimitTerminatesProbeAndReturnsExplicitFailure() = runBlocking {
        val process = FakeProcess(
            stdout = "12345678901",
            stderr = "",
            initialExitCode = 0
        )

        val result = executor(process, policy(maxStdoutBytes = 10))
            .execute(listOf("ffprobe"))

        assertTrue(result is CarMediaProbeExecutionResult.OutputLimitExceeded)
        assertEquals(
            10,
            (result as CarMediaProbeExecutionResult.OutputLimitExceeded).limitBytes
        )
        assertTrue(process.destroyCalls.get() >= 1)
        assertFalse(process.isAlive)
    }

    @Test
    fun largeStderrIsRetainedOnlyAsConfiguredTail() = runBlocking {
        val stderr = buildString {
            append("x".repeat(100_000))
            append("\r\n")
            repeat(50) { index -> append("line-$index\n") }
            append("last")
        }
        val process = FakeProcess(stdout = "{}", stderr = stderr, initialExitCode = 0)

        val result = executor(
            process,
            policy(
                maxStdoutBytes = 10,
                maxStderrLines = 3,
                maxStderrCharacters = 32
            )
        ).execute(listOf("ffprobe"))

        assertTrue(result is CarMediaProbeExecutionResult.Success)
        assertTrue(result.diagnostics.stderrTail.size <= 3)
        assertTrue(result.diagnostics.stderrTail.sumOf(String::length) <= 32)
        assertEquals("last", result.diagnostics.stderrTail.last())
    }

    @Test
    fun nonZeroExitKeepsStdoutSeparateFromBoundedStderr() = runBlocking {
        val process = FakeProcess(
            stdout = """{"partial":true}""",
            stderr = "diagnostic\nprobe failed\n",
            initialExitCode = 7
        )

        val result = executor(process).execute(listOf("ffprobe"))

        assertTrue(result is CarMediaProbeExecutionResult.Failure)
        result as CarMediaProbeExecutionResult.Failure
        assertEquals(7, result.exitCode)
        assertEquals("probe failed", result.message)
        assertEquals(listOf("diagnostic", "probe failed"), result.diagnostics.stderrTail)
    }

    @Test
    fun timeoutDestroysProcessAndLeavesNoFakeAlive() = runBlocking {
        val process = FakeProcess(stdout = "", stderr = "", initialExitCode = null)

        val result = executor(process, policy(timeoutMillis = 20L))
            .execute(listOf("ffprobe"))

        assertTrue(result is CarMediaProbeExecutionResult.TimedOut)
        assertEquals(1, process.destroyCalls.get())
        assertEquals(0, process.destroyForciblyCalls.get())
        assertFalse(process.isAlive)
    }

    @Test
    fun timeoutForceKillsProcessThatIgnoresGracefulDestroy() = runBlocking {
        val process = FakeProcess(
            stdout = "",
            stderr = "",
            initialExitCode = null,
            ignoreDestroy = true
        )

        val result = executor(process, policy(timeoutMillis = 20L))
            .execute(listOf("ffprobe"))

        assertTrue(result is CarMediaProbeExecutionResult.TimedOut)
        assertTrue(process.destroyCalls.get() >= 1)
        assertEquals(1, process.destroyForciblyCalls.get())
        assertFalse(process.isAlive)
        assertTrue(result.diagnostics.forcedTermination)
    }

    @Test
    fun cancellationDestroysProcessAndPropagates() = runBlocking {
        val process = FakeProcess(stdout = "", stderr = "", initialExitCode = null)
        val started = CountDownLatch(1)
        val executor = CarMediaProbeProcessExecutor(
            policy = policy(timeoutMillis = TimeUnit.SECONDS.toMillis(10)),
            processFactory = CarMediaProbeProcessFactory { _, _ ->
                started.countDown()
                process
            }
        )
        val cancellationObserved = AtomicBoolean(false)
        val job = launch(Dispatchers.Default) {
            try {
                executor.execute(listOf("ffprobe"))
            } catch (exception: CancellationException) {
                cancellationObserved.set(true)
                throw exception
            }
        }

        assertTrue(started.await(1, TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertTrue(cancellationObserved.get())
        assertTrue(process.destroyCalls.get() >= 1)
        assertFalse(process.isAlive)
    }

    @Test
    fun cancellationIsNotHiddenByConcurrentStdoutOverflow() = runBlocking {
        val process = FakeProcess(
            stdout = "x".repeat(100),
            stderr = "",
            initialExitCode = null,
            ignoreDestroy = true
        )
        val executor = executor(
            process,
            policy(
                timeoutMillis = TimeUnit.SECONDS.toMillis(10),
                maxStdoutBytes = 10
            )
        )
        val cancellationObserved = AtomicBoolean(false)
        val job = launch(Dispatchers.Default) {
            try {
                executor.execute(listOf("ffprobe"))
            } catch (exception: CancellationException) {
                cancellationObserved.set(true)
                throw exception
            }
        }

        assertTrue(process.destroyCalled.await(1, TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertTrue(cancellationObserved.get())
        assertEquals(1, process.destroyForciblyCalls.get())
        assertFalse(process.isAlive)
    }

    @Test
    fun policiesHaveDedicatedCentralizedDefaults() {
        val policy = CarMediaProbeExecutionPolicy()

        assertEquals(TimeUnit.SECONDS.toMillis(30), policy.timeoutMillis)
        assertEquals(256 * 1024, policy.maxStdoutBytes)
        assertEquals(TimeUnit.MINUTES.toMillis(10), CarMediaFastPathPolicy.REMUX_TIMEOUT_MILLIS)
    }

    private fun executor(
        process: FakeProcess,
        policy: CarMediaProbeExecutionPolicy = policy()
    ): CarMediaProbeProcessExecutor {
        return CarMediaProbeProcessExecutor(
            policy = policy,
            processFactory = CarMediaProbeProcessFactory { _, _ -> process }
        )
    }

    private fun policy(
        timeoutMillis: Long = 100L,
        maxStdoutBytes: Int = 1024,
        maxStderrLines: Int = 20,
        maxStderrCharacters: Int = 1024
    ): CarMediaProbeExecutionPolicy {
        return CarMediaProbeExecutionPolicy(
            timeoutMillis = timeoutMillis,
            gracefulShutdownMillis = 20L,
            forcedShutdownMillis = 20L,
            readerShutdownMillis = 20L,
            maxStdoutBytes = maxStdoutBytes,
            maxStderrLines = maxStderrLines,
            maxStderrCharacters = maxStderrCharacters
        )
    }

    private class FakeProcess(
        stdout: String,
        stderr: String,
        initialExitCode: Int?,
        private val ignoreDestroy: Boolean = false
    ) : Process() {
        private val outputStream = ByteArrayOutputStream()
        private val inputStream = ByteArrayInputStream(stdout.toByteArray())
        private val errorStream = ByteArrayInputStream(stderr.toByteArray())
        private val finished = CountDownLatch(if (initialExitCode == null) 1 else 0)
        private val alive = AtomicBoolean(initialExitCode == null)

        @Volatile
        private var exitCode: Int = initialExitCode ?: 0

        val destroyCalls = AtomicInteger()
        val destroyForciblyCalls = AtomicInteger()
        val destroyCalled = CountDownLatch(1)

        override fun getOutputStream(): OutputStream = outputStream
        override fun getInputStream(): InputStream = inputStream
        override fun getErrorStream(): InputStream = errorStream

        override fun waitFor(): Int {
            finished.await()
            return exitCode
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            return finished.await(timeout, unit)
        }

        override fun exitValue(): Int {
            if (alive.get()) throw IllegalThreadStateException("Process is still running")
            return exitCode
        }

        override fun destroy() {
            destroyCalls.incrementAndGet()
            destroyCalled.countDown()
            if (!ignoreDestroy) finish(-1)
        }

        override fun destroyForcibly(): Process {
            destroyForciblyCalls.incrementAndGet()
            finish(-9)
            return this
        }

        override fun isAlive(): Boolean = alive.get()

        private fun finish(code: Int) {
            if (alive.compareAndSet(true, false)) {
                exitCode = code
                finished.countDown()
            }
        }
    }
}
