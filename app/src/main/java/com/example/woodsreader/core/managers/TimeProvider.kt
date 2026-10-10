package com.example.woodsreader.core.managers

interface TimeProvider {
    fun currentTimeMillis(): Long
    fun currentDateString(): String
}
