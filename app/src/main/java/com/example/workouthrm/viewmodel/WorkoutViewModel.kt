package com.example.workouthrm.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.workouthrm.audio.JumpDetector
import com.example.workouthrm.ble.ConnectionState
import com.example.workouthrm.ble.HrmBleManager
import com.example.workouthrm.ble.ScannedDevice
import com.example.workouthrm.data.WorkoutDatabase
import com.example.workouthrm.data.WorkoutEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = HrmBleManager(application.applicationContext)
    private val workoutDao = WorkoutDatabase.getInstance(application.applicationContext).workoutDao()
    private val jumpDetector = JumpDetector()

    val heartRate: StateFlow<Int?> = bleManager.heartRate
    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val scannedDevices: StateFlow<List<ScannedDevice>> = bleManager.scannedDevices
    val jumpCount: StateFlow<Int> = jumpDetector.jumpCount

    val workoutHistory = workoutDao.getAllDesc()

    private val _isWorkoutActive = MutableStateFlow(false)
    val isWorkoutActive: StateFlow<Boolean> = _isWorkoutActive.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private var timerJob: Job? = null
    private var workoutStartTimeMillis: Long = 0L
    private var hrReadings = mutableListOf<Int>()
    private var hrCollectJob: Job? = null

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connectToDevice(device: BluetoothDevice) {
        bleManager.connectToDevice(device)
    }

    fun disconnectHrm() {
        bleManager.disconnect()
    }

    fun startWorkout() {
        _isWorkoutActive.value = true
        _isPaused.value = false
        _elapsedSeconds.value = 0L
        workoutStartTimeMillis = System.currentTimeMillis()
        hrReadings.clear()
        jumpDetector.resetCount()
        jumpDetector.start()
        startTimer()

        hrCollectJob = viewModelScope.launch {
            heartRate.collect { bpm ->
                if (bpm != null && _isWorkoutActive.value && !_isPaused.value) {
                    hrReadings.add(bpm)
                }
            }
        }
    }

    fun pauseWorkout() {
        _isPaused.value = true
        timerJob?.cancel()
        timerJob = null
        jumpDetector.stop()
    }

    fun resumeWorkout() {
        _isPaused.value = false
        jumpDetector.start()
        startTimer()
    }

    fun stopWorkout() {
        if (!_isWorkoutActive.value) return

        _isWorkoutActive.value = false
        _isPaused.value = false
        timerJob?.cancel()
        timerJob = null
        hrCollectJob?.cancel()
        hrCollectJob = null
        jumpDetector.stop()

        val duration = _elapsedSeconds.value
        if (duration > 0) {
            val avgHr = if (hrReadings.isNotEmpty()) hrReadings.average().toInt() else null
            val jumps = jumpCount.value
            viewModelScope.launch {
                workoutDao.insert(
                    WorkoutEntity(
                        startTimeMillis = workoutStartTimeMillis,
                        durationSeconds = duration,
                        avgHeartRate = avgHr,
                        jumpCount = if (jumps > 0) jumps else null
                    )
                )
            }
        }
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _elapsedSeconds.value += 1
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopWorkout()
        bleManager.disconnect()
    }
}
