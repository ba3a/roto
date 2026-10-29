package com.roto.core.image

/**
 * A rectangular grid of palette indices `0..255`, row-major from the top-left corner.
 *
 * The indexed counterpart of [Raster]: what the draft renderer produces and what an indexed `.ase`
 * cel stores. It knows nothing about the palette its indices point into. Equality is by size and
 * content, and it is mutable through [set] in the same way as [Raster].
 */
class IndexedRaster private constructor(
    val width: Int,
    val height: Int,
    private val indices: ByteArray,
) {
    init {
        require(indices.size == GridBounds.area(KIND, width, height)) {
            "Expected ${width * height} indices for ${width}x$height, got ${indices.size}"
        }
    }

    /** A raster filled with index `0`. */
    constructor(width: Int, height: Int) : this(width, height, ByteArray(GridBounds.area(KIND, width, height)))

    /** The palette index at ([x], [y]), `0..255`. */
    operator fun get(x: Int, y: Int): Int = indices[GridBounds.offset(width, height, x, y)].toInt() and BYTE_MASK

    operator fun set(x: Int, y: Int, index: Int) {
        require(index in 0..MAX_INDEX) { "Palette index out of range: $index" }
        indices[GridBounds.offset(width, height, x, y)] = index.toByte()
    }

    fun contains(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    fun copy(): IndexedRaster = IndexedRaster(width, height, indices.copyOf())

    /** The indices as a fresh row-major array of unsigned bytes; writing to it does not change this raster. */
    fun toByteArray(): ByteArray = indices.copyOf()

    /** The largest index used anywhere in the raster. */
    fun maxIndex(): Int = indices.maxOf { it.toInt() and BYTE_MASK }

    override fun equals(other: Any?): Boolean =
        other is IndexedRaster && width == other.width && height == other.height &&
            indices.contentEquals(other.indices)

    override fun hashCode(): Int = 31 * (31 * width + height) + indices.contentHashCode()

    override fun toString(): String = "IndexedRaster(${width}x$height)"

    companion object {
        const val MAX_INDEX: Int = 255

        private const val KIND = "IndexedRaster"
        private const val BYTE_MASK = 0xFF

        /** A raster whose every index is [index] of its coordinates. */
        fun of(width: Int, height: Int, index: (x: Int, y: Int) -> Int): IndexedRaster {
            val raster = IndexedRaster(width, height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    raster[x, y] = index(x, y)
                }
            }
            return raster
        }

        /** A raster over a copy of [indices], row-major unsigned bytes from the top-left corner. */
        fun fromByteArray(width: Int, height: Int, indices: ByteArray): IndexedRaster =
            IndexedRaster(width, height, indices.copyOf())
    }
}
