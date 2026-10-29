package com.roto.core.track

import kotlinx.serialization.Serializable

/** The largest smoothing window: about a second of video, longer than any one step. */
private const val MAX_SMOOTHING = 31

/**
 * How a clip's root — the point the figure stands on, horizontally — is found and held still;
 * stored per clip in `profile.json`.
 *
 * - [hipBandTop] and [hipBandBottom] bound the hip band, as fractions of the figure's own height
 *   from the top of its mask. The root is the median x of the mask inside the band.
 * - [smoothing] is the moving-average window, in frames, odd; 1 turns smoothing off.
 * - [stabilise] shifts every frame so the smoothed root stays put: for a walk across the frame. Off,
 *   for a clip performed on the spot, the frames are left where they were filmed.
 */
@Serializable
data class RootSettings(
    val stabilise: Boolean = true,
    val hipBandTop: Double = 0.45,
    val hipBandBottom: Double = 0.60,
    val smoothing: Int = 7,
) {
    init {
        require(hipBandTop >= 0.0 && hipBandTop < hipBandBottom && hipBandBottom <= 1.0) {
            "Hip band must satisfy 0 <= top < bottom <= 1, got $hipBandTop..$hipBandBottom"
        }
        require(smoothing in 1..MAX_SMOOTHING && smoothing % 2 == 1) {
            "smoothing must be an odd number of frames from 1 to $MAX_SMOOTHING, got $smoothing"
        }
    }
}
