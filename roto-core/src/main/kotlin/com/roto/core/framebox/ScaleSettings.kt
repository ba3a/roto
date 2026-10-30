package com.roto.core.framebox

import com.roto.core.project.Canvas
import com.roto.core.template.SafeName
import kotlinx.serialization.Serializable

/** The strongest squash: half height. Beyond it a figure no longer reads as standing. */
private const val MIN_SQUASH = 0.5

/**
 * How big a profile's figure is drawn, set once per profile; stored in `profile.json`.
 *
 * The artist marks the top of the head and the soles of the feet on one standing frame, [marks];
 * the figure between them is drawn [bodyHeight] canvas pixels tall. [squash] then shortens it
 * vertically only — `0.85` draws it 85 % as tall at the same width — for the extra top-down feel of a
 * three-quarter view. `1` leaves it unsquashed.
 */
@Serializable
data class ScaleSettings(
    val bodyHeight: Int,
    val marks: ScaleMarks,
    val squash: Double = 1.0,
) {
    init {
        require(bodyHeight >= 1) { "bodyHeight must be at least 1 pixel, got $bodyHeight" }
        require(squash in MIN_SQUASH..1.0) { "squash must be between $MIN_SQUASH and 1, got $squash" }
    }

    /** Canvas pixels per working-frame pixel, across. */
    val scaleX: Double get() = bodyHeight.toDouble() / marks.height

    /** Canvas pixels per working-frame pixel, down: [scaleX] squashed. */
    val scaleY: Double get() = scaleX * squash
}

/** How much of a canvas a figure first fills: the head clear of the top edge, room for a raised arm. */
private const val FIRST_FILL_PERCENT = 85

/** A first guess at a figure's height on [canvas], before the artist tunes it. */
fun proposedBodyHeight(canvas: Canvas): Int = ((canvas.groundRow + 1) * FIRST_FILL_PERCENT / 100).coerceAtLeast(1)

/**
 * Where the artist marked a standing figure: in [frame] of the clip [clip] (its id), the topmost row
 * of the head at [headTopY] and the bottom row of the feet at [feetY], in working-frame pixels.
 */
@Serializable
data class ScaleMarks(val clip: String, val frame: Int, val headTopY: Int, val feetY: Int) {
    init {
        SafeName.validate(clip, "Marked clip")
        require(frame >= 0) { "Marked frame must not be negative, got $frame" }
        require(headTopY in 0 until feetY) { "Head top ($headTopY) must be above the feet ($feetY)" }
    }

    /** The marked figure's height in working-frame pixels, head top and feet rows included. */
    val height: Int get() = feetY - headTopY + 1
}
