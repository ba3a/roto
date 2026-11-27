package com.roto.core.publish

import com.roto.core.image.Raster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetimeTest {

    private fun frames(vararg durations: Int) =
        durations.map { ArtFrame(Raster.of(1, 1) { _, _ -> it }, it) }

    @Test
    fun `duration rounds half up and never drops below 1 ms`() {
        assertEquals(100, Retime().duration(100))
        assertEquals(67, Retime(speedPercent = 150).duration(100))
        assertEquals(3, Retime(speedPercent = 200).duration(5))
        assertEquals(2, Retime(speedPercent = 200).duration(3))
        assertEquals(1, Retime(speedPercent = 400).duration(1))
        assertEquals(1, Retime(speedPercent = 400).duration(0))
        assertEquals(400, Retime(speedPercent = 25).duration(100))
    }

    @Test
    fun `speed outside 25 to 400 and an empty or negative keep are refused`() {
        assertFailsWith<IllegalArgumentException> { Retime(speedPercent = 24) }
        assertFailsWith<IllegalArgumentException> { Retime(speedPercent = 401) }
        assertFailsWith<IllegalArgumentException> { Retime(keep = emptyList()) }
        assertEquals(-2, Retime(keep = listOf(0, -2)).negativeKeep)
    }

    @Test
    fun `spread covers one frame, all frames, two frames and repeats`() {
        assertEquals(listOf(0), FramePick.spread(8, 1))
        assertEquals(listOf(0, 7), FramePick.spread(8, 2))
        assertEquals((0 until 8).toList(), FramePick.spread(8, 8))
        assertEquals(listOf(0, 0, 1, 1, 2, 3), FramePick.spread(4, 6))
    }

    @Test
    fun `apply picks by index with repeats and scales durations`() {
        val result = Retime(speedPercent = 200, keep = listOf(2, 0, 2)).apply(frames(100, 200, 300), "walk_EAST")

        assertEquals(listOf(150, 50, 150), result.map { it.durationMs })
    }

    @Test
    fun `apply refuses an index past the handoff naming clip and index`() {
        val error = assertFailsWith<PublishException> {
            Retime(keep = listOf(0, 3)).apply(frames(100, 100, 100), "walk_EAST")
        }

        assertEquals(true, "walk_EAST" in error.message.orEmpty() && "3" in error.message.orEmpty())
    }
}
