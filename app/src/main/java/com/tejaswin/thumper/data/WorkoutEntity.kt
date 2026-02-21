package com.tejaswin.thumper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMillis: Long,
    val durationSeconds: Long,
    val avgHeartRate: Int?,
    val jumpCount: Int?
)
