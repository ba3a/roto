package com.roto.core.draft

import kotlinx.serialization.Serializable

/** The most extra frames a key frame may be held. */
const val MAX_HOLD = 8

/**
 * How a clip's draft is finished; stored per clip in `profile.json`. [dither] softens the edges
 * between neighbouring ramp steps with a checkerboard. Off by default: a clean edge is easier to
 * polish by hand than a dithered one is to undo. [hold] is how many extra frames a key frame's draft
 * is kept for by hand-polish aids; 0 adds none.
 *
 * @property dither whether to dither the edges between neighbouring ramp steps
 * @property hold extra frames held per key frame, 0 to [MAX_HOLD]
 */
@Serializable
data class DraftSettings(val dither: Boolean = false, val hold: Int = 0) {
    init {
        require(hold in 0..MAX_HOLD) { "hold must be between 0 and $MAX_HOLD, got $hold" }
    }
}
