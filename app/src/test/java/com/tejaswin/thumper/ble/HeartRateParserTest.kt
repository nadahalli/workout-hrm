package com.tejaswin.thumper.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateParserTest {

    @Test
    fun `8-bit format parses single byte`() {
        // flags=0x00 (bit 0 = 0 means uint8), HR=72
        val data = byteArrayOf(0x00, 72)
        assertEquals(72, parseHeartRate(data))
    }

    @Test
    fun `16-bit format parses two bytes little-endian`() {
        // flags=0x01 (bit 0 = 1 means uint16), HR=300 = 0x012C -> little-endian: 0x2C, 0x01
        val data = byteArrayOf(0x01, 0x2C, 0x01)
        assertEquals(300, parseHeartRate(data))
    }

    @Test
    fun `empty byte array returns 0`() {
        assertEquals(0, parseHeartRate(byteArrayOf()))
    }

    @Test
    fun `high 8-bit value parsed correctly via unsigned mask`() {
        // flags=0x00, HR=200 -> 200 as signed byte is -56, but should parse as 200
        val data = byteArrayOf(0x00, 200.toByte())
        assertEquals(200, parseHeartRate(data))
    }
}
