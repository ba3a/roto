package com.roto.core.shade

import kotlinx.serialization.Serializable

/** Percentiles run from 0 to this. */
private const val FULL = 100.0

/**
 * How a clip's zones are shaded; stored per clip in `profile.json`. Each zone's lightness range runs
 * from its [lowPercentile] to its [highPercentile] over every frame of the clip, and is split evenly
 * into the steps of its ramp. Trimming the extremes keeps a few specular highlights or deep creases
 * from stretching the range and flattening everything else into the middle steps.
 */
@Serializable
data class ShadeSettings(
    val lowPercentile: Double = 5.0,
    val highPercentile: Double = 95.0,
) {
    init {
        require(lowPercentile >= 0.0 && lowPercentile < highPercentile && highPercentile <= FULL) {
            "Percentiles must satisfy 0 <= low < high <= 100, got $lowPercentile..$highPercentile"
        }
    }
}
