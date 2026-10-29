package com.roto.core.mask

import com.roto.core.image.Mask

/**
 * Connected regions of a [Mask]. Foreground connects through all eight neighbours and background
 * through the four edge neighbours — the usual pairing, so a diagonal chain of foreground pixels is
 * one region and background never leaks through it.
 */
object ConnectedComponents {

    private const val OUTSIDE = 1

    /**
     * Only the largest foreground region: the figure, without stray blobs elsewhere in the frame. Of
     * regions equally large, the one reached first in row-major order wins.
     */
    fun largest(mask: Mask): Mask {
        val bits = mask.toBooleanArray()
        val labels = IntArray(bits.size)
        val flood = Flood(mask.width, mask.height, Connectivity.EIGHT)
        var best = 0
        var bestSize = 0
        var label = 0
        for (offset in bits.indices) {
            if (!bits[offset] || labels[offset] != 0) continue
            label++
            val size = flood.fill(intArrayOf(offset), labels, label) { bits[it] }
            if (size > bestSize) {
                best = label
                bestSize = size
            }
        }
        val kept = BooleanArray(bits.size) { best != 0 && labels[it] == best }
        return Mask.fromBooleanArray(mask.width, mask.height, kept)
    }

    /** The mask with every hole filled: background the frame's border cannot reach becomes foreground. */
    fun fillHoles(mask: Mask): Mask {
        val bits = mask.toBooleanArray()
        val width = mask.width
        val height = mask.height
        val border = bits.indices.filter { offset ->
            val x = offset % width
            val y = offset / width
            !bits[offset] && (x == 0 || y == 0 || x == width - 1 || y == height - 1)
        }
        val outside = IntArray(bits.size)
        Flood(width, height, Connectivity.FOUR).fill(border.toIntArray(), outside, OUTSIDE) { !bits[it] }
        return Mask.fromBooleanArray(width, height, BooleanArray(bits.size) { bits[it] || outside[it] != OUTSIDE })
    }
}
