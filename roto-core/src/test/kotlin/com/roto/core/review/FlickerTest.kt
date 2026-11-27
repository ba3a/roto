package com.roto.core.review

import com.roto.core.framebox.DepthSettings
import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.palette.Palette
import com.roto.core.palette.RampSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlickerTest {
    private val red = Argb.of(255, 0, 0)
    private val blue = Argb.of(0, 0, 255)
    private val context = LintContext(Palette(listOf(red, blue)), RampSet(ramps = emptyList()), false, DepthSettings())

    /** A 5 x 5 red frame with [colour] at the centre pixel and, when given, at [extra]. */
    private fun frame(colour: Int, extra: Pair<Int, Int>? = null) = Raster.of(5, 5) { x, y ->
        if ((x == 2 && y == 2) || (x to y) == extra) colour else red
    }

    private fun found(vararg frames: Raster) = Flicker.check(frames.toList(), context)

    @Test
    fun `steady frames are clean`() {
        assertTrue(found(frame(red), frame(red), frame(red)).isEmpty())
    }

    @Test
    fun `a one frame twinkle is found on the middle frame`() {
        val finding = LintFinding("Flicker", 1, LintFinding.Pixel(2, 2), "Pixel changes for one frame only")
        assertEquals(listOf(finding), found(frame(red), frame(blue), frame(red)))
    }

    @Test
    fun `a twinkle across the loop wraps`() {
        assertEquals(listOf(3), found(frame(red), frame(red), frame(red), frame(blue)).map { it.frame })
        assertEquals(listOf(0), found(frame(blue), frame(red), frame(red), frame(red)).map { it.frame })
    }

    @Test
    fun `a change that stays is not a twinkle`() {
        assertTrue(found(frame(red), frame(blue), frame(blue)).none { it.frame == 1 })
    }

    @Test
    fun `a changed neighbour makes it a real move`() {
        assertTrue(found(frame(red), frame(blue, 3 to 2), frame(red)).isEmpty())
    }

    @Test
    fun `fewer than three frames are clean`() {
        assertTrue(found(frame(red), frame(blue)).isEmpty())
    }
}
