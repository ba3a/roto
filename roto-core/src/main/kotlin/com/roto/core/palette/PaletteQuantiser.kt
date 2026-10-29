package com.roto.core.palette

import com.roto.core.color.OkLab
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster

/**
 * Maps a raster onto a [Palette]: each pixel at least half opaque takes the palette colour nearest
 * to it in OKLab, the first on a tie, written as a palette label (index plus one); every other pixel
 * is `0`, background. How a reference photo becomes something an indexed sprite can hold.
 */
object PaletteQuantiser {

    private const val HALF_ALPHA = 128

    fun labels(raster: Raster, palette: Palette): IndexedRaster {
        val swatches = palette.colors.map { OkLab.fromArgb(it) }
        // Footage repeats colours; remembering each answer only saves time, it never changes one.
        val known = HashMap<Int, Int>()
        return IndexedRaster.of(raster.width, raster.height) { x, y ->
            val argb = raster[x, y]
            if (Argb.alpha(argb) < HALF_ALPHA) {
                0
            } else {
                known.getOrPut(argb) { nearest(OkLab.fromArgb(argb), swatches) + 1 }
            }
        }
    }

    private fun nearest(color: OkLab, swatches: List<OkLab>): Int {
        var best = 0
        var bestDistance = Double.MAX_VALUE
        swatches.forEachIndexed { index, swatch ->
            val dl = color.l - swatch.l
            val da = color.a - swatch.a
            val db = color.b - swatch.b
            val distance = dl * dl + da * da + db * db
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }
}
