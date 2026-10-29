package com.roto.core.zone

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ZoneSettingsTest {

    private val sample = listOf(ZoneSample(0, 1, 2))

    @Test
    fun `zones need a name, a ramp and samples, and distinct names`() {
        ZoneSettings(listOf(Zone("jacket", "suit", sample), Zone("trousers", "suit", sample)))

        listOf(
            { Zone(" ", "suit", sample) },
            { Zone("jacket", "", sample) },
            { Zone("jacket", "suit", emptyList()) },
            { ZoneSample(-1, 0, 0) },
            { ZoneSample(0, -1, 0) },
            { ZoneSettings(listOf(Zone("jacket", "suit", sample), Zone("jacket", "skin", sample))) },
            { ZoneSettings(lightnessWeight = 1.5) },
            { ZoneSettings((0..255).map { Zone("z$it", "suit", sample) }) },
        ).forEach { assertFailsWith<IllegalArgumentException> { it() } }
    }
}
