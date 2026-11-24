package com.roto.core.palette

import com.roto.core.image.Argb
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RampSetCodecTest {

    @Test
    fun `the sample ramps read with their outline, in order`() {
        val ramps = javaClass.getResourceAsStream("/project/palette/ramps.json")!!.use { RampSetCodec.read(it) }

        assertEquals(HexColor.parse("#141018"), ramps.outline)
        assertEquals(listOf("skin", "suit", "visor"), ramps.ramps.map { it.name })
        assertEquals(HexColor.parse("#5a3a2e"), ramps.ramp("skin")?.darkest)
        assertNull(ramps.ramp("hair"))
    }

    @Test
    fun `reading what was written gives back equal ramps, colours as lower-case hex`() {
        val ramps = RampSet(
            outline = null,
            ramps = listOf(
                Ramp("metal", listOf(Argb.of(0x22, 0x22, 0x33), Argb.of(0x88, 0x88, 0x99), Argb.of(0xEE, 0xEE, 0xFF))),
            ),
        )

        val text = encode(ramps)

        assertEquals(ramps, read(text))
        assertContains(text, "\"#888899\"")
        assertContains(text, "\"outline\": null")
    }

    @Test
    fun `ramps must run dark to light, be named once and use valid colours`() {
        val lightToDark = """{ "ramps": [{ "name": "skin", "colors": ["#e0a98a", "#5a3a2e"] }] }"""
        assertContains(assertFailsWith<RampSetException> { read(lightToDark) }.message.orEmpty(), "dark to light")
        listOf(
            """{ "ramps": [{ "name": "a", "colors": ["#000000"] }, { "name": "a", "colors": ["#ffffff"] }] }""",
            """{ "ramps": [{ "name": "a", "colors": [] }] }""",
            """{ "ramps": [{ "name": " ", "colors": ["#000000"] }] }""",
            """{ "ramps": [{ "name": "a", "colors": ["000000"] }] }""",
            """{ "ramps": [{ "name": "a", "colors": ["#000000", "#000000"] }] }""",
            """{ "outline": "#12", "ramps": [] }""",
            """{ "ramps": [], "shades": 4 }""",
        ).forEach { text -> assertFailsWith<RampSetException>(text) { read(text) } }
    }

    @Test
    fun `writing to a path round-trips and leaves no temporary file`() {
        val dir = createTempDirectory("roto-ramps")
        try {
            val ramps = RampSet(outline = null, ramps = listOf(Ramp("a", listOf(Argb.of(0, 0, 0), Argb.of(9, 9, 9)))))
            val file = dir.resolve("sub/ramps.json").also { RampSetCodec.write(ramps, it) }

            assertEquals(ramps, file.inputStream().use { RampSetCodec.read(it) })
            assertEquals(listOf("ramps.json"), file.parent.listDirectoryEntries().map { it.fileName.toString() })
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun read(text: String): RampSet = RampSetCodec.read(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

    private fun encode(ramps: RampSet): String =
        ByteArrayOutputStream().also { RampSetCodec.write(ramps, it) }.toString(Charsets.UTF_8)
}
