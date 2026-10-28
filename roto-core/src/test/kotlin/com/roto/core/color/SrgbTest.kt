package com.roto.core.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SrgbTest {

    @Test
    fun `every 8-bit channel value survives a round trip through linear light`() {
        for (channel in 0..255) {
            assertEquals(channel, Srgb.fromLinear(Srgb.toLinear(channel)), "channel $channel")
        }
    }

    @Test
    fun `endpoints map to zero and one`() {
        assertEquals(0.0, Srgb.toLinear(0))
        assertEquals(1.0, Srgb.toLinear(255), 1e-12)
    }

    @Test
    fun `mid gray is about a fifth of full linear light`() {
        assertEquals(0.2158605, Srgb.toLinear(128), 1e-6)
    }

    @Test
    fun `linear light is strictly increasing in the channel value`() {
        for (channel in 1..255) {
            assertTrue(Srgb.toLinear(channel) > Srgb.toLinear(channel - 1), "channel $channel")
        }
    }

    @Test
    fun `values outside zero to one clamp`() {
        assertEquals(0, Srgb.fromLinear(-0.25))
        assertEquals(255, Srgb.fromLinear(1.75))
    }
}
