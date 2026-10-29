package com.roto.core.mask

import com.roto.core.color.OkLab
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import kotlin.math.sqrt

/**
 * Masks frames against one clean [plate]:
 *
 * 1. the [difference] of each pixel from the plate in OKLab, its lightness part weighted down;
 * 2. [Hysteresis] between the low and high thresholds;
 * 3. [Morphology] opening, then closing;
 * 4. only the [largest][ConnectedComponents.largest] region, the figure;
 * 5. its [holes filled][ConnectedComponents.fillHoles], so a shirt the colour of the wall stays solid.
 *
 * The plate's OKLab coordinates are computed once, here, and shared by every frame.
 */
class ForegroundMask(plate: Raster, private val settings: MaskSettings) {

    private val width = plate.width
    private val height = plate.height
    private val plateLab = plate.toArgbArray().map { OkLab.fromArgb(it) }

    fun of(frame: Raster): Mask {
        val certainAndEdge = Hysteresis.threshold(width, height, difference(frame), settings.low, settings.high)
        val smooth = Morphology.close(Morphology.open(certainAndEdge, settings.openRadius), settings.closeRadius)
        return ConnectedComponents.fillHoles(ConnectedComponents.largest(smooth))
    }

    /** Each pixel's weighted OKLab distance from the plate, row-major. */
    fun difference(frame: Raster): DoubleArray {
        require(frame.width == width && frame.height == height) {
            "Frame is ${frame.width}x${frame.height}, the plate ${width}x$height"
        }
        val pixels = frame.toArgbArray()
        return DoubleArray(pixels.size) { offset ->
            val pixel = OkLab.fromArgb(pixels[offset])
            val background = plateLab[offset]
            val lightness = settings.lightnessWeight * (pixel.l - background.l)
            val a = pixel.a - background.a
            val b = pixel.b - background.b
            sqrt(lightness * lightness + a * a + b * b)
        }
    }
}
