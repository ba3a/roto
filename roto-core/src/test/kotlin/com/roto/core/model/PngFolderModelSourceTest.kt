package com.roto.core.model

import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.project.Canvas
import com.roto.core.publish.ClipManifest
import com.roto.core.publish.ClipManifestCodec
import com.roto.core.publish.ManifestFrame
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PngFolderModelSourceTest {

    private val dir = createTempDirectory("roto-png-source")
    private val canvas = Canvas(4, 5, 4)

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    private fun frame(width: Int = 4, height: Int = 5, colour: Int = -1): Raster =
        Raster.of(width, height) { _, _ -> colour }

    private fun write(name: String, raster: Raster) = PngCodec.write(raster, dir.resolve(name))

    private fun source(duration: Int = 100, loop: Boolean = false): PngFolderModelSource =
        PngFolderModelSource(dir, canvas, duration, loop)

    @Test
    fun `a folder with clip json yields its durations and loop`() {
        write("b.png", frame(colour = 0xFF00FF00.toInt()))
        write("a.png", frame(colour = 0xFFFF0000.toInt()))
        val manifest = ClipManifest(
            "walk", "east", "walk", false, true,
            listOf(ManifestFrame("x/a.png", 80), ManifestFrame("x/b.png", 120)),
        )
        ClipManifestCodec.write(manifest, dir.resolve("clip.json"))
        val source = source(duration = 999, loop = false)
        val row = source.rows().single()

        assertEquals(listOf(80, 120), source.durations(row))
        assertTrue(source.loop(row))
        assertEquals(2, row.frameCount)
        assertEquals(0xFFFF0000.toInt(), source.frames(row).first()[0, 0])
    }

    @Test
    fun `a folder without clip json uses the given duration and loop`() {
        write("000.png", frame())
        write("001.png", frame())
        write("002.png", frame())
        val source = source(duration = 70, loop = true)
        val row = source.rows().single()

        assertEquals(listOf(70, 70, 70), source.durations(row))
        assertTrue(source.loop(row))
        assertEquals(3, source.frames(row).size)
    }

    @Test
    fun `a frame larger than the canvas is refused naming both sizes`() {
        write("000.png", frame())
        write("001.png", frame(width = 6, height = 5))
        val source = source()

        val e = assertFailsWith<ModelException> { source.frames(source.rows().single()) }
        assertEquals("001.png is 6x5, larger than the 4x5 canvas", e.message)
    }

    @Test
    fun `a smaller frame is placed on the canvas`() {
        write("000.png", frame(width = 2, height = 3, colour = 0xFFFF0000.toInt()))
        val source = source()

        val placed = source.frames(source.rows().single()).single()
        assertEquals(4 to 5, placed.width to placed.height)
        assertEquals(0xFFFF0000.toInt(), placed[1, 2])
        assertEquals(0, placed[0, 4])
        assertEquals(0, placed[1, 1])
    }

    @Test
    fun `a manifest with another frame count is refused`() {
        write("000.png", frame())
        write("001.png", frame())
        val manifest = ClipManifest("walk", "east", "walk", false, false, listOf(ManifestFrame("a.png", 80)))
        ClipManifestCodec.write(manifest, dir.resolve("clip.json"))

        assertFailsWith<ModelException> { source().rows() }
    }

    @Test
    fun `an empty folder is refused`() {
        assertFailsWith<ModelException> { source().rows() }
    }

    @Test
    fun `the folder name proposes action and facing`() {
        val named: Path = dir.resolve("walk_east")
        named.toFile().mkdirs()
        PngCodec.write(frame(), named.resolve("000.png"))
        val row = PngFolderModelSource(named, canvas, 100, false).rows().single()
        assertEquals("walk", row.action)
        assertEquals("east", row.facing)

        val plain = source().also { write("000.png", frame()) }.rows().single()
        assertNull(plain.facing)
        assertFalse(plain.id.isEmpty())
    }
}
