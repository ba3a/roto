package com.roto.core.palette

import com.roto.core.image.Argb
import java.io.ByteArrayInputStream
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaletteCodecTest {

    private lateinit var tempRoot: Path

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-palette")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `the sample gpl reads every colour in order, names and comments skipped`() {
        val palette = javaClass.getResourceAsStream("/project/palette/faultory.gpl")!!.use { PaletteCodec.readGpl(it) }

        assertEquals(11, palette.colors.size)
        assertEquals(Argb.of(20, 16, 24), palette.colors.first())
        assertEquals(Argb.of(242, 209, 107), palette.colors.last())
    }

    @Test
    fun `a gpl with RGBA channels, a byte-order mark and Windows line endings`() {
        val text = Char(0xFEFF) + "GIMP Palette\r\nChannels: RGBA\r\n10 20 30 255 first\r\n40 50 60 255\r\n"

        assertEquals(Palette(listOf(Argb.of(10, 20, 30), Argb.of(40, 50, 60))), gpl(text))
    }

    @Test
    fun `a hex palette, with and without hashes`() {
        assertEquals(Palette(listOf(Argb.of(0x1A, 0x1C, 0x2C), Argb.of(255, 0, 77))), hex("1a1c2c\n\n#FF004D\n"))
    }

    @Test
    fun `the file's extension picks the format`() {
        val gpl = tempRoot.resolve("p.GPL").also { it.writeText("GIMP Palette\n1 2 3\n") }
        val hex = tempRoot.resolve("p.hex").also { it.writeText("010203\n") }
        val png = tempRoot.resolve("p.png").also { it.writeText("") }

        assertEquals(PaletteCodec.read(gpl), PaletteCodec.read(hex))
        val unknown = assertFailsWith<PaletteFormatException> { PaletteCodec.read(png) }
        assertContains(unknown.message.orEmpty(), ".gpl or .hex")
    }

    @Test
    fun `malformed palettes are rejected naming the line`() {
        val badValue = assertFailsWith<PaletteFormatException> { gpl("GIMP Palette\n1 2 3\n4 256 6\n") }
        assertContains(badValue.message.orEmpty(), "line 3")

        listOf(
            "Not a palette\n1 2 3\n",
            "GIMP Palette\n1 2\n",
            "GIMP Palette\nChannels: RGBA\n1 2 3 128\n",
            "GIMP Palette\nChannels: CMYK\n",
            "GIMP Palette\n",
        ).forEach { text -> assertFailsWith<PaletteFormatException>(text) { gpl(text) } }

        assertContains(assertFailsWith<PaletteFormatException> { hex("010203\nnope\n") }.message.orEmpty(), "line 2")
        assertFailsWith<PaletteFormatException> { hex((0..255).joinToString("\n") { "0000%02x".format(it) }) }
    }

    @Test
    fun `a file names itself in the error`() {
        val file = tempRoot.resolve("broken.hex").also { it.writeText("xyz\n") }

        val error = assertFailsWith<PaletteFormatException> { PaletteCodec.read(file) }
        assertContains(error.message.orEmpty(), "broken.hex")
    }

    private fun gpl(text: String) = PaletteCodec.readGpl(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

    private fun hex(text: String) = PaletteCodec.readHex(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
}
