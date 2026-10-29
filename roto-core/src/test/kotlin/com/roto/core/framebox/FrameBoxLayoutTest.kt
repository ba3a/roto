package com.roto.core.framebox

import com.roto.core.image.Mask
import com.roto.core.project.Canvas
import com.roto.core.track.RootSettings
import com.roto.core.track.Stabiliser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FrameBoxLayoutTest {

    private val canvas = Canvas(width = 40, height = 60, groundRow = 59)
    private val scale = ScaleSettings(bodyHeight = 52, marks = ScaleMarks("walk_EAST", 0, headTopY = 10, feetY = 113))

    /** A figure walking 3 pixels right a frame, feet on row 113, one foot lifted every third frame. */
    private val walk = (0 until 9).map { frame ->
        val left = 10 + 3 * frame
        val bottom = if (frame % 3 == 1) 110 else 113
        Mask.of(80, 120) { x, y -> x in left until left + 7 && y in 10..bottom }
    }

    @Test
    fun `the ground is where the feet usually are`() {
        assertEquals(113, FrameBoxLayout.groundY(walk))
        assertEquals(113, FrameBoxLayout.groundY(walk + Mask(80, 120)))
        assertFailsWith<IllegalArgumentException> { FrameBoxLayout.groundY(listOf(Mask(5, 5))) }
    }

    @Test
    fun `a stabilised clip's box follows the figure, an unstabilised one stays put`() {
        val following = FrameBoxLayout.of(walk, Stabiliser.track(walk, RootSettings()), scale, canvas)
        val still = FrameBoxLayout.of(walk, Stabiliser.track(walk, RootSettings(stabilise = false)), scale, canvas)

        assertEquals(walk.indices.map { 13 + 3 * it }, following.centreX)
        assertEquals(List(walk.size) { 25 }, still.centreX)
    }

    @Test
    fun `the scale comes from the marks and the squash`() {
        val boxes = FrameBoxLayout.of(walk, Stabiliser.track(walk, RootSettings()), scale.copy(squash = 0.75), canvas)

        assertEquals(0.5, boxes.scaleX)
        assertEquals(0.375, boxes.scaleY)
        assertEquals(113, boxes.groundY)
        assertEquals(canvas, boxes.canvas)
    }
}
