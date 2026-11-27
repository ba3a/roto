package com.roto.core.review

import com.roto.core.framebox.DepthSettings
import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.palette.Palette
import com.roto.core.palette.RampSet
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeamRuleTest {
    private val red = Argb.of(255, 0, 0)
    private val blue = Argb.of(0, 0, 255)

    private fun context(loop: Boolean) =
        LintContext(Palette(listOf(red, blue)), RampSet(ramps = emptyList()), false, DepthSettings(), loop)

    /** A 10 x 10 red frame, 100 opaque pixels, with the first [blues] pixels blue. */
    private fun frame(blues: Int) = Raster.of(10, 10) { x, y -> if (y * 10 + x < blues) blue else red }

    @Test
    fun `a matching seam is clean`() {
        assertTrue(SeamRule.check(listOf(frame(0), frame(3), frame(0)), context(true)).isEmpty())
    }

    @Test
    fun `exactly the threshold passes`() {
        assertTrue(SeamRule.check(listOf(frame(0), frame(5)), context(true)).isEmpty())
    }

    @Test
    fun `one pixel over is reported on the last frame`() {
        val findings = SeamRule.check(listOf(frame(0), frame(0), frame(6)), context(true))
        assertEquals(listOf("LoopSeam" to 2), findings.map { it.rule to it.frame })
        assertNull(findings.single().pixel)
    }

    @Test
    fun `a clip that does not loop has no seam finding`() {
        assertTrue(SeamRule.check(listOf(frame(0), frame(60)), context(false)).isEmpty())
    }
}
