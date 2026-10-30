package com.roto.core.video

/**
 * Frames per second as the exact fraction [numerator] / [denominator] — `30000/1001` for NTSC-rate
 * phone video — so frame times are computed without floating-point drift.
 */
data class FrameRate(val numerator: Int, val denominator: Int) {
    init {
        require(numerator > 0 && denominator > 0) { "Frame rate must be positive, got $numerator/$denominator" }
    }

    /** When frame [index] of a clip starts, in whole milliseconds from the clip's first frame. */
    fun startMs(index: Int): Long {
        require(index >= 0) { "Frame index must not be negative, got $index" }
        return index.toLong() * MS_PER_SECOND * denominator / numerator
    }

    /** The frame whose start is nearest [ms]; the inverse of [startMs]. */
    fun nearestFrame(ms: Long): Int {
        require(ms >= 0) { "Time must not be negative, got $ms ms" }
        val frameMs = MS_PER_SECOND * denominator
        return Math.toIntExact((2 * ms * numerator + frameMs) / (2 * frameMs))
    }

    /** `numerator/denominator`, the form `ffmpeg` and `ffprobe` use. */
    override fun toString(): String = "$numerator/$denominator"

    companion object {
        private const val MS_PER_SECOND = 1000L

        /** Parses the `numerator/denominator` form; throws on anything else, including `0/0`. */
        fun parse(text: String): FrameRate {
            val numerator = text.substringBefore('/', missingDelimiterValue = "").toIntOrNull()
            val denominator = text.substringAfter('/', missingDelimiterValue = "").toIntOrNull()
            require(numerator != null && denominator != null) { "Frame rate '$text' is not numerator/denominator" }
            return FrameRate(numerator, denominator)
        }
    }
}
