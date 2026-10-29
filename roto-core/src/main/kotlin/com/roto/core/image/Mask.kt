package com.roto.core.image

/**
 * A rectangular grid of foreground (`true`) and background (`false`) pixels, row-major from the
 * top-left corner: which pixels of a frame belong to the figure.
 *
 * The binary counterpart of [Raster]. Equality is by size and content, and it is mutable through
 * [set] in the same way, so brush fixes can paint into a copy.
 */
class Mask private constructor(
    val width: Int,
    val height: Int,
    private val bits: BooleanArray,
) {
    init {
        require(bits.size == GridBounds.area(KIND, width, height)) {
            "Expected ${width * height} pixels for ${width}x$height, got ${bits.size}"
        }
    }

    /** An all-background mask. */
    constructor(width: Int, height: Int) : this(width, height, BooleanArray(GridBounds.area(KIND, width, height)))

    operator fun get(x: Int, y: Int): Boolean = bits[GridBounds.offset(width, height, x, y)]

    operator fun set(x: Int, y: Int, foreground: Boolean) {
        bits[GridBounds.offset(width, height, x, y)] = foreground
    }

    fun contains(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    fun copy(): Mask = Mask(width, height, bits.copyOf())

    /** How many pixels are foreground. */
    fun count(): Int = bits.count { it }

    /** The pixels as a fresh row-major array; writing to it does not change this mask. */
    fun toBooleanArray(): BooleanArray = bits.copyOf()

    /** Foreground as opaque white, background as transparent: how a mask is stored and previewed. */
    fun toRaster(): Raster =
        Raster.fromArgbArray(width, height, IntArray(bits.size) { if (bits[it]) FOREGROUND else Argb.TRANSPARENT })

    override fun equals(other: Any?): Boolean =
        other is Mask && width == other.width && height == other.height && bits.contentEquals(other.bits)

    override fun hashCode(): Int = 31 * (31 * width + height) + bits.contentHashCode()

    override fun toString(): String = "Mask(${width}x$height, ${count()} foreground)"

    companion object {
        private const val KIND = "Mask"
        private const val FOREGROUND = -1
        private const val HALF_ALPHA = 128

        /** A mask whose every pixel is [foreground] of its coordinates. */
        fun of(width: Int, height: Int, foreground: (x: Int, y: Int) -> Boolean): Mask {
            val mask = Mask(width, height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    mask[x, y] = foreground(x, y)
                }
            }
            return mask
        }

        /** A mask over a copy of [bits], row-major from the top-left corner. */
        fun fromBooleanArray(width: Int, height: Int, bits: BooleanArray): Mask = Mask(width, height, bits.copyOf())

        /** The mask [toRaster] stored: a pixel at least half opaque is foreground. */
        fun fromRaster(raster: Raster): Mask {
            val pixels = raster.toArgbArray()
            return Mask(raster.width, raster.height, BooleanArray(pixels.size) { Argb.alpha(pixels[it]) >= HALF_ALPHA })
        }
    }
}
