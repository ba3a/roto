package com.roto.core.color

import com.roto.core.image.Argb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class OkLabTest {

    /**
     * Every seventh colour of the 16.7M keeps this near a second; the full sweep passes too but takes
     * about ten. Seven is coprime with every channel stride, so the sample still covers every value of
     * every channel in varied combinations.
     */
    @Test
    fun `8-bit sRGB colours survive a round trip unchanged`() {
        var mismatches = 0
        var firstMismatch: String? = null
        for (rgb in 0..0xFFFFFF step ROUND_TRIP_STRIDE) {
            val pixel = rgb or OPAQUE
            val back = OkLab.fromArgb(pixel).toArgb()
            if (back != pixel) {
                mismatches++
                if (firstMismatch == null) firstMismatch = "${Argb.format(pixel)} -> ${Argb.format(back)}"
            }
        }
        if (mismatches > 0) fail("$mismatches colours changed, first: $firstMismatch")
    }

    @Test
    fun `primaries match Ottosson's reference values`() {
        assertLab(OkLab(0.627955, 0.224863, 0.125846), OkLab.fromArgb(Argb.of(255, 0, 0)))
        assertLab(OkLab(0.866440, -0.233888, 0.179498), OkLab.fromArgb(Argb.of(0, 255, 0)))
        assertLab(OkLab(0.452014, -0.032457, -0.311528), OkLab.fromArgb(Argb.of(0, 0, 255)))
    }

    @Test
    fun `black and white sit at the ends of the lightness axis`() {
        assertLab(OkLab(0.0, 0.0, 0.0), OkLab.fromArgb(Argb.of(0, 0, 0)))
        assertLab(OkLab(1.0, 0.0, 0.0), OkLab.fromArgb(Argb.of(255, 255, 255)))
    }

    @Test
    fun `grays have no chroma and lightness rises with level`() {
        var previousLightness = -1.0
        for (level in 0..255) {
            val lab = OkLab.fromArgb(Argb.of(level, level, level))
            assertTrue(lab.chroma < NEUTRAL_TOLERANCE, "gray $level has chroma ${lab.chroma}")
            assertTrue(lab.l > previousLightness, "gray $level is not lighter than gray ${level - 1}")
            previousLightness = lab.l
        }
    }

    @Test
    fun `fromArgb ignores alpha and toArgb applies the given alpha`() {
        val opaque = Argb.of(10, 120, 200)
        val translucent = Argb.of(10, 120, 200, alpha = 40)

        assertEquals(OkLab.fromArgb(opaque), OkLab.fromArgb(translucent))
        assertEquals(translucent, OkLab.fromArgb(opaque).toArgb(alpha = 40))
    }

    @Test
    fun `out of gamut colours clamp to valid pixels`() {
        assertEquals(Argb.of(255, 255, 255), OkLab(1.5, 0.0, 0.0).toArgb())
        assertEquals(Argb.of(0, 0, 0), OkLab(-0.5, 0.0, 0.0).toArgb())

        val saturated = OkLab(0.6, 0.4, 0.0).toArgb()
        assertEquals(255, Argb.red(saturated))
        assertEquals(0, Argb.green(saturated))
    }

    @Test
    fun `conversion is repeatable to the last bit`() {
        val first = (0..0xFFFFFF step 997).map { OkLab.fromArgb(it or OPAQUE) }
        val second = (0..0xFFFFFF step 997).map { OkLab.fromArgb(it or OPAQUE) }

        assertEquals(first, second)
    }

    private fun assertLab(expected: OkLab, actual: OkLab) {
        val close = kotlin.math.abs(expected.l - actual.l) < REFERENCE_TOLERANCE &&
            kotlin.math.abs(expected.a - actual.a) < REFERENCE_TOLERANCE &&
            kotlin.math.abs(expected.b - actual.b) < REFERENCE_TOLERANCE
        assertTrue(close, "expected $expected, was $actual")
    }

    private companion object {
        const val OPAQUE = 0xFF000000.toInt()
        const val ROUND_TRIP_STRIDE = 7
        const val REFERENCE_TOLERANCE = 1e-4
        const val NEUTRAL_TOLERANCE = 1e-4
    }
}
