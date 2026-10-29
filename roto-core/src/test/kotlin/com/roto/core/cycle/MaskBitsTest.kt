package com.roto.core.cycle

import com.roto.core.image.Mask
import kotlin.test.Test
import kotlin.test.assertEquals

class MaskBitsTest {

    @Test
    fun `overlap is intersection over union`() {
        val block = Mask.of(6, 4) { x, y -> x in 1..2 && y in 1..2 }
        val nudged = Mask.of(6, 4) { x, y -> x in 2..3 && y in 1..2 }

        assertEquals(1.0, MaskBits.of(block).overlap(MaskBits.of(block)))
        assertEquals(1.0 / 3, MaskBits.of(block).overlap(MaskBits.of(nudged)))
        assertEquals(0.0, MaskBits.of(Mask(6, 4)).overlap(MaskBits.of(Mask(6, 4))))
    }

    @Test
    fun `pixels across word boundaries all count`() {
        val wide = Mask.of(100, 3) { x, _ -> x % 3 == 0 }
        val shifted = Mask.of(100, 3) { x, _ -> x % 3 == 0 && x < 99 }

        assertEquals(99.0 / 102, MaskBits.of(wide).overlap(MaskBits.of(shifted)))
    }
}
