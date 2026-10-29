package com.roto.core.ase

import com.roto.core.ase.AseFileBuilder.Companion.compressedCel
import com.roto.core.ase.AseFileBuilder.Companion.layer
import com.roto.core.ase.AseFileBuilder.Companion.legacyPalette
import com.roto.core.ase.AseFileBuilder.Companion.linkedCel
import com.roto.core.ase.AseFileBuilder.Companion.palette
import com.roto.core.ase.AseFileBuilder.Companion.rawCel
import com.roto.core.ase.AseFileBuilder.Companion.tilemapCel
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AseCodecReadTest {

    private lateinit var tempRoot: Path

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-ase-read")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    // --- Round trip -------------------------------------------------------------------------------

    @Test
    fun `reading what was written gives back an equal sprite`() {
        val sprite = handoffSprite()

        assertEquals(sprite, roundTrip(sprite))
    }

    @Test
    fun `round trip keeps a full 256 colour palette and the transparent index`() {
        val sprite = AseSprite(
            width = 16,
            height = 16,
            palette = List(256) { Argb.of(it, 255 - it, it / 2, alpha = if (it == 7) 0 else 255) },
            layers = listOf(AseLayer("art")),
            frames = listOf(AseFrame(100, listOf(AseCel(0, IndexedRaster.of(16, 16) { x, y -> x * 16 + y })))),
            transparentIndex = 7,
        )

        assertEquals(sprite, roundTrip(sprite))
    }

    @Test
    fun `round trip through a file`() {
        val target = tempRoot.resolve("handoff").resolve("walk_east.ase")

        AseCodec.write(handoffSprite(), target)

        assertEquals(handoffSprite(), AseCodec.read(target))
    }

    @Test
    fun `writing what was read gives back the same bytes`() {
        val bytes = encode(handoffSprite())

        assertEquals(bytes.toList(), encode(AseCodec.read(ByteArrayInputStream(bytes))).toList())
    }

    // --- What editors add on save -----------------------------------------------------------------

    @Test
    fun `a linked cel reads as a copy of the cel it points at`() {
        val indices = byteArrayOf(1, 0, 0, 1)
        val bytes = AseFileBuilder()
            .frame(palette(PALETTE), layer("art"), compressedCel(0, 2, 2, indices, x = 1, y = -1), durationMs = 90)
            .frame(linkedCel(0, toFrame = 0), durationMs = 60)
            .build()

        val sprite = read(bytes)

        val original = AseCel(0, IndexedRaster.fromByteArray(2, 2, indices), x = 1, y = -1)
        assertEquals(listOf(listOf(original), listOf(original)), sprite.frames.map { it.cels })
        assertEquals(listOf(90, 60), sprite.frames.map { it.durationMs })
    }

    @Test
    fun `uncompressed cels are read`() {
        val bytes = AseFileBuilder()
            .frame(palette(PALETTE), layer("art"), rawCel(0, 2, 1, byteArrayOf(2, 3), opacity = 128))
            .build()

        val cel = read(bytes).frames.single().cels.single()

        assertEquals(IndexedRaster.fromByteArray(2, 1, byteArrayOf(2, 3)), cel.image)
        assertEquals(128, cel.opacity)
    }

    @Test
    fun `chunks outside the subset are skipped`() {
        val colourProfile = AseFileBuilder.chunk(0x2007) {
            word(1)
            word(0)
            dword(0)
            zeros(8)
        }
        val tags = AseFileBuilder.chunk(0x2018) {
            word(0)
            zeros(8)
        }
        val userData = AseFileBuilder.chunk(0x2020) { dword(0) }
        val bytes = AseFileBuilder()
            .frame(colourProfile, palette(PALETTE), tags, layer("art"), userData, compressedCel(0, 1, 1, ONE))
            .build()

        val sprite = read(bytes)

        assertEquals(listOf(AseLayer("art")), sprite.layers)
        assertEquals(1, sprite.frames.single().cels.single().image[0, 0])
    }

    @Test
    fun `palette entry names and layer UUIDs are skipped`() {
        val bytes = AseFileBuilder(flags = 1 or 4)
            .frame(
                palette(PALETTE, names = mapOf(1 to "outline", 3 to "skin")),
                layer("art", uuid = true),
                layer("top", uuid = true),
            )
            .build()

        val sprite = read(bytes)

        assertEquals(PALETTE, sprite.palette)
        assertEquals(listOf("art", "top"), sprite.layers.map { it.name })
    }

    @Test
    fun `layer opacity is ignored unless the header says it is valid`() {
        val bytes = AseFileBuilder(flags = 0)
            .frame(palette(PALETTE), layer("reference", opacity = 40))
            .build()

        assertEquals(255, read(bytes).layers.single().opacity)
    }

    @Test
    fun `layer flags map to visible and editable`() {
        val bytes = AseFileBuilder()
            .frame(palette(PALETTE), layer("hidden", flags = 2), layer("locked", flags = 1 or 8))
            .build()

        assertEquals(
            listOf(AseLayer("hidden", visible = false), AseLayer("locked", editable = false)),
            read(bytes).layers,
        )
    }

    @Test
    fun `old chunk count is used when the new one is zero`() {
        val bytes = AseFileBuilder()
            .frame(palette(PALETTE), layer("art"), compressedCel(0, 1, 1, byteArrayOf(2)), newChunkCount = false)
            .build()

        assertEquals(2, read(bytes).frames.single().cels.single().image[0, 0])
    }

    @Test
    fun `a file with only the old palette chunk reads it as opaque colours`() {
        val bytes = AseFileBuilder()
            .frame(legacyPalette(0x0004, listOf(Triple(0, 0, 0), Triple(200, 100, 50))), layer("art"))
            .build()

        assertEquals(listOf(Argb.of(0, 0, 0), Argb.of(200, 100, 50)), read(bytes).palette)
    }

    @Test
    fun `six bit palette channels are scaled to eight bits`() {
        val bytes = AseFileBuilder()
            .frame(legacyPalette(0x0011, listOf(Triple(63, 0, 32))), layer("art"))
            .build()

        assertEquals(listOf(Argb.of(255, 0, 130)), read(bytes).palette)
    }

    @Test
    fun `the current palette chunk wins over an old one in either order`() {
        val old = legacyPalette(0x0004, listOf(Triple(9, 9, 9)))
        val before = AseFileBuilder().frame(old, palette(PALETTE), layer("art")).build()
        val after = AseFileBuilder().frame(palette(PALETTE), old, layer("art")).build()

        assertEquals(PALETTE, read(before).palette)
        assertEquals(PALETTE, read(after).palette)
    }

    // --- Rejections -------------------------------------------------------------------------------

    @Test
    fun `non indexed sprites are rejected with a clear message`() {
        val bytes = AseFileBuilder(colorDepth = 32).frame(palette(PALETTE), layer("art")).build()

        val error = assertFailsWith<AseFormatException> { read(bytes) }
        assertContains(error.message.orEmpty(), "indexed")
    }

    @Test
    fun `group and tilemap layers are rejected`() {
        val group = AseFileBuilder().frame(palette(PALETTE), layer("body", type = 1), layer("arm", childLevel = 1))
        val tilemap = AseFileBuilder().frame(palette(PALETTE), layer("tiles", type = 2))

        assertFailsWith<AseFormatException> { read(group.build()) }
        assertFailsWith<AseFormatException> { read(tilemap.build()) }
    }

    @Test
    fun `tilemap cels are rejected`() {
        val bytes = AseFileBuilder().frame(palette(PALETTE), layer("art"), tilemapCel(0)).build()

        assertFailsWith<AseFormatException> { read(bytes) }
    }

    @Test
    fun `a palette that changes after the first frame is rejected`() {
        val bytes = AseFileBuilder()
            .frame(palette(PALETTE), layer("art"))
            .frame(palette(PALETTE.reversed()))
            .build()

        assertFailsWith<AseFormatException> { read(bytes) }
    }

    @Test
    fun `links forward or to an empty cel are rejected`() {
        val forward = AseFileBuilder().frame(palette(PALETTE), layer("art"), linkedCel(0, toFrame = 1)).frame()
        val empty = AseFileBuilder().frame(palette(PALETTE), layer("art")).frame(linkedCel(0, toFrame = 0))

        assertFailsWith<AseFormatException> { read(forward.build()) }
        assertFailsWith<AseFormatException> { read(empty.build()) }
    }

    @Test
    fun `cels that break the sprite model are reported as format errors`() {
        val outsidePalette = AseFileBuilder()
            .frame(palette(PALETTE), layer("art"), compressedCel(0, 1, 1, byteArrayOf(9)))
            .build()

        assertFailsWith<AseFormatException> { read(outsidePalette) }
    }

    @Test
    fun `missing palette, bad magic and truncation are rejected`() {
        val valid = encode(handoffSprite())
        val badMagic = valid.copyOf().also { it[4] = 0 }

        assertFailsWith<AseFormatException> { read(AseFileBuilder().frame(layer("art")).build()) }
        assertFailsWith<AseFormatException> { read(badMagic) }
        assertFailsWith<AseFormatException> { read(valid.copyOf(valid.size - 10)) }
        assertFailsWith<AseFormatException> { read(valid.copyOf(60)) }
    }

    private fun read(bytes: ByteArray): AseSprite = AseCodec.read(ByteArrayInputStream(bytes))

    private fun encode(sprite: AseSprite): ByteArray =
        ByteArrayOutputStream().also { AseCodec.write(sprite, it) }.toByteArray()

    private fun roundTrip(sprite: AseSprite): AseSprite = read(encode(sprite))

    /** The shape the handoff writer will produce: reference, draft and art layers over a few frames. */
    private fun handoffSprite(): AseSprite {
        val reference = IndexedRaster.of(8, 12) { x, y -> (x * 3 + y) % PALETTE.size }
        val draft = IndexedRaster.of(6, 10) { x, y -> if ((x + y) % 3 == 0) 0 else 1 + (x + y) % 3 }
        return AseSprite(
            width = 8,
            height = 12,
            palette = PALETTE,
            layers = listOf(
                AseLayer("reference", editable = false),
                AseLayer("draft", opacity = 200),
                AseLayer("art"),
            ),
            frames = listOf(
                AseFrame(120, listOf(AseCel(0, reference, opacity = 102), AseCel(1, draft, x = 1, y = 2))),
                AseFrame(90, listOf(AseCel(0, reference, opacity = 102), AseCel(2, draft, x = -2, y = 1))),
                AseFrame(150, emptyList()),
            ),
        )
    }

    private companion object {
        val ONE = byteArrayOf(1)
        val PALETTE = listOf(
            Argb.TRANSPARENT,
            Argb.of(20, 12, 28),
            Argb.of(208, 70, 72),
            Argb.of(222, 238, 214, alpha = 128),
        )
    }
}
