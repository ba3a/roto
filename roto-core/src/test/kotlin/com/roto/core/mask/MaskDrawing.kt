package com.roto.core.mask

import com.roto.core.image.Mask

/** A mask drawn as text, one string per row: `#` is foreground, anything else background. */
internal fun mask(vararg rows: String): Mask {
    require(rows.isNotEmpty() && rows.all { it.length == rows.first().length }) { "Rows must be non-empty and equal" }
    return Mask.of(rows.first().length, rows.size) { x, y -> rows[y][x] == '#' }
}

/** The mask drawn as [mask] reads it, so a failed comparison shows the shapes side by side. */
internal fun Mask.drawing(): String =
    (0 until height).joinToString("\n") { y -> (0 until width).joinToString("") { x -> if (this[x, y]) "#" else "." } }
