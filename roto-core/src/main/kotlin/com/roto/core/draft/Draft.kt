package com.roto.core.draft

import com.roto.core.image.IndexedRaster

/**
 * A draft frame at canvas size: [colours] holds palette labels (palette index + 1, `0` background),
 * [zones] the zone each pixel belongs to (`0` background). The two always agree on where the figure
 * is; the zone tells the outline and the dither which ramp a pixel's colour comes from.
 *
 * @property colours palette labels, palette index + 1, `0` for background
 * @property zones the zone of each pixel, `0` for background
 */
data class Draft(val colours: IndexedRaster, val zones: IndexedRaster) {
    init {
        require(colours.width == zones.width && colours.height == zones.height) {
            "Colours are ${colours.width}x${colours.height}, zones ${zones.width}x${zones.height}"
        }
    }

    /** The canvas width in pixels. */
    val width: Int get() = colours.width

    /** The canvas height in pixels. */
    val height: Int get() = colours.height

    /** Whether ([x], [y]) is on the canvas and part of the figure. */
    fun isFigure(x: Int, y: Int): Boolean = colours.contains(x, y) && zones[x, y] != 0
}
