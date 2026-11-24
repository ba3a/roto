package com.roto.core.palette

import com.roto.core.color.OkLab

/**
 * Suggests ramps for a [Palette]: a deterministic starting point the artist then edits.
 *
 * Colours below [NEUTRAL_CHROMA] form one neutral group; the rest fall into [HUE_BUCKETS] equal
 * slices of the OKLab hue circle, starting at the +a axis. Each group is sorted dark to light. Of
 * colours with exactly equal lightness, the one listed first in the palette stays, so every ramp
 * passes [Ramp] validation. Empty groups are dropped. Groups are named `ramp1`, `ramp2`, ... in hue
 * order, the neutral group last.
 */
object RampSuggester {
    /** The number of equal hue slices colours are grouped into. */
    const val HUE_BUCKETS = 6

    /** The OKLab chroma below which a colour counts as neutral. */
    const val NEUTRAL_CHROMA = 0.03

    private const val FULL_TURN = 360.0

    /** The suggested ramps for [palette], in hue order with the neutral ramp last. */
    fun suggest(palette: Palette): List<Ramp> {
        val groups = palette.colors.withIndex()
            .map { (index, argb) -> Entry(index, argb, OkLab.fromArgb(argb)) }
            .groupBy { group(it.lab) }
            .toSortedMap()
        return groups.values.mapIndexed { number, entries ->
            Ramp("ramp${number + 1}", darkToLight(entries))
        }
    }

    private fun group(lab: OkLab): Int {
        if (lab.chroma < NEUTRAL_CHROMA) return HUE_BUCKETS
        val degrees = StrictMath.toDegrees(StrictMath.atan2(lab.b, lab.a))
        val hue = if (degrees < 0.0) degrees + FULL_TURN else degrees
        return minOf((hue / (FULL_TURN / HUE_BUCKETS)).toInt(), HUE_BUCKETS - 1)
    }

    private fun darkToLight(entries: List<Entry>): List<Int> {
        val sorted = entries.sortedWith(compareBy<Entry> { it.lab.l }.thenBy { it.index })
        return sorted.filterIndexed { i, entry -> i == 0 || entry.lab.l != sorted[i - 1].lab.l }.map { it.argb }
    }

    private class Entry(val index: Int, val argb: Int, val lab: OkLab)
}
