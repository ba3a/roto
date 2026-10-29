package com.roto.core.zone

import com.roto.core.color.OkLab
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NearestZoneTest {

    private val skin = listOf(Argb.of(90, 58, 46), Argb.of(160, 103, 75), Argb.of(224, 169, 138))
    private val suit = listOf(Argb.of(30, 42, 74), Argb.of(52, 80, 138), Argb.of(90, 134, 200))
    private val wall = Argb.of(200, 195, 185)

    /** Head rows 0..5 and body rows 6..17 of a 9-wide figure, shaded dark, mid and light across in 3-pixel bands. */
    private val frame = Raster.of(12, 20) { x, y ->
        when {
            x !in 2..10 || y > 17 -> wall
            y < 6 -> skin[(x - 2) / 3]
            else -> suit[(x - 2) / 3]
        }
    }
    private val mask = Mask.of(12, 20) { x, y -> x in 2..10 && y <= 17 }

    /** Sampled once each, in the middle of the mid shade, so each 3x3 patch is that shade alone. */
    private val settings = ZoneSettings(
        zones = listOf(
            Zone("face", ramp = "skin", samples = listOf(ZoneSample(0, x = 6, y = 2))),
            Zone("jacket", ramp = "suit", samples = listOf(ZoneSample(0, x = 6, y = 11))),
        ),
    )

    @Test
    fun `every shade of a material joins its zone, background stays 0`() {
        val labels = label(settings)

        assertEquals(IndexedRaster.of(12, 20) { x, y -> if (!mask[x, y]) 0 else if (y < 6) 1 else 2 }, labels)
    }

    @Test
    fun `without lightness weighted down, the dark skin is taken for the suit`() {
        val labels = label(settings.copy(lightnessWeight = 1.0))

        assertEquals(2, labels[2, 2])
        assertEquals(1, labels[9, 2])
    }

    @Test
    fun `a prototype is the mean of the 3x3 pixels around the click, clipped at the edge`() {
        val corner = ZoneSettings(listOf(Zone("x", "skin", listOf(ZoneSample(0, 0, 0)))))
        val nearSkin = ZoneSettings(listOf(Zone("x", "skin", listOf(ZoneSample(0, 1, 0)))))

        val atCorner = NearestZone.prototypes(listOf(frame), corner).single()
        val besideSkin = NearestZone.prototypes(listOf(frame), nearSkin).single()

        // The corner's four in-frame neighbours are all wall; beside the head, two of six are skin.
        assertEquals(1, atCorner.label)
        assertTrue(abs(atCorner.color.l - OkLab.fromArgb(wall).l) < 1e-12)
        val mixed = (4 * OkLab.fromArgb(wall).l + 2 * OkLab.fromArgb(skin[0]).l) / 6
        assertTrue(abs(besideSkin.color.l - mixed) < 1e-12)
    }

    @Test
    fun `samples outside the clip or the frame, and no zones at all, are errors`() {
        assertFailsWith<IllegalArgumentException> {
            NearestZone.prototypes(listOf(frame), ZoneSettings(listOf(Zone("x", "skin", listOf(ZoneSample(1, 0, 0))))))
        }
        assertFailsWith<IllegalArgumentException> {
            NearestZone.prototypes(listOf(frame), ZoneSettings(listOf(Zone("x", "skin", listOf(ZoneSample(0, 12, 0))))))
        }
        assertFailsWith<IllegalArgumentException> { NearestZone.prototypes(listOf(frame), ZoneSettings()) }
    }

    private fun label(settings: ZoneSettings): IndexedRaster =
        NearestZone.label(frame, mask, NearestZone.prototypes(listOf(frame), settings), settings.lightnessWeight)
}
