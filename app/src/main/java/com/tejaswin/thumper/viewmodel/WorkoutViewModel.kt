package com.tejaswin.thumper.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.ContentValues
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tejaswin.thumper.audio.JumpDetector
import com.tejaswin.thumper.ble.ConnectionState
import com.tejaswin.thumper.ble.HrmBleManager
import com.tejaswin.thumper.ble.ScannedDevice
import com.tejaswin.thumper.data.WorkoutDatabase
import com.tejaswin.thumper.data.WorkoutEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkoutSummary(
    val durationSeconds: Long,
    val avgHeartRate: Int?,
    val jumpCount: Int?,
    val jumpsPerMinute: Double?
)

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = HrmBleManager(application.applicationContext)
    private val workoutDao = WorkoutDatabase.getInstance(application.applicationContext).workoutDao()
    private val jumpDetector = JumpDetector()
    private val prefs = application.getSharedPreferences("jump_prefs", Context.MODE_PRIVATE)

    val heartRate: StateFlow<Int?> = bleManager.heartRate
    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val scannedDevices: StateFlow<List<ScannedDevice>> = bleManager.scannedDevices
    val jumpCount: StateFlow<Int> = jumpDetector.jumpCount

    val workoutHistory = workoutDao.getAllDesc()
    val savedDeviceName: String? = bleManager.savedDeviceName()

    private val _sensitivity = MutableStateFlow(prefs.getInt("sensitivity", 8000))
    val sensitivity: StateFlow<Int> = _sensitivity.asStateFlow()

    private val _jumpsPerMinute = MutableStateFlow(0.0)
    val jumpsPerMinute: StateFlow<Double> = _jumpsPerMinute.asStateFlow()

    private val _beepInterval = MutableStateFlow(prefs.getInt("beep_interval", 0))
    val beepInterval: StateFlow<Int> = _beepInterval.asStateFlow()

    private val _workoutSummary = MutableStateFlow<WorkoutSummary?>(null)
    val workoutSummary: StateFlow<WorkoutSummary?> = _workoutSummary.asStateFlow()

    private var toneGenerator: ToneGenerator? = null
    private var beepJob: Job? = null

    init {
        bleManager.autoConnectSaved()
        jumpDetector.threshold = _sensitivity.value
    }

    fun setBeepInterval(value: Int) {
        _beepInterval.value = value
        prefs.edit().putInt("beep_interval", value).apply()
    }

    fun dismissSummary() {
        _workoutSummary.value = null
    }

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            val workouts = workoutDao.getAll()
            if (workouts.isEmpty()) {
                Toast.makeText(context, "No workouts to export", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val csv = buildString {
                appendLine("Date,Duration (s),Avg HR,Jumps")
                for (w in workouts) {
                    val date = dateFormat.format(Date(w.startTimeMillis))
                    appendLine("$date,${w.durationSeconds},${w.avgHeartRate ?: ""},${w.jumpCount ?: ""}")
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "hr-jump-export.csv")
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = context.contentResolver
            // Delete existing file with same name if present
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf("hr-jump-export.csv")
            )

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                Toast.makeText(context, "Exported to Downloads/hr-jump-export.csv", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setSensitivity(value: Int) {
        _sensitivity.value = value
        jumpDetector.threshold = value
        prefs.edit().putInt("sensitivity", value).apply()
    }

    private val _isWorkoutActive = MutableStateFlow(false)
    val isWorkoutActive: StateFlow<Boolean> = _isWorkoutActive.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _countdown = MutableStateFlow<Int?>(null)
    val countdown: StateFlow<Int?> = _countdown.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private var countdownJob: Job? = null
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
        _jumpsPerMinute.value = 0.0
        hrReadings.clear()
        jumpDetector.resetCount()

        countdownJob = viewModelScope.launch {
            for (i in 5 downTo 1) {
                _countdown.value = i
                delay(1000L)
            }
            _countdown.value = null

            workoutStartTimeMillis = System.currentTimeMillis()
            jumpDetector.start()
            startTimer()
            startBeepWatcher()

            hrCollectJob = viewModelScope.launch {
                heartRate.collect { bpm ->
                    if (bpm != null && _isWorkoutActive.value && !_isPaused.value) {
                        hrReadings.add(bpm)
                    }
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
        _countdown.value = null
        countdownJob?.cancel()
        countdownJob = null
        timerJob?.cancel()
        timerJob = null
        hrCollectJob?.cancel()
        hrCollectJob = null
        beepJob?.cancel()
        beepJob = null
        toneGenerator?.release()
        toneGenerator = null
        jumpDetector.stop()

        val duration = _elapsedSeconds.value
        if (duration > 0) {
            val avgHr = if (hrReadings.isNotEmpty()) hrReadings.average().toInt() else null
            val jumps = jumpCount.value
            val jpm = if (duration > 0 && jumps > 0) jumps.toDouble() / (duration / 60.0) else null

            _workoutSummary.value = WorkoutSummary(
                durationSeconds = duration,
                avgHeartRate = avgHr,
                jumpCount = if (jumps > 0) jumps else null,
                jumpsPerMinute = jpm
            )

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
                val elapsed = _elapsedSeconds.value
                val jumps = jumpCount.value.toDouble()
                _jumpsPerMinute.value = if (elapsed > 0) jumps / (elapsed / 60.0) else 0.0
            }
        }
    }

    private fun startBeepWatcher() {
        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
        } catch (_: Exception) {
            null
        }
        beepJob = viewModelScope.launch {
            jumpDetector.jumpCount.collect { count ->
                val interval = _beepInterval.value
                if (interval > 0 && count > 0 && count % interval == 0) {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopWorkout()
        bleManager.disconnect()
    }
}
