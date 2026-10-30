package com.roto.core.mask

import kotlinx.serialization.Serializable

/** The widest brush: about a head at the working height. */
private const val MAX_BRUSH_RADIUS = 64

/** What a stroke does to the mask: paints figure in, or paints it out. */
@Serializable
enum class BrushMode { ADD, ERASE }

/**
 * One stroke of the artist's brush on [frame] of a clip's mask; stored per clip in `profile.json`, in
 * the order painted. It stamps a disc of [radius] pixels along [points], in working-frame pixels, and
 * strokes on one frame apply in that order, so a later one wins where they cross.
 */
@Serializable
data class BrushStroke(
    val frame: Int,
    val mode: BrushMode,
    val radius: Int,
    val points: List<BrushPoint>,
) {
    init {
        require(frame >= 0) { "A stroke's frame must not be negative, got $frame" }
        require(radius in 1..MAX_BRUSH_RADIUS) { "Brush radius must be between 1 and $MAX_BRUSH_RADIUS, got $radius" }
        require(points.isNotEmpty()) { "A stroke needs at least one point" }
    }
}

/** A point of a stroke, in working-frame pixels. */
@Serializable
data class BrushPoint(val x: Int, val y: Int) {
    init {
        require(x >= 0 && y >= 0) { "A stroke's coordinates must not be negative: $this" }
    }
}
