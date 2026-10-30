package com.roto.core.mask

import kotlin.math.abs

/**
 * The pixels a brush covers: a disc stamped at every pixel of the straight lines through a stroke's
 * points. In whole pixels only, so the same stroke always covers the same pixels, whatever it is
 * painted on. What runs past the edge of the image is cut off.
 */
internal object BrushPath {

    /** Calls [paint] for each pixel of a [width] by [height] image the stroke covers; some more than once. */
    fun forEachPixel(
        points: List<BrushPoint>,
        radius: Int,
        width: Int,
        height: Int,
        paint: (x: Int, y: Int) -> Unit,
    ) {
        val stamp = { centre: BrushPoint ->
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val x = centre.x + dx
                    val y = centre.y + dy
                    if (dx * dx + dy * dy <= radius * radius && x in 0 until width && y in 0 until height) paint(x, y)
                }
            }
        }
        stamp(points.first())
        points.zipWithNext().forEach { (from, to) ->
            val steps = maxOf(abs(to.x - from.x), abs(to.y - from.y))
            for (step in 1..steps) {
                stamp(
                    BrushPoint(
                        from.x + (to.x - from.x) * step / steps,
                        from.y + (to.y - from.y) * step / steps,
                    ),
                )
            }
        }
    }
}
