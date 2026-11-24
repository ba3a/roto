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
 * canvas's [ground row][Canvas.groundRow] sits on the bottom edge of the frame's [groundY], the row the
 * feet stand on, so the soles fill the ground row however the canvas is scaled. Between them one
 * canvas pixel covers `1 / scaleX` frame pixels across and `1 / scaleY` down. All four vary by frame:
 * for a walk across the frame only [centreX] does, for a walk toward or away from the camera the
 * scale and the ground line do too.
 *
 * @property canvas the project canvas being laid over the frames
 * @property scaleX for each frame, canvas pixels per frame pixel across
 * @property scaleY for each frame, canvas pixels per frame pixel down
 * @property groundY for each frame, the working-frame row the feet stand on
 * @property centreX for each frame, the working-frame column the canvas is centred on
 */
@Serializable
data class FrameBoxes(
    val canvas: Canvas,
    val scaleX: List<Double>,
    val scaleY: List<Double>,
    val groundY: List<Int>,
    val centreX: List<Int>,
) {
    init {
        require(centreX.isNotEmpty()) { "Frame boxes need at least one frame" }
        require(scaleX.size == centreX.size && scaleY.size == centreX.size && groundY.size == centreX.size) {
            "Every frame needs a scale and a ground line: ${centreX.size} frames, " +
                "${scaleX.size} and ${scaleY.size} scales, ${groundY.size} ground lines"
        }
        require(scaleX.all { it > 0.0 } && scaleY.all { it > 0.0 }) { "Scales must be positive" }
    }

    /** The frame x shown at canvas x [canvasX] in [frame]; both continuous, pixel `n` spanning `n..n+1`. */
    fun frameX(frame: Int, canvasX: Double): Double =
        centreX[frame] + HALF + (canvasX - canvas.width / 2.0) / scaleX[frame]

    /** The frame y shown at canvas y [canvasY] in [frame]. */
    fun frameY(frame: Int, canvasY: Double): Double =
        groundY[frame] + 1 + (canvasY - (canvas.groundRow + 1)) / scaleY[frame]
}
