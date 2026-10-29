package com.roto.core.shade

import kotlinx.serialization.Serializable
import kotlin.math.floor

/** Below this lightness range a zone is treated as flat: every pixel takes its ramp's middle step. */
private const val FLAT = 1e-6

/** How every zone of a clip is shaded, by zone: [zones] index `i` shades the zone labelled `i + 1`. */
@Serializable
data class ClipShading(val zones: List<ZoneShading>)

/**
 * How one zone is shaded: lightness [low] to [high] split evenly into [steps] steps, step 0 the
 * darkest. Fixed for the whole clip, so one pixel colour takes one step in every frame.
 */
@Serializable
data class ZoneShading(val low: Double, val high: Double, val steps: Int) {
    init {
        require(steps >= 1) { "A zone needs at least one step, got $steps" }
        require(high >= low) { "Lightness range must not run backwards: $low..$high" }
    }

    /** The step, `0..steps - 1`, a pixel of OKLab [lightness] takes; outside the range it clamps. */
    fun step(lightness: Double): Int {
        if (high - low < FLAT) return (steps - 1) / 2
        val step = floor((lightness - low) / (high - low) * steps).toInt()
        return step.coerceIn(0, steps - 1)
    }
}
