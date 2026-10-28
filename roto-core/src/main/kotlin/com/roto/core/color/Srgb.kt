package com.roto.core.color

import kotlin.math.roundToInt

/**
 * The sRGB transfer function: between 8-bit gamma-encoded channel values and linear light in `0..1`.
 *
 * Decoding goes through a 256-entry table, so the per-pixel hot path does no `pow`. Encoding uses
 * [StrictMath] so the result is bit-identical on every JVM and CPU.
 */
object Srgb {
    private const val MAX_8_BIT = 255
    private const val LINEAR_THRESHOLD = 0.04045
    private const val ENCODED_THRESHOLD = 0.0031308
    private const val LINEAR_SLOPE = 12.92
    private const val OFFSET = 0.055
    private const val SCALE = 1.055
    private const val GAMMA = 2.4

    private val decodeTable = DoubleArray(MAX_8_BIT + 1) { decode(it.toDouble() / MAX_8_BIT) }

    /** Linear light of an 8-bit encoded channel value `0..255`. */
    fun toLinear(channel: Int): Double = decodeTable[channel]

    /** The 8-bit encoded channel value closest to [linear]; values outside `0..1` clamp. */
    fun fromLinear(linear: Double): Int =
        (encode(linear.coerceIn(0.0, 1.0)) * MAX_8_BIT).roundToInt().coerceIn(0, MAX_8_BIT)

    private fun decode(encoded: Double): Double =
        if (encoded <= LINEAR_THRESHOLD) {
            encoded / LINEAR_SLOPE
        } else {
            StrictMath.pow((encoded + OFFSET) / SCALE, GAMMA)
        }

    private fun encode(linear: Double): Double =
        if (linear <= ENCODED_THRESHOLD) {
            linear * LINEAR_SLOPE
        } else {
            SCALE * StrictMath.pow(linear, 1.0 / GAMMA) - OFFSET
        }
}
