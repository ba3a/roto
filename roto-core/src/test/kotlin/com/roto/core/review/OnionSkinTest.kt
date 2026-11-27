package com.roto.core.review

import com.roto.core.image.Argb
import com.roto.core.image.Raster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OnionSkinTest {
    private val clear = Argb.TRANSPARENT
    private val white = Argb.of(255, 255, 255)

    private fun row(vararg pixels: Int) = Raster.fromArgbArray(pixels.size, 1, pixels)

    @Test
    fun `an opaque current pixel hides the ghosts`() {
        assertEquals(white, OnionSkin.compose(row(white), row(white), row(white))[0, 0])
    }

    @Test
    fun `previous alone is its tint at 30 percent`() {
        assertEquals(Argb.of(255, 0, 0, 77), OnionSkin.compose(row(clear), row(white), null)[0, 0])
    }

    @Test
    fun `next alone is its tint at 30 percent`() {
        assertEquals(Argb.of(0, 0, 255, 77), OnionSkin.compose(row(clear), null, row(white))[0, 0])
    }

    @Test
    fun `next draws over previous`() {
        assertEquals(Argb.of(105, 0, 150, 131), OnionSkin.compose(row(clear), row(white), row(white))[0, 0])
    }

    @Test
    fun `a transparent pixel everywhere stays transparent`() {
        assertEquals(clear, OnionSkin.compose(row(clear), row(clear), row(clear))[0, 0])
    }

    @Test
    fun `a half opaque current pixel shows the ghost through it`() {
        val current = Argb.of(255, 255, 255, 128)
        assertEquals(Argb.of(255, 196, 196, 166), OnionSkin.compose(row(current), row(white), null)[0, 0])
    }

    @Test
    fun `a size mismatch is refused`() {
        val error = assertThrows<IllegalArgumentException> {
            OnionSkin.compose(row(clear), row(clear, clear), null)
        }
        assertEquals("Onion skin frames must be the same size: 1x1 and 2x1", error.message)
    }
}
