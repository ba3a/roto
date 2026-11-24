package com.roto.core.model

import com.roto.core.image.Raster
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AtlasParserTest {

    private val atlas = """
        walk.png
        size: 16, 16
        format: RGBA8888
        filter: Nearest, Nearest
        repeat: none
        walk_east_000
          rotate: false
          xy: 2, 3
          size: 2, 2
          orig: 6, 5
          offset: 1, 1
          index: -1
        idle_east_000
          rotate: false
          xy: 0, 0
          size: 1, 1
          orig: 1, 1
          offset: 0, 0
          index: -1

        other.png
        size: 8, 8
        solo
          xy: 0, 0
          size: 1, 1
    """.trimIndent()

    @Test
    fun `parses pages and regions`() {
        val pages = AtlasParser.parse(atlas)
        assertEquals(listOf("walk.png", "other.png"), pages.map { it.name })
        assertEquals(listOf("walk_east_000", "idle_east_000"), pages[0].regions.map { it.name })
        val r = pages[0].regions[0]
        assertEquals(
            listOf(2, 3, 2, 2, 6, 5, 1, 1),
            listOf(r.x, r.y, r.width, r.height, r.origWidth, r.origHeight, r.offsetX, r.offsetY),
        )
        assertEquals(1, pages[1].regions[0].origWidth)
    }

    @Test
    fun `rebuilds a trimmed region at orig size with the offset from the bottom`() {
        val page = Raster(16, 16)
        for (y in 3..4) for (x in 2..3) page[x, y] = 0xFFFF0000.toInt()
        val region = AtlasParser.parse(atlas)[0].regions[0]

        val frame = AtlasParser.rebuild(region, page)

        assertEquals(6, frame.width)
        assertEquals(5, frame.height)
        // offset (1, 1) from the bottom-left: crop spans x 1..2 and, top-down, rows 2..3 of 5.
        val painted = (0 until 5).flatMap { y -> (0 until 6).map { x -> x to y } }
            .filter { (x, y) -> frame[x, y] == 0xFFFF0000.toInt() }
        assertEquals(listOf(1 to 2, 2 to 2, 1 to 3, 2 to 3), painted.sortedBy { it.second * 10 + it.first })
        assertEquals(0, frame[0, 0])
    }

    @Test
    fun `refuses a rotated region naming it`() {
        val text = "p.png\nsize: 4, 4\nspin_1\n  rotate: true\n  xy: 0, 0\n  size: 1, 1\n"
        val e = assertThrows<ModelException> { AtlasParser.parse(text) }
        assertTrue("spin_1" in e.message.orEmpty())
    }

    @Test
    fun `malformed text gives a clear exception`() {
        assertThrows<ModelException> { AtlasParser.parse("") }
        assertThrows<ModelException> { AtlasParser.parse("p.png\nsize: 4, 4\nr\n  xy: a, b\n  size: 1, 1\n") }
        val e = assertThrows<ModelException> { AtlasParser.parse("p.png\nsize: 4, 4\nr\n  xy: 0, 0\n") }
        assertTrue("\"r\"" in e.message.orEmpty() && "size" in e.message.orEmpty())
    }

    @Test
    fun `refuses a region outside its page`() {
        val region = AtlasParser.parse(atlas)[0].regions[0]
        assertThrows<ModelException> { AtlasParser.rebuild(region, Raster(3, 3)) }
    }
}
