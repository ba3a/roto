package com.roto.core.publish

import com.roto.core.image.Raster

/**
 * Mirrors a published frame left to right across the whole canvas. The canvas is centred on the
 * figure's root, so the mirrored figure stands where the original did, on the same ground row.
 */
object Mirror {

    fun flip(raster: Raster): Raster =
        Raster.of(raster.width, raster.height) { x, y -> raster[raster.width - 1 - x, y] }
}
