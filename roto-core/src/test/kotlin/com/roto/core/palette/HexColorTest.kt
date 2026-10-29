package com.roto.core.palette

import com.roto.core.image.Argb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HexColorTest {

    @Test
    fun `parses either case and writes lower case`() {
        assertEquals(Argb.of(0x1A, 0x1C, 0x2C), HexColor.parse("#1A1c2C"))
        assertEquals("#1a1c2c", HexColor.format(Argb.of(0x1A, 0x1C, 0x2C)))
        assertEquals("#000000", HexColor.format(Argb.of(0, 0, 0, alpha = 0)))
        assertEquals(Argb.of(255, 255, 255), HexColor.parse("ffffff", hashOptional = true))
    }

    @Test
    fun `anything but six hex digits is rejected`() {
        listOf("1a1c2c", "#1a1c2", "#1a1c2c0", "#gggggg", "", "#").forEach { text ->
            assertFailsWith<IllegalArgumentException>(text) { HexColor.parse(text) }
        }
        assertFailsWith<IllegalArgumentException> { HexColor.parse("#12345", hashOptional = true) }
    }
}
