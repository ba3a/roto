package com.roto.core.color

import com.roto.core.image.Argb
import kotlin.math.sqrt

/**
 * A colour in Björn Ottosson's OKLab space: [l] is perceived lightness (`0` black to `1` white), [a]
 * runs green to red and [b] blue to yellow. Euclidean distance approximates perceived difference,
 * which is why masking, zone classification and shading all compare colours here rather than in sRGB.
 *
 * Alpha is not a colour coordinate: [fromArgb] ignores it and [toArgb] takes it separately.
 *
 * Every step uses [StrictMath] or plain arithmetic, so a conversion is bit-identical on every JVM and
 * CPU — the pipeline's byte-identical output depends on it.
 */
data class OkLab(val l: Double, val a: Double, val b: Double) {

    /** Colourfulness: distance from the neutral axis. Zero for grays. */
    val chroma: Double get() = sqrt(a * a + b * b)

    /** The nearest 8-bit sRGB pixel; colours outside the sRGB gamut clamp channel by channel. */
    fun toArgb(alpha: Int = Argb.OPAQUE_ALPHA): Int {
        val lCone = cube(LAB_TO_LMS.first.dot(l, a, b))
        val mCone = cube(LAB_TO_LMS.second.dot(l, a, b))
        val sCone = cube(LAB_TO_LMS.third.dot(l, a, b))
        return Argb.of(
            red = Srgb.fromLinear(LMS_TO_LINEAR.first.dot(lCone, mCone, sCone)),
            green = Srgb.fromLinear(LMS_TO_LINEAR.second.dot(lCone, mCone, sCone)),
            blue = Srgb.fromLinear(LMS_TO_LINEAR.third.dot(lCone, mCone, sCone)),
            alpha = alpha,
        )
    }

    companion object {
        // Matrices from Ottosson, "A perceptual color space for image processing" (2020).
        private val LINEAR_TO_LMS = Matrix3(
            Row(0.4122214708, 0.5363325363, 0.0514459929),
            Row(0.2119034982, 0.6806995451, 0.1073969566),
            Row(0.0883024619, 0.2817188376, 0.6299787005),
        )
        private val LMS_TO_LAB = Matrix3(
            Row(0.2104542553, 0.7936177850, -0.0040720468),
            Row(1.9779984951, -2.4285922050, 0.4505937099),
            Row(0.0259040371, 0.7827717662, -0.8086757660),
        )
        private val LAB_TO_LMS = Matrix3(
            Row(1.0, 0.3963377774, 0.2158037573),
            Row(1.0, -0.1055613458, -0.0638541728),
            Row(1.0, -0.0894841775, -1.2914855480),
        )
        private val LMS_TO_LINEAR = Matrix3(
            Row(4.0767416621, -3.3077115913, 0.2309699292),
            Row(-1.2684380046, 2.6097574011, -0.3413193965),
            Row(-0.0041960863, -0.7034186147, 1.7076147010),
        )

        /** The OKLab coordinates of an sRGB pixel; its alpha is ignored. */
        fun fromArgb(argb: Int): OkLab {
            val red = Srgb.toLinear(Argb.red(argb))
            val green = Srgb.toLinear(Argb.green(argb))
            val blue = Srgb.toLinear(Argb.blue(argb))
            val lCone = StrictMath.cbrt(LINEAR_TO_LMS.first.dot(red, green, blue))
            val mCone = StrictMath.cbrt(LINEAR_TO_LMS.second.dot(red, green, blue))
            val sCone = StrictMath.cbrt(LINEAR_TO_LMS.third.dot(red, green, blue))
            return OkLab(
                l = LMS_TO_LAB.first.dot(lCone, mCone, sCone),
                a = LMS_TO_LAB.second.dot(lCone, mCone, sCone),
                b = LMS_TO_LAB.third.dot(lCone, mCone, sCone),
            )
        }

        private fun cube(value: Double): Double = value * value * value
    }

    private class Matrix3(val first: Row, val second: Row, val third: Row)

    private class Row(val x: Double, val y: Double, val z: Double) {
        fun dot(p: Double, q: Double, r: Double): Double = x * p + y * q + z * r
    }
}
