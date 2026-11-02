package com.example.woodsreader.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteVideoTimingTest {

    @Test
    fun testVideoFramePtsUs_atFrame0() {
        assertEquals(0L, videoFramePtsUs(0, 24))
    }

    @Test
    fun testVideoFramePtsUs_atFrame24() {
        assertEquals(1_000_000L, videoFramePtsUs(24, 24))
    }

    @Test
    fun testVideoFramePtsUs_atFrame25() {
        assertEquals(1_041_666L, videoFramePtsUs(25, 24))
    }

    @Test
    fun testVideoFramePtsUs_strictlyIncreasing() {
        for (i in 0L until 840L) {
            assertTrue(
                "PTS at frame ${i + 1} must be greater than PTS at frame $i",
                videoFramePtsUs(i + 1, 24) > videoFramePtsUs(i, 24)
            )
        }
    }

    @Test
    fun testVideoFramePtsUs_noOverflowAtLargeIndex() {
        val pts = videoFramePtsUs(10_000_000L, 24)
        assertEquals(416_666_666_666L, pts)
        assertTrue(pts > 0L)
    }
}
