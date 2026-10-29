package com.roto.core.track

import com.roto.core.image.Argb
import com.roto.core.image.Mask
import com.roto.core.image.Raster

/**
 * Moves an image sideways by whole pixels, keeping its size: what was at `x` lands at `x + dx`, and
 * what moves in from beyond the edge is empty. Whole pixels only, so stabilising never resamples or
 * blurs a frame.
 */
object HorizontalShift {

    fun shift(raster: Raster, dx: Int): Raster =
        Raster.of(raster.width, raster.height) { x, y ->
            if (raster.contains(x - dx, y)) raster[x - dx, y] else Argb.TRANSPARENT
        }

    fun shift(mask: Mask, dx: Int): Mask =
        Mask.of(mask.width, mask.height) { x, y -> mask.contains(x - dx, y) && mask[x - dx, y] }
}
