package com.roto.core.draft

import com.roto.core.image.IndexedRaster

/** A label map drawn as text, one string per row: `.` is 0, `1`..`9` themselves. */
internal fun grid(vararg rows: String): IndexedRaster {
    require(rows.isNotEmpty() && rows.all { it.length == rows.first().length }) { "Rows must be non-empty and equal" }
    return IndexedRaster.of(rows.first().length, rows.size) { x, y ->
        if (rows[y][x] == '.') 0 else rows[y][x].digitToInt()
    }
}

/** The label map drawn as [grid] reads it, so a failed comparison shows the two side by side. */
internal fun IndexedRaster.drawing(): String =
    (0 until height).joinToString("\n") { y ->
        (0 until width).joinToString("") { x -> if (this[x, y] == 0) "." else this[x, y].toString() }
    }
