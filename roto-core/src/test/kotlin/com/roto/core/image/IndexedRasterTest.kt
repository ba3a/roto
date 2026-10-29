package com.roto.core.image

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class IndexedRasterTest {

    @Test
    fun `new raster is filled with index zero`() {
        assertContentEquals(ByteArray(6), IndexedRaster(3, 2).toByteArray())
    }

    @Test
    fun `indices above 127 read back unsigned`() {
        val raster = IndexedRaster(2, 1)

        raster[1, 0] = 255
        raster[0, 0] = 128

        assertEquals(255, raster[1, 0])
        assertEquals(128, raster[0, 0])
        assertEquals(255, raster.maxIndex())
    }

    @Test
    fun `indices outside zero to 255 are rejected`() {
        val raster = IndexedRaster(1, 1)

        assertFailsWith<IllegalArgumentException> { raster[0, 0] = 256 }
        assertFailsWith<IllegalArgumentException> { raster[0, 0] = -1 }
    }

    @Test
    fun `access outside the raster throws instead of wrapping to the next row`() {
        val raster = IndexedRaster(3, 2)

        assertFailsWith<IndexOutOfBoundsException> { raster[3, 0] }
        assertFailsWith<IndexOutOfBoundsException> { raster[0, 2] = 1 }
    }

    @Test
    fun `empty sizes and mismatched arrays are rejected`() {
        assertFailsWith<IllegalArgumentException> { IndexedRaster(0, 1) }
        assertFailsWith<IllegalArgumentException> { IndexedRaster.fromByteArray(2, 2, ByteArray(5)) }
    }

    @Test
    fun `equality is by size and content, and copies are independent`() {
        val a = IndexedRaster.of(2, 2) { x, y -> x + 2 * y }
        val b = a.copy()

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        b[0, 0] = 9
        assertNotEquals(a, b)
        assertEquals(0, a[0, 0])
    }

    @Test
    fun `fromByteArray and toByteArray do not share the backing array`() {
        val source = byteArrayOf(1, 2, 3, 4)
        val raster = IndexedRaster.fromByteArray(2, 2, source)

        source[0] = 9
        raster.toByteArray()[1] = 9

        assertContentEquals(byteArrayOf(1, 2, 3, 4), raster.toByteArray())
    }
}
