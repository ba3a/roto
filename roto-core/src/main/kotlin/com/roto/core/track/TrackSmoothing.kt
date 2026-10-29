package com.roto.core.track

/** Gap filling and smoothing of a per-frame track; plain arithmetic in a fixed order, so exact. */
object TrackSmoothing {

    /**
     * [values] with every null filled: linearly between the known values either side of a gap, and
     * with the nearest known value before the first or after the last. Throws when none is known.
     */
    fun fillGaps(values: List<Double?>): List<Double> {
        val known = values.withIndex().mapNotNull { (index, value) -> value?.let { index to it } }
        require(known.isNotEmpty()) { "No frame has a value to fill the gaps from" }
        return values.indices.map { index -> values[index] ?: between(known, index) }
    }

    /**
     * The centred moving average of [values] over [window] frames. Near either end the window
     * shrinks evenly on both sides, so a figure moving at a steady speed is followed exactly.
     */
    fun movingAverage(values: List<Double>, window: Int): List<Double> {
        require(window >= 1 && window % 2 == 1) { "Window must be a positive odd number, got $window" }
        val radius = window / 2
        return values.indices.map { index ->
            val half = minOf(radius, index, values.lastIndex - index)
            var sum = 0.0
            for (i in index - half..index + half) sum += values[i]
            sum / (2 * half + 1)
        }
    }

    /** The value at [index], a gap, from the [known] values around it. */
    private fun between(known: List<Pair<Int, Double>>, index: Int): Double {
        val before = known.lastOrNull { it.first < index }
        val after = known.firstOrNull { it.first > index }
        return when {
            before == null -> checkNotNull(after).second
            after == null -> before.second
            else -> {
                val fraction = (index - before.first).toDouble() / (after.first - before.first)
                before.second + (after.second - before.second) * fraction
            }
        }
    }
}
