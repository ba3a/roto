package com.roto.core.review

import com.roto.core.image.Argb
import com.roto.core.image.Raster

/**
 * A looping clip whose last frame differs from its first in more pixels than
 * [LintSettings.seamPercent] percent of the first frame's opaque pixels, by [LoopSeam.diff]. A count equal
 * to the limit passes. A clip that does not loop has no seam, so it is never reported. The rule is named
 * `LoopSeam`, like the diff it uses.
 */
object SeamRule : LintRule {
    override val name = "LoopSeam"

    override fun check(frames: List<Raster>, context: LintContext): List<LintFinding> {
        if (!context.loop || frames.size < 2) return emptyList()
        val first = frames.first()
        val last = frames.last()
        if (first.width != last.width || first.height != last.height) return emptyList()
        val differing = LoopSeam.diff(last, first).count
        val figure = (0 until first.height).sumOf { y ->
            (0 until first.width).count { x -> Argb.alpha(first[x, y]) == OPAQUE }
        }
        if (differing.toLong() * PERCENT <= context.settings.seamPercent.toLong() * figure) return emptyList()
        val message = "$differing pixels change across the loop seam, over ${context.settings.seamPercent} % of $figure"
        return listOf(LintFinding(name, frames.size - 1, null, message))
    }

    private const val OPAQUE = 255
    private const val PERCENT = 100
}
