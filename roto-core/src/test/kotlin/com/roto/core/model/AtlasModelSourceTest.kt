package com.roto.core.model

import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.project.Canvas
import com.roto.core.template.PathTemplate
import com.roto.core.template.Template
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtlasModelSourceTest {

    @TempDir
    lateinit var dir: Path

    private val canvas = Canvas(4, 4, 3)
    private val pattern = Template(DEFAULT_ATLAS_PATTERN)

    private fun region(name: String, x: Int, orig: String = "4, 4") =
        "$name\n  rotate: false\n  xy: $x, 0\n  size: 1, 1\n  orig: $orig\n  offset: 0, 0\n  index: -1\n"

    private fun atlas(vararg regions: String): Path {
        val page = Raster.of(8, 1) { x, _ -> 0xFF000000.toInt() or x }
        PngCodec.write(page, dir.resolve("page.png"))
        val file = dir.resolve("chars.atlas")
        file.writeText("page.png\nsize: 8, 1\nformat: RGBA8888\n" + regions.joinToString(""))
        return file
    }

    private fun source(
        file: Path,
        durations: Map<String, Int> = emptyMap(),
        loops: Set<String> = emptySet(),
    ) = AtlasModelSource(file, canvas, listOf("walk", "idle"), listOf("East", "West"), pattern, durations, loops)

    @Test
    fun `groups regions into rows by action and facing ordered by frame`() {
        val file = atlas(
            region("walk_east_001", 1),
            region("idle_west_000", 2),
            region("walk_east_000", 0),
            region("walk_west_000", 3),
        )
        val source = source(file)
        val rows = source.rows()
        assertEquals(listOf("walk_East", "walk_West", "idle_West"), rows.map { it.id })
        assertEquals(listOf(2, 1, 1), rows.map { it.frameCount })
        assertEquals("walk", rows[0].action)
        assertEquals("East", rows[0].facing)
        val frames = source.frames(rows[0])
        assertEquals(0xFF000000.toInt(), frames[0][0, 3])
        assertEquals(0xFF000001.toInt(), frames[1][0, 3])
    }

    @Test
    fun `lists and skips unmatched regions`() {
        val source = source(atlas(region("walk_east_000", 0), region("tree", 1), region("walk_north_000", 2)))
        assertEquals(listOf("walk_East"), source.rows().map { it.id })
        assertEquals(listOf("tree", "walk_north_000"), source.skipped)
    }

    @Test
    fun `refuses a region larger than the canvas naming both sizes`() {
        val source = source(atlas(region("walk_east_000", 0, orig = "5, 4")))
        val e = assertThrows<ModelException> { source.rows() }
        assertEquals("Atlas region \"walk_east_000\" is 5x4, larger than the 4x4 canvas", e.message)
    }

    @Test
    fun `places a region smaller than the canvas on it`() {
        val source = source(atlas(region("walk_east_000", 1, orig = "2, 4")))
        val frame = source.frames(source.rows().single()).single()
        assertEquals(4 to 4, frame.width to frame.height)
        assertEquals(0xFF000001.toInt(), frame[1, 3])
        assertEquals(0, frame[0, 3])
    }

    @Test
    fun `applies the duration and loop of each action`() {
        val source = source(
            atlas(region("walk_east_000", 0), region("walk_east_001", 1), region("idle_east_000", 2)),
            durations = mapOf("walk" to 80),
            loops = setOf("walk"),
        )
        val (walk, idle) = source.rows()
        assertEquals(listOf(80, 80), source.durations(walk))
        assertTrue(source.loop(walk))
        assertEquals(listOf(DEFAULT_ATLAS_DURATION_MS), source.durations(idle))
        assertFalse(source.loop(idle))
    }

    @Test
    fun `refuses two regions with the same frame`() {
        val source = source(atlas(region("walk_east_000", 0), region("walk_east_0000", 1)))
        assertThrows<ModelException> { source.rows() }
    }

    @Test
    fun `refuses a pattern without all three variables`() {
        assertThrows<IllegalArgumentException> {
            AtlasModelSource(
                dir.resolve("a.atlas"),
                canvas,
                listOf("walk"),
                listOf("East"),
                Template("{action}_{frame}"),
            )
        }
    }

    @Test
    fun `derives the default pattern from the output template`() {
        assertEquals(
            "{action}_{facing:lower}_{frame:000}",
            defaultAtlasPattern(PathTemplate("raw-art/{profile}/{action}_{facing:lower}/{frame:000}.png")),
        )
        assertEquals(
            "{action}_{facing}_{frame}",
            defaultAtlasPattern(PathTemplate("{profile}/{action}_{facing}_{frame}.png")),
        )
        assertEquals(
            DEFAULT_ATLAS_PATTERN,
            defaultAtlasPattern(PathTemplate("out/{profile}/{action}/{frame}.png")),
        )
    }
}
