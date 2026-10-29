package com.roto.core.framebox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScaleSettingsTest {

    private val marks = ScaleMarks("walk_EAST", frame = 12, headTopY = 38, feetY = 451)

    @Test
    fun `scale is body height over the marked height, squash only downwards`() {
        val settings = ScaleSettings(bodyHeight = 59, marks = marks, squash = 0.8)

        assertEquals(414, marks.height)
        assertEquals(59.0 / 414, settings.scaleX)
        assertEquals(59.0 / 414 * 0.8, settings.scaleY)
    }

    @Test
    fun `out-of-range settings and marks are rejected`() {
        listOf(
            { ScaleSettings(bodyHeight = 0, marks = marks) },
            { ScaleSettings(bodyHeight = 52, marks = marks, squash = 0.4) },
            { ScaleSettings(bodyHeight = 52, marks = marks, squash = 1.1) },
            { ScaleMarks("walk_EAST", frame = -1, headTopY = 0, feetY = 10) },
            { ScaleMarks("walk_EAST", frame = 0, headTopY = 10, feetY = 10) },
            { ScaleMarks("walk_EAST", frame = 0, headTopY = -1, feetY = 10) },
            { ScaleMarks("../walk", frame = 0, headTopY = 0, feetY = 10) },
        ).forEach { assertFailsWith<IllegalArgumentException> { it() } }
    }
}
