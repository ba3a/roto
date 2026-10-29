package com.roto.core.palette

import com.roto.core.image.Argb
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads a [Palette] from the two plain-text formats pixel artists share palettes in, told apart by
 * the file's extension:
 *
 * - `.gpl`, the GIMP palette: a `GIMP Palette` first line, optional `Name:`, `Columns:` and
 *   `Channels:` headers and `#` comments, then one `red green blue [name]` line per colour — or
 *   `red green blue alpha [name]` under `Channels: RGBA`, where every alpha must be 255;
 * - `.hex`, one `rrggbb` per line, with or without a leading `#`, as Lospec exports it.
 *
 * Entry names are not kept. Anything else is a [PaletteFormatException] naming the line; Roto only
 * reads palettes, it never writes one.
 */
object PaletteCodec {

    private const val GPL_HEADER = "GIMP Palette"
    private val BYTE_ORDER_MARK = Char(0xFEFF).toString()
    private const val RGB = 3
    private const val ALPHA = 3
    private val CHANNEL_COUNTS = mapOf("RGB" to RGB, "RGBA" to RGB + 1)
    private val WHITESPACE = Regex("\\s+")

    /** The palette in [path], read as `.gpl` or `.hex` by its extension. */
    fun read(path: Path): Palette {
        val read: (String) -> Palette = when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
            "gpl" -> ::parseGpl
            "hex" -> ::parseHex
            else -> throw PaletteFormatException("$path: a palette must be a .gpl or .hex file")
        }
        return withLocation("$path: ") { read(Files.readString(path)) }
    }

    fun readGpl(input: InputStream): Palette = withLocation("") { parseGpl(text(input)) }

    fun readHex(input: InputStream): Palette = withLocation("") { parseHex(text(input)) }

    private fun parseGpl(text: String): Palette {
        val lines = lines(text)
        val first = lines.firstOrNull { it.value.isNotBlank() }
        require(first != null && first.value.trim() == GPL_HEADER) { "the first line must be '$GPL_HEADER'" }
        var channels = CHANNEL_COUNTS.getValue("RGB")
        val colors = mutableListOf<Int>()
        for ((index, line) in lines.drop(first.index + 1)) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() || trimmed.startsWith("#") -> Unit
                trimmed.startsWith("Name:") || trimmed.startsWith("Columns:") -> Unit
                trimmed.startsWith("Channels:") -> channels = channelCount(trimmed, index)
                else -> colors += gplColor(trimmed, channels, index)
            }
        }
        return Palette(colors)
    }

    private fun parseHex(text: String): Palette {
        val colors = lines(text).filter { it.value.isNotBlank() }.map { (index, line) ->
            try {
                HexColor.parse(line.trim(), hashOptional = true)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("line ${index + 1}: ${e.message}", e)
            }
        }
        return Palette(colors)
    }

    private fun channelCount(line: String, index: Int): Int {
        val name = line.removePrefix("Channels:").trim()
        return requireNotNull(CHANNEL_COUNTS[name]) { "line ${index + 1}: unsupported channels '$name'" }
    }

    private fun gplColor(line: String, channels: Int, index: Int): Int {
        val fields = line.split(WHITESPACE)
        val values = fields.take(channels).map { it.toIntOrNull() }
        require(values.size == channels && values.all { it != null && it in 0..Argb.OPAQUE_ALPHA }) {
            "line ${index + 1}: expected $channels values from 0 to 255, got '$line'"
        }
        val (red, green, blue) = values.map { checkNotNull(it) }
        require(channels == RGB || values[ALPHA] == Argb.OPAQUE_ALPHA) { "line ${index + 1}: colours must be opaque" }
        return Argb.of(red, green, blue)
    }

    private fun text(input: InputStream): String = input.readAllBytes().toString(Charsets.UTF_8)

    private fun lines(text: String): List<IndexedValue<String>> =
        text.removePrefix(BYTE_ORDER_MARK).lines().withIndex().toList()

    private fun withLocation(where: String, read: () -> Palette): Palette =
        try {
            read()
        } catch (e: IllegalArgumentException) {
            throw PaletteFormatException("${where}Invalid palette: ${e.message}", e)
        }
}
