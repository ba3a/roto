package com.roto.core.palette

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RampEditorTest {
    private val black = 0xFF000000.toInt()
    private val gray = 0xFF808080.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val palette = Palette(listOf(white, black, gray))

    @Test
    fun `picks come back sorted dark to light`() {
        val ramp = RampEditor.build("steel", listOf(white, black, gray), palette)

        assertEquals("steel", ramp.name)
        assertEquals(listOf(black, gray, white), ramp.colors)
    }

    @Test
    fun `equal lightness names both colours`() {
        val twice = Palette(listOf(gray, gray, black))

        val error = assertFailsWith<IllegalArgumentException> { RampEditor.build("steel", listOf(gray, gray), twice) }

        assertTrue(error.message!!.contains("#808080"))
        assertEquals(2, Regex("#808080").findAll(error.message!!).count())
    }

    @Test
    fun `a colour outside the palette is rejected`() {
        val red = 0xFFFF0000.toInt()

        val error = assertFailsWith<IllegalArgumentException> { RampEditor.build("steel", listOf(black, red), palette) }

        assertTrue(error.message!!.contains("#ff0000"))
    }

    @Test
    fun `an empty pick is rejected`() {
        assertFailsWith<IllegalArgumentException> { RampEditor.build("steel", emptyList(), palette) }
    }

    @Test
    fun `a blank name is rejected`() {
        assertFailsWith<IllegalArgumentException> { RampEditor.build("  ", listOf(black), palette) }
    }
}
