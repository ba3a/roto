package com.roto.core.model

import com.roto.core.cycle.CycleAnalysis
import com.roto.core.image.Raster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelTimingTest {
    private fun model(vararg durations: Int) = ModelClip(
        "walk",
        "right",
        durations.map { ModelFrame(Raster(1, 1), it) },
        loop = true,
        origin = "test",
    )

    private fun cycle(start: Int, end: Int) = CycleAnalysis(end - start, 1.0, emptyList(), listOf(start, end))

    @Test
    fun `six equal frames on a 12 frame cycle land every two frames`() {
        val keys = ModelTiming.propose(model(100, 100, 100, 100, 100, 100), cycle(4, 16))
        assertEquals(listOf(4, 6, 8, 10, 12, 14, 16), keys.boundaries)
    }

    @Test
    fun `uneven durations keep their relative times`() {
        val keys = ModelTiming.propose(model(200, 100, 100, 200), cycle(0, 12))
        assertEquals(listOf(0, 4, 6, 8, 12), keys.boundaries)
    }

    @Test
    fun `a cycle of a different total scales and rounds to frame starts`() {
        val keys = ModelTiming.propose(model(100, 100, 100), cycle(0, 10))
        assertEquals(listOf(0, 3, 7, 10), keys.boundaries)
    }

    @Test
    fun `a tight cycle stays strictly rising`() {
        val keys = ModelTiming.propose(model(1, 1000, 1, 1), cycle(0, 4))
        assertEquals(listOf(0, 1, 2, 3, 4), keys.boundaries)
        assertTrue(keys.boundaries.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `a cycle shorter than the model fails with a message`() {
        val e = assertFailsWith<ModelException> { ModelTiming.propose(model(100, 100, 100), cycle(0, 2)) }
        assertTrue("too short" in e.message.orEmpty())
    }
}
