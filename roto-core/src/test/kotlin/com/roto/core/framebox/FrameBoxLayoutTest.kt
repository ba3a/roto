package com.roto.core.framebox

import com.roto.core.image.Mask
import com.roto.core.project.Canvas
import com.roto.core.track.RootSettings
import com.roto.core.track.Stabiliser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

        assertEquals(List(walk.size) { 0.5 }, boxes.scaleX)
        assertEquals(List(walk.size) { 0.375 }, boxes.scaleY)
        assertEquals(List(walk.size) { 113 }, boxes.groundY)
        assertEquals(canvas, boxes.canvas)
    }

    /** A figure walking toward the camera: 40 rows tall at first, growing 2 rows a frame, feet always lower. */
    private val approach = (0 until 9).map { frame ->
        val height = 40 + 2 * frame
        Mask.of(80, 120) { x, y -> x in 30..36 && y in 50 + 2 * frame - height + 1..50 + 2 * frame }
    }

    @Test
    fun `a figure walking toward the camera is scaled to the same size in every frame`() {
        val track = Stabiliser.track(approach, RootSettings())
        val boxes = FrameBoxLayout.of(approach, track, scale, canvas, DepthSettings(follow = true, smoothing = 1))

        assertEquals(approach.indices.map { 50 + 2 * it }, boxes.groundY)
        approach.indices.forEach { frame ->
            assertEquals(52.0 / (40 + 2 * frame), boxes.scaleX[frame], 1e-9)
            // the head top and the feet land on the same canvas rows in every frame
            val headTop = 50.0 + 2 * frame - (40 + 2 * frame) + 1
            assertEquals(headTop, boxes.frameY(frame, canvas.groundRow + 1 - 52.0), 1e-9)
        }
    }

    @Test
    fun `smoothing keeps a lifted foot from pumping the scale`() {
        val track = Stabiliser.track(walk, RootSettings())
        val raw = FrameBoxLayout.of(walk, track, scale, canvas, DepthSettings(follow = true, smoothing = 1))
        val smooth = FrameBoxLayout.of(walk, track, scale, canvas, DepthSettings(follow = true, smoothing = 3))

        fun spread(boxes: FrameBoxes) = boxes.scaleX.max() - boxes.scaleX.min()
        assertTrue(spread(raw) > 0.0)
        assertTrue(spread(smooth) < spread(raw))
    }

    @Test
    fun `smoothing must be an odd window`() {
        assertFailsWith<IllegalArgumentException> { DepthSettings(smoothing = 4) }
        assertFailsWith<IllegalArgumentException> { DepthSettings(smoothing = 63) }
    }
}
