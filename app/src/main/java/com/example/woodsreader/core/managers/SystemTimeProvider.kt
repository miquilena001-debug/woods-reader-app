package com.example.woodsreader.core.managers

import android.os.SystemClock
import java.time.LocalDate
import javax.inject.Inject

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun currentDateString(): String = LocalDate.now().toString()
}
