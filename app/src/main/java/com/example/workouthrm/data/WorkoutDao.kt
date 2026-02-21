package com.example.workouthrm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insert(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts ORDER BY startTimeMillis DESC")
    fun getAllDesc(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY startTimeMillis DESC")
    suspend fun getAll(): List<WorkoutEntity>
}
