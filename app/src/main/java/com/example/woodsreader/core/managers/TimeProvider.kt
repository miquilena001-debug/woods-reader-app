package com.example.woodsreader.core.managers

interface TimeProvider {
    fun currentTimeMillis(): Long
    fun elapsedRealtime(): Long = currentTimeMillis()
    fun currentDateString(): String
}
