package com.roto.core.track

import com.roto.core.image.Argb
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import kotlin.test.Test
import kotlin.test.assertEquals

class HorizontalShiftTest {

    private val ramp = Raster.of(4, 2) { x, y -> Argb.of(x * 60, y * 100, 0) }

    @Test
    fun `a raster moves right and left by whole pixels, empty where it moved away`() {
        val right = HorizontalShift.shift(ramp, 1)
        val left = HorizontalShift.shift(ramp, -2)

        assertEquals(Argb.TRANSPARENT, right[0, 1])
        assertEquals(ramp[0, 1], right[1, 1])
        assertEquals(ramp[2, 0], right[3, 0])
        assertEquals(ramp[3, 0], left[1, 0])
        assertEquals(Argb.TRANSPARENT, left[2, 0])
        assertEquals(ramp, HorizontalShift.shift(ramp, 0))
    }

    @Test
    fun `a mask moves the same way`() {
        val column = Mask.of(5, 2) { x, _ -> x == 1 }

        assertEquals(Mask.of(5, 2) { x, _ -> x == 3 }, HorizontalShift.shift(column, 2))
        assertEquals(Mask(5, 2), HorizontalShift.shift(column, -2))
    }
}
