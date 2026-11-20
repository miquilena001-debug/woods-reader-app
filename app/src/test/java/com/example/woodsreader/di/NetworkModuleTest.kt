package com.example.woodsreader.di

import com.example.woodsreader.BuildConfig
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber

class NetworkModuleTest {
    @Test
    fun `shared client uses phase timeouts without a total transfer deadline`() {
        val client = NetworkModule.provideOkHttpClient()

        assertEquals(20_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertEquals(0, client.callTimeoutMillis)
        assertEquals(
            BuildConfig.DEBUG,
            client.interceptors.any { it is SafeNetworkDebugInterceptor }
        )
    }

    @Test
    fun `debug interceptor logs status without URL or request headers`() {
        val request = Request.Builder()
            .url("https://example.test/private/book?token=secret")
            .header("Authorization", "Bearer sensitive")
            .get()
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("payload".toResponseBody())
            .build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns response
        val tree = RecordingTree()

        Timber.uprootAll()
        Timber.plant(tree)
        try {
            SafeNetworkDebugInterceptor().intercept(chain)

            val log = tree.messages.single()
            assertTrue(log.contains("GET"))
            assertTrue(log.contains("200"))
            assertFalse(log.contains("example.test"))
            assertFalse(log.contains("private/book"))
            assertFalse(log.contains("secret"))
            assertFalse(log.contains("sensitive"))
        } finally {
            Timber.uprootAll()
        }
    }

    private class RecordingTree : Timber.Tree() {
        val messages = mutableListOf<String>()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            messages.add(message)
        }
    }
}
