package com.roto.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.roto.core.image.Argb
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import java.awt.image.BufferedImage

/** What the mask preview lays over everything that is not figure. */
private val OUTSIDE_FIGURE = Argb.of(150, 0, 40, 170)

/** Shows a [Raster] on screen. */
internal fun Raster.toImageBitmap(): ImageBitmap {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, width, height, toArgbArray(), 0, width)
    return image.toComposeImageBitmap()
}

/** This mask as a veil over a frame: the figure clear, the rest tinted. */
internal fun Mask.toVeil(): ImageBitmap {
    val pixels = toBooleanArray()
    return Raster.fromArgbArray(
        width,
        height,
        IntArray(pixels.size) { if (pixels[it]) Argb.TRANSPARENT else OUTSIDE_FIGURE },
    ).toImageBitmap()
}

/**
 * How a picture of [pictureWidth] by [pictureHeight] sits in a box: as large as fits, centred, whole
 * pixels. [scale] is screen pixels per picture pixel; ([left], [top]) is its corner in the box.
 */
internal data class Fit(val scale: Float, val left: Float, val top: Float, val width: Int, val height: Int) {

    /** The picture pixel column under box position [x]. */
    fun pictureX(x: Float): Int = ((x - left) / scale).toInt()

    /** The picture pixel row under box position [y]. */
    fun pictureY(y: Float): Int = ((y - top) / scale).toInt()

    companion object {
        fun of(boxWidth: Int, boxHeight: Int, pictureWidth: Int, pictureHeight: Int): Fit {
            val scale = minOf(boxWidth.toFloat() / pictureWidth, boxHeight.toFloat() / pictureHeight)
            val width = (pictureWidth * scale).toInt()
            val height = (pictureHeight * scale).toInt()
            return Fit(scale, (boxWidth - width) / 2f, (boxHeight - height) / 2f, width, height)
        }
    }
}
