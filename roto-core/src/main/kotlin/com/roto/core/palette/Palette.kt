package com.roto.core.palette

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

    companion object {
        const val MAX_COLORS = 255
    }
}
