package com.roto.core.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FrameRateTest {

    @Test
    fun `parses and prints the fraction form`() {
        assertEquals(FrameRate(30000, 1001), FrameRate.parse("30000/1001"))
        assertEquals("30000/1001", FrameRate(30000, 1001).toString())
    }

    @Test
    fun `frame start times are exact, without drift`() {
        val ntsc = FrameRate(30000, 1001)

        assertEquals(0, ntsc.startMs(0))
        assertEquals(33, ntsc.startMs(1))
        assertEquals(1001, ntsc.startMs(30))
        assertEquals(1_001_000, ntsc.startMs(30_000))
        assertEquals(40, FrameRate(25, 1).startMs(1))
    }

    @Test
    fun `invalid rates are rejected`() {
        listOf("0/0", "30", "30/", "/1", "30/1/1", "-30/1", "30.0/1", "").forEach { text ->
            assertFailsWith<IllegalArgumentException>(text) { FrameRate.parse(text) }
        }
        assertFailsWith<IllegalArgumentException> { FrameRate(30, 1).startMs(-1) }
    }
}
