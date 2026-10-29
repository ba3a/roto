package com.roto.core.shade

import com.roto.core.palette.ProjectPalette
import com.roto.core.zone.ZoneSettings
import kotlinx.serialization.Serializable

/** The highest palette label: palette index 254 plus one. */
private const val MAX_LABEL = 255

/**
 * The colours a clip's draft is shaded and finished in, as palette labels — palette index plus
 * one, so `0` stays background, as in zone maps, and can be the transparent entry of an indexed
 * `.ase`. [zones] holds each zone's ramp, darkest first; [outline] the project's outline colour, or
 * null to outline each zone in its ramp's darkest.
 */
@Serializable
data class DraftColours(val zones: List<List<Int>>, val outline: Int? = null) {
    init {
        zones.forEachIndexed { zone, ramp ->
            require(ramp.isNotEmpty() && ramp.all { it in 1..MAX_LABEL }) {
                "Zone ${zone + 1}'s colours must be palette labels 1..$MAX_LABEL, got $ramp"
            }
        }
        require(outline == null || outline in 1..MAX_LABEL) {
            "Outline must be a palette label 1..$MAX_LABEL, got $outline"
        }
    }

    companion object {
        /** The colours of [zoning]'s zones and of the outline, looked up in the project's [palette]. */
        fun of(palette: ProjectPalette, zoning: ZoneSettings): DraftColours {
            val label = { argb: Int -> palette.palette.colors.indexOf(argb) + 1 }
            return DraftColours(
                zones = palette.ramps(zoning.zones.map { it.ramp }).map { ramp -> ramp.colors.map(label) },
                outline = palette.ramps.outline?.let(label),
            )
        }
    }
}
