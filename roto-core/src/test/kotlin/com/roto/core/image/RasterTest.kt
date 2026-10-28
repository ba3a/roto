package com.roto.core.image

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RasterTest {

    @Test
    fun `new raster is fully transparent`() {
        val raster = Raster(3, 2)

        assertContentEquals(IntArray(6) { Argb.TRANSPARENT }, raster.toArgbArray())
    }

    @Test
    fun `of fills every pixel from its coordinates`() {
        val raster = Raster.of(3, 2) { x, y -> y * 10 + x }

        assertContentEquals(intArrayOf(0, 1, 2, 10, 11, 12), raster.toArgbArray())
        assertEquals(12, raster[2, 1])
    }

    @Test
    fun `set writes only the addressed pixel`() {
        val raster = Raster(2, 2)
        val red = Argb.of(255, 0, 0)

        raster[1, 0] = red

        assertEquals(red, raster[1, 0])
        assertEquals(Argb.TRANSPARENT, raster[0, 0])
        assertEquals(Argb.TRANSPARENT, raster[0, 1])
        assertEquals(Argb.TRANSPARENT, raster[1, 1])
    }

    @Test
    fun `access outside the raster throws instead of wrapping to the next row`() {
        val raster = Raster(3, 2)

        assertFailsWith<IndexOutOfBoundsException> { raster[3, 0] }
        assertFailsWith<IndexOutOfBoundsException> { raster[-1, 1] }
        assertFailsWith<IndexOutOfBoundsException> { raster[0, 2] }
        assertFailsWith<IndexOutOfBoundsException> { raster[0, -1] = 0 }
    }

    @Test
    fun `contains matches the raster bounds`() {
        val raster = Raster(3, 2)

        assertTrue(raster.contains(0, 0))
        assertTrue(raster.contains(2, 1))
        assertFalse(raster.contains(3, 1))
        assertFalse(raster.contains(2, 2))
        assertFalse(raster.contains(-1, 0))
    }

    @Test
    fun `empty or negative sizes are rejected`() {
        assertFailsWith<IllegalArgumentException> { Raster(0, 5) }
        assertFailsWith<IllegalArgumentException> { Raster(5, -1) }
        assertFailsWith<IllegalArgumentException> { Raster.fromArgbArray(2, 2, IntArray(3)) }
    }

    @Test
    fun `equality is by size and content`() {
        val a = Raster.of(2, 3) { x, y -> x + y }
        val b = Raster.of(2, 3) { x, y -> x + y }

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, Raster.of(3, 2) { x, y -> x + y })
        b[0, 0] = 99
        assertNotEquals(a, b)
    }

    @Test
    fun `copy is independent of the original`() {
        val original = Raster.of(2, 2) { _, _ -> 7 }
        val copy = original.copy()

        copy[0, 0] = 1

        assertEquals(7, original[0, 0])
    }

    @Test
    fun `fromArgbArray and toArgbArray do not share the backing array`() {
        val source = intArrayOf(1, 2, 3, 4)
        val raster = Raster.fromArgbArray(2, 2, source)

        source[0] = 99
        raster.toArgbArray()[1] = 99

        assertContentEquals(intArrayOf(1, 2, 3, 4), raster.toArgbArray())
    }
}
