package com.tejaswin.thumper.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSummaryTest {

    @Test
    fun `average HR computed from readings`() {
        val summary = computeSummary(60L, listOf(100, 120, 140), 0, 0L)
        assertEquals(120, summary.avgHeartRate)
    }

    @Test
    fun `no HR readings yields null avgHeartRate`() {
        val summary = computeSummary(60L, emptyList(), 0, 0L)
        assertNull(summary.avgHeartRate)
    }

    @Test
    fun `zero jumps yields null jumpCount and null jumpsPerMinute`() {
        val summary = computeSummary(60L, listOf(100), 0, 0L)
        assertNull(summary.jumpCount)
        assertNull(summary.jumpsPerMinute)
    }

    @Test
    fun `jumps per minute based on jump time`() {
        // 30 jumps, jump time = 90s -> 30 / (90/60) = 20 jpm
        val summary = computeSummary(120L, emptyList(), 30, 90_000L)
        assertEquals(30, summary.jumpCount)
        assertEquals(20.0, summary.jumpsPerMinute!!, 0.001)
        assertEquals(90L, summary.jumpTimeSeconds)
    }

    @Test
    fun `zero jump time yields null jumpsPerMinute`() {
        val summary = computeSummary(60L, emptyList(), 5, 0L)
        assertEquals(5, summary.jumpCount)
        assertNull(summary.jumpsPerMinute)
    }

    @Test
    fun `durationSeconds propagated correctly`() {
        val summary = computeSummary(300L, emptyList(), 0, 0L)
        assertEquals(300L, summary.durationSeconds)
    }
}
