package com.roto.core.model

import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelColoursTest {

    private val skin = listOf(Argb.of(60, 30, 20), Argb.of(120, 70, 50), Argb.of(200, 140, 110))
    private val cloth = listOf(Argb.of(10, 20, 60), Argb.of(30, 50, 110), Argb.of(60, 90, 170), Argb.of(100, 140, 220))
    private val outline = Argb.of(0, 0, 0)
    private val rampSet = RampSet(outline, listOf(Ramp("skin", skin), Ramp("cloth", cloth)))

    private fun frameOf(vararg colours: Int): Raster {
        val raster = Raster(colours.size, 1)
        colours.forEachIndexed { x, c -> raster[x, 0] = c }
        return raster
    }

    @Test
    fun `ramp colours map to their steps and the summary reads like the spec`() {
        val frames = listOf(frameOf(skin[0], skin[2], cloth[1]), frameOf(cloth[3], outline, skin[1]))

        val colours = ModelMeasure.colours(frames, rampSet)

        assertEquals(listOf(RampUse("skin", 1, 3), RampUse("cloth", 2, 4)), colours.ramps)
        assertEquals(true, colours.usesOutline)
        assertEquals("skin 1-3, cloth 2-4, outline", colours.summary())
    }

    @Test
    fun `colours outside every ramp are listed and faint pixels are ignored`() {
        val stray = Argb.of(250, 0, 250)
        val faint = Argb.of(1, 2, 3, 100)

        val colours = ModelMeasure.colours(listOf(frameOf(skin[1], stray, faint)), rampSet)

        assertEquals(listOf(stray), colours.outside)
        assertEquals(false, colours.usesOutline)
        assertEquals("skin 2, outside #fa00fa", colours.summary())
    }
}
