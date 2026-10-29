package com.roto.core.cycle

import com.roto.core.image.Mask

/**
 * How often a figure's silhouette repeats: the autocorrelation of its root-aligned masks, scored as
 * the mean intersection over union of every frame with the frame [lag][Repeat.frames] later. The
 * best lag wins; of equally good lags the shortest, so a clip holding three repeats reports one.
 */
object RepeatPeriod {

    /** A repeat every [frames] frames, the masks overlapping by [score] on average at that lag. */
    data class Repeat(val frames: Int, val score: Double)

    /**
     * The repeat of [aligned] masks — already shifted so the root stands still — at a lag in
     * [lags], or null when the clip is too short to hold any lag in the range twice.
     */
    fun of(aligned: List<Mask>, lags: IntRange): Repeat? {
        val bits = aligned.map { MaskBits.of(it) }
        val usable = lags.first.coerceAtLeast(1)..minOf(lags.last, bits.size / 2)
        var best: Repeat? = null
        for (lag in usable) {
            val score = (0 until bits.size - lag).sumOf { bits[it].overlap(bits[it + lag]) } / (bits.size - lag)
            if (best == null || score > best.score) best = Repeat(lag, score)
        }
        return best
    }
}
