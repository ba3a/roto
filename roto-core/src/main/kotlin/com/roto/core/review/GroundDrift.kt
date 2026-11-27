package com.roto.core.review

import com.roto.core.image.Argb
import com.roto.core.image.Raster
import kotlin.math.abs

/**
 * A frame whose lowest opaque row differs from the clip's median lowest row by more than
 * [LintSettings.groundDriftPx]. The median is an integer: the lower middle for an even count. A frame
 * with no opaque pixel is left out of the median and never reported. Skipped for a clip that follows depth.
 */
object GroundDrift : LintRule {
    override val name = "GroundDrift"

    override fun check(frames: List<Raster>, context: LintContext): List<LintFinding> {
        if (context.depth.follow) return emptyList()
        val lowest = frames.map(::lowestRow)
        val rows = lowest.filterNotNull().sorted()
        if (rows.isEmpty()) return emptyList()
        val median = rows[(rows.size - 1) / 2]
        return lowest.mapIndexedNotNull { index, row ->
            val drift = if (row == null) 0 else abs(row - median)
            if (drift > context.settings.groundDriftPx) {
                LintFinding(name, index, null, "Lowest row $row is $drift px from the clip's median $median")
            } else {
                null
            }
        }
    }

    private fun lowestRow(frame: Raster): Int? = (frame.height - 1 downTo 0).firstOrNull { y ->
        (0 until frame.width).any { x -> Argb.alpha(frame[x, y]) == OPAQUE }
    }

    private const val OPAQUE = 255
}
