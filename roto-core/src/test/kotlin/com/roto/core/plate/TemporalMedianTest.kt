package com.roto.core.plate

import com.roto.core.image.Argb
import com.roto.core.image.Raster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class TemporalMedianTest {

    private val wall = Raster.of(12, 4) { x, y -> Argb.of(100 + x * 10, 80 + y * 20, 60) }
    private val figure = Argb.of(200, 30, 30)

    @Test
    fun `a figure walking across vanishes from the median`() {
        // A 3-pixel-wide figure steps 3 pixels per frame, so it covers each pixel in one of four frames.
        val frames = (0 until 4).map { step ->
            Raster.of(12, 4) { x, y -> if (x in step * 3 until step * 3 + 3) figure else wall[x, y] }
        }

        assertEquals(wall, TemporalMedian.of(frames))
    }

    @Test
    fun `a figure covering a pixel in under half the frames vanishes, in over half it stays`() {
        val covered = { frames: Int, of: Int ->
            (0 until of).map { index -> Raster.of(12, 4) { x, y -> if (index < frames) figure else wall[x, y] } }
        }

        assertEquals(wall, TemporalMedian.of(covered(2, 5)))
        assertEquals(Raster.of(12, 4) { _, _ -> figure }, TemporalMedian.of(covered(3, 5)))
    }

    @Test
    fun `noise on a still wall settles on the middle value`() {
        val frames = listOf(10, 14, 11, 13, 12).map { level ->
            Raster.of(2, 2) { _, _ -> Argb.of(level, level, level) }
        }

        assertEquals(Raster.of(2, 2) { _, _ -> Argb.of(12, 12, 12) }, TemporalMedian.of(frames))
    }

    @Test
    fun `each channel takes its own median, and an even count takes the lower middle`() {
        val frames = listOf(
            Argb.of(10, 200, 0, 255),
            Argb.of(20, 100, 9, 0),
            Argb.of(30, 150, 3, 255),
            Argb.of(40, 50, 6, 128),
        ).map { argb -> Raster.of(1, 1) { _, _ -> argb } }

        assertEquals(Argb.of(20, 100, 3, 128), TemporalMedian.of(frames)[0, 0])
    }

    @Test
    fun `one frame gives a copy of itself`() {
        val median = TemporalMedian.of(listOf(wall))

        assertEquals(wall, median)
        assertNotSame(wall, median)
    }

    @Test
    fun `frames must be given and of one size`() {
        assertFailsWith<IllegalArgumentException> { TemporalMedian.of(emptyList()) }
        assertFailsWith<IllegalArgumentException> { TemporalMedian.of(listOf(wall, Raster(12, 5))) }
    }
}
