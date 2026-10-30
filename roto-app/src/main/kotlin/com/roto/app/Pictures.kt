package com.roto.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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

/** What a [PictureView] tells whoever is using it, in picture pixels. */
interface PictureGestures {
    fun press(x: Int, y: Int)

    /** The pointer, still down, is now at ([x], [y]), which may be off the picture. */
    fun extend(x: Int, y: Int)

    fun release()
}

/**
 * [picture] as large as fits on black, its pixels drawn square so the artist sees exactly what it
 * holds. [gestures] hears the pointer in picture pixels, whatever size it is shown at, and [overlay]
 * draws on top of it.
 */
@Composable
internal fun PictureView(
    picture: Raster?,
    gestures: PictureGestures,
    modifier: Modifier,
    overlay: DrawScope.(Fit) -> Unit = {},
) {
    val bitmap = remember(picture) { picture?.toImageBitmap() }
    val current by rememberUpdatedState(picture)
    Box(
        modifier.background(Color.Black).pointerInput(gestures) {
            val fit = { current?.let { Fit.of(size.width, size.height, it.width, it.height) } }
            awaitEachGesture {
                val down = awaitFirstDown()
                fit()?.let { gestures.press(it.pictureX(down.position.x), it.pictureY(down.position.y)) }
                drag(down.id) { change ->
                    fit()?.let { gestures.extend(it.pictureX(change.position.x), it.pictureY(change.position.y)) }
                    change.consume()
                }
                gestures.release()
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val image = bitmap ?: return@Canvas
            val fit = Fit.of(size.width.toInt(), size.height.toInt(), image.width, image.height)
            drawPixels(image, fit)
            overlay(fit)
        }
    }
}

/** [image] where [fit] puts it, its pixels square. */
internal fun DrawScope.drawPixels(image: ImageBitmap, fit: Fit) {
    drawImage(
        image,
        dstOffset = IntOffset(fit.left.toInt(), fit.top.toInt()),
        dstSize = IntSize(fit.width, fit.height),
        filterQuality = FilterQuality.None,
    )
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

    /** The box position of picture position [x]. */
    fun screenX(x: Double): Float = left + (x * scale).toFloat()

    /** The box position of picture position [y]. */
    fun screenY(y: Double): Float = top + (y * scale).toFloat()

    companion object {
        fun of(boxWidth: Int, boxHeight: Int, pictureWidth: Int, pictureHeight: Int): Fit {
            val scale = minOf(boxWidth.toFloat() / pictureWidth, boxHeight.toFloat() / pictureHeight)
            val width = (pictureWidth * scale).toInt()
            val height = (pictureHeight * scale).toInt()
            return Fit(scale, (boxWidth - width) / 2f, (boxHeight - height) / 2f, width, height)
        }
    }
}
