package com.roto.core.track

import kotlinx.serialization.Serializable

/**
 * Where a clip's figure stands, frame by frame. [rootX] is the root as found, null where a frame
 * showed no figure; [smoothedX] the gaps filled and the moving average taken; [anchorX] the whole
 * pixel the smoothed roots centre on; [shifts] the whole pixels each frame moves right (left when
 * negative) to hold its smoothed root at [anchorX] — all zero when the clip is not stabilised.
 */
@Serializable
data class RootTrack(
    val rootX: List<Double?>,
    val smoothedX: List<Double>,
    val anchorX: Int,
    val shifts: List<Int>,
) {
    init {
        require(rootX.isNotEmpty()) { "A root track needs at least one frame" }
        require(smoothedX.size == rootX.size && shifts.size == rootX.size) {
            "Root track lists differ in length: ${rootX.size}, ${smoothedX.size}, ${shifts.size}"
        }
    }

    /**
     * Where the figure is held in [frame] as filmed, before shifting: its smoothed root to the whole
     * pixel when stabilised, so a crop centred here follows the figure; [anchorX] in every frame when
     * not, so the crop stays put.
     */
    fun centreX(frame: Int): Int = anchorX - shifts[frame]
}
