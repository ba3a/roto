package com.roto.core.cache

import kotlin.test.Test
import kotlin.test.assertEquals

class StageOutputTest {

    @Test
    fun `the value is produced on the first get and kept`() {
        var produced = 0
        val output = StageOutput(CacheKey.of("plate", 1) {}) {
            produced++
            "plate"
        }

        assertEquals(0, produced)
        assertEquals("plate", output.get())
        assertEquals("plate", output.get())
        assertEquals(1, produced)
    }
}
