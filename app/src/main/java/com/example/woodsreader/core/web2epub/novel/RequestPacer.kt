package com.example.woodsreader.core.web2epub.novel

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class RequestPacer(
    private val minMs: Long,
    private val maxMs: Long,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val random: Random = Random.Default
) {
    private val mutex = Mutex()
    private var lastRequestAt: Long = 0L

    suspend fun await() {
        mutex.withLock {
            val currentTime = now()
            if (lastRequestAt > 0L) {
                val effectiveMin = maxOf(0L, minMs)
                val effectiveMax = maxOf(effectiveMin, maxMs)
                val targetDelay = if (effectiveMax > effectiveMin) {
                    effectiveMin + (random.nextDouble() * (effectiveMax - effectiveMin)).toLong()
                } else {
                    effectiveMin
                }

                val elapsed = currentTime - lastRequestAt
                val waitTime = targetDelay - elapsed
                if (waitTime > 0L) {
                    delay(waitTime)
                }
            }
            lastRequestAt = now()
        }
    }
}
