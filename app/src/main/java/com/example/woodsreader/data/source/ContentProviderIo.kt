package com.example.woodsreader.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

internal class ProviderIoTimeoutException(cause: Throwable? = null) :
    IOException("Content provider operation timed out", cause)

internal class ProviderIoSaturatedException(cause: Throwable? = null) :
    IOException("Content provider workers are unavailable", cause)

/**
 * Isolates synchronous provider calls from app dispatchers. A timed-out Binder call may ignore
 * interruption; at most two such calls can retain worker threads, after which work fails fast.
 */
internal object ContentProviderIo {
    private const val DEFAULT_TIMEOUT_MS = 8_000L
    private const val MAX_BLOCKED_PROVIDER_CALLS = 2
    private val threadIds = AtomicInteger()
    private val executor = ThreadPoolExecutor(
        0,
        MAX_BLOCKED_PROVIDER_CALLS,
        30L,
        TimeUnit.SECONDS,
        SynchronousQueue(),
        ThreadFactory { runnable ->
            Thread(runnable, "woods-provider-io-${threadIds.incrementAndGet()}").apply {
                isDaemon = true
            }
        }
    )

    suspend fun <T> run(
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        operation: () -> T
    ): T {
        require(timeoutMillis > 0)
        val future = try {
            executor.submit(Callable(operation))
        } catch (e: RejectedExecutionException) {
            throw ProviderIoSaturatedException(e)
        }

        return try {
            withContext(Dispatchers.IO) {
                runInterruptible {
                    try {
                        future.get(timeoutMillis, TimeUnit.MILLISECONDS)
                    } catch (e: TimeoutException) {
                        throw ProviderIoTimeoutException(e)
                    } catch (e: ExecutionException) {
                        throw (e.cause ?: e)
                    }
                }
            }
        } finally {
            future.cancel(true)
        }
    }
}
