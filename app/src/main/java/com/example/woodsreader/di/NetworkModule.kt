package com.example.woodsreader.di

import com.example.woodsreader.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(SafeNetworkDebugInterceptor())
        }
        return builder.build()
    }
}

internal class SafeNetworkDebugInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAt = System.nanoTime()
        return try {
            chain.proceed(request).also { response ->
                Timber.d(
                    "HTTP %s completed with status %d in %d ms",
                    request.method,
                    response.code,
                    elapsedMillis(startedAt)
                )
            }
        } catch (e: IOException) {
            Timber.w(
                "HTTP %s failed (%s) after %d ms",
                request.method,
                e.javaClass.simpleName,
                elapsedMillis(startedAt)
            )
            throw e
        }
    }

    private fun elapsedMillis(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
}
