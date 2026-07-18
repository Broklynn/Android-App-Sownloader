package com.androiddownload.download.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

class FfmpegProcessExecutorTest {
    @Test
    fun successfulProcessReturnsExitCodeAndLogTail() = runBlocking {
        val process = FakeProcess(
            output = "start\nframe=1\nfinished\n",
            initialExitCode = 0
        )

        val result = executor(process).execute(listOf("ffmpeg", "-version"))

        assertTrue(result is FfmpegExecutionResult.Success)
        assertEquals(
            listOf("start", "frame=1", "finished"),
            result.diagnostics.logTail
        )
        assertFalse(result.diagnostics.forcedTermination)
    }

    @Test
    fun nonZeroExitReturnsFailureWithLastUsefulLine() = runBlocking {
        val process = FakeProcess(
            output = "configuration\ninput metadata\nencoder failed\n",
            initialExitCode = 7
        )

        val result = executor(process).execute(listOf("ffmpeg"))

        assertTrue(result is FfmpegExecutionResult.Failure)
        result as FfmpegExecutionResult.Failure
        assertEquals(7, result.exitCode)
        assertEquals("encoder failed", result.message)
        assertEquals(
            listOf("configuration", "input metadata", "encoder failed"),
            result.diagnostics.logTail
        )
    }

    @Test
    fun processStartFailureReturnsFailureWithoutExitCode() = runBlocking {
        val executor = FfmpegProcessExecutor(
            policy = shortPolicy(),
            processFactory = FfmpegProcessFactory { _, _ ->
                throw IOException("cannot start")
            }
        )

        val result = executor.execute(listOf("ffmpeg"))

        assertTrue(result is FfmpegExecutionResult.Failure)
        result as FfmpegExecutionResult.Failure
        assertEquals("cannot start", result.message)
        assertNull(result.exitCode)
        assertTrue(result.exception is IOException)
    }

    @Test
    fun timeoutDestroysProcessGracefully() = runBlocking {
        val process = FakeProcess(output = "", initialExitCode = null)

        val result = executor(process).execute(listOf("ffmpeg"))

        assertTrue(result is FfmpegExecutionResult.TimedOut)
        assertEquals(1, process.destroyCalls.get())
        assertEquals(0, process.destroyForciblyCalls.get())
        assertFalse(process.isAlive)
        assertFalse(result.diagnostics.forcedTermination)
    }

    @Test
    fun timeoutForcesProcessThatIgnoresGracefulDestroy() = runBlocking {
        val process = FakeProcess(
            output = "",
            initialExitCode = null,
            ignoreDestroy = true
        )

        val result = executor(process).execute(listOf("ffmpeg"))

        assertTrue(result is FfmpegExecutionResult.TimedOut)
        assertEquals(1, process.destroyCalls.get())
        assertEquals(1, process.destroyForciblyCalls.get())
        assertFalse(process.isAlive)
        assertTrue(result.diagnostics.forcedTermination)
    }

    @Test
    fun cancellationDestroysProcessAndPropagatesCancellationException() = runBlocking {
        val process = FakeProcess(output = "", initialExitCode = null)
        val started = CountDownLatch(1)
        val executor = FfmpegProcessExecutor(
            policy = shortPolicy(timeoutMillis = TimeUnit.SECONDS.toMillis(10)),
            processFactory = FfmpegProcessFactory { _, _ ->
                started.countDown()
                process
            }
        )
        val cancellationObserved = AtomicBoolean(false)
        val job = launch(Dispatchers.Default) {
            try {
                executor.execute(listOf("ffmpeg"))
            } catch (exception: CancellationException) {
                cancellationObserved.set(true)
                throw exception
            }
        }

        assertTrue(started.await(1, TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertTrue(cancellationObserved.get())
        assertEquals(1, process.destroyCalls.get())
        assertFalse(process.isAlive)
    }

    @Test
    fun logRetentionKeepsOnlyConfiguredTail() = runBlocking {
        val output = (1..20).joinToString(separator = "\n", postfix = "\n") { "line-$it" }
        val process = FakeProcess(output = output, initialExitCode = 0)
        val policy = shortPolicy(maxLogLines = 3, maxLogCharacters = 100)

        val result = executor(process, policy).execute(listOf("ffmpeg"))

        assertEquals(
            listOf("line-18", "line-19", "line-20"),
            result.diagnostics.logTail
        )
        assertTrue(result.diagnostics.logTail.sumOf(String::length) <= 100)
    }

    @Test
    fun oversizedLogLineIsRetainedWithinCharacterLimit() = runBlocking {
        val process = FakeProcess(
            output = "${"x".repeat(500)}\nlast\n",
            initialExitCode = 0
        )
        val policy = shortPolicy(maxLogLines = 10, maxLogCharacters = 32)

        val result = executor(process, policy).execute(listOf("ffmpeg"))

        assertTrue(result.diagnostics.logTail.sumOf(String::length) <= 32)
        assertEquals("last", result.diagnostics.logTail.last())
    }

    private fun executor(
        process: FakeProcess,
        policy: FfmpegExecutionPolicy = shortPolicy()
    ): FfmpegProcessExecutor {
        return FfmpegProcessExecutor(
            policy = policy,
            processFactory = FfmpegProcessFactory { _, _ -> process }
        )
    }

    private fun shortPolicy(
        timeoutMillis: Long = 20L,
        maxLogLines: Int = 20,
        maxLogCharacters: Int = 1024
    ): FfmpegExecutionPolicy {
        return FfmpegExecutionPolicy(
            timeoutMillis = timeoutMillis,
            gracefulShutdownMillis = 20L,
            forcedShutdownMillis = 20L,
            logReaderShutdownMillis = 20L,
            maxLogLines = maxLogLines,
            maxLogCharacters = maxLogCharacters
        )
    }

    private class FakeProcess(
        output: String,
        initialExitCode: Int?,
        private val ignoreDestroy: Boolean = false
    ) : Process() {
        private val outputStream = ByteArrayOutputStream()
        private val inputStream = ByteArrayInputStream(output.toByteArray())
        private val errorStream = ByteArrayInputStream(ByteArray(0))
        private val finished = CountDownLatch(if (initialExitCode == null) 1 else 0)
        private val alive = AtomicBoolean(initialExitCode == null)

        @Volatile
        private var exitCode: Int = initialExitCode ?: 0

        val destroyCalls = AtomicInteger()
        val destroyForciblyCalls = AtomicInteger()

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
