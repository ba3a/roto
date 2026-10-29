package com.roto.core.mask

import com.roto.core.color.OkLab
import com.roto.core.image.Argb
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ForegroundMaskTest {

    private val wall = Argb.of(200, 195, 185)
    private val plate = Raster.of(40, 30) { _, _ -> wall }

    private val shirt = Argb.of(40, 70, 160)
    private val skin = Argb.of(220, 170, 140)

    /** A quarter darker than the wall, its colour unchanged: a soft shadow. */
    private val shadow = Argb.of(150, 146, 138)

    /** Barely off the wall: sensor noise. */
    private val noise = Argb.of(206, 199, 183)

    private val body = (12..27) to (10..24)
    private val head = (17..22) to (4..9)

    @Test
    fun `the figure is masked exactly, without its shadow, specks, blobs or holes`() {
        val frame = Raster.of(40, 30) { x, y ->
            when {
                // A wall-coloured patch on the shirt, wider than the closing could bridge.
                x in 16..23 && y in 13..20 -> wall
                inBody(x, y) -> shirt
                inHead(x, y) -> skin
                // A shadow on the floor, touching the feet.
                x in 8..31 && y in 25..28 -> shadow
                // A one-pixel speck and a separate blob, both clearly not wall.
                x == 2 && y == 2 -> shirt
                x in 33..36 && y in 2..5 -> shirt
                (x + y) % 7 == 0 -> noise
                else -> wall
            }
        }

        val mask = ForegroundMask(plate, MaskSettings()).of(frame)

        assertEquals(expected().drawing(), mask.drawing())
    }

    @Test
    fun `faint skin is kept where it joins the shirt and dropped where it floats free`() {
        val frame = Raster.of(40, 30) { x, y ->
            when {
                inBody(x, y) -> shirt
                inHead(x, y) -> skin
                x in 1..5 && y in 1..5 -> skin
                else -> wall
            }
        }

        assertEquals(expected().drawing(), ForegroundMask(plate, MaskSettings()).of(frame).drawing())
    }

    @Test
    fun `the lightness part of the difference is weighted down`() {
        val gray = Raster.of(1, 1) { _, _ -> Argb.of(128, 128, 128) }
        val lighter = Raster.of(1, 1) { _, _ -> Argb.of(160, 160, 160) }
        val lightnessGap = abs(OkLab.fromArgb(lighter[0, 0]).l - OkLab.fromArgb(gray[0, 0]).l)

        val weighted = ForegroundMask(gray, MaskSettings(lightnessWeight = 0.25)).difference(lighter).single()
        val full = ForegroundMask(gray, MaskSettings(lightnessWeight = 1.0)).difference(lighter).single()

        assertTrue(abs(full - lightnessGap) < 1e-3, "full weight $full against a lightness gap of $lightnessGap")
        assertTrue(abs(weighted - 0.25 * full) < 1e-3, "weighted $weighted against full $full")
    }

    @Test
    fun `a frame must match the plate's size`() {
        assertFailsWith<IllegalArgumentException> { ForegroundMask(plate, MaskSettings()).of(Raster(40, 31)) }
    }

    private fun inBody(x: Int, y: Int) = x in body.first && y in body.second

    private fun inHead(x: Int, y: Int) = x in head.first && y in head.second

    /** The figure, head and body, solid: its hole filled. */
    private fun expected() = Mask.of(40, 30) { x, y -> inBody(x, y) || inHead(x, y) }
}
