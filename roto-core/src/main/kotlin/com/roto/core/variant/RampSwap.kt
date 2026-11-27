package com.roto.core.variant

import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.palette.RampSet

/**
 * The colour map of a variant's ramp swaps, applied to the base's art.
 *
 * Every swap reads the original colours, so swaps never chain: swapping `a` to `b` and `b` to `a`
 * exchanges them. A colour at step `i` of a swapped `from` ramp of length `n` becomes step `j` of the
 * `to` ramp of length `m`: `j = i` when `n == m`, else `j = (i * (m - 1) + (n - 1) / 2) / (n - 1)`
 * with integer division, and `j = 0` when `n = 1`. Colours are compared exactly.
 *
 * @param ramps the project's ramps
 * @param swaps the ramp swaps, each source ramp once
 * @param outline the colour the project's outline colour becomes, or null to keep it; it has no effect
 *   when [ramps] has no outline colour, since each ramp's darkest step is then the outline and swaps with it
 */
class RampSwap(ramps: RampSet, swaps: List<RampSwapPair>, outline: Int? = null) {
    private val map: Map<Int, Int>

    init {
        val built = LinkedHashMap<Int, Int>()
        swaps.forEach { swap ->
            val from = requireNotNull(ramps.ramp(swap.from)) { "Ramp '${swap.from}' is not in ramps.json" }
            val to = requireNotNull(ramps.ramp(swap.to)) { "Ramp '${swap.to}' is not in ramps.json" }
            from.colors.forEachIndexed { i, colour ->
                built.putIfAbsent(colour, to.colors[step(i, from.colors.size, to.colors.size)])
            }
        }
        val outlineColour = ramps.outline
        if (outline != null && outlineColour != null) {
            built[outlineColour] = outline
        }
        map = built
    }

    /** The colour [argb] becomes: its mapped colour, or itself when it is transparent or in no swap. */
    fun map(argb: Int): Int = if (Argb.alpha(argb) == Argb.OPAQUE_ALPHA) map[argb] ?: argb else argb

    /** A new raster with every pixel passed through [map]; [raster] is not changed. */
    fun apply(raster: Raster): Raster = Raster.of(raster.width, raster.height) { x, y -> map(raster[x, y]) }

    private fun step(i: Int, n: Int, m: Int): Int = when {
        n == 1 -> 0
        n == m -> i
        else -> (i * (m - 1) + (n - 1) / 2) / (n - 1)
    }
}
