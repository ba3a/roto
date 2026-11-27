package com.roto.core.review

import com.roto.core.framebox.DepthSettings
import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.palette.Palette
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutlineGapTest {
    private val dark = Argb.of(10, 10, 10)
    private val light = Argb.of(200, 200, 200)
    private val black = Argb.of(0, 0, 0)
    private val palette = Palette(listOf(dark, light, black))
    private val ramps = RampSet(ramps = listOf(Ramp("a", listOf(dark, light))))

    private fun context(outline: Boolean, set: RampSet = ramps) =
        LintContext(palette, set, outline, DepthSettings())

    /** `.` transparent, `d` dark, `l` light, `k` black. */
    private fun art(vararg rows: String) = Raster.of(rows.first().length, rows.size) { x, y ->
        when (rows[y][x]) {
            'd' -> dark
            'l' -> light
            'k' -> black
            else -> Argb.TRANSPARENT
        }
    }

    private fun gaps(frame: Raster, context: LintContext) =
        OutlineGap.check(listOf(frame), context).map { it.pixel!!.x to it.pixel.y }

    @Test
    fun `a ring of the ramp's darkest is clean`() {
        assertTrue(gaps(art(".....", ".ddd.", ".dld.", ".ddd.", "....."), context(true)).isEmpty())
    }

    @Test
    fun `a light edge pixel is found`() {
        assertEquals(listOf(2 to 1), gaps(art(".....", ".dld.", ".ddd.", "....."), context(true)))
    }

    @Test
    fun `a pixel touching only the image border is not an edge`() {
        assertTrue(gaps(art("lll", "lll", "lll"), context(true)).isEmpty())
    }

    @Test
    fun `the project outline colour replaces the ramp darkest`() {
        val set = RampSet(outline = black, ramps = ramps.ramps)
        assertTrue(gaps(art(".....", ".kkk.", ".klk.", ".kkk.", "....."), context(true, set)).isEmpty())
        assertEquals(8, gaps(art(".....", ".ddd.", ".dld.", ".ddd.", "....."), context(true, set)).size)
    }

    @Test
    fun `nothing is reported with outline off`() {
        assertTrue(gaps(art(".lll.", ".lll."), context(false)).isEmpty())
    }
}
