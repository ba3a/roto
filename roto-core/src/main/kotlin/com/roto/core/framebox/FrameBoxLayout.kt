package com.roto.core.framebox

import com.roto.core.image.Mask
import com.roto.core.project.Canvas
import com.roto.core.track.RootTrack

/**
 * Lays the canvas over every frame of a clip: scaled by the profile's [ScaleSettings], centred
 * across on each frame's root from the [RootTrack], and with the canvas's ground row on the clip's
 * [ground line][groundY].
 */
object FrameBoxLayout {

    fun of(masks: List<Mask>, track: RootTrack, scale: ScaleSettings, canvas: Canvas): FrameBoxes {
        require(track.shifts.size == masks.size) { "Root track has ${track.shifts.size} frames, masks ${masks.size}" }
        return FrameBoxes(canvas, scale.scaleX, scale.scaleY, groundY(masks), masks.indices.map { track.centreX(it) })
    }

    /**
     * The row a clip's feet stand on: the median of each frame's lowest foreground row. While
     * walking one foot is nearly always down, and the median ignores the few frames where both are
     * up, or where a shadow survived masking under one of them.
     */
    fun groundY(masks: List<Mask>): Int {
        val bottoms = masks.mapNotNull { mask ->
            (mask.height - 1 downTo 0).firstOrNull { y -> (0 until mask.width).any { x -> mask[x, y] } }
        }.sorted()
        require(bottoms.isNotEmpty()) { "No frame shows a figure to stand on the ground" }
        return bottoms[(bottoms.size - 1) / 2]
    }
}
