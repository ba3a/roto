package com.roto.core.review

import com.roto.core.image.Argb
import com.roto.core.image.Mask
import com.roto.core.image.Raster

/**
 * The diff across a loop's seam: the pixels that change when playback jumps from the last frame to the
 * first.
 *
 * @property mask foreground where the two frames differ
 * @property count how many pixels differ
 */
class SeamDiff(val mask: Mask, val count: Int)

/** Compares a loop's last frame with its first. */
object LoopSeam {
    /**
     * The pixels where [last] and [first] differ. Two fully transparent pixels are equal whatever
     * their colour channels hold.
     *
     * @throws IllegalArgumentException when the frames differ in size
     */
    fun diff(last: Raster, first: Raster): SeamDiff {
        require(last.width == first.width && last.height == first.height) {
            "Loop seam frames must be the same size: ${last.width}x${last.height} and ${first.width}x${first.height}"
        }
        val mask = Mask.of(last.width, last.height) { x, y -> differs(last[x, y], first[x, y]) }
        return SeamDiff(mask, mask.count())
    }

    private fun differs(a: Int, b: Int): Boolean =
        a != b && !(Argb.alpha(a) == 0 && Argb.alpha(b) == 0)
}
