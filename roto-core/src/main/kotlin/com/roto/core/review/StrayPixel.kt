package com.roto.core.review

import com.roto.core.draft.NeighbourMatch
import com.roto.core.image.Argb
import com.roto.core.image.Raster

/**
 * An opaque pixel that matches none of its 8 neighbours, by the test the draft's orphan cleanup uses.
 * Every transparent pixel counts as one colour, whatever its channels.
 */
object StrayPixel : LintRule {
    override val name = "StrayPixel"

    override fun check(frames: List<Raster>, context: LintContext): List<LintFinding> =
        frames.flatMapIndexed { index, frame ->
            (0 until frame.height).flatMap { y ->
                (0 until frame.width).mapNotNull { x ->
                    if (Argb.alpha(frame[x, y]) == OPAQUE && isStray(frame, x, y)) {
                        LintFinding(name, index, LintFinding.Pixel(x, y), "Pixel matches none of its neighbours")
                    } else {
                        null
                    }
                }
            }
        }

    private fun isStray(frame: Raster, x: Int, y: Int): Boolean {
        val keys = NeighbourMatch.neighbours(frame.width, frame.height, x, y).map { (nx, ny) -> key(frame[nx, ny]) }
        return NeighbourMatch.isOrphan(key(frame[x, y]), keys)
    }

    private fun key(argb: Int): Int = if (Argb.alpha(argb) == 0) 0 else argb

    private const val OPAQUE = 255
}
