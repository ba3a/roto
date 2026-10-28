package com.roto.core.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArgbTest {

    @Test
    fun `of packs channels in AARRGGBB order`() {
        assertEquals(0x80112233.toInt(), Argb.of(red = 0x11, green = 0x22, blue = 0x33, alpha = 0x80))
    }

    @Test
    fun `alpha defaults to opaque`() {
        assertEquals(0xFF000000.toInt(), Argb.of(0, 0, 0))
    }

    @Test
    fun `channel accessors unpack what of packed`() {
        val pixel = Argb.of(red = 200, green = 100, blue = 50, alpha = 255)

        assertEquals(200, Argb.red(pixel))
        assertEquals(100, Argb.green(pixel))
        assertEquals(50, Argb.blue(pixel))
        assertEquals(255, Argb.alpha(pixel))
    }

    @Test
    fun `of rejects out of range channels`() {
        assertFailsWith<IllegalArgumentException> { Argb.of(256, 0, 0) }
        assertFailsWith<IllegalArgumentException> { Argb.of(0, -1, 0) }
        assertFailsWith<IllegalArgumentException> { Argb.of(0, 0, 0, alpha = 300) }
    }

    @Test
    fun `format prints eight hex digits`() {
        assertEquals("#00000000", Argb.format(Argb.TRANSPARENT))
        assertEquals("#FF0A0B0C", Argb.format(Argb.of(10, 11, 12)))
    }
}
