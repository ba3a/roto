package com.roto.core.track

import com.roto.core.image.Mask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RootFinderTest {

    @Test
    fun `the root is the middle of the hips, not of a leaning head`() {
        // 20 rows tall: head leaning right at the top, hips centred on x = 10 in rows 9..11.
        val figure = Mask.of(30, 20) { x, y ->
            when (y) {
                in 0..4 -> x in 14..17
                in 5..13 -> x in 8..12
                else -> x in 7..9 || x in 11..13
            }
        }

        assertEquals(10.0, RootFinder.rootX(figure, RootSettings()))
    }

    @Test
    fun `an arm swung far out at hip height barely pulls the root`() {
        val withArm = Mask.of(40, 20) { x, y ->
            when (y) {
                in 0..13 -> x in 8..12 || (y in 8..11 && x in 25..27)
                else -> x in 7..9 || x in 11..13
            }
        }
        val bandXs = (9..11).flatMap { y -> (0 until 40).filter { withArm[it, y] } }

        // The hips are centred on 10: the median moves one pixel, the mean six.
        assertEquals(11.0, RootFinder.rootX(withArm, RootSettings()))
        assertEquals(16.0, bandXs.average(), "the mean, for comparison")
    }

    @Test
    fun `the band follows the figure up and down`() {
        val standing = Mask.of(30, 40) { x, y -> y in 10..29 && x in 8 + (y - 10) / 10..12 + (y - 10) / 10 }
        val raised = Mask.of(30, 40) { x, y -> y in 6..25 && x in 8 + (y - 6) / 10..12 + (y - 6) / 10 }

        assertEquals(RootFinder.rootX(standing, RootSettings()), RootFinder.rootX(raised, RootSettings()))
    }

    @Test
    fun `an empty mask has no root`() {
        assertNull(RootFinder.rootX(Mask(10, 10), RootSettings()))
    }
}
