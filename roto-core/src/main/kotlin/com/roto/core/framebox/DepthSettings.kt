package com.roto.core.framebox

import kotlinx.serialization.Serializable

/** The largest smoothing window: about a second of video, longer than any one step. */
private const val MAX_SMOOTHING = 61

/**
 * How a clip's figure is kept one size when it walks toward or away from the camera; stored per
 * clip in `profile.json`.
 *
 * Off, the whole profile is drawn at the one scale its marks set, and the feet stay on one ground
 * line: right for a walk across the frame. On, each frame is scaled so the figure's height on the
 * canvas stays the profile's body height, and the ground line follows the feet down or up the frame.
 *
 * Both come from the figure's own mask, smoothed over [smoothing] frames: a walk cycle raises a foot
 * and swings the arms every step, and the average over a cycle or more is what the camera distance
 * really did.
 *
 * @property follow whether to scale each frame to the figure's size and follow its feet
 * @property smoothing the moving-average window in frames, odd; 1 turns smoothing off
 */
@Serializable
data class DepthSettings(
    val follow: Boolean = false,
    val smoothing: Int = 15,
) {
    init {
        require(smoothing in 1..MAX_SMOOTHING && smoothing % 2 == 1) {
            "smoothing must be an odd number of frames from 1 to $MAX_SMOOTHING, got $smoothing"
        }
    }
}
