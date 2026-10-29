package com.roto.core.palette

import com.roto.core.project.Project

/**
 * A project's [palette] and its [ramps], checked against each other: every ramp colour, and the
 * outline colour if there is one, is a palette colour. So whatever a draft is shaded or outlined in
 * can be written into the indexed handoff.
 */
class ProjectPalette(val palette: Palette, val ramps: RampSet) {
    init {
        ramps.outline?.let { outline ->
            require(outline in palette) { "Outline colour ${HexColor.format(outline)} is not in the palette" }
        }
        for (ramp in ramps.ramps) {
            ramp.colors.firstOrNull { it !in palette }?.let { missing ->
                throw IllegalArgumentException(
                    "Ramp '${ramp.name}' uses ${HexColor.format(missing)}, which is not in the palette",
                )
            }
        }
    }

    /** The ramps called [names], in that order — a profile's ramps; throws naming any that do not exist. */
    fun ramps(names: List<String>): List<Ramp> = names.map { name ->
        requireNotNull(ramps.ramp(name)) {
            "Ramp '$name' is not in ramps.json, which has ${ramps.ramps.map { it.name }}"
        }
    }

    companion object {
        /** Reads [project]'s palette file and `ramps.json` and checks them against each other. */
        fun load(project: Project): ProjectPalette {
            val palette = PaletteCodec.read(project.paletteFile)
            val ramps = RampSetCodec.read(project.rampsFile)
            return try {
                ProjectPalette(palette, ramps)
            } catch (e: IllegalArgumentException) {
                throw RampSetException("${project.rampsFile}: ${e.message}", e)
            }
        }
    }
}
