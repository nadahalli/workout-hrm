package com.tejaswin.thumper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_samples")
data class WorkoutSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val timestampMillis: Long,
    val heartRate: Int?,
    val jumpCount: Int
)
