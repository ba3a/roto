package com.roto.core.model

import com.roto.core.ase.AseFileBuilder
import com.roto.core.ase.AseFileBuilder.Companion.compressedCel
import com.roto.core.ase.AseFileBuilder.Companion.layer
import com.roto.core.ase.AseFileBuilder.Companion.palette
import com.roto.core.ase.AseFileBuilder.Companion.tags
import com.roto.core.ase.AseFormatException
import com.roto.core.ase.Tag
import com.roto.core.project.Canvas
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AseModelSourceTest {

    private val dir = createTempDirectory("roto-ase-source")
    private val canvas = Canvas(2, 2, 1)
    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val colours = listOf(0, red, green)

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    private fun write(name: String, bytes: ByteArray): Path = dir.resolve(name).also { it.writeBytes(bytes) }

    private fun cel(layer: Int, vararg indices: Int) = compressedCel(layer, 2, 2, ByteArray(4) { indices[it].toByte() })

    @Test
    fun `visible layers composite and hidden ones are left out`() {
        val bytes = AseFileBuilder()
            .frame(
                palette(colours),
                layer("bottom"),
                layer("top"),
                layer("hidden", flags = 2),
                cel(0, 1, 1, 1, 1),
                cel(1, 0, 2, 0, 0),
                cel(2, 2, 2, 2, 2),
                durationMs = 80,
            )
            .build()
        val source = AseModelSource(write("walk_east.ase", bytes), canvas, defaultLoop = true)
        val row = source.rows().single()
        val frame = source.frames(row).single()

        assertEquals("walk_east", row.id)
        assertEquals("walk" to "east", row.action to row.facing)
        assertEquals(red, frame[0, 0])
        assertEquals(green, frame[1, 0])
        assertEquals(red, frame[0, 1])
        assertEquals(listOf(80), source.durations(row))
        assertTrue(source.loop(row))
    }

    @Test
    fun `cel position and opacity are applied`() {
        val bytes = AseFileBuilder()
            .frame(
                palette(colours),
                layer("a"),
                compressedCel(0, 1, 1, byteArrayOf(1), x = 1, y = 1),
            )
            .build()
        val frame = AseModelSource(write("a.ase", bytes), canvas, false).let { it.frames(it.rows().single()) }.single()

        assertEquals(0, frame[0, 0])
        assertEquals(red, frame[1, 1])
    }

    @Test
    fun `durations are read per frame`() {
        val bytes = AseFileBuilder()
            .frame(palette(colours), layer("a"), cel(0, 1, 1, 1, 1), durationMs = 60)
            .frame(cel(0, 2, 2, 2, 2), durationMs = 140)
            .build()
        val source = AseModelSource(write("a.ase", bytes), canvas, false)
        val row = source.rows().single()

        assertEquals(listOf(60, 140), source.durations(row))
        assertEquals(2, row.frameCount)
        assertNull(row.action)
        assertFalse(source.loop(row))
    }

    @Test
    fun `tags split into rows with their own frames and loop`() {
        val bytes = AseFileBuilder()
            .frame(
                palette(colours),
                layer("a"),
                tags(Tag("walk_east", 0, 1), Tag("idle_west", 2, 2, repeat = 1)),
                cel(0, 1, 1, 1, 1),
                durationMs = 10,
            )
            .frame(cel(0, 2, 2, 2, 2), durationMs = 20)
            .frame(cel(0, 1, 2, 1, 2), durationMs = 30)
            .build()
        val source = AseModelSource(write("all.ase", bytes), canvas, false)
        val (walk, idle) = source.rows()

        assertEquals(listOf("walk_east", "idle_west"), listOf(walk.id, idle.id))
        assertEquals(listOf(2, 1), listOf(walk.frameCount, idle.frameCount))
        assertEquals(listOf(10, 20), source.durations(walk))
        assertEquals(listOf(30), source.durations(idle))
        assertEquals(green, source.frames(walk)[1][0, 0])
        assertEquals(green, source.frames(idle).single()[1, 0])
        assertTrue(source.loop(walk))
        assertFalse(source.loop(idle))
    }

    @Test
    fun `an unsupported file raises the ase message as it is`() {
        val bytes = AseFileBuilder(colorDepth = 32).frame(layer("a")).build()
        val source = AseModelSource(write("rgb.ase", bytes), canvas, false)

        val failure = assertFailsWith<AseFormatException> { source.rows() }
        assertTrue(failure.message!!.startsWith("Only indexed-colour sprites are supported"))
    }

    @Test
    fun `a sprite larger than the canvas is refused`() {
        val bytes = AseFileBuilder().frame(palette(colours), layer("a"), cel(0, 1, 1, 1, 1)).build()
        val source = AseModelSource(write("a.ase", bytes), Canvas(1, 2, 1), false)

        val failure = assertFailsWith<ModelException> { source.rows() }
        assertEquals("a.ase is 2x2, larger than the 1x2 canvas", failure.message)
    }

    @Test
    fun `a sprite smaller than the canvas is placed on it`() {
        val bytes = AseFileBuilder().frame(palette(colours), layer("a"), cel(0, 1, 1, 1, 1)).build()
        val source = AseModelSource(write("a.ase", bytes), Canvas(4, 3, 2), false)

        val frame = source.frames(source.rows().single()).single()
        assertEquals(4 to 3, frame.width to frame.height)
        assertEquals(red, frame[1, 1])
        assertEquals(red, frame[2, 2])
        assertEquals(0, frame[1, 0])
        assertEquals(0, frame[0, 2])
    }
}
