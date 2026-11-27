package com.roto.core.draft

import com.roto.core.mask.Connectivity

/**
 * The one test for an orphan: a pixel that matches none of its in-image 8-neighbours. The draft's
 * [OrphanCleanup] and the art lint's stray-pixel rule both use it, so they agree by construction.
 */
internal object NeighbourMatch {

    /** The in-image 8-neighbours of [x], [y] in a [width] by [height] image. */
    fun neighbours(width: Int, height: Int, x: Int, y: Int): List<Pair<Int, Int>> =
        Connectivity.EIGHT.steps
            .map { (dx, dy) -> x + dx to y + dy }
            .filter { (nx, ny) -> nx in 0 until width && ny in 0 until height }

    /** Whether a pixel with key [own] matches none of its neighbours' keys; false when it has none. */
    fun isOrphan(own: Int, neighbourKeys: List<Int>): Boolean = neighbourKeys.isNotEmpty() && own !in neighbourKeys
}
