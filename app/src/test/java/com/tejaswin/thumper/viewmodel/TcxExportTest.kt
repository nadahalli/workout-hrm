package com.tejaswin.thumper.viewmodel

import com.tejaswin.thumper.data.WorkoutEntity
import com.tejaswin.thumper.data.WorkoutSampleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TcxExportTest {

    private fun workout(
        id: Long = 1L,
        startTimeMillis: Long = 1_700_000_000_000L,
        durationSeconds: Long = 300L
    ) = WorkoutEntity(
        id = id,
        startTimeMillis = startTimeMillis,
        durationSeconds = durationSeconds,
        avgHeartRate = null,
        jumpCount = null
    )

    private fun sample(
        workoutId: Long = 1L,
        timestampMillis: Long = 1_700_000_005_000L,
        heartRate: Int? = 120,
        jumpCount: Int = 0
    ) = WorkoutSampleEntity(
        workoutId = workoutId,
        timestampMillis = timestampMillis,
        heartRate = heartRate,
        jumpCount = jumpCount
    )

    @Test
    fun `single workout with samples produces valid TCX structure`() {
        val w = workout()
        val s = sample()
        val tcx = buildTcx(listOf(w), mapOf(w.id to listOf(s)))

        assertTrue(tcx.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(tcx.contains("<TrainingCenterDatabase"))
        assertTrue(tcx.contains("<Activities>"))
        assertTrue(tcx.contains("""<Activity Sport="Other">"""))
        assertTrue(tcx.contains("<Lap StartTime="))
        assertTrue(tcx.contains("<Track>"))
        assertTrue(tcx.contains("<Trackpoint>"))
        assertTrue(tcx.contains("</TrainingCenterDatabase>"))
    }

    @Test
    fun `timestamps are ISO 8601 UTC`() {
        // 1_700_000_000_000L = 2023-11-14T22:13:20.000Z
        val w = workout(startTimeMillis = 1_700_000_000_000L)
        val s = sample(timestampMillis = 1_700_000_005_000L)
        val tcx = buildTcx(listOf(w), mapOf(w.id to listOf(s)))

        assertTrue(tcx.contains("<Id>2023-11-14T22:13:20.000Z</Id>"))
        assertTrue(tcx.contains("<Time>2023-11-14T22:13:25.000Z</Time>"))
    }

    @Test
    fun `HeartRateBpm present only when heartRate is non-null`() {
        val w = workout()
        val withHr = sample(heartRate = 145)
        val withoutHr = sample(timestampMillis = 1_700_000_010_000L, heartRate = null)
        val tcx = buildTcx(listOf(w), mapOf(w.id to listOf(withHr, withoutHr)))

        assertTrue(tcx.contains("<HeartRateBpm><Value>145</Value></HeartRateBpm>"))
        // Count occurrences: only one HeartRateBpm element despite two trackpoints
        assertEquals(1, Regex("<HeartRateBpm>").findAll(tcx).count())
        assertEquals(2, Regex("<Trackpoint>").findAll(tcx).count())
    }

    @Test
    fun `multiple workouts produce multiple Activity elements`() {
        val w1 = workout(id = 1L, startTimeMillis = 1_700_000_000_000L)
        val w2 = workout(id = 2L, startTimeMillis = 1_700_100_000_000L)
        val tcx = buildTcx(
            listOf(w1, w2),
            mapOf(w1.id to emptyList(), w2.id to emptyList())
        )

        assertEquals(2, Regex("""<Activity Sport="Other">""").findAll(tcx).count())
        assertEquals(2, Regex("</Activity>").findAll(tcx).count())
    }

    @Test
    fun `empty samples list produces Lap with empty Track`() {
        val w = workout()
        val tcx = buildTcx(listOf(w), mapOf(w.id to emptyList()))

        assertTrue(tcx.contains("<Track>"))
        assertTrue(tcx.contains("</Track>"))
        assertFalse(tcx.contains("<Trackpoint>"))
    }

    @Test
    fun `TotalTimeSeconds matches workout durationSeconds`() {
        val w = workout(durationSeconds = 1234L)
        val tcx = buildTcx(listOf(w), mapOf(w.id to emptyList()))

        assertTrue(tcx.contains("<TotalTimeSeconds>1234</TotalTimeSeconds>"))
    }

    @Test
    fun `Notes contains jump time when present`() {
        val w = workout().copy(jumpTimeSeconds = 90L)
        val tcx = buildTcx(listOf(w), mapOf(w.id to emptyList()))

        assertTrue(tcx.contains("<Notes>Jump time: 90s</Notes>"))
    }

    @Test
    fun `Notes absent when jumpTimeSeconds is null`() {
        val w = workout()
        val tcx = buildTcx(listOf(w), mapOf(w.id to emptyList()))

        assertFalse(tcx.contains("<Notes>"))
    }
}
