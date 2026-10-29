package com.roto.core.draft

import kotlinx.serialization.Serializable

/**
 * How a clip's draft is finished; stored per clip in `profile.json`. [dither] softens the edges
 * between neighbouring ramp steps with a checkerboard. Off by default: a clean edge is easier to
 * polish by hand than a dithered one is to undo.
 */
@Serializable
data class DraftSettings(val dither: Boolean = false)
