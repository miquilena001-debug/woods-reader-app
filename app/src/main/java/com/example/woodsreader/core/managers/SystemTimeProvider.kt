package com.example.woodsreader.core.managers

import java.time.LocalDate
import javax.inject.Inject

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun currentDateString(): String = LocalDate.now().toString()
}
