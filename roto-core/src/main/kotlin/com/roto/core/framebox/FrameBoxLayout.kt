package com.roto.core.framebox

import com.roto.core.image.Mask
import com.roto.core.project.Canvas
import com.roto.core.track.RootTrack
import com.roto.core.track.TrackSmoothing
import kotlin.math.roundToInt

/**
 * Lays the canvas over every frame of a clip: scaled by the profile's [ScaleSettings], centred
 * across on each frame's root from the [RootTrack], and with the canvas's ground row on the clip's
 * ground line. A clip whose figure walks toward or away from the camera, per its [DepthSettings],
 * takes its scale and ground line from the figure in each frame instead.
 */
object FrameBoxLayout {

    /**
     * The [FrameBoxes] of a clip's [masks] with its root [track], at [scale], on the project's [canvas],
     * with the figure kept one size as [depth] says.
     */
    fun of(
        masks: List<Mask>,
        track: RootTrack,
        scale: ScaleSettings,
        canvas: Canvas,
        depth: DepthSettings = DepthSettings(),
    ): FrameBoxes {
        require(track.shifts.size == masks.size) { "Root track has ${track.shifts.size} frames, masks ${masks.size}" }
        val centreX = masks.indices.map { track.centreX(it) }
        if (!depth.follow) {
            val scaleX = List(masks.size) { scale.scaleX }
            val scaleY = List(masks.size) { scale.scaleY }
            val ground = groundY(masks)
            return FrameBoxes(canvas, scaleX, scaleY, List(masks.size) { ground }, centreX)
        }
        val extents = smoothedExtents(masks, depth.smoothing)
        val scaleX = extents.map { (top, bottom) -> scale.bodyHeight / (bottom - top + 1.0) }
        return FrameBoxes(
            canvas,
            scaleX,
            scaleX.map { it * scale.squash },
            extents.map { (_, bottom) -> bottom.roundToInt() },
            centreX,
        )
    }

    /**
     * The row a clip's feet stand on: the median of each frame's lowest foreground row. While
     * walking one foot is nearly always down, and the median ignores the few frames where both are
     * up, or where a shadow survived masking under one of them.
     */
    fun groundY(masks: List<Mask>): Int {
        val bottoms = masks.mapNotNull { bottomRow(it) }.sorted()
        require(bottoms.isNotEmpty()) { "No frame shows a figure to stand on the ground" }
        return bottoms[(bottoms.size - 1) / 2]
    }

    /**
     * Each frame's topmost and lowest foreground row, a frame without a figure taking them from its
     * neighbours, then both moving-averaged over [window] frames.
     */
    private fun smoothedExtents(masks: List<Mask>, window: Int): List<Pair<Double, Double>> {
        val tops = TrackSmoothing.fillGaps(masks.map { mask -> topRow(mask)?.toDouble() })
        val bottoms = TrackSmoothing.fillGaps(masks.map { mask -> bottomRow(mask)?.toDouble() })
        return TrackSmoothing.movingAverage(tops, window).zip(TrackSmoothing.movingAverage(bottoms, window))
    }

    private fun topRow(mask: Mask): Int? =
        (0 until mask.height).firstOrNull { y -> (0 until mask.width).any { x -> mask[x, y] } }

    private fun bottomRow(mask: Mask): Int? =
        (mask.height - 1 downTo 0).firstOrNull { y -> (0 until mask.width).any { x -> mask[x, y] } }
}
