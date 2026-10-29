package com.roto.core.draft

import com.roto.core.mask.Connectivity

/**
 * Softens the edge between two neighbouring steps of one ramp with a checkerboard: on the darker
 * side of the edge, every pixel at an even place of the 2×2 Bayer matrix takes the lighter step.
 * Only within a zone and only between adjacent steps, so an outline or a change of material is
 * never dithered. Off unless the clip asks for it.
 */
object RampDither {

    /** The 2×2 Bayer matrix, row by row; a pixel whose entry is below half dithers. */
    private val BAYER = arrayOf(intArrayOf(0, 2), intArrayOf(3, 1))
    private const val HALF = 2

    fun dither(draft: Draft, zoneColours: List<List<Int>>): Draft {
        val colours = draft.colours.copy()
        for (y in 0 until draft.height) {
            for (x in 0 until draft.width) {
                val zone = draft.zones[x, y]
                if (zone == 0 || BAYER[y % 2][x % 2] >= HALF) continue
                val ramp = zoneColours[zone - 1]
                val step = ramp.indexOf(draft.colours[x, y])
                if (step < 0 || step + 1 >= ramp.size) continue
                val lighter = ramp[step + 1]
                val touchesLighter = Connectivity.FOUR.steps.any { (dx, dy) ->
                    draft.isFigure(x + dx, y + dy) && draft.zones[x + dx, y + dy] == zone &&
                        draft.colours[x + dx, y + dy] == lighter
                }
                if (touchesLighter) colours[x, y] = lighter
            }
        }
        return Draft(colours, draft.zones)
    }
}
