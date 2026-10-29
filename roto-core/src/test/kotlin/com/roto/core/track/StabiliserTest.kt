package com.roto.core.track

import com.roto.core.image.Mask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StabiliserTest {

    /** A 5-wide, 20-tall figure walking 3 pixels right per frame, bobbing a pixel every other frame. */
    private val walk = (0 until 9).map { frame ->
        val left = 4 + 3 * frame
        val top = 5 - frame % 2
        Mask.of(60, 30) { x, y -> x in left until left + 5 && y in top until top + 20 }
    }

    @Test
    fun `shifting by the track holds the root still`() {
        val track = Stabiliser.track(walk, RootSettings())

        val shiftedRoots = walk.indices.map { frame ->
            RootFinder.rootX(HorizontalShift.shift(walk[frame], track.shifts[frame]), RootSettings())
        }

        assertEquals(1, shiftedRoots.toSet().size, "roots after shifting: $shiftedRoots")
        assertEquals(walk.indices.map { 6.0 + 3 * it }, track.rootX)
        assertEquals(track.rootX, track.smoothedX)
    }

    @Test
    fun `the vertical bob is left as filmed`() {
        val track = Stabiliser.track(walk, RootSettings())

        val tops = walk.indices.map { frame ->
            val shifted = HorizontalShift.shift(walk[frame], track.shifts[frame])
            (0 until shifted.height).first { y -> (0 until shifted.width).any { shifted[it, y] } }
        }

        assertEquals(walk.indices.map { 5 - it % 2 }, tops)
    }

    @Test
    fun `a clip on the spot is not shifted`() {
        val track = Stabiliser.track(walk, RootSettings(stabilise = false))

        assertEquals(List(walk.size) { 0 }, track.shifts)
    }

    @Test
    fun `frames without a figure take their root from their neighbours`() {
        val withGap = walk.toMutableList().also { it[4] = Mask(60, 30) }

        val track = Stabiliser.track(withGap, RootSettings(smoothing = 1))

        assertEquals(null, track.rootX[4])
        assertEquals(18.0, track.smoothedX[4])
    }

    @Test
    fun `a clip with no figure at all cannot be tracked`() {
        assertFailsWith<IllegalArgumentException> { Stabiliser.track(List(3) { Mask(10, 10) }, RootSettings()) }
    }
}
