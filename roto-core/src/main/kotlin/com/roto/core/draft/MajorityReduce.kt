package com.roto.core.draft

import com.roto.core.image.IndexedRaster
import kotlin.math.abs

/**
 * Reduces a draft shaded at `factor` times the canvas to the canvas, one block of `factor × factor`
 * pixels per final pixel, by vote. A block is figure when at least half of it is; it then takes the
 * zone most of its figure pixels have, and the colour most of that zone's pixels in the block have,
 * so zone and colour always agree. Ties go to the lowest label, the same way every time.
 *
 * With the previous key frame's reduction and a hold, a block keeps what the previous frame had unless
 * the vote moves clearly: coverage more than `hold` samples from the 50 % line, or another label more
 * than `hold` samples above the previous one. That keeps a draft from flickering between key frames.
 */
object MajorityReduce {

    private const val LABELS = 256

    /**
     * The [Draft] of [colours] and [zones], shaded at [factor] times the canvas, reduced to the canvas.
     * [previous] is the previous key frame's reduced draft, held to by [hold] samples; with no
     * [previous], or a [hold] of 0, the plain rules apply.
     */
    fun reduce(
        colours: IndexedRaster,
        zones: IndexedRaster,
        factor: Int,
        previous: Draft? = null,
        hold: Int = 0,
    ): Draft {
        require(factor >= 1) { "Factor must be at least 1, got $factor" }
        require(hold >= 0) { "Hold must not be negative, got $hold" }
        require(colours.width == zones.width && colours.height == zones.height) { "Colours and zones differ in size" }
        require(colours.width % factor == 0 && colours.height % factor == 0) {
            "A ${colours.width}x${colours.height} draft does not divide into $factor-pixel blocks"
        }
        val width = colours.width / factor
        val height = colours.height / factor
        require(previous == null || (previous.width == width && previous.height == height)) {
            "The previous draft is ${previous?.width}x${previous?.height}, this one ${width}x$height"
        }
        val votes = Votes(colours, zones, factor, if (hold > 0) previous else null, hold)
        val reducedColours = IndexedRaster(width, height)
        val reducedZones = IndexedRaster(width, height)
        for (by in 0 until height) {
            for (bx in 0 until width) votes.reduceBlock(bx, by, reducedColours, reducedZones)
        }
        return Draft(reducedColours, reducedZones)
    }

    /** One reduction's inputs and the vote buffers its blocks share. */
    private class Votes(
        private val colours: IndexedRaster,
        private val zones: IndexedRaster,
        private val factor: Int,
        private val held: Draft?,
        private val hold: Int,
    ) {
        private val total = factor * factor
        private val zoneVotes = IntArray(LABELS)
        private val colourVotes = IntArray(LABELS)

        fun reduceBlock(bx: Int, by: Int, reducedColours: IndexedRaster, reducedZones: IndexedRaster) {
            zoneVotes.fill(0)
            forEachInBlock(bx, by, factor) { x, y -> zoneVotes[zones[x, y]]++ }
            val figure = total - zoneVotes[0]
            val previousZone = held?.zones?.get(bx, by) ?: 0
            val withinMargin = held != null && abs(2 * figure - total) <= 2 * hold
            val isFigure = if (withinMargin) previousZone != 0 else 2 * figure >= total
            if (!isFigure || figure == 0) return
            val zone = holdWinner(zoneVotes, previousZone, from = 1, hold = hold)
            colourVotes.fill(0)
            forEachInBlock(bx, by, factor) { x, y -> if (zones[x, y] == zone) colourVotes[colours[x, y]]++ }
            val previousColour = if (held != null && zone == previousZone) held.colours[bx, by] else -1
            reducedZones[bx, by] = zone
            reducedColours[bx, by] = holdWinner(colourVotes, previousColour, from = 0, hold = hold)
        }
    }

    private inline fun forEachInBlock(bx: Int, by: Int, factor: Int, visit: (x: Int, y: Int) -> Unit) {
        for (y in by * factor until (by + 1) * factor) {
            for (x in bx * factor until (bx + 1) * factor) visit(x, y)
        }
    }

    /**
     * The winner by [votes], except that [previous], when it is a label from [from] up, stays unless
     * another label has more than [hold] votes over it.
     */
    private fun holdWinner(votes: IntArray, previous: Int, from: Int, hold: Int): Int {
        val best = winner(votes, from)
        if (hold == 0 || previous < from) return best
        return if (votes[best] - votes[previous] > hold) best else previous
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
