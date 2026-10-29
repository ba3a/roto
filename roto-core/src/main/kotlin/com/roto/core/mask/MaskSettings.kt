package com.roto.core.mask

import kotlinx.serialization.Serializable

/** The largest open or close radius: beyond it a figure loses its hands and feet. */
private const val MAX_RADIUS = 16

/**
 * How a clip's figure is told apart from its clean plate; stored per clip in `profile.json`.
 *
 * - [lightnessWeight] scales the lightness part of the OKLab difference, against a weight of 1 for
 *   the colour part. Below 1, a soft shadow — the wall a little darker, its colour unchanged — counts
 *   for less than a change of colour, and drops out of the mask.
 * - [low] and [high] are the [Hysteresis] thresholds on that weighted difference.
 * - [openRadius] removes specks and spurs up to about twice its size; [closeRadius] fills gaps of
 *   the same size. Zero turns either off.
 */
@Serializable
data class MaskSettings(
    val lightnessWeight: Double = 0.25,
    val low: Double = 0.045,
    val high: Double = 0.10,
    val openRadius: Int = 1,
    val closeRadius: Int = 2,
) {
    init {
        require(lightnessWeight in 0.0..1.0) { "lightnessWeight must be between 0 and 1, got $lightnessWeight" }
        require(low > 0.0) { "low must be positive, got $low" }
        require(high >= low) { "high ($high) must not be below low ($low)" }
        require(openRadius in 0..MAX_RADIUS) { "openRadius must be between 0 and $MAX_RADIUS, got $openRadius" }
        require(closeRadius in 0..MAX_RADIUS) { "closeRadius must be between 0 and $MAX_RADIUS, got $closeRadius" }
    }
}
