package com.roto.core.cycle

import com.roto.core.image.Mask

/**
 * A mask packed 64 pixels to a word, row-major, so the overlap of two masks costs a few thousand
 * word operations instead of a pass over every pixel. Comparing every frame of a clip with every
 * other at every lag stays quick that way.
 */
internal class MaskBits private constructor(private val words: LongArray) {

    /** Intersection over union with [other], `0` to `1`; two empty masks overlap not at all. */
    fun overlap(other: MaskBits): Double {
        require(words.size == other.words.size) { "Masks differ in size" }
        var both = 0
        var either = 0
        for (i in words.indices) {
            both += java.lang.Long.bitCount(words[i] and other.words[i])
            either += java.lang.Long.bitCount(words[i] or other.words[i])
        }
        return if (either == 0) 0.0 else both.toDouble() / either
    }

    companion object {
        fun of(mask: Mask): MaskBits {
            val bits = mask.toBooleanArray()
            val words = LongArray((bits.size + Long.SIZE_BITS - 1) / Long.SIZE_BITS)
            for (offset in bits.indices) {
                if (bits[offset]) {
                    val word = offset / Long.SIZE_BITS
                    words[word] = words[word] or (1L shl (offset % Long.SIZE_BITS))
                }
            }
            return MaskBits(words)
        }
    }
}
