package com.roto.core.mask

import com.roto.core.image.Mask
import kotlin.math.abs

/**
 * Applies [BrushStroke]s to a mask: pure, in whole pixels, so the same strokes always give the same
 * mask. A stroke is a disc stamped at every pixel of the straight lines through its points; a stroke
 * running past the mask's edge is cut off there.
 */
object MaskBrush {

    /** [mask] with [strokes] applied in order, whatever frame they name; [mask] itself is left as it was. */
    fun apply(mask: Mask, strokes: List<BrushStroke>): Mask {
        if (strokes.isEmpty()) return mask
        val fixed = mask.copy()
        strokes.forEach { stroke -> paint(fixed, stroke) }
        return fixed
    }

    private fun paint(mask: Mask, stroke: BrushStroke) {
        val foreground = stroke.mode == BrushMode.ADD
        stamp(mask, stroke.points.first(), stroke.radius, foreground)
        stroke.points.zipWithNext().forEach { (from, to) ->
            val steps = maxOf(abs(to.x - from.x), abs(to.y - from.y))
            for (step in 1..steps) {
                val x = from.x + (to.x - from.x) * step / steps
                val y = from.y + (to.y - from.y) * step / steps
                stamp(mask, BrushPoint(x, y), stroke.radius, foreground)
            }
        }
    }

    private fun stamp(mask: Mask, centre: BrushPoint, radius: Int, foreground: Boolean) {
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = centre.x + dx
                val y = centre.y + dy
                if (dx * dx + dy * dy <= radius * radius && mask.contains(x, y)) mask[x, y] = foreground
            }
        }
    }
}
