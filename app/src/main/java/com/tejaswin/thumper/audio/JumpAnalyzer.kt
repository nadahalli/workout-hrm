package com.tejaswin.thumper.audio

import kotlin.math.abs

class JumpAnalyzer(
    @Volatile var threshold: Int = 8000,
    private val cooldownMs: Long = 200L
) {
    private var lastJumpTimeMs: Long = 0L

    fun processBuffer(buffer: ShortArray, readCount: Int, nowMs: Long): Boolean {
        if (readCount <= 0) return false

        var maxAmplitude: Short = 0
        for (i in 0 until readCount) {
            val sample = abs(buffer[i].toInt()).toShort()
            if (sample > maxAmplitude) maxAmplitude = sample
        }

        if (maxAmplitude > threshold && nowMs - lastJumpTimeMs > cooldownMs) {
            lastJumpTimeMs = nowMs
            return true
        }
        return false
    }

    fun reset() {
        lastJumpTimeMs = 0L
    }
}
