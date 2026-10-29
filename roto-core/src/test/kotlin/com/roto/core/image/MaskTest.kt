package com.roto.core.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MaskTest {

    @Test
    fun `pixels read back as set, and equality is by content`() {
        val mask = Mask(3, 2)
        mask[1, 0] = true
        mask[2, 1] = true

        assertTrue(mask[1, 0])
        assertFalse(mask[0, 0])
        assertEquals(2, mask.count())
        assertEquals(Mask.of(3, 2) { x, y -> (x == 1 && y == 0) || (x == 2 && y == 1) }, mask)
        assertNotEquals(Mask(3, 2), mask)
        assertNotEquals(Mask(2, 3), Mask(3, 2))
    }

    @Test
    fun `copies and arrays are independent of the mask`() {
        val mask = Mask(2, 2)
        val copy = mask.copy()
        val bits = mask.toBooleanArray()

        copy[0, 0] = true
        bits[1] = true

        assertEquals(Mask(2, 2), mask)
    }

    @Test
    fun `stored as opaque white on transparent, and read back`() {
        val mask = Mask.of(3, 2) { x, y -> (x + y) % 2 == 0 }

        val raster = mask.toRaster()

        assertEquals(-1, raster[0, 0])
        assertEquals(Argb.TRANSPARENT, raster[1, 0])
        assertEquals(mask, Mask.fromRaster(raster))
        assertEquals(
            Mask.of(2, 1) { x, _ -> x == 0 },
            Mask.fromRaster(Raster.of(2, 1) { x, _ -> Argb.of(0, 0, 0, if (x == 0) 128 else 127) }),
        )
    }

    @Test
    fun `size and coordinates are checked`() {
        assertFailsWith<IllegalArgumentException> { Mask(0, 1) }
        assertFailsWith<IndexOutOfBoundsException> { Mask(2, 2)[2, 0] }
        assertFailsWith<IllegalArgumentException> { Mask.fromBooleanArray(2, 2, BooleanArray(3)) }
    }
}
