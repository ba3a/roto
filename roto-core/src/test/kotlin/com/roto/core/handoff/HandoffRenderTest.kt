package com.roto.core.handoff

import com.roto.core.cycle.KeyFrames
import com.roto.core.framebox.FrameBoxes
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster
import com.roto.core.palette.Palette
import com.roto.core.palette.PaletteQuantiser
import com.roto.core.project.Canvas
import com.roto.core.video.FrameRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HandoffRenderTest {

    private val palette = Palette(listOf(Argb.of(0, 0, 0), Argb.of(200, 30, 30), Argb.of(250, 250, 250)))

    @Test
    fun `the reference is the frame mapped to the nearest palette colours, transparent stays 0`() {
        val photo = Raster.of(3, 1) { x, _ -> listOf(Argb.of(20, 10, 10), Argb.of(180, 40, 35), Argb.TRANSPARENT)[x] }

        assertEquals(IndexedRaster.of(3, 1) { x, _ -> listOf(1, 2, 0)[x] }, PaletteQuantiser.labels(photo, palette))
    }

    @Test
    fun `each key frame gets its duration, its reference and its draft`() {
        val boxes =
            FrameBoxes(Canvas(7, 6, groundRow = 4), scaleX = 1.0, scaleY = 1.0, groundY = 4, centreX = List(9) { 3 })
        val dark = Raster.of(6, 6) { _, _ -> Argb.of(0, 0, 0) }
        val light = Raster.of(6, 6) { _, _ -> Argb.of(240, 240, 240) }
        val frames = List(9) { frame -> if (frame < 4) dark else light }
        val drafts = List(2) { IndexedRaster.of(7, 6) { _, _ -> 2 } }

        val sources = HandoffRender.Sources(frames, boxes, drafts)
        val render = HandoffRender.of(palette, KeyFrames(listOf(0, 4, 9)), FrameRate(25, 1), sources)

        assertEquals(listOf(160, 200), render.frames.map { it.durationMs })
        assertEquals(1, render.frames[0].reference[2, 2])
        assertEquals(3, render.frames[1].reference[2, 2])
        assertEquals(0, render.frames[1].reference[6, 2])
        assertEquals(7 to 6, render.width to render.height)
    }

    @Test
    fun `drafts must match the key frames and the canvas`() {
        assertFailsWith<IllegalArgumentException> {
            HandoffRender(palette, 2, 2, listOf(HandoffFrame(100, IndexedRaster(2, 2), IndexedRaster(3, 2))))
        }
        assertFailsWith<IllegalArgumentException> {
            val beyond = IndexedRaster.of(2, 2) { _, _ -> 4 }
            HandoffRender(palette, 2, 2, listOf(HandoffFrame(100, IndexedRaster(2, 2), beyond)))
        }
        assertFailsWith<IllegalArgumentException> { HandoffFrame(0, IndexedRaster(2, 2), IndexedRaster(2, 2)) }
    }
}
