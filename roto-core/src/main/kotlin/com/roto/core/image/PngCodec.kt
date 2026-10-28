package com.roto.core.image

import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.ImageWriter
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream

/**
 * Reads and writes [Raster]s as PNG through `javax.imageio`.
 *
 * Writing always produces 8-bit RGBA, and the same raster always encodes to the same bytes on the
 * same JDK. Reading accepts any PNG colour type and bit depth and returns the stored sample values
 * without colour management: a gray PNG reads back as the gray levels it holds, not as a linear-to-sRGB
 * conversion of them, and gamma or ICC chunks are ignored.
 *
 * Streams are buffered in memory rather than through `ImageIO`'s temp-file cache, so no call here
 * touches global `ImageIO` state or the file system beyond the given path.
 */
object PngCodec {
    private const val FORMAT = "png"
    private const val MAX_8_BIT = 255

    fun read(path: Path): Raster = Files.newInputStream(path).use { read(it) }

    fun read(input: InputStream): Raster {
        val reader = pngReader()
        try {
            MemoryCacheImageInputStream(input).use { stream ->
                reader.input = stream
                return toRaster(reader.read(0))
            }
        } finally {
            reader.dispose()
        }
    }

    fun write(raster: Raster, path: Path) {
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(path).use { write(raster, it) }
    }

    fun write(raster: Raster, output: OutputStream) {
        val image = BufferedImage(raster.width, raster.height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, raster.width, raster.height, raster.toArgbArray(), 0, raster.width)
        val writer = pngWriter()
        try {
            MemoryCacheImageOutputStream(output).use { stream ->
                writer.output = stream
                writer.write(image)
            }
        } finally {
            writer.dispose()
        }
    }

    private fun pngReader(): ImageReader =
        ImageIO.getImageReadersByFormatName(FORMAT).asSequence().firstOrNull()
            ?: throw IOException("No PNG reader available in this JRE")

    private fun pngWriter(): ImageWriter =
        ImageIO.getImageWritersByFormatName(FORMAT).asSequence().firstOrNull()
            ?: throw IOException("No PNG writer available in this JRE")

    private fun toRaster(image: BufferedImage): Raster =
        if (isGray(image)) grayToRaster(image) else colorToRaster(image)

    /**
     * `BufferedImage.getRGB` maps a gray image from its linear gray colour space into sRGB, which
     * brightens every mid-tone. Read the samples straight off the raster instead.
     */
    private fun grayToRaster(image: BufferedImage): Raster {
        val samples = image.raster
        val model = image.colorModel
        val grayMax = (1 shl model.getComponentSize(0)) - 1
        val alphaMax = if (model.hasAlpha()) (1 shl model.getComponentSize(1)) - 1 else 0
        return Raster.of(image.width, image.height) { x, y ->
            val gray = scaleTo8Bit(samples.getSample(x, y, 0), grayMax)
            val alpha = if (model.hasAlpha()) scaleTo8Bit(samples.getSample(x, y, 1), alphaMax) else MAX_8_BIT
            Argb.of(gray, gray, gray, alpha)
        }
    }

    private fun colorToRaster(image: BufferedImage): Raster {
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        return Raster.fromArgbArray(image.width, image.height, pixels)
    }

    private fun isGray(image: BufferedImage): Boolean =
        image.colorModel !is IndexColorModel && image.colorModel.colorSpace.type == ColorSpace.TYPE_GRAY

    private fun scaleTo8Bit(sample: Int, max: Int): Int =
        if (max == MAX_8_BIT) sample else (sample * MAX_8_BIT + max / 2) / max
}
