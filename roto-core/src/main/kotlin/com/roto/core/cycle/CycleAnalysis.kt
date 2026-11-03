package com.roto.core.cycle

import kotlinx.serialization.Serializable

/**
 * What the cycle stage found in a clip: the silhouette repeating every [repeatFrames] frames with a
 * mean mask overlap of [repeatScore] (both null when the clip does not loop or is too short to
 * repeat), the [contacts] where the feet strike, and the proposed key-frame [markers] as
 * [KeyFrames] boundaries.
 *
 * @property repeatFrames how many frames the silhouette takes to repeat, or null
 * @property repeatScore the mean mask overlap at that repeat, or null
 * @property contacts the frames where the feet strike, in rising order
 * @property markers the proposed key-frame boundaries, in rising order
 */
@Serializable
data class CycleAnalysis(
    val repeatFrames: Int?,
    val repeatScore: Double?,
    val contacts: List<Int>,
    val markers: List<Int>,
) {
    init {
        require((repeatFrames == null) == (repeatScore == null)) { "A repeat comes with its score, or neither" }
        KeyFrames(markers)
    }

    /** The proposed key frames. */
    val keyFrames: KeyFrames get() = KeyFrames(markers)
}
