package com.roto.core.cycle

import com.roto.core.image.Mask
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactFinderTest {

    @Test
    fun `leg spread is the extent of the bottom band only`() {
        // Arms out wide at the top, feet 6 pixels apart edge to edge at the bottom.
        val figure = Mask.of(30, 20) { x, y ->
            when {
                y < 4 -> x in 2..27
                y < 14 -> x in 12..16
                else -> x == 11 || x == 17
            }
        }

        assertEquals(6, ContactFinder.legSpread(figure, 0.15))
        assertEquals(0, ContactFinder.legSpread(Mask(5, 5), 0.15))
    }

    @Test
    fun `contacts fall at the phase of widest spread, once per repeat`() {
        val walk = (0 until 35).map { walker(it) }

        assertEquals(listOf(2, 12, 22, 32), ContactFinder.contacts(walk, 10, 0.15))
        assertEquals(listOf(2, 7, 12, 17, 22, 27, 32), ContactFinder.contacts(walk, 5, 0.15))
    }
}
