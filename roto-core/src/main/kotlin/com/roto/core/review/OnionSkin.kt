package com.roto.core.review

import com.roto.core.image.Argb
import com.roto.core.image.Raster

/**
 * One raster from the current, previous and next frame: the previous frame tinted [PREVIOUS_TINT] and
 * the next tinted [NEXT_TINT], each at 30 % opacity, behind the current frame. Integer arithmetic
 * only, so the result is the same on every machine.
 */
object OnionSkin {
    /** The colour the previous frame's figure is drawn in. */
    val PREVIOUS_TINT: Int = Argb.of(255, 0, 0)

    /** The colour the next frame's figure is drawn in. */
    val NEXT_TINT: Int = Argb.of(0, 0, 255)

    private const val GHOST_PERCENT = 30
    private const val PERCENT = 100
    private const val FULL = 255
    private const val ALPHA_SHIFT = 24
    private const val RGB_MASK = 0xFFFFFF

    /**
     * The onion skin of [current] with [previous] and [next] behind it; a frame that does not exist
     * (the first or last of a clip that does not loop) is `null`. A ghost pixel takes its tint's
     * colour and 30 % of the frame pixel's alpha, rounded to nearest.
     *
     * @throws IllegalArgumentException when the frames differ in size
     */
    fun compose(current: Raster, previous: Raster?, next: Raster?): Raster {
        listOfNotNull(previous, next).forEach {
            require(it.width == current.width && it.height == current.height) {
                val sizes = "${current.width}x${current.height} and ${it.width}x${it.height}"
                "Onion skin frames must be the same size: $sizes"
            }
        }
        return Raster.of(current.width, current.height) { x, y ->
            var pixel = Argb.TRANSPARENT
            if (previous != null) pixel = over(ghost(previous[x, y], PREVIOUS_TINT), pixel)
            if (next != null) pixel = over(ghost(next[x, y], NEXT_TINT), pixel)
            over(current[x, y], pixel)
        }
    }

    private fun ghost(pixel: Int, tint: Int): Int {
        val alpha = (Argb.alpha(pixel) * GHOST_PERCENT + PERCENT / 2) / PERCENT
        return (alpha shl ALPHA_SHIFT) or (tint and RGB_MASK)
    }

    /** Straight-alpha source-over of [top] on [bottom], rounded to nearest. */
    private fun over(top: Int, bottom: Int): Int {
        val topAlpha = Argb.alpha(top)
        val weight = Argb.alpha(bottom) * (FULL - topAlpha)
        val total = topAlpha * FULL + weight
        if (total == 0) return Argb.TRANSPARENT
        fun channel(top: Int, bottom: Int): Int = (top * topAlpha * FULL + bottom * weight + total / 2) / total
        return Argb.of(
            channel(Argb.red(top), Argb.red(bottom)),
            channel(Argb.green(top), Argb.green(bottom)),
            channel(Argb.blue(top), Argb.blue(bottom)),
            (total + FULL / 2) / FULL,
        )
    }
}
