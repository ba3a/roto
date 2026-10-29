package com.roto.core.mask

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MaskSettingsTest {

    @Test
    fun `the defaults are valid and out-of-range settings are rejected`() {
        MaskSettings()
        MaskSettings(lightnessWeight = 0.0, low = 0.1, high = 0.1, openRadius = 0, closeRadius = 16)

        listOf(
            { MaskSettings(lightnessWeight = -0.1) },
            { MaskSettings(lightnessWeight = 1.5) },
            { MaskSettings(low = 0.0) },
            { MaskSettings(low = 0.2, high = 0.1) },
            { MaskSettings(openRadius = -1) },
            { MaskSettings(closeRadius = 17) },
        ).forEach { assertFailsWith<IllegalArgumentException> { it() } }
    }
}
