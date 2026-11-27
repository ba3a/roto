package com.roto.core.review

/**
 * Which frame shows at a time. Integer arithmetic only; the app feeds elapsed time in, so no wall
 * clock is read here.
 */
object FlipbookClock {

    /**
     * The index of the frame showing [elapsedMs] after the start, for frames shown for [durations]
     * milliseconds each. A frame starts exactly at the sum of the durations before it. With [loop] the
     * time wraps over the total; without it, time past the end stays on the last frame. A negative time
     * counts as zero.
     */
    fun frameAt(elapsedMs: Long, durations: List<Int>, loop: Boolean): Int {
        require(durations.isNotEmpty()) { "A flipbook needs at least one frame" }
        require(durations.all { it > 0 }) { "Frame durations must be positive" }
        val total = durations.sumOf { it.toLong() }
        val time = when {
            elapsedMs <= 0 -> 0L
            loop -> elapsedMs % total
            else -> minOf(elapsedMs, total - 1)
        }
        var start = 0L
        for ((index, duration) in durations.withIndex()) {
            start += duration
            if (time < start) return index
        }
        return durations.lastIndex
    }
}
