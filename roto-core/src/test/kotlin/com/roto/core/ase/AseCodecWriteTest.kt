package com.roto.core.ase

import com.roto.core.ase.AseLayout.Companion.inflateRest
import com.roto.core.ase.AseLayout.Companion.s16
import com.roto.core.ase.AseLayout.Companion.skip
import com.roto.core.ase.AseLayout.Companion.string
import com.roto.core.ase.AseLayout.Companion.u16
import com.roto.core.ase.AseLayout.Companion.u8
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AseCodecWriteTest {

    private lateinit var tempRoot: Path

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-ase")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `header describes an indexed sprite of the given size and frame count`() {
        val bytes = encode(sampleSprite())
        val header = AseLayout(bytes).header

        assertEquals(bytes.size, header.getInt(0))
        assertEquals(0xA5E0, header.getShort(4).toInt() and 0xFFFF)
        assertEquals(2, header.getShort(6).toInt())
        assertEquals(6, header.getShort(8).toInt())
        assertEquals(4, header.getShort(10).toInt())
        assertEquals(8, header.getShort(12).toInt(), "colour depth")
        assertEquals(1, header.getInt(14), "layer opacity flag")
        assertEquals(120, header.getShort(18).toInt(), "deprecated speed")
        assertEquals(0, header.get(28).toInt(), "transparent index")
        assertEquals(4, header.getShort(32).toInt(), "number of colours")
        assertEquals(1, header.get(34).toInt(), "pixel width")
        assertEquals(1, header.get(35).toInt(), "pixel height")
    }

    @Test
    fun `first frame carries palettes then layers then cels, later frames only cels`() {
        val frames = AseLayout(encode(sampleSprite())).frames

        assertEquals(
            listOf(0x2019, 0x0004, 0x2004, 0x2004, 0x2004, 0x2005, 0x2005),
            frames[0].chunks.map { it.type },
        )
        assertEquals(listOf(0x2005), frames[1].chunks.map { it.type })
        assertEquals(7, frames[0].oldChunkCount)
    }

    @Test
    fun `frame durations are written per frame`() {
        val frames = AseLayout(encode(sampleSprite())).frames

        assertEquals(listOf(120, 80), frames.map { it.durationMs })
    }

    @Test
    fun `palette chunk lists every colour with its alpha`() {
        val chunk = chunkOfType(AseLayout(encode(sampleSprite())).frames[0], 0x2019).data

        assertEquals(4, chunk.int, "palette size")
        assertEquals(0, chunk.int, "first index")
        assertEquals(3, chunk.int, "last index")
        chunk.skip(8)
        val colours = List(4) {
            assertEquals(0, chunk.u16(), "entry flags")
            Argb.of(red = chunk.u8(), green = chunk.u8(), blue = chunk.u8(), alpha = chunk.u8())
        }
        assertEquals(PALETTE, colours)
    }

    @Test
    fun `old palette chunk repeats the colours as one RGB packet`() {
        val chunk = chunkOfType(AseLayout(encode(sampleSprite())).frames[0], 0x0004).data

        assertEquals(1, chunk.u16(), "packets")
        assertEquals(0, chunk.u8(), "entries to skip")
        assertEquals(4, chunk.u8(), "colours in packet")
        val colours = List(4) { Argb.of(red = chunk.u8(), green = chunk.u8(), blue = chunk.u8()) }
        assertEquals(PALETTE.map { it or (0xFF shl 24) }, colours)
    }

    @Test
    fun `a full palette is written as zero in the eight bit count fields`() {
        val palette = List(256) { Argb.of(it, it, it) }
        val sprite = AseSprite(
            width = 1,
            height = 1,
            palette = palette,
            layers = listOf(AseLayer("art")),
            frames = listOf(AseFrame(100, listOf(AseCel(0, IndexedRaster.of(1, 1) { _, _ -> 255 })))),
        )
        val layout = AseLayout(encode(sprite))
        val oldPalette = chunkOfType(layout.frames[0], 0x0004).data

        assertEquals(0, layout.header.getShort(32).toInt())
        assertEquals(256, chunkOfType(layout.frames[0], 0x2019).data.int)
        oldPalette.skip(3)
        assertEquals(0, oldPalette.u8())
    }

    @Test
    fun `layer chunks carry name, visibility, editability and opacity bottom to top`() {
        val layers = AseLayout(encode(sampleSprite())).frames[0].chunks.filter { it.type == 0x2004 }.map { chunk ->
            val data = chunk.data
            val flags = data.u16()
            assertEquals(0, data.u16(), "layer type")
            assertEquals(0, data.u16(), "child level")
            data.skip(4)
            assertEquals(0, data.u16(), "blend mode")
            val opacity = data.u8()
            data.skip(3)
            AseLayer(data.string(), opacity, visible = flags and 1 != 0, editable = flags and 2 != 0)
        }

        assertEquals(sampleSprite().layers, layers)
    }

    @Test
    fun `cel chunks carry layer, position, opacity and the compressed indices`() {
        val sprite = sampleSprite()
        val cels = AseLayout(encode(sprite)).frames.flatMap { frame ->
            frame.chunks.filter { it.type == 0x2005 }.map { chunk ->
                val data = chunk.data
                val layerIndex = data.u16()
                val x = data.s16()
                val y = data.s16()
                val opacity = data.u8()
                assertEquals(2, data.u16(), "cel type")
                data.skip(7)
                val width = data.u16()
                val height = data.u16()
                val indices = data.inflateRest(width * height)
                AseCel(layerIndex, IndexedRaster.fromByteArray(width, height, indices), x, y, opacity)
            }
        }

        assertEquals(sprite.frames.flatMap { frame -> frame.cels.sortedBy { it.layerIndex } }, cels)
    }

    @Test
    fun `same sprite always encodes to the same bytes, whatever order its cels are listed in`() {
        val sprite = sampleSprite()
        val shuffled = sprite.copy(frames = sprite.frames.map { it.copy(cels = it.cels.reversed()) })

        assertContentEquals(encode(sprite), encode(sprite))
        assertContentEquals(encode(sprite), encode(shuffled))
    }

    @Test
    fun `writing to a path creates missing parent directories`() {
        val target = tempRoot.resolve("handoff").resolve("walk_east.ase")

        AseCodec.write(sampleSprite(), target)

        assertContentEquals(encode(sampleSprite()), target.readBytes())
    }

    @Test
    fun `sprite rejects cels that do not fit its palette or layers`() {
        val layers = listOf(AseLayer("art"))
        val image = IndexedRaster.of(2, 2) { x, _ -> x }

        assertFailsWith<IllegalArgumentException>("index outside palette") {
            AseSprite(2, 2, listOf(Argb.TRANSPARENT), layers, listOf(AseFrame(100, listOf(AseCel(0, image)))))
        }
        assertFailsWith<IllegalArgumentException>("unknown layer") {
            AseSprite(2, 2, PALETTE, layers, listOf(AseFrame(100, listOf(AseCel(1, image)))))
        }
        assertFailsWith<IllegalArgumentException>("two cels on one layer") {
            AseSprite(2, 2, PALETTE, layers, listOf(AseFrame(100, listOf(AseCel(0, image), AseCel(0, image)))))
        }
        assertFailsWith<IllegalArgumentException>("transparent index outside palette") {
            AseSprite(2, 2, PALETTE, layers, listOf(AseFrame(100, emptyList())), transparentIndex = 4)
        }
        assertFailsWith<IllegalArgumentException>("no frames") {
            AseSprite(2, 2, PALETTE, layers, emptyList())
        }
        assertFailsWith<IllegalArgumentException>("no layers") {
            AseSprite(2, 2, PALETTE, emptyList(), listOf(AseFrame(100, emptyList())))
        }
    }

    @Test
    fun `frames, layers and cels reject out of range fields`() {
        assertFailsWith<IllegalArgumentException> { AseFrame(0, emptyList()) }
        assertFailsWith<IllegalArgumentException> { AseLayer("art", opacity = 256) }
        assertFailsWith<IllegalArgumentException> { AseCel(0, IndexedRaster(1, 1), opacity = -1) }
        assertFailsWith<IllegalArgumentException> { AseCel(0, IndexedRaster(1, 1), x = 40_000) }
    }

    private fun encode(sprite: AseSprite): ByteArray =
        ByteArrayOutputStream().also { AseCodec.write(sprite, it) }.toByteArray()

    private fun chunkOfType(frame: AseLayout.Frame, type: Int): AseLayout.Chunk =
        frame.chunks.single { it.type == type }

    private fun sampleSprite(): AseSprite {
        val draft = IndexedRaster.of(6, 4) { x, y -> (x + y) % 4 }
        val art = IndexedRaster.of(3, 2) { x, _ -> 1 + x % 3 }
        return AseSprite(
            width = 6,
            height = 4,
            palette = PALETTE,
            layers = listOf(
                AseLayer("reference", opacity = 102, editable = false),
                AseLayer("draft", visible = false),
                AseLayer("art"),
            ),
            frames = listOf(
                AseFrame(120, listOf(AseCel(2, art, x = -1, y = 2), AseCel(1, draft, opacity = 200))),
                AseFrame(80, listOf(AseCel(1, draft.copy().also { it[0, 0] = 3 }))),
            ),
        )
    }

    private companion object {
        val PALETTE = listOf(
            Argb.TRANSPARENT,
            Argb.of(20, 12, 28),
            Argb.of(208, 70, 72),
            Argb.of(222, 238, 214, alpha = 128),
        )
    }
}
