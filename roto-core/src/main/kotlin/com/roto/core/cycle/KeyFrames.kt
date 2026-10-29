package com.roto.core.cycle

import com.roto.core.video.FrameRate

/**
 * A clip's key frames as [boundaries]: frame indices in rising order, each but the last a key frame
 * that lasts until the next. For a looping clip the last boundary is where the cycle starts over —
 * the frame showing the first key frame's pose again, not drawn itself — so moving it changes the
 * length of the loop. Marker spacing is frame duration, nothing else.
 */
class KeyFrames(val boundaries: List<Int>) {
    init {
        require(boundaries.size >= 2) { "Key frames need at least two boundaries, got $boundaries" }
        require(boundaries.first() >= 0) { "Key frames must not start before frame 0: $boundaries" }
        require(boundaries.zipWithNext().all { (a, b) -> a < b }) { "Key frame boundaries must rise: $boundaries" }
    }

    /** The frames drawn: every boundary but the last. */
    val frames: List<Int> get() = boundaries.dropLast(1)

    /** How long each key frame shows, in whole milliseconds at [frameRate]; exact, without drift. */
    fun durationsMs(frameRate: FrameRate): List<Long> =
        boundaries.zipWithNext { from, to -> frameRate.startMs(to) - frameRate.startMs(from) }

    override fun equals(other: Any?): Boolean = other is KeyFrames && boundaries == other.boundaries

    override fun hashCode(): Int = boundaries.hashCode()

    override fun toString(): String = "KeyFrames($boundaries)"

    companion object {
        /**
         * [count] key frames spread evenly over [length] frames from [start], boundaries rounded to
         * the nearest frame; fewer when [length] holds fewer frames than [count].
         */
        fun even(start: Int, length: Int, count: Int): KeyFrames {
            require(length >= 1 && count >= 1) { "Need a length and a count of at least 1, got $length and $count" }
            val keys = minOf(count, length)
            return KeyFrames((0..keys).map { k -> start + (2 * k * length + keys) / (2 * keys) })
        }
    }
}
