package com.roto.core.framebox

import com.roto.core.project.Canvas
import kotlinx.serialization.Serializable

/** Half a pixel: pixel `n` spans `n..n+1`, so its centre is at `n + HALF`. */
private const val HALF = 0.5

/**
 * Where the project's [canvas] sits on each frame of a clip, in working-frame pixels.
 *
 * Canvas and frame are tied at two places: the canvas's horizontal centre sits on the frame's
 * [centreX] — the figure's root, so a walk across stays in the middle — and the bottom edge of the
 * canvas's [ground row][Canvas.groundRow] sits on the bottom edge of [groundY], the row the clip's
 * feet stand on, so the soles fill the ground row however the canvas is scaled. Between them one
 * canvas pixel covers `1 / scaleX` frame pixels across and `1 / scaleY` down.
 */
@Serializable
data class FrameBoxes(
    val canvas: Canvas,
    val scaleX: Double,
    val scaleY: Double,
    val groundY: Int,
    val centreX: List<Int>,
) {
    init {
        require(scaleX > 0.0 && scaleY > 0.0) { "Scales must be positive, got $scaleX and $scaleY" }
        require(centreX.isNotEmpty()) { "Frame boxes need at least one frame" }
    }

    /** The frame x shown at canvas x [canvasX] in [frame]; both continuous, pixel `n` spanning `n..n+1`. */
    fun frameX(frame: Int, canvasX: Double): Double = centreX[frame] + HALF + (canvasX - canvas.width / 2.0) / scaleX

    /** The frame y shown at canvas y [canvasY]; the same in every frame, since the ground never moves. */
    fun frameY(canvasY: Double): Double = groundY + 1 + (canvasY - (canvas.groundRow + 1)) / scaleY
}
