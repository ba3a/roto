package com.roto.core.image

/**
 * A rectangular grid of [Argb] pixels, row-major from the top-left corner.
 *
 * The one image type every pipeline stage reads and writes. Equality is by size and pixel content,
 * so two stages that produce the same image produce equal rasters — the determinism tests rely on it.
 * Mutable through [set] so brush fixes and per-pixel stages can fill a raster in place; take a [copy]
 * before handing one raster to code that must not see later writes.
 */
class Raster private constructor(
    val width: Int,
    val height: Int,
    private val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "Raster must be at least 1x1, was ${width}x$height" }
        require(pixels.size == width * height) {
            "Expected ${width * height} pixels for ${width}x$height, got ${pixels.size}"
        }
    }

    /** A fully transparent raster. */
    constructor(width: Int, height: Int) : this(width, height, IntArray(checkedArea(width, height)))

    operator fun get(x: Int, y: Int): Int = pixels[indexOf(x, y)]

    operator fun set(x: Int, y: Int, argb: Int) {
        pixels[indexOf(x, y)] = argb
    }

    fun contains(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    fun copy(): Raster = Raster(width, height, pixels.copyOf())

    /** The pixels as a fresh row-major array; writing to it does not change this raster. */
    fun toArgbArray(): IntArray = pixels.copyOf()

    private fun indexOf(x: Int, y: Int): Int {
        if (!contains(x, y)) {
            throw IndexOutOfBoundsException("($x, $y) is outside ${width}x$height")
        }
        return y * width + x
    }

    override fun equals(other: Any?): Boolean =
        other is Raster && width == other.width && height == other.height && pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = 31 * (31 * width + height) + pixels.contentHashCode()

    override fun toString(): String = "Raster(${width}x$height)"

    companion object {
        /** A raster whose every pixel is [pixel] of its coordinates. */
        fun of(width: Int, height: Int, pixel: (x: Int, y: Int) -> Int): Raster {
            val raster = Raster(width, height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    raster[x, y] = pixel(x, y)
                }
            }
            return raster
        }

        /** A raster over a copy of [pixels], row-major from the top-left corner. */
        fun fromArgbArray(width: Int, height: Int, pixels: IntArray): Raster =
            Raster(width, height, pixels.copyOf())

        private fun checkedArea(width: Int, height: Int): Int {
            require(width > 0 && height > 0) { "Raster must be at least 1x1, was ${width}x$height" }
            return Math.multiplyExact(width, height)
        }
    }
}
