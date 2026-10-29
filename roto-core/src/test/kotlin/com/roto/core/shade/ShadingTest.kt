package com.roto.core.shade

import com.roto.core.color.OkLab
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster
import com.roto.core.palette.Palette
import com.roto.core.palette.ProjectPalette
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import com.roto.core.zone.Zone
import com.roto.core.zone.ZoneSample
import com.roto.core.zone.ZoneSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShadingTest {

    private fun gray(level: Int) = Argb.of(level, level, level)

    private fun lightness(level: Int) = OkLab.fromArgb(gray(level)).l

    @Test
    fun `a zone's lightness range splits evenly into its steps, clamped outside`() {
        val shading = ZoneShading(low = 0.2, high = 0.8, steps = 3)

        assertEquals(listOf(0, 0, 0, 1, 2, 2, 2), listOf(0.1, 0.2, 0.39, 0.41, 0.79, 0.8, 1.0).map { shading.step(it) })
        assertEquals(1, ZoneShading(0.5, 0.5, 4).step(0.9))
        assertEquals(0, ZoneShading(0.0, 1.0, 1).step(0.9))
        assertFailsWith<IllegalArgumentException> { ZoneShading(0.8, 0.2, 3) }
        assertFailsWith<IllegalArgumentException> { ZoneShading(0.2, 0.8, 0) }
        assertFailsWith<IllegalArgumentException> { ShadeSettings(lowPercentile = 50.0, highPercentile = 50.0) }
    }

    @Test
    fun `the range is fitted to percentiles of the whole clip, background ignored`() {
        // Zone 1 is gray 100..150 in one frame and 150..200 in the next; the background is black.
        val frames = listOf(0, 50).map { offset ->
            Raster.of(103, 1) { x, _ -> if (x < 51) gray(100 + offset + x) else gray(0) }
        }
        val zones = List(2) { IndexedRaster.of(103, 1) { x, _ -> if (x < 51) 1 else 0 } }

        val shading = ShadingFit.fit(frames, zones, steps = listOf(4), settings = ShadeSettings())

        // 102 lightnesses, gray 150 twice: rank 5 is gray 105, rank 95 is gray 194.
        assertEquals(ZoneShading(lightness(105), lightness(194), 4), shading.zones.single())
    }

    @Test
    fun `each zone gets its own range, and an empty zone its middle step`() {
        val frame = Raster.of(4, 1) { x, _ -> gray(listOf(20, 40, 200, 220)[x]) }
        val zones = IndexedRaster.of(4, 1) { x, _ -> if (x < 2) 1 else 2 }

        val everything = ShadeSettings(lowPercentile = 0.0, highPercentile = 100.0)
        val shading = ShadingFit.fit(listOf(frame), listOf(zones), steps = listOf(2, 3, 5), settings = everything)

        assertEquals(ZoneShading(lightness(20), lightness(40), 2), shading.zones[0])
        assertEquals(ZoneShading(lightness(200), lightness(220), 3), shading.zones[1])
        assertEquals(ZoneShading(0.0, 0.0, 5), shading.zones[2])
    }

    @Test
    fun `the shader turns each zone pixel into its step's palette label`() {
        val shading = ClipShading(listOf(ZoneShading(lightness(50), lightness(200), 2), ZoneShading(0.0, 0.0, 1)))
        val shader = RampShader(shading, zoneColours = listOf(listOf(5, 6), listOf(9)))
        val frame = Raster.of(4, 1) { x, _ -> gray(listOf(60, 190, 128, 0)[x]) }
        val zones = IndexedRaster.of(4, 1) { x, _ -> listOf(1, 1, 2, 0)[x] }

        assertEquals(IndexedRaster.of(4, 1) { x, _ -> listOf(5, 6, 9, 0)[x] }, shader.shade(frame, zones))
        assertFailsWith<IllegalArgumentException> { RampShader(shading, listOf(listOf(5, 6), listOf(9, 10))) }
    }

    @Test
    fun `draft colours are the ramp and outline colours' palette indices plus one`() {
        val palette = Palette(listOf(gray(0), Argb.of(200, 30, 30), gray(128), gray(255)))
        val grays = Ramp("gray", listOf(gray(0), gray(128), gray(255)))
        val project = ProjectPalette(palette, RampSet(outline = Argb.of(200, 30, 30), ramps = listOf(grays)))
        val sample = listOf(ZoneSample(0, 0, 0))

        val zoning = ZoneSettings(listOf(Zone("coat", "gray", sample), Zone("hat", "gray", sample)))
        val colours = DraftColours.of(project, zoning)

        assertEquals(DraftColours(listOf(listOf(1, 3, 4), listOf(1, 3, 4)), outline = 2), colours)
        val red = ZoneSettings(listOf(Zone("x", "red", sample)))
        assertFailsWith<IllegalArgumentException> { DraftColours.of(project, red) }
        assertFailsWith<IllegalArgumentException> { DraftColours(listOf(listOf(0, 3))) }
    }
}
