package com.roto.core.track

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RootSettingsTest {

    @Test
    fun `the defaults are valid and out-of-range settings are rejected`() {
        RootSettings()
        RootSettings(stabilise = false, hipBandTop = 0.0, hipBandBottom = 1.0, smoothing = 31)

        listOf(
            { RootSettings(hipBandTop = -0.1) },
            { RootSettings(hipBandTop = 0.6, hipBandBottom = 0.6) },
            { RootSettings(hipBandBottom = 1.1) },
            { RootSettings(smoothing = 0) },
            { RootSettings(smoothing = 4) },
            { RootSettings(smoothing = 33) },
        ).forEach { assertFailsWith<IllegalArgumentException> { it() } }
    }
}
