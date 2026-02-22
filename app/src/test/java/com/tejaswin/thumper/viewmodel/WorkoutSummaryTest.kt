package com.tejaswin.thumper.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSummaryTest {

    @Test
    fun `average HR computed from readings`() {
        val summary = computeSummary(60L, listOf(100, 120, 140), 0)
        assertEquals(120, summary.avgHeartRate)
    }

    @Test
    fun `no HR readings yields null avgHeartRate`() {
        val summary = computeSummary(60L, emptyList(), 0)
        assertNull(summary.avgHeartRate)
    }

    @Test
    fun `zero jumps yields null jumpCount and null jumpsPerMinute`() {
        val summary = computeSummary(60L, listOf(100), 0)
        assertNull(summary.jumpCount)
        assertNull(summary.jumpsPerMinute)
    }

    @Test
    fun `jumps per minute calculation correct`() {
        // 30 jumps in 120s = 15 jpm
        val summary = computeSummary(120L, emptyList(), 30)
        assertEquals(30, summary.jumpCount)
        assertEquals(15.0, summary.jumpsPerMinute!!, 0.001)
    }

    @Test
    fun `durationSeconds propagated correctly`() {
        val summary = computeSummary(300L, emptyList(), 0)
        assertEquals(300L, summary.durationSeconds)
    }
}
