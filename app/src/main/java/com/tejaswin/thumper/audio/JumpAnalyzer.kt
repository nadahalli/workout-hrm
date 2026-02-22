package com.tejaswin.thumper.audio

import kotlin.math.abs

class JumpAnalyzer(
    @Volatile var threshold: Int = 8000,
    private val cooldownMs: Long = 200L,
    private val maxGapMs: Long = 2000L
) {
    private var lastJumpTimeMs: Long = 0L

    /** Cumulative "active jumping" time in milliseconds. */
    var jumpTimeMs: Long = 0L
        private set

    fun processBuffer(buffer: ShortArray, readCount: Int, nowMs: Long): Boolean {
        if (readCount <= 0) return false

        var maxAmplitude: Short = 0
        for (i in 0 until readCount) {
            val sample = abs(buffer[i].toInt()).toShort()
            if (sample > maxAmplitude) maxAmplitude = sample
        }

        if (maxAmplitude > threshold && nowMs - lastJumpTimeMs > cooldownMs) {
            if (lastJumpTimeMs > 0L) {
                val gap = nowMs - lastJumpTimeMs
                if (gap <= maxGapMs) {
                    jumpTimeMs += gap
                }
            }
            lastJumpTimeMs = nowMs
            return true
        }
        return false
    }

    fun reset() {
        lastJumpTimeMs = 0L
        jumpTimeMs = 0L
    }
}
