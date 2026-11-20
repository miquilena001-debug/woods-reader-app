package com.example.woodsreader.data.source

import java.util.concurrent.atomic.AtomicLong

internal class LatestImportRequestGate {
    private val latestRequest = AtomicLong()

    fun begin(): Long = latestRequest.incrementAndGet()

    fun isCurrent(requestId: Long): Boolean = latestRequest.get() == requestId
}
