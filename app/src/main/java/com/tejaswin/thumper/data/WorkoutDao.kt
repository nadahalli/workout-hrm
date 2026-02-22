package com.tejaswin.thumper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts ORDER BY startTimeMillis DESC")
    fun getAllDesc(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY startTimeMillis DESC")
    suspend fun getAll(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): WorkoutEntity?

    @Insert
    suspend fun insertSample(sample: WorkoutSampleEntity)

    @Query("SELECT * FROM workout_samples WHERE workoutId = :workoutId ORDER BY timestampMillis ASC")
    suspend fun getSamplesForWorkout(workoutId: Long): List<WorkoutSampleEntity>
}
