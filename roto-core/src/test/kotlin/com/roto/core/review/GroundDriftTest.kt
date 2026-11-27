package com.roto.core.review

import com.roto.core.framebox.DepthSettings
import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.palette.Palette
import com.roto.core.palette.RampSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroundDriftTest {
    private val red = Argb.of(255, 0, 0)

    private fun context(depth: Boolean = false) = LintContext(
        Palette(listOf(red)),
        RampSet(ramps = emptyList()),
        false,
        DepthSettings(follow = depth),
    )

    /** A 1 x 10 frame whose lowest opaque row is [row]. */
    private fun frame(row: Int) = Raster.of(1, 10) { _, y -> if (y <= row) red else Argb.TRANSPARENT }

    private fun drifted(rows: List<Int>, context: LintContext) =
        GroundDrift.check(rows.map(::frame), context).map { it.frame }

    @Test
    fun `equal rows are clean`() {
        assertTrue(drifted(listOf(8, 8, 8), context()).isEmpty())
    }

    @Test
    fun `a frame beyond the threshold is found`() {
        assertEquals(listOf(2), drifted(listOf(8, 8, 5), context()))
    }

    @Test
    fun `a frame exactly at the threshold is clean`() {
        assertTrue(drifted(listOf(8, 8, 7), context()).isEmpty())
    }

    @Test
    fun `the median of an even count is the lower middle`() {
        assertEquals(listOf(3), drifted(listOf(5, 5, 6, 8), context()))
        assertTrue(drifted(listOf(5, 6, 6, 7), context()).isEmpty())
    }

    @Test
    fun `a clip that follows depth is skipped`() {
        assertTrue(drifted(listOf(8, 8, 2), context(depth = true)).isEmpty())
    }
}
