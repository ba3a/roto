package com.roto.core.handoff

import com.roto.core.ase.AseCel
import com.roto.core.ase.AseCodec
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.palette.Palette
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HandoffWriterTest {

    private lateinit var tempRoot: Path
    private lateinit var file: Path

    private val palette = Palette(listOf(Argb.of(0, 0, 0), Argb.of(200, 30, 30), Argb.of(255, 255, 255)))

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-handoff")
        file = tempRoot.resolve("handoff").resolve("walk_EAST.ase")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `the art the artist saved survives a re-render, through the file`() {
        HandoffWriter.write(file, render(draftLabel = 2))
        val written = AseCodec.read(file)
        val art = IndexedRaster.of(3, 3) { x, y -> if (x == y) 3 else 0 }
        val frames = written.frames.mapIndexed { index, frame ->
            if (index == 0) frame.copy(cels = frame.cels + AseCel(2, art, x = 1, y = 0)) else frame
        }
        AseCodec.write(written.copy(frames = frames), file)

        HandoffWriter.write(file, render(draftLabel = 3))

        val rerendered = AseCodec.read(file)
        assertEquals(AseCel(2, art, x = 1, y = 0), rerendered.frames[0].cels.last())
        assertEquals(3, rerendered.frames[0].cels[1].image[0, 0])
    }

    @Test
    fun `writing the same render twice gives the same bytes`() {
        HandoffWriter.write(file, render(draftLabel = 2))
        val first = file.readBytes()

        HandoffWriter.write(file, render(draftLabel = 2))

        assertContentEquals(first, file.readBytes())
    }

    @Test
    fun `a file Roto cannot read is left exactly as it was`() {
        Files.createDirectories(file.parent)
        val damaged = byteArrayOf(1, 2, 3, 4, 5)
        file.writeBytes(damaged)

        assertFailsWith<java.io.IOException> { HandoffWriter.write(file, render(draftLabel = 2)) }

        assertContentEquals(damaged, file.readBytes())
    }

    @Test
    fun `a merge the art forbids fails naming the file and leaves it as it was`() {
        HandoffWriter.write(file, render(draftLabel = 2))
        val written = AseCodec.read(file)
        val redDot = AseCel(2, IndexedRaster.of(1, 1) { _, _ -> 2 })
        AseCodec.write(written.copy(frames = written.frames.map { it.copy(cels = it.cels + redDot) }), file)
        val before = file.readBytes()

        val error = assertFailsWith<HandoffException> {
            HandoffWriter.write(file, render(draftLabel = 1, palette = Palette(listOf(Argb.of(0, 0, 0)))))
        }

        assertEquals(true, error.message?.contains("walk_EAST.ase"))
        assertContentEquals(before, file.readBytes())
    }

    private fun render(draftLabel: Int, palette: Palette = this.palette) = HandoffRender(
        palette,
        width = 3,
        height = 3,
        frames = List(2) {
            HandoffFrame(120, IndexedRaster.of(3, 3) { _, _ -> 1 }, IndexedRaster.of(3, 3) { _, _ -> draftLabel })
        },
    )
}
