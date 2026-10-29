package com.roto.core.cycle

import com.roto.core.video.FrameRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeyFramesTest {

    @Test
    fun `every boundary but the last is drawn, lasting until the next`() {
        val keys = KeyFrames(listOf(2, 5, 9))

        assertEquals(listOf(2, 5), keys.frames)
        assertEquals(listOf(120L, 160L), keys.durationsMs(FrameRate(25, 1)))
    }

    @Test
    fun `durations are exact at a fractional frame rate`() {
        val keys = KeyFrames((0..30 step 3).toList())

        val durations = keys.durationsMs(FrameRate(30000, 1001))

        assertEquals(1001L, durations.sum())
        assertEquals(List(9) { 100L } + 101L, durations)
    }

    @Test
    fun `even spacing rounds to the nearest frame and never repeats one`() {
        assertEquals(listOf(2, 5, 7, 10, 12, 15, 17, 20, 22), KeyFrames.even(2, 20, 8).boundaries)
        assertEquals(listOf(0, 1, 2, 3), KeyFrames.even(0, 3, 8).boundaries)
    }

    @Test
    fun `boundaries must rise from frame zero or later`() {
        assertFailsWith<IllegalArgumentException> { KeyFrames(listOf(3)) }
        assertFailsWith<IllegalArgumentException> { KeyFrames(listOf(-1, 4)) }
        assertFailsWith<IllegalArgumentException> { KeyFrames(listOf(3, 3, 5)) }
        assertFailsWith<IllegalArgumentException> { KeyFrames(listOf(5, 3)) }
    }
}
