package com.roto.core.shade

import com.roto.core.palette.ProjectPalette
import com.roto.core.zone.ZoneSettings

/**
 * The colours each zone of a clip is shaded in, as palette labels: for each zone, its ramp's
 * colours from darkest to lightest, each written as its index in the project palette plus one. A
 * draft holds labels, not colours — `0` for background, as zone maps do — so it can be written into
 * an indexed `.ase` whose entry `0` is transparent.
 */
object ZoneColours {

    fun of(palette: ProjectPalette, zoning: ZoneSettings): List<List<Int>> {
        val ramps = palette.ramps(zoning.zones.map { it.ramp })
        return ramps.map { ramp -> ramp.colors.map { palette.palette.colors.indexOf(it) + 1 } }
    }
}
