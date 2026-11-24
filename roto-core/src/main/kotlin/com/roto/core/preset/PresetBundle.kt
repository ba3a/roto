package com.roto.core.preset

import com.roto.core.io.AtomicFile
import com.roto.core.palette.PaletteCodec
import com.roto.core.palette.RampSetCodec
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val PALETTE_ENTRY = "palette.hex"
private const val COMPRESSION_LEVEL = Deflater.BEST_COMPRESSION

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

    private fun entry(zip: ZipOutputStream, name: String, body: (OutputStream) -> Unit) {
        val bytes = ByteArrayOutputStream().also(body).toByteArray()
        val entry = ZipEntry(name)
        entry.timeLocal = FIXED_TIME
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }
}
