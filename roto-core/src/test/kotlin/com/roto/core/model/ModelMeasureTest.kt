package com.roto.core.model

import com.roto.core.image.Argb
import com.roto.core.image.Raster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelMeasureTest {

    private val ink = Argb.of(200, 50, 50)

    private fun frame(top: Int, bottom: Int, left: Int = 4, right: Int = 6): Raster {
        val raster = Raster(12, 24)
        for (y in top..bottom) for (x in left..right) raster[x, y] = ink
        return raster
    }

    @Test
    fun `known figure gives box, height, feet row and root`() {
        val measurement = ModelMeasure.measure(listOf(frame(4, 19), frame(4, 19)))

        assertEquals(FigureBox(4, 4, 6, 19), measurement.frames[0].box)
        assertEquals(16, measurement.bodyHeight)
        assertEquals(19, measurement.feetRow)
        assertEquals(5, measurement.frames[0].rootX)
    }

    @Test
    fun `clip height comes from the median frame`() {
        val measurement = ModelMeasure.measure(listOf(frame(4, 19), frame(4, 19), frame(0, 23)))

        assertEquals(16, measurement.bodyHeight)
        assertEquals(19, measurement.feetRow)
    }

    @Test
    fun `semi-transparent shadow does not move the box`() {
        val raster = frame(4, 19)
        for (x in 2..9) raster[x, 22] = Argb.of(0, 0, 0, 127)

        val measure = ModelMeasure.measureFrame(raster)

        assertEquals(FigureBox(4, 4, 6, 19), measure.box)
    }

    @Test
    fun `empty frame has no box, root, height or feet row`() {
        val measurement = ModelMeasure.measure(listOf(Raster(12, 24)))

        assertNull(measurement.frames[0].box)
        assertNull(measurement.frames[0].rootX)
        assertNull(measurement.bodyHeight)
        assertNull(measurement.feetRow)
    }

    @Test
    fun `empty frames are skipped for the clip's height`() {
        val measurement = ModelMeasure.measure(listOf(Raster(12, 24), frame(4, 19)))

        assertEquals(16, measurement.bodyHeight)
    }
}
