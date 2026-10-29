package com.roto.core.framebox

import com.roto.core.image.Argb
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import com.roto.core.project.Canvas
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasSamplerTest {

    /** A figure 8 frame pixels wide, 24 tall, feet on row 31 of a 40x40 frame. */
    private val figure = Mask.of(40, 40) { x, y -> x in 16..23 && y in 8..31 }

    private val canvas = Canvas(width = 10, height = 14, groundRow = 12)

    /** Half a canvas pixel per frame pixel: the figure becomes 4 wide and 12 tall. */
    private fun boxes(scaleY: Double = 0.5, centreX: Int = 20) =
        FrameBoxes(canvas, scaleX = 0.5, scaleY = scaleY, groundY = 31, centreX = listOf(centreX))

    @Test
    fun `the figure is scaled, centred across and standing on the ground row`() {
        val sampled = CanvasSampler.sample(figure, boxes(), frame = 0, factor = 1)

        assertEquals(Mask.of(10, 14) { x, y -> x in 3..6 && y in 1..12 }, sampled)
    }

    @Test
    fun `at four times the canvas the soles fill the ground row to its bottom edge`() {
        val sampled = CanvasSampler.sample(figure, boxes(), frame = 0, factor = 4)
        val rows = (0 until sampled.height).filter { y -> (0 until sampled.width).any { sampled[it, y] } }

        assertEquals(40 to 56, sampled.width to sampled.height)
        assertEquals(51, rows.last())
        assertEquals(48, rows.size)
    }

    @Test
    fun `squash shortens the figure and keeps its width and its feet on the ground`() {
        val sampled = CanvasSampler.sample(figure, boxes(scaleY = 0.25), frame = 0, factor = 1)

        assertEquals(Mask.of(10, 14) { x, y -> x in 3..6 && y in 7..12 }, sampled)
    }

    @Test
    fun `a frame's centre moves the box with the figure`() {
        val walkedOn = Mask.of(40, 40) { x, y -> x in 22..29 && y in 8..31 }

        assertEquals(
            CanvasSampler.sample(figure, boxes(), frame = 0, factor = 1),
            CanvasSampler.sample(walkedOn, boxes(centreX = 26), frame = 0, factor = 1),
        )
    }

    @Test
    fun `rasters sample their own pixels, and beyond the frame is empty`() {
        val ramp = Raster.of(40, 40) { x, y -> Argb.of(x * 6, y * 6, 0) }
        val nearEdge = FrameBoxes(canvas, scaleX = 0.5, scaleY = 0.5, groundY = 31, centreX = listOf(2))

        val sampled = CanvasSampler.sample(ramp, nearEdge, frame = 0, factor = 1)

        assertEquals(Argb.TRANSPARENT, sampled[0, 5])
        // Canvas (6, 5) shows frame pixel (2 + 0.5 + (6.5 - 5) * 2, 32 + (5.5 - 13) * 2) = (5.5, 17).
        assertEquals(ramp[5, 17], sampled[6, 5])
    }
}
