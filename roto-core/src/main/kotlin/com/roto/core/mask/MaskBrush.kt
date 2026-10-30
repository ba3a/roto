package com.roto.core.mask

import com.roto.core.image.Mask

/** Applies [BrushStroke]s to a mask: pure, so the same strokes always give the same mask. */
object MaskBrush {

    /** [mask] with [strokes] applied in order, whatever frame they name; [mask] itself is left as it was. */
    fun apply(mask: Mask, strokes: List<BrushStroke>): Mask {
        if (strokes.isEmpty()) return mask
        val fixed = mask.copy()
        strokes.forEach { stroke ->
            val foreground = stroke.mode == BrushMode.ADD
            BrushPath.forEachPixel(stroke.points, stroke.radius, mask.width, mask.height) { x, y ->
                fixed[x, y] = foreground
            }
        }
        return fixed
    }
}
