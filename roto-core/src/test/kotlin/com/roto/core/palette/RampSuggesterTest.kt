package com.roto.core.palette

import kotlin.test.Test
import kotlin.test.assertEquals

class RampSuggesterTest {
    private val darkRed = 0xFF800000.toInt()
    private val red = 0xFFFF0000.toInt()
    private val lightRed = 0xFFFF8080.toInt()
    private val green = 0xFF00C000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val black = 0xFF000000.toInt()
    private val gray = 0xFF808080.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun `groups by hue, sorts by lightness and puts neutrals last`() {
        val palette = Palette(listOf(white, blue, lightRed, gray, green, darkRed, black, red))

        val ramps = RampSuggester.suggest(palette)

        assertEquals(listOf("ramp1", "ramp2", "ramp3", "ramp4"), ramps.map { it.name })
        assertEquals(listOf(darkRed, red, lightRed), ramps[0].colors)
        assertEquals(listOf(green), ramps[1].colors)
        assertEquals(listOf(blue), ramps[2].colors)
        assertEquals(listOf(black, gray, white), ramps[3].colors)
    }

    @Test
    fun `equal lightness keeps the lowest palette index`() {
        val ramps = RampSuggester.suggest(Palette(listOf(white, gray, white, black)))

        assertEquals(listOf(listOf(black, gray, white)), ramps.map { it.colors })
    }

    @Test
    fun `same palette gives the same ramps`() {
        val palette = Palette(listOf(blue, red, green, gray, lightRed))

        assertEquals(RampSuggester.suggest(palette), RampSuggester.suggest(palette))
    }
}
