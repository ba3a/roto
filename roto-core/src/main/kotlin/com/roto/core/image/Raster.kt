package com.roto.core.image

/**
 * A rectangular grid of [Argb] pixels, row-major from the top-left corner.
 *
 * The one image type every pipeline stage reads and writes. Equality is by size and pixel content,
 * so two stages that produce the same image produce equal rasters — the determinism tests rely on it.
 * Mutable through [set] so brush fixes and per-pixel stages can fill a raster in place; take a [copy]
 * before handing one raster to code that must not see later writes.
 *
 * @property width the width in pixels
 * @property height the height in pixels
 */
class Raster private constructor(
    val width: Int,
    val height: Int,
    private val pixels: IntArray,
) {
    init {
        require(pixels.size == GridBounds.area(KIND, width, height)) {
            "Expected ${width * height} pixels for ${width}x$height, got ${pixels.size}"
        }
    }

    /** A fully transparent raster. */
    constructor(width: Int, height: Int) : this(width, height, IntArray(GridBounds.area(KIND, width, height)))

    /** The pixel at ([x], [y]) as an [Argb] value. */
    operator fun get(x: Int, y: Int): Int = pixels[GridBounds.offset(width, height, x, y)]

    /** Sets the pixel at ([x], [y]) to [argb]. */
    operator fun set(x: Int, y: Int, argb: Int) {
        pixels[GridBounds.offset(width, height, x, y)] = argb
    }

    /** Whether ([x], [y]) lies inside the grid. */
    fun contains(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    /** A copy; later writes to either one do not show in the other. */
    fun copy(): Raster = Raster(width, height, pixels.copyOf())

    /** The pixels as a fresh row-major array; writing to it does not change this raster. */
    fun toArgbArray(): IntArray = pixels.copyOf()

    /** Equal when the size and every pixel are. */
    override fun equals(other: Any?): Boolean =
        other is Raster && width == other.width && height == other.height && pixels.contentEquals(other.pixels)

    /** Consistent with [equals]. */
    override fun hashCode(): Int = 31 * (31 * width + height) + pixels.contentHashCode()

    /** The size, for messages and tests. */
    override fun toString(): String = "Raster(${width}x$height)"

    /** Creation of whole grids. */
    companion object {
        private const val KIND = "Raster"

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
    }
}
