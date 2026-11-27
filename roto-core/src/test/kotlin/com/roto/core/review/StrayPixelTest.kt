package com.roto.core.review

import com.roto.core.draft.Draft
import com.roto.core.draft.OrphanCleanup
import com.roto.core.framebox.DepthSettings
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster
import com.roto.core.palette.Palette
import com.roto.core.palette.RampSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrayPixelTest {
    private val red = Argb.of(255, 0, 0)
    private val blue = Argb.of(0, 0, 255)
    private val context = LintContext(Palette(listOf(red, blue)), RampSet(ramps = emptyList()), false, DepthSettings())

    /** `.` transparent, `r` red, `b` blue. */
    private fun art(vararg rows: String) = Raster.of(rows.first().length, rows.size) { x, y ->
        when (rows[y][x]) {
            'r' -> red
            'b' -> blue
            else -> Argb.TRANSPARENT
        }
    }

    private fun strays(frame: Raster) =
        StrayPixel.check(listOf(frame), context).map { it.pixel!!.x to it.pixel.y }

    @Test
    fun `a solid block is clean`() {
        assertTrue(strays(art("rrr", "rrr", "rrr")).isEmpty())
    }

    @Test
    fun `a lone colour inside a block is found`() {
        assertEquals(listOf(1 to 1), strays(art("rrr", "rbr", "rrr")))
    }

    @Test
    fun `an opaque pixel on empty ground is found`() {
        assertEquals(listOf(2 to 1), strays(art("...", "..r", "...")))
    }

    @Test
    fun `a one pixel line keeps its place`() {
        assertTrue(strays(art(".....", "rrrrr", ".....")).isEmpty())
    }

    @Test
    fun `an edge pixel is judged by the neighbours inside the image`() {
        assertEquals(listOf(0 to 0), strays(art("br", "rr")))
        assertTrue(strays(art("rb", "rb")).isEmpty())
    }

    @Test
    fun `a single pixel image has no neighbours and is clean`() {
        assertTrue(strays(art("r")).isEmpty())
    }

    @Test
    fun `the draft cleanup and the lint agree on the same fixture`() {
        val rows = arrayOf("rrr.b", "rbr..", "rrr.r", "..b..", "r...b")
        val frame = art(*rows)
        val colours = IndexedRaster.of(frame.width, frame.height) { x, y ->
            when (rows[y][x]) {
                'r' -> 1
                'b' -> 2
                else -> 0
            }
        }
        val draft = Draft(colours, colours)
        val cleaned = OrphanCleanup.clean(draft)
        val changed = (0 until frame.height).flatMap { y ->
            (0 until frame.width)
                .filter { x -> cleaned.colours[x, y] != colours[x, y] && colours[x, y] != 0 }
                .map { x -> x to y }
        }
        assertEquals(changed, strays(frame))
        assertTrue(changed.isNotEmpty())
    }
}
