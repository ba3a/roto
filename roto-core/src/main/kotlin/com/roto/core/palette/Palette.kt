package com.roto.core.palette

import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster

/**
 * A project's palette: the opaque colours every draft is drawn in, in the order the palette file
 * lists them. At most [MAX_COLORS]: an indexed `.ase` holds 256 entries, and the handoff keeps one
 * of them for transparency.
 */
data class Palette(val colors: List<Int>) {
    init {
        require(colors.size in 1..MAX_COLORS) { "A palette holds 1..$MAX_COLORS colours, this one ${colors.size}" }
        colors.firstOrNull { !HexColor.isOpaque(it) }?.let { color ->
            throw IllegalArgumentException("Palette colour ${HexColor.format(color)} is not opaque")
        }
    }

    operator fun contains(argb: Int): Boolean = argb in colors

    /** A map of palette labels — palette index plus one, `0` transparent — as the colours they stand for. */
    fun paint(labels: IndexedRaster): Raster = Raster.of(labels.width, labels.height) { x, y ->
        val label = labels[x, y]
        require(label <= colors.size) { "Label $label is beyond the palette's ${colors.size} colours" }
        if (label == 0) Argb.TRANSPARENT else colors[label - 1]
    }

    companion object {
        const val MAX_COLORS = 255
    }
}
