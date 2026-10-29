package com.roto.core.draft

import com.roto.core.mask.Connectivity

/**
 * Removes orphans: single pixels — figure or background — that match none of their eight
 * neighbours in both zone and colour. Each takes the zone and colour most of its neighbours share,
 * the lowest on a tie. A pixel along a one-pixel line keeps its place, since the line's next pixel
 * matches it. Every decision reads the draft as it was, so the result does not depend on scan order.
 */
object OrphanCleanup {

    private const val LABELS = 256

    fun clean(draft: Draft): Draft {
        val colours = draft.colours.copy()
        val zones = draft.zones.copy()
        for (y in 0 until draft.height) {
            for (x in 0 until draft.width) {
                val own = key(draft, x, y)
                val neighbours = neighbours(draft, x, y).map { (nx, ny) -> key(draft, nx, ny) }
                if (neighbours.isEmpty() || own in neighbours) continue
                val replacement = neighbours.groupingBy { it }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                    .first().key
                zones[x, y] = replacement / LABELS
                colours[x, y] = replacement % LABELS
            }
        }
        return Draft(colours, zones)
    }

    private fun key(draft: Draft, x: Int, y: Int): Int = draft.zones[x, y] * LABELS + draft.colours[x, y]

    private fun neighbours(draft: Draft, x: Int, y: Int): List<Pair<Int, Int>> =
        Connectivity.EIGHT.steps
            .map { (dx, dy) -> x + dx to y + dy }
            .filter { (nx, ny) -> draft.colours.contains(nx, ny) }
}
