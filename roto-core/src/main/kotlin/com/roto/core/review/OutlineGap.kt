package com.roto.core.review

import com.roto.core.image.Argb
import com.roto.core.image.Raster

/**
 * A silhouette-edge pixel (opaque, with a transparent 4-neighbour inside the image) that is not an
 * outline colour: the project's outline colour, else the darkest step of any ramp. Does nothing when
 * the clip's outline is off.
 */
object OutlineGap : LintRule {
    override val name = "OutlineGap"

    private val steps = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)

    override fun check(frames: List<Raster>, context: LintContext): List<LintFinding> {
        if (!context.outline) return emptyList()
        val outlineColours = context.ramps.outline?.let { setOf(it) } ?: context.ramps.ramps.map { it.darkest }.toSet()
        return frames.flatMapIndexed { index, frame ->
            (0 until frame.height).flatMap { y ->
                (0 until frame.width).mapNotNull { x ->
                    if (isOpaque(frame, x, y) && frame[x, y] !in outlineColours && onEdge(frame, x, y)) {
                        LintFinding(name, index, LintFinding.Pixel(x, y), "Edge pixel is not an outline colour")
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun onEdge(frame: Raster, x: Int, y: Int): Boolean = steps.any { (dx, dy) ->
        val nx = x + dx
        val ny = y + dy
        nx in 0 until frame.width && ny in 0 until frame.height && Argb.alpha(frame[nx, ny]) == 0
    }

    private fun isOpaque(frame: Raster, x: Int, y: Int): Boolean = Argb.alpha(frame[x, y]) == OPAQUE

    private const val OPAQUE = 255
}
