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

    /** Which key frame is showing at [frame], or null outside them all. */
    fun keyFrameAt(frame: Int): Int? = boundaries.zipWithNext().indexOfFirst { (from, to) -> frame in from until to }
        .takeIf { it >= 0 }

    /** Whether a boundary can go: at least one key frame must stay. */
    val canRemove: Boolean get() = boundaries.size > 2

    /**
     * These key frames with boundary [index] moved as near [frame] as the rules allow: it stays
     * between its neighbours, and inside a clip of [frameCount] frames. The last boundary may reach
     * [frameCount] itself, since it is where the last key frame ends, not a frame drawn.
     */
    fun moved(index: Int, frame: Int, frameCount: Int): KeyFrames {
        require(index in boundaries.indices) { "No boundary $index in $boundaries" }
        val lowest = boundaries.getOrNull(index - 1)?.plus(1) ?: 0
        val highest = boundaries.getOrNull(index + 1)?.minus(1) ?: frameCount
        val placed = frame.coerceIn(lowest, highest)
        return KeyFrames(boundaries.mapIndexed { at, boundary -> if (at == index) placed else boundary })
    }

    /** These key frames with a boundary at [frame] too; one already there changes nothing. */
    fun added(frame: Int, frameCount: Int): KeyFrames {
        require(frame in 0..frameCount) { "Frame $frame is outside the clip's $frameCount frames" }
        return KeyFrames((boundaries + frame).distinct().sorted())
    }

    /** These key frames without boundary [index]: the key frame before it lasts until the next one. */
    fun removed(index: Int): KeyFrames {
        require(index in boundaries.indices) { "No boundary $index in $boundaries" }
        return KeyFrames(boundaries.filterIndexed { at, _ -> at != index })
    }

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
