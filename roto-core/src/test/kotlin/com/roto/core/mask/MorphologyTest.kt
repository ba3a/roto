package com.roto.core.mask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MorphologyTest {

    @Test
    fun `erosion and dilation by a square`() {
        val block = mask(
            ".......",
            ".#####.",
            ".#####.",
            ".#####.",
            ".......",
        )

        assertEquals(
            mask(
                ".......",
                ".......",
                "..###..",
                ".......",
                ".......",
            ).drawing(),
            Morphology.erode(block, 1).drawing(),
        )
        assertEquals(
            mask(
                "#######",
                "#######",
                "#######",
                "#######",
                "#######",
            ).drawing(),
            Morphology.dilate(block, 1).drawing(),
        )
    }

    @Test
    fun `opening removes a speck and a thin spur and keeps the body exactly`() {
        val noisy = mask(
            "#.........",
            "..######..",
            "..######..",
            "..########",
            "..######..",
            "..........",
        )

        assertEquals(
            mask(
                "..........",
                "..######..",
                "..######..",
                "..######..",
                "..######..",
                "..........",
            ).drawing(),
            Morphology.open(noisy, 1).drawing(),
        )
    }

    @Test
    fun `closing fills a narrow gap and keeps square corners`() {
        val split = mask(
            "..........",
            ".###.####.",
            ".###.####.",
            ".###.####.",
            "..........",
        )

        assertEquals(
            mask(
                "..........",
                ".########.",
                ".########.",
                ".########.",
                "..........",
            ).drawing(),
            Morphology.close(split, 1).drawing(),
        )
    }

    @Test
    fun `closing does not glue a figure to a nearby frame edge`() {
        val nearTheEdge = mask(
            "........",
            "........",
            "..####..",
            "..####..",
            "..####..",
            "........",
        )

        assertEquals(nearTheEdge.drawing(), Morphology.close(nearTheEdge, 2).drawing())
    }

    @Test
    fun `a figure cut off by the frame edge is not eroded from that edge`() {
        val feetOnTheEdge = mask(
            "......",
            ".####.",
            ".####.",
            ".####.",
        )

        assertEquals(feetOnTheEdge.drawing(), Morphology.open(feetOnTheEdge, 1).drawing())
    }

    @Test
    fun `radius zero changes nothing and a negative radius is rejected`() {
        val shape = mask("#.#", ".#.")

        assertEquals(shape, Morphology.open(shape, 0))
        assertEquals(shape, Morphology.close(shape, 0))
        assertFailsWith<IllegalArgumentException> { Morphology.erode(shape, -1) }
    }
}
