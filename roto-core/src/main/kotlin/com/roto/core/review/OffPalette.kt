package com.roto.core.review

import com.roto.core.image.Argb
import com.roto.core.image.Raster

/** An opaque pixel whose colour is not in the project palette. */
object OffPalette : LintRule {
    override val name = "OffPalette"

    override fun check(frames: List<Raster>, context: LintContext): List<LintFinding> =
        frames.flatMapIndexed { index, frame ->
            (0 until frame.height).flatMap { y ->
                (0 until frame.width).mapNotNull { x ->
                    val argb = frame[x, y]
                    if (Argb.alpha(argb) == OPAQUE && argb !in context.palette) {
                        LintFinding(name, index, LintFinding.Pixel(x, y), "Colour ${hex(argb)} is not in the palette")
                    } else {
                        null
                    }
                }
            }
        }

    private const val OPAQUE = 255
    private const val RGB_MASK = 0xFFFFFF

    private fun hex(argb: Int): String = "#%06x".format(argb and RGB_MASK)
}
