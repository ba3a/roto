package com.roto.core.variant

import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import kotlin.test.Test
import kotlin.test.assertEquals

class RampSwapTest {
    private fun grey(level: Int) = Argb.of(level, level, level)

    private fun ramp(name: String, base: Int, length: Int) =
        Ramp(name, List(length) { grey(base + it * 10) })

    private fun swap(ramps: RampSet, vararg pairs: Pair<String, String>, outline: Int? = null) =
        RampSwap(ramps, pairs.map { RampSwapPair(it.first, it.second) }, outline)

    @Test
    fun `equal lengths map step to step`() {
        val a = ramp("a", 10, 4)
        val b = ramp("b", 100, 4)
        val s = swap(RampSet(ramps = listOf(a, b)), "a" to "b")
        a.colors.indices.forEach { assertEquals(b.colors[it], s.map(a.colors[it])) }
    }

    @Test
    fun `three steps to five round as the formula says`() {
        val a = ramp("a", 10, 3)
        val b = ramp("b", 100, 5)
        val s = swap(RampSet(ramps = listOf(a, b)), "a" to "b")
        assertEquals(listOf(0, 2, 4).map { b.colors[it] }, a.colors.map(s::map))
    }

    @Test
    fun `five steps to three round as the formula says`() {
        val a = ramp("a", 10, 5)
        val b = ramp("b", 100, 3)
        val s = swap(RampSet(ramps = listOf(a, b)), "a" to "b")
        assertEquals(listOf(0, 1, 1, 2, 2).map { b.colors[it] }, a.colors.map(s::map))
    }

    @Test
    fun `a one step ramp maps to the first step`() {
        val a = ramp("a", 10, 1)
        val b = ramp("b", 100, 3)
        val s = swap(RampSet(ramps = listOf(a, b)), "a" to "b")
        assertEquals(b.colors[0], s.map(a.colors[0]))
    }

    @Test
    fun `a rotation swaps without chaining`() {
        val a = ramp("a", 10, 2)
        val b = ramp("b", 100, 2)
        val c = ramp("c", 200, 2)
        val s = swap(RampSet(ramps = listOf(a, b, c)), "a" to "b", "b" to "c", "c" to "a")
        assertEquals(b.colors, a.colors.map(s::map))
        assertEquals(c.colors, b.colors.map(s::map))
        assertEquals(a.colors, c.colors.map(s::map))
    }

    @Test
    fun `the outline swaps to a palette colour`() {
        val a = ramp("a", 10, 2)
        val b = ramp("b", 100, 2)
        val outline = grey(1)
        val target = grey(250)
        val s = swap(RampSet(outline = outline, ramps = listOf(a, b)), "a" to "b", outline = target)
        assertEquals(target, s.map(outline))
    }

    @Test
    fun `the outline stays without a swap for it`() {
        val a = ramp("a", 10, 2)
        val b = ramp("b", 100, 2)
        val s = swap(RampSet(outline = grey(1), ramps = listOf(a, b)), "a" to "b")
        assertEquals(grey(1), s.map(grey(1)))
    }

    @Test
    fun `apply recolours swapped pixels and passes the rest through`() {
        val a = ramp("a", 10, 2)
        val b = ramp("b", 100, 2)
        val other = Argb.of(255, 0, 0)
        val clear = Argb.TRANSPARENT
        val translucent = Argb.of(10, 10, 10, 128)
        val pixels = listOf(a.colors[0], a.colors[1], other, clear, translucent)
        val input = Raster.of(5, 1) { x, _ -> pixels[x] }
        val result = swap(RampSet(ramps = listOf(a, b)), "a" to "b").apply(input)
        assertEquals(listOf(b.colors[0], b.colors[1], other, clear, translucent), List(5) { result[it, 0] })
        assertEquals(a.colors[0], input[0, 0])
    }
}
