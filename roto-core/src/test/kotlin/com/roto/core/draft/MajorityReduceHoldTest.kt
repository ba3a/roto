package com.roto.core.draft

import com.roto.core.image.IndexedRaster
import kotlin.test.Test
import kotlin.test.assertEquals

class MajorityReduceHoldTest {

    /** One 4x4 block: the first [first] samples are [a], the next [second] are [b], the rest background. */
    private fun block(first: Int, a: Int, second: Int, b: Int): IndexedRaster {
        val labels = IntArray(16) { if (it < first) a else if (it < first + second) b else 0 }
        return IndexedRaster.of(4, 4) { x, y -> labels[y * 4 + x] }
    }

    private fun previous(zone: Int, colour: Int) = Draft(grid("$colour"), grid("$zone"))

    private fun reduce(colours: IndexedRaster, zones: IndexedRaster, previous: Draft?, hold: Int) =
        MajorityReduce.reduce(colours, zones, 4, previous, hold)

    @Test
    fun `without a previous draft or with hold 0 the plain rules apply`() {
        val figure = block(6, 3, 0, 0)
        val zones = block(6, 1, 0, 0)

        assertEquals(".", reduce(figure, zones, null, 3).zones.drawing())
        assertEquals(".", reduce(figure, zones, previous(1, 3), 0).zones.drawing())
        val half = block(8, 3, 0, 0)
        assertEquals("3", reduce(half, block(8, 1, 0, 0), previous(0, 0), 0).colours.drawing())
    }

    @Test
    fun `a figure block within the margin keeps its previous state and one beyond it changes`() {
        val figure = block(6, 3, 0, 0)
        val zones = block(6, 1, 0, 0)
        assertEquals("1", reduce(figure, zones, previous(1, 3), 2).zones.drawing())
        assertEquals(".", reduce(figure, zones, previous(1, 3), 1).zones.drawing())

        val mostly = block(10, 3, 0, 0)
        val mostlyZones = block(10, 1, 0, 0)
        assertEquals(".", reduce(mostly, mostlyZones, previous(0, 0), 2).zones.drawing())
        assertEquals("1", reduce(mostly, mostlyZones, previous(0, 0), 1).zones.drawing())
    }

    @Test
    fun `a zone within the margin keeps its previous label and one beyond it changes`() {
        val zones = block(7, 1, 9, 2)
        val colours = block(7, 3, 9, 6)
        assertEquals("1", reduce(colours, zones, previous(1, 3), 2).zones.drawing())
        assertEquals("2", reduce(colours, zones, previous(1, 3), 1).zones.drawing())
    }

    @Test
    fun `a colour within the margin keeps its previous label and one beyond it changes`() {
        val zones = block(16, 1, 0, 0)
        val colours = block(7, 3, 9, 4)
        assertEquals("3", reduce(colours, zones, previous(1, 3), 2).colours.drawing())
        assertEquals("4", reduce(colours, zones, previous(1, 3), 1).colours.drawing())
    }

    @Test
    fun `hold 0 reproduces the plain rules on a multi-block fixture, with or without a previous draft`() {
        val shaded = grid("3334..47", "3334..77", "2.......", "3.......")
        val zones = grid("1111..12", "1111..22", "1.......", "1.......")
        val previous = Draft(grid("77.3", "7777"), grid("22.1", "2222"))

        val expected = Draft(grid("33.7", "2..."), grid("11.2", "1..."))

        assertEquals(expected, MajorityReduce.reduce(shaded, zones, 2))
        assertEquals(expected, MajorityReduce.reduce(shaded, zones, 2, null, 0))
        assertEquals(expected, MajorityReduce.reduce(shaded, zones, 2, previous, 0))
    }
}
