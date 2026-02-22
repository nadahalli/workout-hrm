package com.tejaswin.thumper.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpAnalyzerTest {

    private fun bufferWithAmplitude(amplitude: Short, size: Int = 64): ShortArray {
        val buf = ShortArray(size)
        buf[0] = amplitude
        return buf
    }

    @Test
    fun `buffer above threshold triggers jump`() {
        val analyzer = JumpAnalyzer(threshold = 5000)
        val buf = bufferWithAmplitude(6000)
        assertTrue(analyzer.processBuffer(buf, buf.size, 1000L))
    }

    @Test
    fun `buffer below threshold does not trigger`() {
        val analyzer = JumpAnalyzer(threshold = 5000)
        val buf = bufferWithAmplitude(3000)
        assertFalse(analyzer.processBuffer(buf, buf.size, 1000L))
    }

    @Test
    fun `cooldown prevents rapid consecutive jumps`() {
        val analyzer = JumpAnalyzer(threshold = 5000, cooldownMs = 200L)
        val buf = bufferWithAmplitude(6000)

        assertTrue(analyzer.processBuffer(buf, buf.size, 1000L))
        assertFalse(analyzer.processBuffer(buf, buf.size, 1100L))
    }

    @Test
    fun `jump detected after cooldown expires`() {
        val analyzer = JumpAnalyzer(threshold = 5000, cooldownMs = 200L)
        val buf = bufferWithAmplitude(6000)

        assertTrue(analyzer.processBuffer(buf, buf.size, 1000L))
        assertFalse(analyzer.processBuffer(buf, buf.size, 1100L))
        assertTrue(analyzer.processBuffer(buf, buf.size, 1201L))
    }

    @Test
    fun `empty buffer does not trigger`() {
        val analyzer = JumpAnalyzer(threshold = 5000)
        val buf = ShortArray(64)
        assertFalse(analyzer.processBuffer(buf, 0, 1000L))
    }

    @Test
    fun `threshold change takes effect immediately`() {
        val analyzer = JumpAnalyzer(threshold = 10000)
        val buf = bufferWithAmplitude(6000)

        assertFalse(analyzer.processBuffer(buf, buf.size, 1000L))

        analyzer.threshold = 5000
        assertTrue(analyzer.processBuffer(buf, buf.size, 1300L))
    }

    @Test
    fun `reset clears cooldown state`() {
        val analyzer = JumpAnalyzer(threshold = 5000, cooldownMs = 200L)
        val buf = bufferWithAmplitude(6000)

        assertTrue(analyzer.processBuffer(buf, buf.size, 1000L))
        assertFalse(analyzer.processBuffer(buf, buf.size, 1050L))

        analyzer.reset()
        assertTrue(analyzer.processBuffer(buf, buf.size, 1050L))
    }

    @Test
    fun `jump time accumulates for consecutive jumps within max gap`() {
        val analyzer = JumpAnalyzer(threshold = 5000, cooldownMs = 200L, maxGapMs = 2000L)
        val buf = bufferWithAmplitude(6000)

        assertTrue(analyzer.processBuffer(buf, buf.size, 1000L))  // first jump, no gap
        assertEquals(0L, analyzer.jumpTimeMs)

        assertTrue(analyzer.processBuffer(buf, buf.size, 1500L))  // +500ms
        assertEquals(500L, analyzer.jumpTimeMs)

        assertTrue(analyzer.processBuffer(buf, buf.size, 2000L))  // +500ms
        assertEquals(1000L, analyzer.jumpTimeMs)
    }

    @Test
    fun `jump time excludes gaps beyond max gap`() {
        val analyzer = JumpAnalyzer(threshold = 5000, cooldownMs = 200L, maxGapMs = 2000L)
        val buf = bufferWithAmplitude(6000)

        assertTrue(analyzer.processBuffer(buf, buf.size, 1000L))
        assertTrue(analyzer.processBuffer(buf, buf.size, 1500L))
        assertEquals(500L, analyzer.jumpTimeMs)

        // 5s gap (rest period), should not count
        assertTrue(analyzer.processBuffer(buf, buf.size, 6500L))
        assertEquals(500L, analyzer.jumpTimeMs)

        // resume jumping
        assertTrue(analyzer.processBuffer(buf, buf.size, 7000L))
        assertEquals(1000L, analyzer.jumpTimeMs)
    }

    @Test
    fun `reset clears jump time`() {
        val analyzer = JumpAnalyzer(threshold = 5000, cooldownMs = 200L)
        val buf = bufferWithAmplitude(6000)

        assertTrue(analyzer.processBuffer(buf, buf.size, 1000L))
        assertTrue(analyzer.processBuffer(buf, buf.size, 1500L))
        assertEquals(500L, analyzer.jumpTimeMs)

        analyzer.reset()
        assertEquals(0L, analyzer.jumpTimeMs)
    }
}
