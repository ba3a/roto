package com.roto.core.shade

import com.roto.core.color.OkLab
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster

/**
 * Fits a clip's [ClipShading]: for each zone, the [ShadeSettings] percentiles of the OKLab
 * lightness of all its pixels across every frame. Percentiles are nearest-rank on the sorted
 * lightnesses, so the fit is exact and the same on every machine.
 */
object ShadingFit {

    private const val FULL = 100.0

    /**
     * The shading of [frames], labelled by zone in [zones], where zone `i + 1` has [steps]`[i]` ramp
     * steps. A zone no pixel belongs to gets an empty range, and so its middle step.
     */
    fun fit(frames: List<Raster>, zones: List<IndexedRaster>, steps: List<Int>, settings: ShadeSettings): ClipShading {
        require(frames.size == zones.size) { "${frames.size} frames but ${zones.size} zone maps" }
        val lightness = List(steps.size) { Lightnesses() }
        frames.forEachIndexed { index, frame ->
            val labels = zones[index]
            require(labels.width == frame.width && labels.height == frame.height) { "Zone map $index differs in size" }
            for (y in 0 until frame.height) {
                for (x in 0 until frame.width) {
                    val zone = labels[x, y]
                    if (zone != 0) lightness[zone - 1].add(OkLab.fromArgb(frame[x, y]).l)
                }
            }
        }
        return ClipShading(
            steps.indices.map { zone ->
                val sorted = lightness[zone].sorted()
                val low = if (sorted.isEmpty()) 0.0 else percentile(sorted, settings.lowPercentile)
                val high = if (sorted.isEmpty()) 0.0 else percentile(sorted, settings.highPercentile)
                ZoneShading(low, high, steps[zone])
            },
        )
    }

    private fun percentile(sorted: DoubleArray, percent: Double): Double =
        sorted[(percent * (sorted.size - 1) / FULL).toInt()]

    /** A growable array of lightnesses, unboxed: a clip holds millions of zone pixels. */
    private class Lightnesses {
        private var values = DoubleArray(INITIAL_CAPACITY)
        private var size = 0

        fun add(value: Double) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        fun sorted(): DoubleArray = values.copyOf(size).also { it.sort() }

        private companion object {
            const val INITIAL_CAPACITY = 1024
        }
    }
}
