package com.roto.core.review

import com.roto.core.image.Argb
import com.roto.core.image.Raster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LoopSeamTest {
    private val red = Argb.of(255, 0, 0)
    private val blue = Argb.of(0, 0, 255)

    @Test
    fun `count equals the differing pixels`() {
        val last = Raster.fromArgbArray(2, 2, intArrayOf(red, red, blue, Argb.TRANSPARENT))
        val first = Raster.fromArgbArray(2, 2, intArrayOf(red, blue, blue, red))
        val diff = LoopSeam.diff(last, first)
        assertEquals(2, diff.count)
        val marked = listOf(diff.mask[0, 0], diff.mask[1, 0], diff.mask[0, 1], diff.mask[1, 1])
        assertEquals(listOf(false, true, false, true), marked)
    }

    @Test
    fun `equal frames have no difference`() {
        val frame = Raster.fromArgbArray(2, 1, intArrayOf(red, blue))
        assertEquals(0, LoopSeam.diff(frame, frame).count)
    }

    @Test
    fun `transparent pixels are equal whatever their colour`() {
        val a = Raster.fromArgbArray(1, 1, intArrayOf(0x00FF0000))
        val b = Raster.fromArgbArray(1, 1, intArrayOf(0x000000FF))
        assertEquals(0, LoopSeam.diff(a, b).count)
    }

    @Test
    fun `a size mismatch is refused`() {
        val one = Raster.fromArgbArray(1, 1, intArrayOf(red))
        val two = Raster.fromArgbArray(2, 1, intArrayOf(red, red))
        val error = assertThrows<IllegalArgumentException> { LoopSeam.diff(one, two) }
        assertEquals("Loop seam frames must be the same size: 1x1 and 2x1", error.message)
    }
}
