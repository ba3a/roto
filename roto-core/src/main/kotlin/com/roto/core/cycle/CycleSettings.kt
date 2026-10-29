package com.roto.core.cycle

import kotlinx.serialization.Serializable

/** The most key frames one cycle may be proposed with. */
private const val MAX_MARKERS = 64

/**
 * How a clip's cycle is found and its key frames proposed; stored per clip in `profile.json`.
 *
 * - [minPeriodMs] and [maxPeriodMs] bound how often the silhouette may repeat.
 * - [repeatsPerCycle] is how many times the silhouette repeats in one animation cycle. A walk or a
 *   run repeats twice: halfway through, the legs have swapped and the outline looks the same, though
 *   the near and far leg have not. One for anything whose outline repeats only once.
 * - [contactBand] is the bottom fraction of the figure's height its leg spread is measured in.
 * - [keyFrames] is how many key frames are proposed per cycle, or across a clip that does not loop.
 */
@Serializable
data class CycleSettings(
    val minPeriodMs: Int = 300,
    val maxPeriodMs: Int = 2500,
    val repeatsPerCycle: Int = 1,
    val contactBand: Double = 0.15,
    val keyFrames: Int = 8,
) {
    init {
        require(minPeriodMs > 0 && maxPeriodMs >= minPeriodMs) {
            "Period bounds must satisfy 0 < min <= max, got $minPeriodMs..$maxPeriodMs ms"
        }
        require(repeatsPerCycle >= 1) { "repeatsPerCycle must be at least 1, got $repeatsPerCycle" }
        require(contactBand > 0.0 && contactBand <= 1.0) { "contactBand must be in (0, 1], got $contactBand" }
        require(keyFrames in 1..MAX_MARKERS) { "keyFrames must be between 1 and $MAX_MARKERS, got $keyFrames" }
    }
}
