package com.roto.core.image

/** Size validation and row-major addressing shared by [Raster] and [IndexedRaster]. */
internal object GridBounds {

    /** `width * height`, after checking both are positive; [kind] names the grid in the message. */
    fun area(kind: String, width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "$kind must be at least 1x1, was ${width}x$height" }
        return Math.multiplyExact(width, height)
    }

    /** The row-major offset of ([x], [y]); throws instead of wrapping to the next row. */
    fun offset(width: Int, height: Int, x: Int, y: Int): Int {
        if (x !in 0 until width || y !in 0 until height) {
            throw IndexOutOfBoundsException("($x, $y) is outside ${width}x$height")
        }
        return y * width + x
    }
}
