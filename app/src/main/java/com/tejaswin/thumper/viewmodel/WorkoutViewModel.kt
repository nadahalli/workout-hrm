package com.tejaswin.thumper.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.ContentValues
import android.content.Context
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
import com.tejaswin.thumper.data.WorkoutSampleEntity
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

    private val _workoutSummary = MutableStateFlow<WorkoutSummary?>(null)
    val workoutSummary: StateFlow<WorkoutSummary?> = _workoutSummary.asStateFlow()

    init {
        bleManager.autoConnectSaved()
        jumpDetector.threshold = _sensitivity.value
    }

    fun dismissSummary() {
        _workoutSummary.value = null
    }

    fun exportTcx(context: Context, workoutId: Long) {
        viewModelScope.launch {
            val workout = workoutDao.getById(workoutId)
            if (workout == null) {
                Toast.makeText(context, "Workout not found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val samples = workoutDao.getSamplesForWorkout(workoutId)
            val tcx = buildTcx(listOf(workout), mapOf(workoutId to samples))
            val fileName = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.getDefault())
                .format(Date(workout.startTimeMillis))
            writeToDownloads(context, "workout-$fileName.tcx", tcx)
        }
    }

    fun exportAllTcx(context: Context) {
        viewModelScope.launch {
            val workouts = workoutDao.getAll()
            if (workouts.isEmpty()) {
                Toast.makeText(context, "No workouts to export", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val samplesByWorkout = workouts.associate { w ->
                w.id to workoutDao.getSamplesForWorkout(w.id)
            }
            val tcx = buildTcx(workouts, samplesByWorkout)
            writeToDownloads(context, "workouts-export.tcx", tcx)
        }
    }

    private fun buildTcx(
        workouts: List<WorkoutEntity>,
        samplesByWorkout: Map<Long, List<WorkoutSampleEntity>>
    ): String = com.tejaswin.thumper.viewmodel.buildTcx(workouts, samplesByWorkout)

    private fun writeToDownloads(context: Context, fileName: String, content: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.garmin.tcx+xml")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(fileName)
        )
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            Toast.makeText(context, "Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
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
    private var currentWorkoutId: Long = 0L

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
        hrReadings.clear()
        jumpDetector.resetCount()

        countdownJob = viewModelScope.launch {
            for (i in 5 downTo 1) {
                _countdown.value = i
                delay(1000L)
            }
            _countdown.value = null

            workoutStartTimeMillis = System.currentTimeMillis()
            currentWorkoutId = workoutDao.insert(
                WorkoutEntity(
                    startTimeMillis = workoutStartTimeMillis,
                    durationSeconds = 0,
                    avgHeartRate = null,
                    jumpCount = null
                )
            )
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
        jumpDetector.stop()

        val duration = _elapsedSeconds.value
        if (duration > 0) {
            val summary = computeSummary(duration, hrReadings, jumpCount.value)
            _workoutSummary.value = summary

            viewModelScope.launch {
                workoutDao.update(
                    WorkoutEntity(
                        id = currentWorkoutId,
                        startTimeMillis = workoutStartTimeMillis,
                        durationSeconds = summary.durationSeconds,
                        avgHeartRate = summary.avgHeartRate,
                        jumpCount = summary.jumpCount
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
                if (_elapsedSeconds.value % 5 == 0L) {
                    workoutDao.insertSample(
                        WorkoutSampleEntity(
                            workoutId = currentWorkoutId,
                            timestampMillis = System.currentTimeMillis(),
                            heartRate = heartRate.value,
                            jumpCount = jumpCount.value
                        )
                    )
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

internal fun buildTcx(
    workouts: List<WorkoutEntity>,
    samplesByWorkout: Map<Long, List<WorkoutSampleEntity>>
): String {
    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    return buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">""")
        appendLine("  <Activities>")
        for (workout in workouts) {
            val startIso = isoFormat.format(Date(workout.startTimeMillis))
            appendLine("""    <Activity Sport="Other">""")
            appendLine("      <Id>$startIso</Id>")
            appendLine("      <Lap StartTime=\"$startIso\">")
            appendLine("        <TotalTimeSeconds>${workout.durationSeconds}</TotalTimeSeconds>")
            appendLine("        <Calories>0</Calories>")
            appendLine("        <Intensity>Active</Intensity>")
            appendLine("        <TriggerMethod>Manual</TriggerMethod>")
            appendLine("        <Track>")
            val samples = samplesByWorkout[workout.id] ?: emptyList()
            for (sample in samples) {
                appendLine("          <Trackpoint>")
                appendLine("            <Time>${isoFormat.format(Date(sample.timestampMillis))}</Time>")
                if (sample.heartRate != null) {
                    appendLine("            <HeartRateBpm><Value>${sample.heartRate}</Value></HeartRateBpm>")
                }
                appendLine("          </Trackpoint>")
            }
            appendLine("        </Track>")
            appendLine("      </Lap>")
            appendLine("    </Activity>")
        }
        appendLine("  </Activities>")
        appendLine("</TrainingCenterDatabase>")
    }
}

internal fun computeSummary(
    durationSeconds: Long,
    hrReadings: List<Int>,
    jumpCount: Int
): WorkoutSummary {
    val avgHr = if (hrReadings.isNotEmpty()) hrReadings.average().toInt() else null
    val jpm = if (durationSeconds > 0 && jumpCount > 0) {
        jumpCount.toDouble() / (durationSeconds / 60.0)
    } else {
        null
    }
    return WorkoutSummary(
        durationSeconds = durationSeconds,
        avgHeartRate = avgHr,
        jumpCount = if (jumpCount > 0) jumpCount else null,
        jumpsPerMinute = jpm
    )
}
