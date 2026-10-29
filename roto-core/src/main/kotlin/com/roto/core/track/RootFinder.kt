package com.roto.core.track

import com.roto.core.image.Mask

/**
 * The root of one mask: the median x of its foreground in the hip band. The band is measured on the
 * mask's own height, so it follows the figure as it bobs. An arm swinging at hip height pulls the
 * median by a pixel or so, where it would drag the mean or the band's extent most of the way out to
 * the hand.
 */
object RootFinder {

    /** The root x of [mask], or null when it holds no figure. */
    fun rootX(mask: Mask, settings: RootSettings): Double? {
        val rows = (0 until mask.height).filter { y -> (0 until mask.width).any { x -> mask[x, y] } }
        if (rows.isEmpty()) return null
        val top = rows.first()
        val height = rows.last() - top + 1
        val bandTop = top + (settings.hipBandTop * height).toInt()
        val bandBottom = maxOf(bandTop, top + (settings.hipBandBottom * height).toInt() - 1)
        val xs = (bandTop..bandBottom).flatMap { y -> (0 until mask.width).filter { x -> mask[x, y] } }.sorted()
        return if (xs.isEmpty()) null else xs[(xs.size - 1) / 2].toDouble()
    }
}
