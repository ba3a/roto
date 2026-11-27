package com.roto.core.review

import com.roto.core.ase.AseCel
import com.roto.core.ase.AseFrame
import com.roto.core.ase.AseLayer
import com.roto.core.ase.AseSprite
import com.roto.core.image.IndexedRaster
import com.roto.core.publish.Mirror
import com.roto.core.publish.Retime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlipbookTest {

    private val palette = listOf(0x00000000, 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())

    private fun cel(layer: Int, entry: Int, opacity: Int = 255) =
        AseCel(layer, IndexedRaster.of(2, 1) { x, _ -> if (x == 0) entry else 0 }, opacity = opacity)

    private fun sprite() = AseSprite(
        width = 2,
        height = 1,
        palette = palette,
        layers = listOf(AseLayer("reference"), AseLayer("draft"), AseLayer("art")),
        frames = listOf(
            AseFrame(100, listOf(cel(0, 1, 102), cel(1, 2), cel(2, 3))),
            AseFrame(200, listOf(cel(0, 1, 102), cel(1, 2))),
            AseFrame(300, listOf(cel(0, 1, 102))),
        ),
    )

    @Test
    fun `retimed flipbook keeps the picked frames at scaled durations`() {
        val book = Flipbook.of(sprite(), "clip", loop = true)

        val retimed = book.retimed(Retime(speedPercent = 200, keep = listOf(2, 0, 0)))

        assertEquals(listOf(150, 50, 50), retimed.durations)
        val images = listOf(2, 0, 0).map { book.frames[it].image }
        assertEquals(images, retimed.frames.map { it.image })
        assertEquals(true, retimed.loop)
        assertFailsWith<IllegalArgumentException> { book.retimed(Retime(keep = listOf(3))) }
    }

    @Test
    fun `each frame takes art, else draft, else reference`() {
        val book = Flipbook.of(sprite(), "clip", loop = true)
        val expected = listOf(FlipbookSource.Art, FlipbookSource.Draft, FlipbookSource.Reference)
        assertEquals(expected, book.frames.map { it.source })
        assertEquals(listOf(100, 200, 300), book.durations)
        assertEquals(palette[3], book.frames[0].image[0, 0])
        assertEquals(palette[2], book.frames[1].image[0, 0])
        assertEquals(palette[1], book.frames[2].image[0, 0])
    }

    @Test
    fun `mirrored flipbook flips each frame`() {
        val plain = Flipbook.of(sprite(), "clip", loop = false)
        val mirrored = Flipbook.of(sprite(), "clip", loop = false, mirrored = true)
        plain.frames.zip(mirrored.frames).forEach { (a, b) -> assertEquals(Mirror.flip(a.image), b.image) }
    }

    @Test
    fun `clock is exact at frame boundaries`() {
        val d = listOf(100, 200, 300)
        val expected = mapOf(0L to 0, 99L to 0, 100L to 1, 299L to 1, 300L to 2, 599L to 2)
        expected.forEach { (t, i) -> assertEquals(i, FlipbookClock.frameAt(t, d, loop = true), "loop $t") }
        assertEquals(0, FlipbookClock.frameAt(600, d, loop = true))
        assertEquals(1, FlipbookClock.frameAt(700, d, loop = true))
        assertEquals(2, FlipbookClock.frameAt(600, d, loop = false))
        assertEquals(2, FlipbookClock.frameAt(Long.MAX_VALUE, d, loop = false))
        assertContentEquals(listOf(0, 0), listOf(-5L, 0L).map { FlipbookClock.frameAt(it, d, loop = false) })
    }

    @Test
    fun `clock handles very long times`() {
        val d = listOf(100, 200, 300)
        val total = 600L
        assertEquals(1, FlipbookClock.frameAt(total * 1_000_000_000_000L + 150, d, loop = true))
    }
}
