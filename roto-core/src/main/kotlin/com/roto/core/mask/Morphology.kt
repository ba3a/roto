package com.roto.core.mask

import com.roto.core.image.Mask

/**
 * Binary morphology with a square structuring element of side `2 × radius + 1`. A square is
 * separable, so each operation runs along rows and then columns, with a sliding count that makes the
 * cost independent of the radius.
 *
 * Outside the mask is background. Opening is then the union of every square that fits inside the
 * foreground, so a thick figure cut off by the frame edge keeps its edge; closing works on the mask
 * padded by the radius and cropped back, so a figure near the edge is not glued to it.
 */
object Morphology {

    /** Opening, erosion then dilation: removes specks and spurs narrower than the square. */
    fun open(mask: Mask, radius: Int): Mask = dilate(erode(mask, radius), radius)

    /** Closing, dilation then erosion: fills gaps and notches narrower than the square. */
    fun close(mask: Mask, radius: Int): Mask {
        require(radius >= 0) { "Radius must not be negative, got $radius" }
        val padded = Mask.of(mask.width + 2 * radius, mask.height + 2 * radius) { x, y ->
            mask.contains(x - radius, y - radius) && mask[x - radius, y - radius]
        }
        val closed = erode(dilate(padded, radius), radius)
        return Mask.of(mask.width, mask.height) { x, y -> closed[x + radius, y + radius] }
    }

    /** A pixel stays foreground only when the whole square around it is foreground. */
    fun erode(mask: Mask, radius: Int): Mask = apply(mask, radius) { inWindow -> inWindow == 2 * radius + 1 }

    /** A pixel becomes foreground when anything in the square around it is foreground. */
    fun dilate(mask: Mask, radius: Int): Mask = apply(mask, radius) { inWindow -> inWindow > 0 }

    private fun apply(mask: Mask, radius: Int, keep: (inWindow: Int) -> Boolean): Mask {
        require(radius >= 0) { "Radius must not be negative, got $radius" }
        if (radius == 0) return mask.copy()
        val width = mask.width
        val height = mask.height
        val alongRows = Lines(count = height, length = width, stride = 1, step = width)
        val alongColumns = Lines(count = width, length = height, stride = width, step = 1)
        val both = pass(pass(mask.toBooleanArray(), alongRows, radius, keep), alongColumns, radius, keep)
        return Mask.fromBooleanArray(width, height, both)
    }

    /** Counts the foreground in the window of [radius] around every pixel of [lines] and applies [keep]. */
    private fun pass(bits: BooleanArray, lines: Lines, radius: Int, keep: (inWindow: Int) -> Boolean): BooleanArray {
        val result = BooleanArray(bits.size)
        val prefix = IntArray(lines.length + 1)
        for (line in 0 until lines.count) {
            val start = line * lines.step
            for (i in 0 until lines.length) {
                prefix[i + 1] = prefix[i] + if (bits[start + i * lines.stride]) 1 else 0
            }
            for (i in 0 until lines.length) {
                val inWindow = prefix[minOf(lines.length, i + radius + 1)] - prefix[maxOf(0, i - radius)]
                result[start + i * lines.stride] = keep(inWindow)
            }
        }
        return result
    }

    /** [count] lines of [length] pixels each; pixel `i` of line `n` sits at `n * step + i * stride`. */
    private class Lines(val count: Int, val length: Int, val stride: Int, val step: Int)
}
