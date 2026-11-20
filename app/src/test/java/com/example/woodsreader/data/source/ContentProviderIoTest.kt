package com.example.woodsreader.data.source

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ContentProviderIoTest {
    @Test
    fun `provider timeout returns without waiting for an uninterruptible operation`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val startedAt = System.nanoTime()

        try {
            ContentProviderIo.run(timeoutMillis = 100) {
                entered.countDown()
                while (true) {
                    try {
                        release.await()
                        break
                    } catch (_: InterruptedException) {
                        // Simulates a provider call that does not honor interruption.
                    }
                }
                "done"
            }
        } catch (_: ProviderIoTimeoutException) {
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            assertTrue("caller waited $elapsedMs ms after provider timeout", elapsedMs < 1_000)
            return@runBlocking
        } finally {
            release.countDown()
        }
        throw AssertionError("Expected the provider operation to time out")
    }

    @Test
    fun `provider operations return values and propagate failures`() = runBlocking {
        assertEquals("ok", ContentProviderIo.run { "ok" })

        val expected = IllegalStateException("provider failure")
        try {
            ContentProviderIo.run { throw expected }
            throw AssertionError("Expected provider failure")
        } catch (actual: IllegalStateException) {
            assertEquals(expected.message, actual.message)
        }
    }

    @Test
    fun `provider pool fails fast when all isolated workers are stuck`() = runBlocking {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val blockers = (1..2).map {
            async(Dispatchers.IO) {
                try {
                    ContentProviderIo.run(timeoutMillis = 150) {
                        entered.countDown()
                        while (true) {
                            try {
                                release.await()
                                break
                            } catch (_: InterruptedException) {
                                // Simulates Binder work that ignores thread interruption.
                            }
                        }
                        "done"
                    }
                } catch (_: ProviderIoTimeoutException) {
                    Unit
                }
            }
        }

        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val startedAt = System.nanoTime()
            try {
                ContentProviderIo.run(timeoutMillis = 150) { "unexpected" }
                throw AssertionError("Expected saturated provider pool")
            } catch (_: ProviderIoSaturatedException) {
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                assertTrue("saturated call waited $elapsedMs ms", elapsedMs < 500)
            }
        } finally {
            release.countDown()
        }
        blockers.forEach { it.await() }
    }
}
