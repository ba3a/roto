package com.roto.core.mask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HysteresisTest {

    @Test
    fun `faint pixels survive only when they touch certain ones, corners included`() {
        // 9 is certain, 5 faint, 1 below both thresholds.
        val values = listOf(
            "9 5 1 1 1 5",
            "1 1 5 1 1 1",
            "1 1 1 5 1 5",
            "1 1 1 1 1 1",
        )

        val result = Hysteresis.threshold(6, 4, parse(values), low = 0.4, high = 0.8)

        assertEquals(
            mask(
                "##....",
                "..#...",
                "...#..",
                "......",
            ).drawing(),
            result.drawing(),
        )
    }

    @Test
    fun `values exactly on a threshold count`() {
        val result = Hysteresis.threshold(3, 1, doubleArrayOf(0.8, 0.4, 0.39), low = 0.4, high = 0.8)

        assertEquals(mask("##.").drawing(), result.drawing())
    }

    @Test
    fun `thresholds and sizes are checked`() {
        assertFailsWith<IllegalArgumentException> { Hysteresis.threshold(2, 1, doubleArrayOf(1.0, 1.0), 0.9, 0.1) }
        assertFailsWith<IllegalArgumentException> { Hysteresis.threshold(2, 2, doubleArrayOf(1.0), 0.1, 0.9) }
    }

    private fun parse(rows: List<String>): DoubleArray =
        rows.flatMap { row -> row.split(' ').map { it.toDouble() / 10 } }.toDoubleArray()
}
