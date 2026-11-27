package com.roto.core.publish

import kotlinx.serialization.Serializable

private const val MIN_SPEED = 25
private const val MAX_SPEED = 400
private const val FULL_SPEED = 100

/**
 * How a clip's finished art is published: faster or slower, and with only some of its frames. Stored
 * per clip in `profile.json`; the handoff itself is never changed.
 *
 * [keep] is checked in two places, because the frame count lives in the handoff: here and in the
 * clip, only that no index is negative; [apply] checks the upper bound against the real frames and
 * fails with a [PublishException] naming the clip and the index.
 *
 * @property speedPercent playback speed from 25 to 400; 100 leaves durations as painted
 * @property keep the handoff frame indexes to publish, in order, repeats allowed; null for every frame
 */
@Serializable
data class Retime(
    val speedPercent: Int = FULL_SPEED,
    val keep: List<Int>? = null,
) {
    init {
        require(speedPercent in MIN_SPEED..MAX_SPEED) {
            "speedPercent must be from $MIN_SPEED to $MAX_SPEED, got $speedPercent"
        }
        require(keep == null || keep.isNotEmpty()) { "keep must list at least one frame" }
    }

    /** The first negative index of [keep], or null when there is none. */
    val negativeKeep: Int? get() = keep?.firstOrNull { it < 0 }

    /** The published duration of a frame painted for [durationMs]: scaled by the speed, half up, at least 1 ms. */
    fun duration(durationMs: Int): Int =
        maxOf(1L, (durationMs.toLong() * FULL_SPEED + speedPercent / 2) / speedPercent).toInt()

    /** [frames], the art of [clip], picked by [keep] and with durations scaled by the speed. */
    fun apply(frames: List<ArtFrame>, clip: String): List<ArtFrame> {
        val picked = keep?.map { index ->
            if (index !in frames.indices) {
                throw PublishException("Clip $clip keeps frame $index, but its handoff has ${frames.size} frames")
            }
            frames[index]
        } ?: frames
        return picked.map { ArtFrame(it.image, duration(it.durationMs)) }
    }
}

/** Picks of frames for a [Retime]'s `keep`. */
object FramePick {

    /** [count] indexes spread evenly over [total] frames, first and last included: `[0]` for one. */
    fun spread(total: Int, count: Int): List<Int> {
        require(total >= 1) { "total must be at least 1, got $total" }
        require(count >= 1) { "count must be at least 1, got $count" }
        if (count == 1) return listOf(0)
        return List(count) { it * (total - 1) / (count - 1) }
    }
}
