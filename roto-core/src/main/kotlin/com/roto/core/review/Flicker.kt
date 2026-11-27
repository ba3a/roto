package com.roto.core.review

import com.roto.core.draft.NeighbourMatch
import com.roto.core.image.Argb
import com.roto.core.image.Raster

/**
 * A one-frame twinkle: a pixel that changes colour from frame `i` to `i + 1` and back at `i + 2`, while
 * all its neighbours in frame `i + 1` equal those in frame `i`. The finding is on frame `i + 1`. Indexes
 * wrap, so the loop's seam counts. Needs at least 3 frames. Every transparent pixel counts as one colour.
 */
object Flicker : LintRule {
    override val name = "Flicker"

    override fun check(frames: List<Raster>, context: LintContext): List<LintFinding> {
        val count = frames.size
        if (count < MIN_FRAMES) return emptyList()
        return (0 until count).flatMap { i ->
            val middle = (i + 1) % count
            twinkles(frames[i], frames[middle], frames[(i + 2) % count]).map { (x, y) ->
                LintFinding(name, middle, LintFinding.Pixel(x, y), "Pixel changes for one frame only")
            }
        }
    }

    private fun twinkles(before: Raster, middle: Raster, after: Raster): List<Pair<Int, Int>> {
        if (before.width != middle.width || before.height != middle.height) return emptyList()
        if (before.width != after.width || before.height != after.height) return emptyList()
        return (0 until before.height).flatMap { y ->
            (0 until before.width).filter { x ->
                key(before[x, y]) != key(middle[x, y]) &&
                    key(before[x, y]) == key(after[x, y]) &&
                    NeighbourMatch.neighbours(before.width, before.height, x, y).all { (nx, ny) ->
                        key(before[nx, ny]) == key(middle[nx, ny])
                    }
            }.map { it to y }
        }
    }

    private fun key(argb: Int): Int = if (Argb.alpha(argb) == 0) 0 else argb

    private const val MIN_FRAMES = 3
}
