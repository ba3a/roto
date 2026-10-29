package com.roto.core.draft

import com.roto.core.mask.Connectivity

/**
 * Draws a one-pixel outline inside the silhouette: every figure pixel with background, or the edge
 * of the canvas, beside one of its edges takes the project's outline colour when there is one, or
 * else the darkest colour of its own ramp. Inside, so the figure keeps the size it was drawn at.
 */
object Outline {

    /** [draft] outlined; [outline] is the project's outline palette label, or null for each ramp's darkest. */
    fun draw(draft: Draft, zoneColours: List<List<Int>>, outline: Int?): Draft {
        val colours = draft.colours.copy()
        for (y in 0 until draft.height) {
            for (x in 0 until draft.width) {
                val zone = draft.zones[x, y]
                if (zone == 0) continue
                val onEdge = Connectivity.FOUR.steps.any { (dx, dy) -> !draft.isFigure(x + dx, y + dy) }
                if (onEdge) colours[x, y] = outline ?: zoneColours[zone - 1].first()
            }
        }
        return Draft(colours, draft.zones)
    }
}
