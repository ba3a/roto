package com.roto.core.mask

import com.roto.core.image.Mask

/**
 * Two-level thresholding. A pixel at or above `high` is certainly foreground; one at or above `low`
 * is foreground only when it connects, through all eight neighbours, to a certain one. So the soft
 * edge of the figure is kept while equally faint noise elsewhere in the frame is not.
 */
object Hysteresis {

    /** Thresholds [values], a row-major `width × height` map such as a colour difference. */
    fun threshold(width: Int, height: Int, values: DoubleArray, low: Double, high: Double): Mask {
        require(values.size == width * height) { "Expected ${width * height} values, got ${values.size}" }
        require(low <= high) { "Low threshold $low must not exceed high threshold $high" }
        val labels = IntArray(values.size)
        val certain = values.indices.filter { values[it] >= high }.toIntArray()
        Flood(width, height, Connectivity.EIGHT).fill(certain, labels, FOREGROUND) { values[it] >= low }
        return Mask.fromBooleanArray(width, height, BooleanArray(values.size) { labels[it] == FOREGROUND })
    }

    private const val FOREGROUND = 1
}
