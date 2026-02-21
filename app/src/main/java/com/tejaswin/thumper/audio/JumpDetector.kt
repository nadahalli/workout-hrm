package com.tejaswin.thumper.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

@SuppressLint("MissingPermission")
class JumpDetector {

    companion object {
        private const val TAG = "JumpDetector"
        private const val SAMPLE_RATE = 44100
        private const val COOLDOWN_MS = 200L
    }

    private val _jumpCount = MutableStateFlow(0)
    val jumpCount: StateFlow<Int> = _jumpCount.asStateFlow()

    @Volatile
    var threshold: Int = 8000

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isListening = false

    fun resetCount() {
        _jumpCount.value = 0
    }

    fun start() {
        if (isListening) return

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return
        }

        isListening = true
        audioRecord?.startRecording()

        Thread {
            val buffer = ShortArray(bufferSize / 2)
            var lastJumpTime = 0L

            while (isListening) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read <= 0) continue

                var maxAmplitude: Short = 0
                for (i in 0 until read) {
                    val sample = abs(buffer[i].toInt()).toShort()
                    if (sample > maxAmplitude) maxAmplitude = sample
                }

                val now = System.currentTimeMillis()
                if (maxAmplitude > threshold && now - lastJumpTime > COOLDOWN_MS) {
                    lastJumpTime = now
                    _jumpCount.value += 1
                }
            }
        }.apply {
            name = "JumpDetectorThread"
            isDaemon = true
            start()
        }

        Log.d(TAG, "Jump detection started")
    }

    fun stop() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "Jump detection stopped")
    }
}
