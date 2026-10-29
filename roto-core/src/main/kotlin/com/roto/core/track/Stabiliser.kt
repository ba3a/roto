package com.roto.core.track

import com.roto.core.image.Mask
import kotlin.math.floor

/**
 * Horizontal stabilisation of a clip from its masks: each root [found][RootFinder], gaps filled,
 * [smoothed][TrackSmoothing], and — when stabilising — the shift that moves each smoothed root to
 * their mean, rounded to the nearest whole pixel.
 *
 * Only horizontal motion is taken out. The camera stands on a tripod, so the ground line never moves
 * and the figure's vertical bob stays as filmed.
 */
object Stabiliser {

    fun track(masks: List<Mask>, settings: RootSettings): RootTrack {
        val roots = masks.map { RootFinder.rootX(it, settings) }
        require(roots.any { it != null }) { "No frame shows a figure to track" }
        val smoothed = TrackSmoothing.movingAverage(TrackSmoothing.fillGaps(roots), settings.smoothing)
        val shifts = if (settings.stabilise) {
            val anchor = nearest(smoothed.sum() / smoothed.size)
            smoothed.map { anchor - nearest(it) }
        } else {
            smoothed.map { 0 }
        }
        return RootTrack(roots, smoothed, shifts)
    }

    /** Rounds half up, the same way on every platform. */
    private fun nearest(value: Double): Int = floor(value + 0.5).toInt()
}
