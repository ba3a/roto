package com.roto.core.model

import com.roto.core.cycle.CycleAnalysis
import com.roto.core.cycle.KeyFrames

/**
 * Key frames for a clip taken from a [ModelClip]'s timing: one key frame per model frame, each
 * boundary at the model's relative time along the clip's detected cycle. A cycle whose length differs
 * from the model's total duration scales the boundaries proportionally; they round to frame starts
 * and stay strictly rising.
 */
object ModelTiming {
    /**
     * The proposal for [model] over the cycle in [cycle]: from its first to its last proposed marker.
     * Fails with a [ModelException] when the cycle holds fewer frames than the model has.
     */
    fun propose(model: ModelClip, cycle: CycleAnalysis): KeyFrames {
        val count = model.frames.size
        val start = cycle.markers.first()
        val length = cycle.markers.last() - start
        if (length < count) {
            throw ModelException(
                "The cycle is $length frames long, too short for the model ${model.key}'s $count frames; " +
                    "each key frame needs a frame of its own",
            )
        }
        val total = model.frames.sumOf { it.durationMs.toLong() }
        var elapsed = 0L
        val raw = listOf(0L) + model.frames.map { frame ->
            elapsed += frame.durationMs
            elapsed
        }
        val rounded = raw.map { start + ((2 * it * length + total) / (2 * total)).toInt() }
        val end = start + length
        val rising = rounded.toMutableList()
        for (i in 1..count) rising[i] = maxOf(rising[i], rising[i - 1] + 1)
        for (i in count downTo 0) rising[i] = minOf(rising[i], end - (count - i))
        return KeyFrames(rising)
    }
}
