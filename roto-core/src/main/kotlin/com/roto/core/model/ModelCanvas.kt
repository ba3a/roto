package com.roto.core.model

import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.project.Canvas

/**
 * The one rule for a model frame drawn smaller than the project canvas, shared by every [ModelSource]:
 * it is placed centred across, its bottom row on the canvas's bottom row, so the feet stay on the
 * ground. Roto never rescales a model, so a frame larger than the canvas either way is refused.
 */
object ModelCanvas {

    /** Fails with a [ModelException] naming [what] when a [width] x [height] frame does not fit in [canvas]. */
    fun requireFits(what: String, width: Int, height: Int, canvas: Canvas) {
        if (width > canvas.width || height > canvas.height) {
            throw ModelException("$what is ${width}x$height, larger than the ${canvas.width}x${canvas.height} canvas")
        }
    }

    /**
     * [frame] on a transparent [canvas]: centred across, an odd gap leaving the extra column on the right,
     * and bottom-aligned. A frame of canvas size comes back as it is; one that does not fit fails as in
     * [requireFits], naming [what].
     */
    fun place(what: String, frame: Raster, canvas: Canvas): Raster {
        requireFits(what, frame.width, frame.height, canvas)
        if (frame.width == canvas.width && frame.height == canvas.height) return frame
        val left = (canvas.width - frame.width) / 2
        val top = canvas.height - frame.height
        return Raster.of(canvas.width, canvas.height) { x, y ->
            val fx = x - left
            val fy = y - top
            if (frame.contains(fx, fy)) frame[fx, fy] else Argb.TRANSPARENT
        }
    }
}
