package com.roto.core.draft

import com.roto.core.image.IndexedRaster

/**
 * Reduces a draft shaded at `factor` times the canvas to the canvas, one block of `factor × factor`
 * pixels per final pixel, by vote. A block is figure when at least half of it is; it then takes the
 * zone most of its figure pixels have, and the colour most of that zone's pixels in the block have,
 * so zone and colour always agree. Ties go to the lowest label, the same way every time.
 */
object MajorityReduce {

    private const val LABELS = 256

    fun reduce(colours: IndexedRaster, zones: IndexedRaster, factor: Int): Draft {
        require(factor >= 1) { "Factor must be at least 1, got $factor" }
        require(colours.width == zones.width && colours.height == zones.height) { "Colours and zones differ in size" }
        require(colours.width % factor == 0 && colours.height % factor == 0) {
            "A ${colours.width}x${colours.height} draft does not divide into $factor-pixel blocks"
        }
        val width = colours.width / factor
        val height = colours.height / factor
        val reducedColours = IndexedRaster(width, height)
        val reducedZones = IndexedRaster(width, height)
        val zoneVotes = IntArray(LABELS)
        val colourVotes = IntArray(LABELS)
        for (by in 0 until height) {
            for (bx in 0 until width) {
                zoneVotes.fill(0)
                forEachInBlock(bx, by, factor) { x, y -> zoneVotes[zones[x, y]]++ }
                val figure = factor * factor - zoneVotes[0]
                if (2 * figure < factor * factor) continue
                val zone = winner(zoneVotes, from = 1)
                colourVotes.fill(0)
                forEachInBlock(bx, by, factor) { x, y -> if (zones[x, y] == zone) colourVotes[colours[x, y]]++ }
                reducedZones[bx, by] = zone
                reducedColours[bx, by] = winner(colourVotes, from = 0)
            }
        }
        return Draft(reducedColours, reducedZones)
    }

    private inline fun forEachInBlock(bx: Int, by: Int, factor: Int, visit: (x: Int, y: Int) -> Unit) {
        for (y in by * factor until (by + 1) * factor) {
            for (x in bx * factor until (bx + 1) * factor) visit(x, y)
        }
    }

    /** The label with the most votes from [from] up; of equals, the lowest. */
    private fun winner(votes: IntArray, from: Int): Int {
        var best = from
        for (label in from until votes.size) {
            if (votes[label] > votes[best]) best = label
        }
        return best
    }
}
