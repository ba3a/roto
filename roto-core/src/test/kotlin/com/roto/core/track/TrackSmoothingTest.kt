package com.roto.core.track

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrackSmoothingTest {

    @Test
    fun `gaps fill linearly inside and with the nearest value at the ends`() {
        assertEquals(
            listOf(4.0, 4.0, 5.0, 6.0, 7.0, 7.0),
            TrackSmoothing.fillGaps(listOf(null, 4.0, null, null, 7.0, null)),
        )
        assertFailsWith<IllegalArgumentException> { TrackSmoothing.fillGaps(listOf(null, null)) }
    }

    @Test
    fun `steady motion passes through unchanged, ends included`() {
        val walking = (0 until 12).map { 10.0 + 3.0 * it }

        assertEquals(walking, TrackSmoothing.movingAverage(walking, 7))
    }

    @Test
    fun `jitter is averaged out`() {
        val jitter = listOf(10.0, 12.0, 8.0, 12.0, 8.0, 12.0, 8.0, 10.0)

        assertEquals(
            listOf(10.0, 10.0, 10.0, 10.4, 9.6, 10.0, 10.0, 10.0),
            TrackSmoothing.movingAverage(jitter, 5).map { Math.round(it * 10) / 10.0 },
        )
    }

    @Test
    fun `a window of one changes nothing and an even window is rejected`() {
        val values = listOf(1.0, 5.0, 2.0)

        assertEquals(values, TrackSmoothing.movingAverage(values, 1))
        assertFailsWith<IllegalArgumentException> { TrackSmoothing.movingAverage(values, 4) }
    }
}
