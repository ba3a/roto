package com.roto.core.preset

import com.roto.core.palette.PaletteCodec
import com.roto.core.palette.RampSetCodec
import java.io.IOException
import java.io.InputStream

private const val ROOT = "presets"

/**
 * The presets shipped inside `roto-core` as classpath resources under `presets/<id>/`: `preset.json`,
 * `palette.hex` and `ramps.json` each, with `presets/index.txt` listing the ids, one per line. They
 * are generic starting points that name no game, engine or consumer layout.
 */
object BuiltInPresets {

    /** The ids of the built-in presets, in the order `index.txt` lists them. */
    fun ids(): List<String> =
        open("$ROOT/index.txt").use { it.readAllBytes().toString(Charsets.UTF_8) }
            .lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** The built-in preset [id]. Throws [PresetException] when it is not listed or a file is invalid. */
    fun load(id: String): ProjectPreset {
        if (id !in ids()) throw PresetException("Unknown built-in preset '$id'")
        val info = read(id, PresetCodec.fileName) { PresetCodec.read(it) }
        val palette = read(id, "palette.hex") { PaletteCodec.readHex(it) }
        val ramps = read(id, RampSetCodec.fileName) { RampSetCodec.read(it) }
        return try {
            ProjectPreset.of(info, palette, ramps)
        } catch (e: IllegalArgumentException) {
            throw PresetException("Built-in preset '$id': ${e.message}", e)
        }
    }

    /** Every built-in preset, in the order of [ids]. */
    fun all(): List<ProjectPreset> = ids().map(::load)

    private fun <T> read(id: String, file: String, parse: (InputStream) -> T): T =
        try {
            open("$ROOT/$id/$file").use(parse)
        } catch (e: IOException) {
            throw PresetException("Built-in preset '$id', $file: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw PresetException("Built-in preset '$id', $file: ${e.message}", e)
        }

    private fun open(resource: String): InputStream =
        requireNotNull(BuiltInPresets::class.java.classLoader.getResourceAsStream(resource)) {
            "Built-in resource $resource is missing"
        }
}
