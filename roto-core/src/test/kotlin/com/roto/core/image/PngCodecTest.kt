package com.roto.core.image

import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PngCodecTest {

    private lateinit var tempRoot: Path

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-png")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `round trip preserves every channel including colour under zero alpha`() {
        val raster = sampleRaster()

        val decoded = PngCodec.read(ByteArrayInputStream(encode(raster)))

        assertEquals(raster, decoded)
    }

    @Test
    fun `encoding the same raster twice gives identical bytes`() {
        val raster = sampleRaster()

        assertContentEquals(encode(raster), encode(raster.copy()))
    }

    @Test
    fun `path round trip creates missing parent directories`() {
        val raster = sampleRaster()
        val target = tempRoot.resolve("walk_east").resolve("000.png")

        PngCodec.write(raster, target)

        assertTrue(target.exists())
        assertEquals(raster, PngCodec.read(target))
    }

    @Test
    fun `gray png reads back its stored levels without colour conversion`() {
        val levels = intArrayOf(0, 64, 128, 200, 255, 17)
        val gray = BufferedImage(3, 2, BufferedImage.TYPE_BYTE_GRAY)
        gray.raster.setPixels(0, 0, 3, 2, levels)

        val decoded = PngCodec.read(ByteArrayInputStream(encodeWithImageIo(gray)))

        val expected = levels.map { Argb.of(it, it, it) }.toIntArray()
        assertEquals(Raster.fromArgbArray(3, 2, expected), decoded)
    }

    @Test
    fun `sixteen bit gray png is scaled down to eight bits`() {
        val gray = BufferedImage(2, 1, BufferedImage.TYPE_USHORT_GRAY)
        gray.raster.setPixels(0, 0, 2, 1, intArrayOf(0xFFFF, 0x8080))

        val decoded = PngCodec.read(ByteArrayInputStream(encodeWithImageIo(gray)))

        assertEquals(Argb.of(255, 255, 255), decoded[0, 0])
        assertEquals(Argb.of(128, 128, 128), decoded[1, 0])
    }

    @Test
    fun `indexed png reads back its palette colours and transparent index`() {
        val red = byteArrayOf(0, 255.toByte(), 10)
        val green = byteArrayOf(0, 0, 20)
        val blue = byteArrayOf(0, 0, 30)
        val palette = IndexColorModel(8, 3, red, green, blue, 0)
        val indexed = BufferedImage(3, 1, BufferedImage.TYPE_BYTE_INDEXED, palette)
        indexed.raster.setPixels(0, 0, 3, 1, intArrayOf(0, 1, 2))

        val decoded = PngCodec.read(ByteArrayInputStream(encodeWithImageIo(indexed)))

        assertEquals(0, Argb.alpha(decoded[0, 0]))
        assertEquals(Argb.of(255, 0, 0), decoded[1, 0])
        assertEquals(Argb.of(10, 20, 30), decoded[2, 0])
    }

    @Test
    fun `non png input is rejected`() {
        assertFailsWith<IOException> {
            PngCodec.read(ByteArrayInputStream("not a png".toByteArray()))
        }
    }

    private fun sampleRaster(): Raster = Raster.of(5, 4) { x, y ->
        when ((x + y) % 4) {
            0 -> Argb.of(red = x * 40, green = y * 60, blue = 7)
            1 -> Argb.of(red = 255, green = 128, blue = 0, alpha = 77)
            2 -> Argb.of(red = 12, green = 34, blue = 56, alpha = 0)
            else -> Argb.TRANSPARENT
        }
    }

    private fun encode(raster: Raster): ByteArray =
        ByteArrayOutputStream().also { PngCodec.write(raster, it) }.toByteArray()

    private fun encodeWithImageIo(image: BufferedImage): ByteArray =
        ByteArrayOutputStream().also { assertTrue(ImageIO.write(image, "png", it)) }.toByteArray()
}
