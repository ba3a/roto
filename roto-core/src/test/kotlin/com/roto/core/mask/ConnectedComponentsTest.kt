package com.roto.core.mask

import com.roto.core.image.Mask
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectedComponentsTest {

    @Test
    fun `only the largest region is kept, and corners connect`() {
        val scene = mask(
            "##.......",
            "##...#...",
            "......#..",
            ".....###.",
            "........#",
        )

        assertEquals(
            mask(
                ".........",
                ".....#...",
                "......#..",
                ".....###.",
                "........#",
            ).drawing(),
            ConnectedComponents.largest(scene).drawing(),
        )
    }

    @Test
    fun `of equally large regions the first in reading order wins`() {
        val twins = mask(
            "...##",
            "##...",
        )

        assertEquals(mask("...##", ".....").drawing(), ConnectedComponents.largest(twins).drawing())
    }

    @Test
    fun `an empty mask stays empty`() {
        assertEquals(Mask(3, 2), ConnectedComponents.largest(Mask(3, 2)))
        assertEquals(Mask(3, 2), ConnectedComponents.fillHoles(Mask(3, 2)))
    }

    @Test
    fun `enclosed holes fill and a notch open to the border does not`() {
        val shirt = mask(
            "..........",
            ".########.",
            ".#..#...#.",
            ".#..#...#.",
            ".########.",
            ".###..###.",
            ".###..###.",
        )

        assertEquals(
            mask(
                "..........",
                ".########.",
                ".########.",
                ".########.",
                ".########.",
                ".###..###.",
                ".###..###.",
            ).drawing(),
            ConnectedComponents.fillHoles(shirt).drawing(),
        )
    }

    @Test
    fun `background does not leak through a diagonal wall`() {
        val diamond = mask(
            "..#..",
            ".#.#.",
            "#...#",
            ".#.#.",
            "..#..",
        )

        assertEquals(
            mask(
                "..#..",
                ".###.",
                "#####",
                ".###.",
                "..#..",
            ).drawing(),
            ConnectedComponents.fillHoles(diamond).drawing(),
        )
    }
}
