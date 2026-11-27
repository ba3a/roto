package com.roto.core.review

import com.roto.core.framebox.DepthSettings
import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.palette.Palette
import com.roto.core.palette.RampSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtLintTest {
    private val red = Argb.of(255, 0, 0)
    private val blue = Argb.of(0, 0, 255)
    private val context = LintContext(Palette(listOf(red)), RampSet(ramps = emptyList()), false, DepthSettings())

    private fun frame(vararg pixels: Int) = Raster.fromArgbArray(pixels.size, 1, pixels)

    @Test
    fun `a frame in the palette is clean`() {
        assertTrue(ArtLint.run(listOf(frame(red, red)), context).clean)
    }

    @Test
    fun `an opaque colour outside the palette is found`() {
        val report = ArtLint.run(listOf(frame(red, red, blue, blue)), context)
        val expected = LintFinding("OffPalette", 0, LintFinding.Pixel(2, 0), "Colour #0000ff is not in the palette")
        assertEquals(listOf(expected), report.findings.filter { it.rule == "OffPalette" && it.pixel!!.x == 2 })
        assertEquals(2, report.counts.getValue("OffPalette"))
    }

    @Test
    fun `a transparent pixel is ignored whatever its colour`() {
        val clear = Argb.of(0, 0, 255) and 0x00FFFFFF
        assertTrue(ArtLint.run(listOf(frame(clear)), context).clean)
    }

    @Test
    fun `the report order is fixed across runs`() {
        val frames = listOf(frame(blue, blue), frame(blue))
        val first = ArtLint.run(frames, context)
        val second = ArtLint.run(frames, context)
        assertEquals(first.findings, second.findings)
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0), first.findings.map { it.frame to it.pixel!!.x })
    }

    @Test
    fun `unpainted frames are skipped and painted ones keep their index`() {
        val report = ArtLint.run(listOf(null, frame(blue), null), context)
        assertEquals(listOf(0, 2), report.skipped)
        assertEquals(listOf(1), report.findings.map { it.frame })
    }
}
