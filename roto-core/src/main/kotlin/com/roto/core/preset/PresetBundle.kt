package com.roto.core.preset

import com.roto.core.io.AtomicFile
import com.roto.core.palette.PaletteCodec
import com.roto.core.palette.RampSetCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val PALETTE_ENTRY = "palette.hex"
private const val COMPRESSION_LEVEL = Deflater.BEST_COMPRESSION
private const val MAX_BUNDLE_BYTES = 1L shl 20
private const val MAX_ENTRY_BYTES = 256 * 1024
private const val BUFFER_BYTES = 8192

/**
 * The timestamp of every entry, in local time so no time zone reaches the bytes. It is the day after the
 * earliest a ZIP can hold: the earliest itself reads as "before 1980" and makes the JDK add an extended
 * timestamp field.
 */
private val FIXED_TIME = LocalDateTime.of(1980, 1, 2, 0, 0)

/**
 * A preset as one `.rotopreset` file: a ZIP of exactly three entries, `preset.json`, `palette.hex`
 * and `ramps.json`.
 */
object PresetBundle {

    /** The file extension of a bundle, without the dot. */
    const val EXTENSION = "rotopreset"

    /**
     * Writes [preset] to [path] through [AtomicFile]. Entries come in a fixed order with a fixed
     * timestamp and compression level, and no extra field or comment, so the same preset always gives
     * the same bytes.
     */
    fun write(preset: ProjectPreset, path: Path) {
        AtomicFile.write(path) { write(preset, it) }
    }

    /** Writes [preset] to [output] as [write] does; [output] is not closed. */
    fun write(preset: ProjectPreset, output: OutputStream) {
        val zip = ZipOutputStream(output)
        zip.setLevel(COMPRESSION_LEVEL)
        entry(zip, PresetCodec.fileName) { PresetCodec.write(preset.info, it) }
        entry(zip, PALETTE_ENTRY) { PaletteCodec.writeHex(preset.palette, it) }
        entry(zip, RampSetCodec.fileName) { RampSetCodec.write(preset.ramps, it) }
        zip.finish()
    }

    /**
     * Reads a bundle from [path]. A bundle comes from someone else, so: a file over 1 MB is refused
     * before it is opened; only the three fixed entry names are accepted (any other entry, separator
     * or `..` is refused, and no entry name is ever used as a path); at most three entries are read, each
     * at most 256 KB uncompressed, counted while streaming rather than taken from the headers; and every
     * model is validated by its own codec. Nothing is written anywhere. Every failure is a
     * [PresetException] naming the entry.
     */
    fun read(path: Path): ProjectPreset {
        val entries = try {
            checkSize(path)
            Files.newInputStream(path).use { readEntries(it) }
        } catch (e: PresetException) {
            throw e
        } catch (e: IOException) {
            throw PresetException("$path: not a valid bundle: ${e.message}", e)
        }
        return build(entries)
    }

    private fun checkSize(path: Path) {
        if (Files.size(path) > MAX_BUNDLE_BYTES) throw PresetException("$path: bundle is larger than 1 MB")
    }

    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val name = entry.name
                checkEntry(name, entries.keys)
                entries[name] = readCapped(zip, name)
            }
        }
        return entries
    }

    private fun checkEntry(name: String, seen: Set<String>) {
        val problem = when {
            name !in setOf(PresetCodec.fileName, PALETTE_ENTRY, RampSetCodec.fileName) ->
                "not an entry of a preset bundle"
            name in seen -> "appears twice in the bundle"
            else -> null
        }
        if (problem != null) throw PresetException("$name: $problem")
    }

    private fun readCapped(zip: InputStream, name: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            if (out.size() + read > MAX_ENTRY_BYTES) throw PresetException("$name: larger than 256 KB uncompressed")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun build(entries: Map<String, ByteArray>): ProjectPreset {
        fun bytes(name: String) = entries[name] ?: throw PresetException("$name: missing from the bundle")
        val info = decode(PresetCodec.fileName) { PresetCodec.read(ByteArrayInputStream(bytes(PresetCodec.fileName))) }
        val palette = decode(PALETTE_ENTRY) { PaletteCodec.readHex(ByteArrayInputStream(bytes(PALETTE_ENTRY))) }
        val ramps = decode(RampSetCodec.fileName) {
            RampSetCodec.read(ByteArrayInputStream(bytes(RampSetCodec.fileName)))
        }
        return decode(PresetCodec.fileName) { ProjectPreset.of(info, palette, ramps) }
    }

    private fun <T> decode(name: String, read: () -> T): T =
        try {
            read()
        } catch (e: PresetException) {
            throw PresetException("$name: ${e.message}", e)
        } catch (e: IOException) {
            throw PresetException("$name: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw PresetException("$name: ${e.message}", e)
        }

    private fun entry(zip: ZipOutputStream, name: String, body: (OutputStream) -> Unit) {
        val bytes = ByteArrayOutputStream().also(body).toByteArray()
        val entry = ZipEntry(name)
        entry.timeLocal = FIXED_TIME
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }
}
